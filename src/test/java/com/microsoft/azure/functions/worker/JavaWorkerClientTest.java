package com.microsoft.azure.functions.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaWorkerClientTest {
    @Test
    public void legacyStartupDefaultsToPlaintextTransport() {
        assertFalse(JavaWorkerClient.useTransportSecurity(null));
    }

    @Test
    public void httpFunctionsUriUsesPlaintextTransport() {
        assertFalse(JavaWorkerClient.useTransportSecurity("http://functions.example:7071"));
    }

    @Test
    public void httpsFunctionsUriUsesTransportSecurity() {
        assertTrue(JavaWorkerClient.useTransportSecurity("https://functions.example:8443"));
    }

    @Test
    public void unsupportedFunctionsUriSchemeFailsFast() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> JavaWorkerClient.useTransportSecurity("unix:///tmp/functions.sock"));

        assertTrue(exception.getMessage().contains("Only http and https are supported."));
    }
}
