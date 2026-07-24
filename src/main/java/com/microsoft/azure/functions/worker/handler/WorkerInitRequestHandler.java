package com.microsoft.azure.functions.worker.handler;

import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.http.HttpInvocationCoordinator;
import com.microsoft.azure.functions.worker.http.HttpProxyHandler;
import com.microsoft.azure.functions.worker.http.HttpProxyServer;

import java.io.IOException;
import java.util.logging.Level;

import static com.microsoft.azure.functions.worker.Constants.JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY;
import static com.microsoft.azure.functions.worker.Constants.JAVA_ENABLE_OPENTELEMETRY;

public class WorkerInitRequestHandler extends MessageHandler<WorkerInitRequest, WorkerInitResponse.Builder> {
    public WorkerInitRequestHandler(JavaFunctionBroker broker) {
        this(broker, null, null);
    }

    public WorkerInitRequestHandler(JavaFunctionBroker broker,
                                    HttpProxyServer httpProxyServer,
                                    HttpInvocationCoordinator httpInvocationCoordinator) {
        super(StreamingMessage::getWorkerInitRequest,
              WorkerInitResponse::newBuilder,
              WorkerInitResponse.Builder::setResult,
              StreamingMessage.Builder::setWorkerInitResponse);
        this.broker = broker;
        this.httpProxyServer = httpProxyServer;
        this.httpInvocationCoordinator = httpInvocationCoordinator;
    }

    @Override
    String execute(WorkerInitRequest request, WorkerInitResponse.Builder response) {
        WorkerLogManager.getSystemLogger().log(Level.INFO, "WorkerInitRequest received by the Java worker");
        broker.setWorkerDirectory(request.getWorkerDirectory());
        response.setWorkerVersion(Application.version());
        response.putCapabilities("TypedDataCollection", "TypedDataCollection");
        response.putCapabilities("WorkerStatus", "WorkerStatus");
        response.putCapabilities("RpcHttpBodyOnly", "RpcHttpBodyOnly");
        response.putCapabilities("RpcHttpTriggerMetadataRemoved", "RpcHttpTriggerMetadataRemoved");
        response.putCapabilities("HandlesWorkerTerminateMessage", "HandlesWorkerTerminateMessage");
        response.putCapabilities("HandlesWorkerWarmupMessage", "HandlesWorkerWarmupMessage");

        advertiseHttpProxy(response);

        if (Boolean.parseBoolean(System.getenv(JAVA_ENABLE_OPENTELEMETRY)) ||
                Boolean.parseBoolean(System.getenv(JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY))) {
            response.putCapabilities("WorkerOpenTelemetryEnabled", "true");
            response.putCapabilities("WorkerApplicationInsightsLoggingEnabled", "true");
        }

        response.setWorkerMetadata(composeWorkerMetadata());

        return "Worker initialized";
    }

    private void advertiseHttpProxy(WorkerInitResponse.Builder response) {
        if (httpProxyServer == null || httpInvocationCoordinator == null) {
            return;
        }
        try {
            String uri = httpProxyServer.start(new HttpProxyHandler(httpInvocationCoordinator));
            response.putCapabilities("HttpUri", uri);
            response.putCapabilities("RequiresRouteParameters", "true");
            WorkerLogManager.getSystemLogger().log(Level.INFO,
                "Java worker HTTP proxy listening on " + uri);
        } catch (IOException ex) {
            // Fall back to gRPC-only path: simply do not advertise HttpUri.
            WorkerLogManager.getSystemLogger().log(Level.WARNING,
                "Failed to start HTTP proxy server; continuing without HttpUri capability", ex);
        }
    }

    private WorkerMetadata.Builder composeWorkerMetadata(){
        WorkerMetadata.Builder workerMetadataBuilder = WorkerMetadata.newBuilder();
        workerMetadataBuilder.setRuntimeName("java");
        workerMetadataBuilder.setRuntimeVersion(System.getProperty("java.version"));
        workerMetadataBuilder.setWorkerVersion(Application.version());
        workerMetadataBuilder.setWorkerBitness(System.getProperty("os.arch"));
        return workerMetadataBuilder;
    }

    private final JavaFunctionBroker broker;
    private final HttpProxyServer httpProxyServer;
    private final HttpInvocationCoordinator httpInvocationCoordinator;
}
