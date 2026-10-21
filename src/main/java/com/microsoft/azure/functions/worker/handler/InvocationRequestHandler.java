package com.microsoft.azure.functions.worker.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.logging.*;

import com.microsoft.azure.functions.HttpResponseMessage.IOConsumer;
import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.worker.broker.*;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker.HttpInvocationOutcome;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.http.HttpBodyBridge;
import com.microsoft.azure.functions.worker.http.HttpInvocationCoordinator;
import com.microsoft.azure.functions.worker.http.HttpProxyServer;
import com.microsoft.azure.functions.worker.http.HttpRequestBody;
import com.microsoft.azure.functions.rpc.messages.*;
import com.sun.net.httpserver.HttpExchange;

public class InvocationRequestHandler extends MessageHandler<InvocationRequest, InvocationResponse.Builder> {
    public InvocationRequestHandler(JavaFunctionBroker broker) {
        this(broker, null);
    }

    /**
     * @param httpProxyServer embedded HTTP proxy, or {@code null} when HTTP
     *                        invocations are carried entirely over gRPC
     */
    public InvocationRequestHandler(JavaFunctionBroker broker, HttpProxyServer httpProxyServer) {
        super(StreamingMessage::getInvocationRequest,
              InvocationResponse::newBuilder,
              InvocationResponse.Builder::setResult,
              StreamingMessage.Builder::setInvocationResponse);
        assert broker != null;
        this.broker = broker;
        this.httpProxyServer = httpProxyServer;
        this.invocationLogger = super.getLogger();
    }

    @Override
    Logger getLogger() { return this.invocationLogger; }

    @Override
    String execute(InvocationRequest request, InvocationResponse.Builder response) throws Exception {
        WorkerLogManager.getSystemLogger().log(Level.INFO, "InvocationRequest received by the Java worker");
        final String functionId = request.getFunctionId();
        final String invocationId = request.getInvocationId();

        this.invocationLogger = WorkerLogManager.getInvocationLogger(invocationId);
        response.setInvocationId(invocationId);

        // Proxy-mode envelopes omit method and URL. A legacy host still sends
        // both, even for an empty body, and must not wait for an HTTP handoff.
        if (httpProxyServer != null && httpProxyServer.isRunning()
                && broker.isHttpTrigger(functionId) && hasProxiedHttpInput(request)) {
            return executeProxiedHttp(request, response, functionId, invocationId);
        }

        List<ParameterBinding> outputBindings = new ArrayList<>();
        this.broker.invokeMethod(functionId, request, outputBindings).ifPresent(response::setReturnValue);
        response.addAllOutputData(outputBindings);

        return String.format("Function \"%s\" (Id: %s) invoked by Java Worker",
                this.broker.getMethodName(functionId).orElse("UNKNOWN"), invocationId);
    }

    private String executeProxiedHttp(InvocationRequest request,
                                      InvocationResponse.Builder response,
                                      String functionId,
                                      String invocationId) throws Exception {
        HttpInvocationCoordinator coordinator = httpProxyServer.coordinator();
        try {
            HttpExchange exchange = coordinator.awaitHttpArrival(invocationId);

            // A streaming-input function reads the live request body itself, so the
            // body is not folded into the gRPC request.
            boolean streamingInput = broker.methodHasStreamingHttpBody(functionId);
            InvocationRequest enriched = HttpBodyBridge.enrichRequest(request, exchange, !streamingInput);
            try (HttpRequestBody streamingBody = streamingInput ? new HttpRequestBody(exchange.getRequestBody()) : null) {
                List<ParameterBinding> outputBindings = new ArrayList<>();
                HttpInvocationOutcome outcome =
                        broker.invokeMethodForHttpProxy(functionId, enriched,
                            streamingBody == null ? null : streamingBody.getStream(), outputBindings);
                try {
                    if (streamingBody != null) {
                        streamingBody.prepareForResponse();
                    }
                } catch (IOException ex) {
                    if (outcome.getRawHttpResponseBody() instanceof InputStream) {
                        try {
                            ((InputStream) outcome.getRawHttpResponseBody()).close();
                        } catch (IOException closeFailure) {
                            ex.addSuppressed(closeFailure);
                        }
                    }
                    throw ex;
                }
                writeHttpResponse(exchange, extractHttpResponse(functionId, outcome.getReturnValue(), outputBindings),
                    outcome.getRawHttpResponseBody());
                outcome.getReturnValue().filter(value -> !isHttpOutput(functionId, BindingDataStore.RETURN_NAME, value))
                    .ifPresent(response::setReturnValue);
                outputBindings.stream().filter(binding -> !isHttpOutput(functionId, binding.getName(), binding.getData()))
                    .forEach(response::addOutputData);
            }
            coordinator.releaseInvocation(invocationId);
            return String.format("Function \"%s\" (Id: %s) invoked by Java Worker (HTTP proxy)",
                    this.broker.getMethodName(functionId).orElse("UNKNOWN"), invocationId);
        } catch (Exception | Error ex) {
            coordinator.failInvocation(invocationId, ex);
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw ex;
        }
    }

    /**
     * Writes the HTTP response to the {@code HttpExchange}. If {@code rawBody}
     * is a streaming body ({@link InputStream} or
     * {@link IOConsumer}{@code <OutputStream>}), bypasses the buffered protobuf
     * body and streams directly. Otherwise falls back to the buffered path.
     */
    @SuppressWarnings("unchecked")
    private static void writeHttpResponse(HttpExchange exchange, RpcHttp envelope, Object rawBody) throws IOException {
        if (rawBody instanceof InputStream) {
            HttpBodyBridge.writeStreamingResponse(exchange, envelope, (InputStream) rawBody);
        } else if (rawBody instanceof IOConsumer) {
            HttpBodyBridge.writeStreamingResponse(exchange, envelope, (IOConsumer<OutputStream>) rawBody);
        } else {
            HttpBodyBridge.writeRpcHttpResponse(exchange, envelope);
        }
    }

    private static boolean hasProxiedHttpInput(InvocationRequest request) {
        for (ParameterBinding binding : request.getInputDataList()) {
            if (binding.getData().hasHttp()
                    && binding.getData().getHttp().getMethod().isEmpty()
                    && binding.getData().getHttp().getUrl().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean isHttpOutput(String functionId, String name, TypedData value) {
        return value.hasHttp() || broker.isHttpOutput(functionId, name);
    }

    private RpcHttp extractHttpResponse(String functionId, Optional<TypedData> returnValue,
                                               List<ParameterBinding> outputBindings) {
        if (returnValue.isPresent() && isHttpOutput(functionId, BindingDataStore.RETURN_NAME, returnValue.get())) {
            return asHttpResponse(returnValue.get());
        }
        for (ParameterBinding binding : outputBindings) {
            if (isHttpOutput(functionId, binding.getName(), binding.getData())) {
                return asHttpResponse(binding.getData());
            }
        }
        // No HTTP response binding produced; respond with an empty 200 so the
        // host doesn't see a hung connection.
        return RpcHttp.newBuilder().setStatusCode("200").build();
    }

    private static RpcHttp asHttpResponse(TypedData value) {
        return value.hasHttp() ? value.getHttp() : RpcHttp.newBuilder().setStatusCode("200").setBody(value).build();
    }

    private final JavaFunctionBroker broker;
    private final HttpProxyServer httpProxyServer;
    private Logger invocationLogger;
}
