package com.microsoft.azure.functions.worker.opentelemetry;
import com.azure.monitor.opentelemetry.autoconfigure.AzureMonitorAutoConfigure;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import java.util.logging.*;

public final class OpenTelemetryInitializer {
    private static boolean initialized = false;
    private static boolean already = false;


    public static synchronized void initialize() {
        if (initialized) return;

//        String cs = System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING");
//        //String cs = "InstrumentationKey=2048d270-b7e7-4076-8845-3f4b53ddbadf;IngestionEndpoint=https://eastus-8.in.applicationinsights.azure.com/;LiveEndpoint=https://eastus.livediagnostics.monitor.azure.com/;ApplicationId=30a5e6b4-7e83-4b6b-b7b9-4b239f8200ad";
//
//        Logger root = LogManager.getLogManager().getLogger("");
//        root.setLevel(Level.FINE);
//        for (Handler h : root.getHandlers()) {
//            if (h instanceof ConsoleHandler) {
//                already = true;
//                break;
//            }
//        }
//        if (!already) {
//            ConsoleHandler ch = new ConsoleHandler();
//            ch.setLevel(Level.FINE);                     // LoggingSpanExporter logs at INFO
//            root.addHandler(ch);
//        }
//
//        // 1. Create an auto-config builder
//        AutoConfiguredOpenTelemetrySdkBuilder sdkBuilder =
//                AutoConfiguredOpenTelemetrySdk.builder();
//
//        // 2. Let azure-monitor-autoconfigure add the AI exporter
//        //    (If you have APPLICATIONINSIGHTS_CONNECTION_STRING set in ENV,
//        //     you can just call AzureMonitorAutoConfigure.customize(sdkBuilder).)
//        AzureMonitorAutoConfigure.customize(sdkBuilder, cs);
//
//        sdkBuilder.addTracerProviderCustomizer(
//                (b,c) -> b.addSpanProcessor(
//                        SimpleSpanProcessor.create(LoggingSpanExporter.create())));
//
//        // 3. Optionally add your own Resource attributes
//        // Merge default Resource with your FunctionsResourceDetector
//        sdkBuilder.addResourceCustomizer((resource, config) ->
//                resource.merge(FunctionsResourceDetector.getResource()));
//
////        sdkBuilder.addSpanProcessorCustomizer(((spanProcessor, configProperties) ->
////                spanProcessor.));
//
//        // 4. (Optional) Add another exporter (e.g. OTLP, Logging)
//        // sdkBuilder.addTracerProviderCustomizer((tracerProviderBuilder, config) -> {
//        //     SpanExporter otlp = OtlpGrpcSpanExporter.builder().build();
//        //     tracerProviderBuilder.addSpanProcessor(
//        //         BatchSpanProcessor.builder(otlp).build()
//        //     );
//        // });
//
//        // 5. Build the final autoconfigured OTel SDK
//        AutoConfiguredOpenTelemetrySdk autoSdk = sdkBuilder.build();
//
//        // 6. Register as global if you want
//        GlobalOpenTelemetry.set(autoSdk.getOpenTelemetrySdk());

        initialized = true;
    }
}
