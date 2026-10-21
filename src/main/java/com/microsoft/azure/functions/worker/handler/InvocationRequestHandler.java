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
import com.microsoft.azure.functions.worker.http.HttpBodyBridge;
import com.microsoft.azure.functions.worker.http.HttpInvocationCoordinator;
import com.microsoft.azure.functions.worker.http.HttpProxyServer;
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

        // Once the proxy is serving, the host forwards HTTP triggers to it: the gRPC
        // request carries trigger metadata but an empty body, and the body and
        // response travel over the matching HttpExchange.
        if (httpProxyServer != null && httpProxyServer.isRunning() && hasHttpInput(request)) {
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
            HttpExchange exchange = coordinator.slotFor(invocationId).httpArrival().get();

            // A streaming-input function reads the live request body itself, so the
            // body is not folded into the gRPC request.
            boolean streamingInput = broker.methodHasStreamingHttpBody(functionId);
            InvocationRequest enriched = streamingInput
                    ? request
                    : HttpBodyBridge.enrichRequestWithBody(request, exchange);
            InputStream streamingBody = streamingInput ? exchange.getRequestBody() : null;

            List<ParameterBinding> outputBindings = new ArrayList<>();
            HttpInvocationOutcome outcome =
                    broker.invokeMethodForHttpProxy(functionId, enriched, streamingBody, outputBindings);
            outcome.getReturnValue().ifPresent(response::setReturnValue);
            response.addAllOutputData(outputBindings);

            writeHttpResponse(exchange, extractHttpResponse(response, outputBindings), outcome.getRawHttpResponseBody());
            coordinator.releaseInvocation(invocationId);
            return String.format("Function \"%s\" (Id: %s) invoked by Java Worker (HTTP proxy)",
                    this.broker.getMethodName(functionId).orElse("UNKNOWN"), invocationId);
        } catch (Exception | Error ex) {
            coordinator.failInvocation(invocationId, ex);
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

    private static boolean hasHttpInput(InvocationRequest request) {
        for (ParameterBinding binding : request.getInputDataList()) {
            if (binding.getData().hasHttp()) {
                return true;
            }
        }
        return false;
    }

    private static RpcHttp extractHttpResponse(InvocationResponse.Builder response,
                                               List<ParameterBinding> outputBindings) {
        if (response.hasReturnValue() && response.getReturnValue().hasHttp()) {
            return response.getReturnValue().getHttp();
        }
        for (ParameterBinding binding : outputBindings) {
            if (binding.getData().hasHttp()) {
                return binding.getData().getHttp();
            }
        }
        // No HTTP response binding produced; respond with an empty 200 so the
        // host doesn't see a hung connection.
        return RpcHttp.newBuilder().setStatusCode("200").build();
    }

    private final JavaFunctionBroker broker;
    private final HttpProxyServer httpProxyServer;
    private Logger invocationLogger;
}
