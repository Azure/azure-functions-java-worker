package com.microsoft.azure.functions.worker;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JavaWorkerClientTest {
    @Test
    public void hostEndpointUsesTheNormalizedHostAndPort() {
        IApplication app = mock(IApplication.class);
        when(app.getHost()).thenReturn("functions.example");
        when(app.getPort()).thenReturn(8443);
        assertEquals(URI.create("https://functions.example:8443"), JavaWorkerClient.buildHostEndpoint(app, true));
        assertEquals(URI.create("http://functions.example:8443"), JavaWorkerClient.buildHostEndpoint(app, false));
    }

    @Test
    public void hostEndpointBracketsIpv6Hosts() {
        IApplication app = mock(IApplication.class);
        when(app.getHost()).thenReturn("::1");
        when(app.getPort()).thenReturn(1234);
        assertEquals(URI.create("http://[::1]:1234"), JavaWorkerClient.buildHostEndpoint(app, false));
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
