package com.microsoft.azure.functions.worker.opentelemetry.tests;

import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.handler.FunctionLoadRequestHandler;
import com.microsoft.azure.functions.worker.description.FunctionMethodDescriptor;
import com.microsoft.azure.functions.worker.reflect.FactoryClassLoader;
import com.microsoft.azure.functions.worker.chain.OpenTelemetryInvocationMiddleware;
import com.microsoft.azure.functions.worker.chain.InvocationChainFactory;
import com.microsoft.azure.functions.rpc.messages.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves that the worker adds (or omits) OpenTelemetryInvocationMiddleware
 * solely based on the JAVA_ENABLE_OPENTELEMETRY environment variable.
 */
class OtelMiddlewarePresenceTest {

    /*───────────────────────────────────────────────────────────────────*/
    /*  helpers to patch   JAVA_ENABLE_OPENTELEMETRY   for one test run */
    /*───────────────────────────────────────────────────────────────────*/
    private static Map<String,String> originalEnv;

    @BeforeAll
    static void takeEnvSnapshot() { originalEnv = new HashMap<>(System.getenv()); }

    @AfterEach
    void restoreEnv() throws Exception { setEnv(originalEnv); }

    @SuppressWarnings({"unchecked","JavaReflectionMemberAccess"})
    private static void setEnv(Map<String,String> newEnv) throws Exception {

        // 1) rewrite the ProcessEnvironment maps (works on both Linux & Windows)
        Class<?> pe = Class.forName("java.lang.ProcessEnvironment");

        try {                                   // present on Windows
            Field f = pe.getDeclaredField("theCaseInsensitiveEnvironment");
            f.setAccessible(true);
            Map<String,String> cienv = (Map<String,String>) f.get(null);
            cienv.clear();                     // <- remove previous content
            cienv.putAll(newEnv);              // <- add new entries
        } catch (NoSuchFieldException ignore) { }

        try {                                   // present on every OS
            Field f = pe.getDeclaredField("theEnvironment");
            f.setAccessible(true);
            Map<String,String> env = (Map<String,String>) f.get(null);
            env.clear();
            env.putAll(newEnv);
        } catch (NoSuchFieldException ignore) { }

        // 2) also rewrite the wrapper returned by System.getenv()
        Class<?> cl = Class.forName("java.util.Collections$UnmodifiableMap");
        Field m = cl.getDeclaredField("m");
        m.setAccessible(true);
        Map<String,String> map = (Map<String,String>) m.get(System.getenv());
        map.clear();
        map.putAll(newEnv);
    }

    /*───────────────────────────────────────────────────────────────────*/
    /*              Parameterised over “true | false”                   */
    /*───────────────────────────────────────────────────────────────────*/
    @ParameterizedTest(name = "otelEnabled={0}")
    @ValueSource(booleans = {false, true})
    void middlewarePresenceMatchesFlag(boolean otelEnabled) throws Exception {

        /* 1) mutate env for this test-run */
        Map<String,String> mutated = new HashMap<>(originalEnv);
        mutated.put("JAVA_ENABLE_OPENTELEMETRY", Boolean.toString(otelEnabled));
        setEnv(mutated);

        /* 2) spin-up a real broker & load a trivial function */
        JavaFunctionBroker broker =
                new JavaFunctionBroker(new FactoryClassLoader()
                        .createClassLoaderProvider());

        String   functionId  = UUID.randomUUID().toString();
        String   scriptFile  = Objects.requireNonNull(
                        Dummy.class.getProtectionDomain()
                                .getCodeSource()
                                .getLocation())
                .getPath();                       // target/test-classes
        String   entryPoint  = Dummy.class.getName() + ".run";

        RpcFunctionMetadata meta = RpcFunctionMetadata.newBuilder()
                .setName("Dummy")
                .setEntryPoint(entryPoint)
                .setScriptFile(scriptFile)
                .build();

        broker.loadMethod(
                new FunctionMethodDescriptor(
                        functionId,               // id
                        meta.getName(),           // name
                        meta.getEntryPoint(),     // fullMethod
                        meta.getScriptFile(),     // jarPath
                        false                     // isWarmup
                ),
                meta.getBindingsMap());           // usually empty for this test

        /* 3) obtain the InvocationChainFactory the same way the runtime does */
        List<?> middlewareList = middlewareFromBroker(broker, functionId);

        boolean present = middlewareList.stream()
                .anyMatch(mw -> mw.getClass() == OpenTelemetryInvocationMiddleware.class);

        if (otelEnabled) {
            assertTrue(present,
                    "JAVA_ENABLE_OPENTELEMETRY=true ⇒ middleware must be present");
        } else {
            assertFalse(present,
                    "JAVA_ENABLE_OPENTELEMETRY=false ⇒ middleware must be absent");
        }
    }

    /*───────────────────────────────────────────────────────────────────*/
    /*  Reflection helper – works for both global & per-function chain  */
    /*───────────────────────────────────────────────────────────────────*/
    @SuppressWarnings("unchecked")
    private static List<?> middlewareFromBroker(JavaFunctionBroker broker,
                                                String functionId)
            throws Exception {

        // case 1: SDK-types disabled → single global factory
        try {
            Field f = JavaFunctionBroker.class
                    .getDeclaredField("invocationChainFactory");
            f.setAccessible(true);
            InvocationChainFactory factory = (InvocationChainFactory) f.get(broker);
            if (factory != null) {                       // global path active
                Field m = InvocationChainFactory.class.getDeclaredField("middlewares");
                m.setAccessible(true);
                return (List<?>) m.get(factory);
            }
        } catch (NoSuchFieldException ignored) { }

        // case 2: SDK-types enabled → per-function factories
        Field mapField = JavaFunctionBroker.class
                .getDeclaredField("functionFactories");
        mapField.setAccessible(true);
        Map<String, InvocationChainFactory> map =
                (Map<String, InvocationChainFactory>) mapField.get(broker);

        InvocationChainFactory fnFactory = map.get(functionId);
        Field m = InvocationChainFactory.class.getDeclaredField("middlewares");
        m.setAccessible(true);
        return (List<?>) m.get(fnFactory);
    }

    /*───────────────────────────────────────────────────────────────────*/
    /*         A do-nothing function used only for the test             */
    /*───────────────────────────────────────────────────────────────────*/
    public static class Dummy { public static void run() { } }
}
