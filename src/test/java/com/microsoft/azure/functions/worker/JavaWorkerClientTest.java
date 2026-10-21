package com.microsoft.azure.functions.worker;

import org.junit.jupiter.api.Test;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JavaWorkerClientTest {
    @Test
    public void sdkEndpointUsesFunctionsUriUnmodified() {
        IApplication app = mock(IApplication.class);
        when(app.getFunctionsUri()).thenReturn("https://functions.example:8443");
        assertEquals(URI.create("https://functions.example:8443"), JavaWorkerClient.getHostEndpoint(app));
    }

    @Test
    public void sdkEndpointUsesLegacyHostAndPortIncludingIpv6() {
        IApplication app = mock(IApplication.class);
        when(app.getHost()).thenReturn("::1");
        when(app.getPort()).thenReturn(1234);
        assertEquals(URI.create("http://[::1]:1234"), JavaWorkerClient.getHostEndpoint(app));
    }

    @Test
    public void missingEndpointDoesNotPreventMessageOnlyBindings() {
        assertNull(JavaWorkerClient.getHostEndpoint(mock(IApplication.class)));
    }

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
    public void httpsFunctionsUriSchemeIsCaseInsensitive() {
        assertTrue(JavaWorkerClient.useTransportSecurity("HTTPS://functions.example:8443"));
    }
}
