package com.microsoft.azure.functions.worker.handler;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.*;

import com.microsoft.azure.functions.HttpResponseMessage.IOConsumer;
import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.worker.broker.*;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker.HttpInvocationOutcome;
import com.microsoft.azure.functions.worker.http.HttpBodyBridge;
import com.microsoft.azure.functions.worker.http.HttpInvocationCoordinator;
import com.microsoft.azure.functions.worker.http.HttpInvocationSlot;
import com.microsoft.azure.functions.rpc.messages.*;
import com.sun.net.httpserver.HttpExchange;

public class InvocationRequestHandler extends MessageHandler<InvocationRequest, InvocationResponse.Builder> {
    public InvocationRequestHandler(JavaFunctionBroker broker) {
        this(broker, null);
    }

    public InvocationRequestHandler(JavaFunctionBroker broker, HttpInvocationCoordinator httpInvocationCoordinator) {
        super(StreamingMessage::getInvocationRequest,
              InvocationResponse::newBuilder,
              InvocationResponse.Builder::setResult,
              StreamingMessage.Builder::setInvocationResponse);
        assert broker != null;
        this.broker = broker;
        this.httpInvocationCoordinator = httpInvocationCoordinator;
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

        // For HTTP-triggered invocations dispatched via the HttpUri capability, the
        // gRPC request carries trigger metadata but an empty body. We rendezvous
        // with the HTTP arrival via the coordinator, fold the body bytes back into
        // the request, and write the response to the held HttpExchange.
        if (httpInvocationCoordinator != null && hasHttpInput(request)) {
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
        HttpInvocationSlot slot = httpInvocationCoordinator.registerGrpcArrival(request);
        HttpExchange exchange = null;
        try {
            try {
                exchange = slot.httpArrival().get();
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                throw asException(cause);
            }
            InvocationRequest enriched = HttpBodyBridge.enrichRequestWithBody(request, exchange);
            List<ParameterBinding> outputBindings = new ArrayList<>();
            HttpInvocationOutcome outcome = this.broker.invokeMethodForHttpProxy(functionId, enriched, outputBindings);
            outcome.getReturnValue().ifPresent(response::setReturnValue);
            response.addAllOutputData(outputBindings);
            RpcHttp httpEnvelope = extractHttpResponse(response, outputBindings);
            writeHttpResponse(exchange, httpEnvelope, outcome.getRawHttpResponseBody());
            httpInvocationCoordinator.releaseInvocation(invocationId);
            return String.format("Function \"%s\" (Id: %s) invoked by Java Worker (HTTP proxy)",
                    this.broker.getMethodName(functionId).orElse("UNKNOWN"), invocationId);
        } catch (Throwable t) {
            httpInvocationCoordinator.failInvocation(invocationId, t);
            throw asException(t);
        }
    }

    /**
     * Writes the HTTP response to the {@code HttpExchange}. If {@code rawBody}
     * is a streaming body ({@link InputStream} or
     * {@link IOConsumer}{@code <OutputStream>}), bypasses the buffered protobuf
     * body and streams directly. Otherwise falls back to the buffered path.
     */
    @SuppressWarnings("unchecked")
    private static void writeHttpResponse(HttpExchange exchange, RpcHttp envelope, Object rawBody) throws Exception {
        if (rawBody instanceof InputStream) {
            HttpBodyBridge.writeStreamingResponse(exchange, envelope, (InputStream) rawBody);
            return;
        }
        if (rawBody instanceof IOConsumer) {
            HttpBodyBridge.writeStreamingResponse(exchange, envelope, (IOConsumer<java.io.OutputStream>) rawBody);
            return;
        }
        HttpBodyBridge.writeRpcHttpResponse(exchange, envelope);
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

    private static Exception asException(Throwable t) {
        if (t instanceof Exception) {
            return (Exception) t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        return new RuntimeException(t);
    }

    private final JavaFunctionBroker broker;
    private final HttpInvocationCoordinator httpInvocationCoordinator;
    private Logger invocationLogger;
}
