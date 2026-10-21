package com.microsoft.azure.functions.worker.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Embedded HTTP proxy server used to receive HTTP-triggered invocations
 * directly from the Functions host (HttpUri capability).
 *
 * <p>Uses the JDK HTTP server with a cached executor, matching the worker's
 * gRPC dispatch pool. Production configuration binds to loopback on an
 * ephemeral port. Closing the server cancels pending handoffs.</p>
 */
public final class HttpProxyServer implements AutoCloseable {
    private static final long EXECUTOR_SHUTDOWN_SECONDS = 15L;
    private static final long SERVER_STOP_SECONDS = 5L;

    private final ProxyConfig config;
    private final HttpInvocationCoordinator coordinator;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private HttpServer server;
    private ExecutorService executor;
    private String boundUri;
    private boolean closed;

    public HttpProxyServer(ProxyConfig config, HttpInvocationCoordinator coordinator) {
        this.config = Objects.requireNonNull(config, "config");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    /** Coordinator that correlates proxied HTTP requests with gRPC invocations. */
    public HttpInvocationCoordinator coordinator() {
        return coordinator;
    }

    /**
     * Returns {@code true} once the server is bound and serving. Invocations
     * may only expect a proxied HTTP request while this is {@code true}.
     */
    public boolean isRunning() {
        return started.get();
    }

    /**
     * Starts serving requests with an {@link HttpProxyHandler} bound to this
     * server's coordinator. Returns the absolute {@code http://host:port} URI
     * to advertise to the Functions host via the {@code HttpUri} capability.
     *
     * @throws IllegalStateException if start has already been called
     * @throws IOException           if the server cannot bind
     */
    public String start() throws IOException {
        return start(new HttpProxyHandler(coordinator));
    }

    /** Visible for tests that need to substitute the root handler. */
    synchronized String start(HttpHandler rootHandler) throws IOException {
        Objects.requireNonNull(rootHandler, "rootHandler");
        if (closed) {
            throw new IllegalStateException("HttpProxyServer is closed");
        }
        if (started.get()) {
            throw new IllegalStateException("HttpProxyServer already started");
        }
        InetSocketAddress bindAddress = new InetSocketAddress(
            config.getBindAddress(), config.getBindPort());
        this.server = HttpServer.create(bindAddress, 0);
        try {
            this.executor = Executors.newCachedThreadPool(new ProxyThreadFactory());
            this.server.setExecutor(this.executor);
            this.server.createContext("/", rootHandler);
            this.server.start();
        } catch (RuntimeException | Error ex) {
            this.server.stop(0);
            if (this.executor != null) {
                this.executor.shutdownNow();
            }
            throw ex;
        }
        InetSocketAddress actual = this.server.getAddress();
        this.boundUri = "http://" + actual.getHostString() + ":" + actual.getPort();
        started.set(true);
        WorkerLogManager.getSystemLogger().log(Level.INFO,
            "HTTP proxy server bound to " + boundUri);
        return boundUri;
    }

    /**
     * Returns the URI the server is listening on, or {@code null} if the
     * server has not been started.
     */
    public synchronized String getBoundUri() {
        return boundUri;
    }

    @Override
    public synchronized void close() {
        closed = true;
        coordinator.close();
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
    private static final class ProxyThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "functions-http-proxy-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
