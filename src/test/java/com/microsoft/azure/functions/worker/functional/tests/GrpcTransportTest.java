package com.microsoft.azure.functions.worker.functional.tests;

import java.nio.file.Path;
import java.util.concurrent.*;
import javax.net.ssl.*;

import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.test.utilities.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class GrpcTransportTest extends FunctionsTestBase {
    private static final String RETURN_VALUE = "transport-ok";

    public String ReturnStringFunction() {
        return RETURN_VALUE;
    }

    @Test
    public void legacyPlaintextTransportStillWorks() throws Exception {
        try (SkipTestingScope ignored = SkipTestingScope.enable();
             FunctionsTestHost host = new FunctionsTestHost()) {
            InvocationResponse response = this.invokeReturnString(host, "plaintext-function", "plaintext-request");

            assertEquals(TypedData.DataCase.STRING, response.getReturnValue().getDataCase());
            assertEquals(RETURN_VALUE, response.getReturnValue().getString());
        }
    }

    @Test
    public void trustedHttpsFunctionsUriConnectsToTlsHost() throws Exception {
        try (SkipTestingScope ignored = SkipTestingScope.enable();
             TrustStoreScope ignoredTrustStore = TrustStoreScope.use();
             FunctionsTestHost host = new FunctionsTestHost(FunctionsTestHost.ServerTransport.TLS, FunctionsTestHost.ClientTransport.HTTPS)) {
            InvocationResponse response = this.invokeReturnString(host, "tls-function", "tls-request");

            assertEquals(TypedData.DataCase.STRING, response.getReturnValue().getDataCase());
            assertEquals(RETURN_VALUE, response.getReturnValue().getString());
        }
    }

    @Test
    public void httpsFunctionsUriDoesNotDowngradeToPlaintextWhenTlsFails() {
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            try (SkipTestingScope ignored = SkipTestingScope.enable();
                 FunctionsTestHost ignoredHost = new FunctionsTestHost(FunctionsTestHost.ServerTransport.PLAINTEXT, FunctionsTestHost.ClientTransport.HTTPS)) {
            }
        });

        assertTrue(hasCause(exception, SSLException.class), "Expected TLS failure but got: " + exception);
    }

    private InvocationResponse invokeReturnString(FunctionsTestHost host, String functionId, String requestId) throws Exception {
        this.loadFunction(host, functionId, "ReturnStringFunction");
        return host.call(requestId, functionId);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class TrustStoreScope implements AutoCloseable {
        private final String originalTrustStore;
        private final String originalTrustStorePassword;
        private final String originalTrustStoreType;

        private TrustStoreScope(String originalTrustStore, String originalTrustStorePassword, String originalTrustStoreType) {
            this.originalTrustStore = originalTrustStore;
            this.originalTrustStorePassword = originalTrustStorePassword;
            this.originalTrustStoreType = originalTrustStoreType;
        }

        static TrustStoreScope use() {
            String originalTrustStore = System.getProperty("javax.net.ssl.trustStore");
            String originalTrustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
            String originalTrustStoreType = System.getProperty("javax.net.ssl.trustStoreType");

            Path trustStore = TestTlsMaterial.getInstance().trustStorePath();
            System.setProperty("javax.net.ssl.trustStore", trustStore.toAbsolutePath().toString());
            System.setProperty("javax.net.ssl.trustStorePassword", TestTlsMaterial.PASSWORD);
            System.setProperty("javax.net.ssl.trustStoreType", TestTlsMaterial.STORE_TYPE);
            return new TrustStoreScope(originalTrustStore, originalTrustStorePassword, originalTrustStoreType);
        }

        @Override
        public void close() {
            restore("javax.net.ssl.trustStore", this.originalTrustStore);
            restore("javax.net.ssl.trustStorePassword", this.originalTrustStorePassword);
            restore("javax.net.ssl.trustStoreType", this.originalTrustStoreType);
        }

        private static void restore(String key, String value) {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        }
    }

    private static final class SkipTestingScope implements AutoCloseable {
        private static final String SKIP_TESTING_PROPERTY = "azure.functions.worker.java.skip.testing";
        private final String originalValue;

        private SkipTestingScope(String originalValue) {
            this.originalValue = originalValue;
        }

        static SkipTestingScope enable() {
            String originalValue = System.getProperty(SKIP_TESTING_PROPERTY);
            System.setProperty(SKIP_TESTING_PROPERTY, "true");
            return new SkipTestingScope(originalValue);
        }

        @Override
        public void close() {
            if (this.originalValue == null) {
                System.clearProperty(SKIP_TESTING_PROPERTY);
            } else {
                System.setProperty(SKIP_TESTING_PROPERTY, this.originalValue);
            }
        }
    }
}
