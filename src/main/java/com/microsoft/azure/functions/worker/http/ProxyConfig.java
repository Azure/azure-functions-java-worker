package com.microsoft.azure.functions.worker.http;

import java.util.Objects;

/**
 * Configuration for the embedded HTTP proxy server used to receive HTTP-triggered
 * invocations directly from the Functions host (HttpUri capability).
 *
 * <p>The configuration deliberately does not impose request body size limits or
 * per-request timeouts. The Functions front-end (nginx) enforces an upstream
 * ceiling, and per-worker overload is managed by the platform — matching the
 * behavior of the Go, Python, and .NET isolated workers.</p>
 */
public final class ProxyConfig {
    /** Loopback bind address. Other workers also bind to 127.0.0.1 only. */
    public static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";

    /** Ephemeral port. The OS picks an unused port at bind time. */
    public static final int DEFAULT_BIND_PORT = 0;

    private final String bindAddress;
    private final int bindPort;

    public ProxyConfig(String bindAddress, int bindPort) {
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        if (bindPort < 0 || bindPort > 65535) {
            throw new IllegalArgumentException("bindPort out of range: " + bindPort);
        }
        this.bindPort = bindPort;
    }

    public static ProxyConfig defaults() {
        return new ProxyConfig(DEFAULT_BIND_ADDRESS, DEFAULT_BIND_PORT);
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public int getBindPort() {
        return bindPort;
    }
}
