package com.microsoft.azure.functions.worker;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApplicationTest {
    @Test
    public void legacyImplementationsDefaultFunctionsUriToNull() {
        IApplication application = new LegacyApplication();

        assertNull(application.getFunctionsUri());
    }

    @Test
    public void prefixedStartupArgumentsTakePrecedenceOverLegacyValues() throws Exception {
        Application application = createApplication(
                "--functions-uri", "https://functions.example:8443",
                "--host", "legacy.example",
                "--port", "7071",
                "--functions-worker-id", "prefixed-worker",
                "--workerId", "legacy-worker",
                "--functions-request-id", "prefixed-request",
                "--requestId", "legacy-request",
                "--functions-grpc-max-message-length", "2048",
                "--grpcMaxMessageLength", "1024");

        assertTrue(isCommandLineValid(application));
        assertEquals("https://functions.example:8443", application.getFunctionsUri());
        assertEquals("functions.example", application.getHost());
        assertEquals(8443, application.getPort());
        assertEquals(Integer.valueOf(2048), application.getMaxMessageSize());
        assertEquals("prefixed-worker", invokePrivateStringAccessor(application, "getWorkerId"));
        assertEquals("prefixed-request", invokePrivateStringAccessor(application, "getRequestId"));
    }

    @Test
    public void legacyHostAndPortRemainAvailableWhenFunctionsUriIsAbsent() throws Exception {
        Application application = createApplication(
                "--host", "localhost",
                "--port", "7001",
                "--workerId", "legacy-worker",
                "--requestId", "legacy-request",
                "--grpcMaxMessageLength", "4096");

        assertTrue(isCommandLineValid(application));
        assertNull(application.getFunctionsUri());
        assertEquals("localhost", application.getHost());
        assertEquals(7001, application.getPort());
        assertEquals(Integer.valueOf(4096), application.getMaxMessageSize());
    }

    @Test
    public void httpsFunctionsUriDefaultsToPort443WhenPortIsMissing() throws Exception {
        Application application = createApplication(
                "--functions-uri", "https://functions.example/path",
                "--functions-worker-id", "prefixed-worker",
                "--functions-request-id", "prefixed-request",
                "--functions-grpc-max-message-length", "2048");

        assertTrue(isCommandLineValid(application));
        assertEquals("functions.example", application.getHost());
        assertEquals(443, application.getPort());
    }

    @Test
    public void httpFunctionsUriDefaultsToPort80WhenPortIsMissing() throws Exception {
        Application application = createApplication(
                "--functions-uri", "http://functions.example/path",
                "--functions-worker-id", "prefixed-worker",
                "--functions-request-id", "prefixed-request",
                "--functions-grpc-max-message-length", "2048");

        assertTrue(isCommandLineValid(application));
        assertEquals("functions.example", application.getHost());
        assertEquals(80, application.getPort());
    }

    @Test
    public void unsupportedFunctionsUriSchemeFailsCommandLineValidation() throws Exception {
        Application application = createApplication(
                "--functions-uri", "unix:///tmp/functions.sock",
                "--functions-worker-id", "prefixed-worker",
                "--functions-request-id", "prefixed-request",
                "--functions-grpc-max-message-length", "2048");

        assertFalse(isCommandLineValid(application));
    }

    private static Application createApplication(String... args) throws Exception {
        Constructor<Application> constructor = Application.class.getDeclaredConstructor(String[].class);
        constructor.setAccessible(true);
        return constructor.newInstance((Object) args);
    }

    private static boolean isCommandLineValid(Application application) throws Exception {
        Method method = Application.class.getDeclaredMethod("isCommandlineValid");
        method.setAccessible(true);
        return (boolean) method.invoke(application);
    }

    private static String invokePrivateStringAccessor(Application application, String methodName) throws Exception {
        Method method = Application.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (String) method.invoke(application);
    }

    private static final class LegacyApplication implements IApplication {
        @Override
        public boolean logToConsole() {
            return false;
        }

        @Override
        public String getHost() {
            return "localhost";
        }

        @Override
        public int getPort() {
            return 7071;
        }

        @Override
        public Integer getMaxMessageSize() {
            return null;
        }
    }
}
