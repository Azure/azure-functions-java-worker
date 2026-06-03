package com.microsoft.azure.functions.worker.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Embedded HTTP proxy server used to receive HTTP-triggered invocations
 * directly from the Functions host (HttpUri capability).
 *
 * <p>Backed by {@link com.sun.net.httpserver.HttpServer}, a JDK built-in
 * since Java 6, so the worker takes on no new runtime dependencies.</p>
 *
 * <p>The server binds to the loopback address on an ephemeral port and is
 * started by {@link #start(HttpHandler)} with a single root handler.
 * Worker threads come from a cached executor that mirrors the gRPC dispatch
 * pool: unbounded growth, named for diagnostics, 15&nbsp;s drain on shutdown.
 * Capping concurrency is left to the platform, matching the Go, Python, and
 * .NET isolated workers.</p>
 */
public final class HttpProxyServer implements AutoCloseable {
    private static final long EXECUTOR_SHUTDOWN_SECONDS = 15L;
    private static final long SERVER_STOP_SECONDS = 5L;

    private final ProxyConfig config;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private HttpServer server;
    private ExecutorService executor;
    private String boundUri;

    public HttpProxyServer(ProxyConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Binds the server, attaches {@code rootHandler} to {@code "/"}, and starts
     * serving requests. Returns the absolute {@code http://host:port} URI that
     * should be advertised to the Functions host via the {@code HttpUri}
     * capability.
     *
     * @throws IllegalStateException if start has already been called
     * @throws IOException           if the server cannot bind
     */
    public synchronized String start(HttpHandler rootHandler) throws IOException {
        Objects.requireNonNull(rootHandler, "rootHandler");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("HttpProxyServer already started");
        }
        InetSocketAddress bindAddress = new InetSocketAddress(
            config.getBindAddress(), config.getBindPort());
        // Backlog 0 → JDK default.
        this.server = HttpServer.create(bindAddress, 0);
        this.executor = Executors.newCachedThreadPool(new ProxyThreadFactory());
        this.server.setExecutor(this.executor);
        this.server.createContext("/", rootHandler);
        this.server.start();
        InetSocketAddress actual = this.server.getAddress();
        this.boundUri = "http://" + actual.getHostString() + ":" + actual.getPort();
        WorkerLogManager.getSystemLogger().log(Level.INFO,
            "HTTP proxy server bound to " + boundUri);
        return boundUri;
    }

    /**
     * Returns the URI the server is listening on, or {@code null} if the
     * server has not been started.
     */
    public String getBoundUri() {
        return boundUri;
    }

    @Override
    public synchronized void close() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        if (server != null) {
            try {
                // Allow in-flight requests up to SERVER_STOP_SECONDS to drain.
                server.stop((int) SERVER_STOP_SECONDS);
            } catch (RuntimeException ex) {
                WorkerLogManager.getSystemLogger().log(Level.WARNING,
                    "Error stopping HTTP proxy server", ex);
            }
            server = null;
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        boundUri = null;
    }

    /**
     * Thread factory that names worker threads for diagnostics. Daemon threads
     * so they do not block JVM shutdown if the server is not explicitly closed.
     */
    private static final class ProxyThreadFactory implements java.util.concurrent.ThreadFactory {
        private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "functions-http-proxy-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
