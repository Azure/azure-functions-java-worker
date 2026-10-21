package com.microsoft.azure.functions.worker.broker;

import com.google.protobuf.ByteString;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;
import com.microsoft.azure.functions.rpc.messages.CollectionModelBindingData;
import com.microsoft.azure.functions.rpc.messages.ModelBindingData;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.sdktype.SdkParameterAnalysisResult;
import com.microsoft.azure.functions.sdktype.SdkParameterAnalyzer;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.cache.WorkerObjectCache;
import com.microsoft.azure.functions.worker.chain.SdkTypeMiddleware;
import com.microsoft.azure.functions.worker.reflect.EnhancedClassLoaderProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

@EnabledIfSystemProperty(named = "servicebus.sample.root", matches = ".+")
class ServiceBusSampleIntegrationTest {
    @Test
    void packagedSampleUsesSharedSpiAndHydratesNativeSingleBatchAndActions() throws Exception {
        File root = new File(System.getProperty("servicebus.sample.root"));
        verifyMetadata(root, "ServiceBusSdkManualSettlement", "message", false);
        verifyMetadata(root, "ServiceBusSdkBatch", "messages", true);
        EnhancedClassLoaderProvider provider = new EnhancedClassLoaderProvider();
        provider.addCustomerUrl(new File(root, "servicebus-sdk-binding-1.0-SNAPSHOT.jar").toURI().toURL());
        File[] libraries = new File(root, "lib").listFiles(file -> file.getName().endsWith(".jar"));
        assertNotNull(libraries, "Package the sample before running this integration test");
        for (File library : libraries) {
            assertFalse(library.getName().startsWith("azure-functions-java-spi-"));
            assertFalse(library.getName().startsWith("azure-functions-java-sdktypes-"));
            provider.addCustomerUrl(library.toURI().toURL());
        }
        ClassLoader loader = provider.createClassLoader();
        assertSame(SdkInvocationContext.class, loader.loadClass(SdkInvocationContext.class.getName()));
        Class<?> functions = loader.loadClass("com.microsoft.azure.functions.samples.ServiceBusSdkFunctions");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);
            for (String methodName : Arrays.asList("receive", "receiveBatch")) {
                Method method = Arrays.stream(functions.getMethods())
                        .filter(candidate -> candidate.getName().equals(methodName)).findFirst().get();
                boolean batch = methodName.equals("receiveBatch");
                String bindingName = batch ? "messages" : "message";
                TypedData data = batch
                        ? TypedData.newBuilder().setCollectionModelBindingData(CollectionModelBindingData.newBuilder()
                                .addModelBindingData(envelope()).addModelBindingData(envelope())).build()
                        : TypedData.newBuilder().setModelBindingData(envelope()).build();
                List<ParameterBinding> inputs = Collections.singletonList(ParameterBinding.newBuilder()
                        .setName(bindingName).setData(data).build());
                BindingDataStore store = new BindingDataStore();
                store.addParameterSources(inputs);
                ExecutionContextDataSource context;
                try (MockedStatic<WorkerLogManager> logs = mockStatic(WorkerLogManager.class)) {
                    logs.when(() -> WorkerLogManager.getInvocationLogger(methodName))
                            .thenReturn(Logger.getAnonymousLogger());
                    context = new ExecutionContextDataSource(methodName, null, null, methodName, store,
                            new MethodBindInfo(method), functions, inputs, null, new WorkerObjectCache<>(),
                            URI.create("http://localhost:1234"));
                }
                try (com.microsoft.azure.functions.worker.binding.WorkerSdkInvocationContext scope =
                             context.getSdkInvocationContext()) {
                    SdkParameterAnalyzer analyzer = new SdkParameterAnalyzer();
                    SdkParameterAnalysisResult analysis = analyzer.analyze(method);
                    assertEquals(batch ? 1 : 2, analysis.getSdkTypesMetaData().size());
                    new SdkTypeMiddleware(loader, analysis.getSdkTypesMetaData(), analyzer.getRegistry())
                            .invoke(context, next -> { });
                    Parameter messageParameter = method.getParameters()[0];
                    Object value = context.getSdkParameterValue(messageParameter).get().getValue();
                    Object message = batch ? ((Object[]) value)[0] : value;
                    if (batch) {
                        assertEquals(2, ((Object[]) value).length);
                    } else {
                        Object actions = context.getSdkParameterValue(method.getParameters()[1]).get().getValue();
                        assertEquals("com.microsoft.azure.functions.servicebus.ServiceBusMessageActions",
                                actions.getClass().getName());
                    }
                    assertEquals("com.azure.messaging.servicebus.ServiceBusReceivedMessage",
                            message.getClass().getName());
                    assertSame(loader, message.getClass().getClassLoader());
                    assertEquals("00112233-4455-6677-8899-aabbccddeeff",
                            message.getClass().getMethod("getLockToken").invoke(message));
                    Object body = message.getClass().getMethod("getBody").invoke(message);
                    assertArrayEquals(new byte[] {97, 98, 99},
                            (byte[]) body.getClass().getMethod("toBytes").invoke(body));
                }
                assertFalse(context.getSdkInvocationContext().isActive());
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static void verifyMetadata(File root, String function, String bindingName, boolean batch)
            throws Exception {
        String json = new String(Files.readAllBytes(new File(root, function + File.separator + "function.json")
                .toPath()), StandardCharsets.UTF_8);
        JsonObject metadata = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(1, metadata.getAsJsonArray("bindings").size(), "Actions must not create another binding");
        JsonObject trigger = metadata.getAsJsonArray("bindings").get(0).getAsJsonObject();
        assertEquals("serviceBusTrigger", trigger.get("type").getAsString());
        assertEquals(bindingName, trigger.get("name").getAsString());
        assertTrue(trigger.has("properties"), function + " must request raw SDK envelopes");
        JsonObject properties = trigger.getAsJsonObject("properties");
        assertTrue(properties.has("supportsDeferredBinding"), function + " must request raw SDK envelopes");
        assertTrue(properties.get("supportsDeferredBinding").getAsBoolean());
        if (batch) {
            assertEquals("many", trigger.get("cardinality").getAsString().toLowerCase(java.util.Locale.ROOT));
        } else {
            assertTrue(trigger.get("autoCompleteMessages").getAsJsonPrimitive().isBoolean());
            assertFalse(trigger.get("autoCompleteMessages").getAsBoolean());
        }
    }

    private static ModelBindingData envelope() {
        byte[] bytes = new byte[] {0x33, 0x22, 0x11, 0, 0x55, 0x44, 0x77, 0x66,
                (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd,
                (byte) 0xee, (byte) 0xff, 0, 0x53, 0x75, (byte) 0xa0, 3, 97, 98, 99};
        return ModelBindingData.newBuilder().setVersion("1.0").setSource("AzureServiceBusReceivedMessage")
                .setContentType("application/octet-stream").setContent(ByteString.copyFrom(bytes)).build();
    }
}
