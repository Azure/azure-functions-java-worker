package com.microsoft.azure.functions.worker.functional.tests;

import java.net.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.test.utilities.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class GrpcTransportTest extends FunctionsTestBase {
    private static final String RETURN_VALUE = "transport-ok";
    private static final String TRUSTSTORE_RESOURCE = "grpc-tls/localhost-truststore.p12";
    private static final String TRUSTSTORE_PASSWORD = "changeit";

    public String ReturnStringFunction() {
        return RETURN_VALUE;
    }

    @Test
    public void legacyPlaintextTransportStillWorks() throws Exception {
        System.setProperty("azure.functions.worker.java.skip.testing", "true");
        try (FunctionsTestHost host = new FunctionsTestHost()) {
            InvocationResponse response = this.invokeReturnString(host, "plaintext-function", "plaintext-request");

            assertEquals(TypedData.DataCase.STRING, response.getReturnValue().getDataCase());
            assertEquals(RETURN_VALUE, response.getReturnValue().getString());
        }
    }

    @Test
    public void trustedHttpsFunctionsUriConnectsToTlsHost() throws Exception {
        System.setProperty("azure.functions.worker.java.skip.testing", "true");
        try (TrustStoreScope ignored = TrustStoreScope.use(TRUSTSTORE_RESOURCE, TRUSTSTORE_PASSWORD, "PKCS12");
             FunctionsTestHost host = new FunctionsTestHost(FunctionsTestHost.ServerTransport.TLS, FunctionsTestHost.ClientTransport.HTTPS)) {
            InvocationResponse response = this.invokeReturnString(host, "tls-function", "tls-request");

            assertEquals(TypedData.DataCase.STRING, response.getReturnValue().getDataCase());
            assertEquals(RETURN_VALUE, response.getReturnValue().getString());
        }
    }

    @Test
    public void httpsFunctionsUriDoesNotDowngradeToPlaintextWhenTlsFails() {
        System.setProperty("azure.functions.worker.java.skip.testing", "true");
        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> {
                    try (FunctionsTestHost ignored = new FunctionsTestHost(FunctionsTestHost.ServerTransport.PLAINTEXT, FunctionsTestHost.ClientTransport.HTTPS)) {
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

        static TrustStoreScope use(String resourceName, String password, String storeType) {
            String originalTrustStore = System.getProperty("javax.net.ssl.trustStore");
            String originalTrustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
            String originalTrustStoreType = System.getProperty("javax.net.ssl.trustStoreType");
            URL resource = GrpcTransportTest.class.getClassLoader().getResource(resourceName);
            if (resource == null) {
                throw new IllegalStateException("Missing TLS truststore resource: " + resourceName);
            }

            try {
                System.setProperty("javax.net.ssl.trustStore", new java.io.File(resource.toURI()).getAbsolutePath());
            } catch (URISyntaxException ex) {
                throw new IllegalStateException("Invalid TLS truststore resource path: " + resourceName, ex);
            }
            System.setProperty("javax.net.ssl.trustStorePassword", password);
            System.setProperty("javax.net.ssl.trustStoreType", storeType);
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
}
