package com.microsoft.azure.functions.worker.handler;

import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.http.HttpProxyServer;

import java.io.IOException;
import java.util.logging.Level;

import static com.microsoft.azure.functions.worker.Constants.JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY;
import static com.microsoft.azure.functions.worker.Constants.JAVA_ENABLE_OPENTELEMETRY;

public class WorkerInitRequestHandler extends MessageHandler<WorkerInitRequest, WorkerInitResponse.Builder> {
    public WorkerInitRequestHandler(JavaFunctionBroker broker) {
        this(broker, null);
    }

    /**
     * @param httpProxyServer embedded HTTP proxy to start and advertise via the
     *                        {@code HttpUri} capability, or {@code null} to
     *                        keep all HTTP traffic on the gRPC path
     */
    public WorkerInitRequestHandler(JavaFunctionBroker broker, HttpProxyServer httpProxyServer) {
        super(StreamingMessage::getWorkerInitRequest,
              WorkerInitResponse::newBuilder,
              WorkerInitResponse.Builder::setResult,
              StreamingMessage.Builder::setWorkerInitResponse);
        this.broker = broker;
        this.httpProxyServer = httpProxyServer;
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
        if (httpProxyServer == null) {
            return;
        }
        try {
            response.putCapabilities("HttpUri", httpProxyServer.start());
            response.putCapabilities("RequiresRouteParameters", "true");
        } catch (IOException ex) {
            // Without the capability the host keeps using the gRPC-only path.
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
}
