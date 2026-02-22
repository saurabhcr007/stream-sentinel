package com.streamsentinel.flink;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight HTTP server that exposes health and readiness endpoints.
 *
 * <h3>Endpoints</h3>
 * <ul>
 * <li>{@code GET /health} – Returns {@code 200 OK} with body
 * {@code {"status":"UP"}}</li>
 * <li>{@code GET /readiness} – Same; Kubernetes readiness probe target</li>
 * </ul>
 *
 * <p>
 * Uses the JDK built-in {@link HttpServer} so no external dependencies
 * (Jetty, Netty, etc.) are required.
 * </p>
 *
 * @since 1.0.0
 */
public class HealthServer {

    private static final Logger LOG = LoggerFactory.getLogger(HealthServer.class);
    private static final byte[] HEALTH_RESPONSE = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Start the health server on the given port.
     *
     * @param port TCP port to bind to; must be in range [1, 65535]
     * @throws IllegalArgumentException if port is out of range
     */
    public void start(int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException(
                    "Health port must be in range [1, 65535], got: " + port);
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/health", HealthServer::handleHealthCheck);
            server.createContext("/readiness", HealthServer::handleHealthCheck);

            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "health-server");
                t.setDaemon(true);
                return t;
            }));

            server.start();
            running.set(true);
            LOG.info("Health server started on port {}", port);
        } catch (IOException e) {
            LOG.error("Failed to start health server on port {}: {}", port, e.getMessage(), e);
        }
    }

    /**
     * Stop the health server gracefully.
     */
    public void stop() {
        if (server != null && running.compareAndSet(true, false)) {
            server.stop(0);
            LOG.info("Health server stopped");
        }
    }

    /**
     * @return {@code true} if the server is currently running
     */
    public boolean isRunning() {
        return running.get();
    }

    // ---------------------------------------------------------------
    // Handler (shared between /health and /readiness)
    // ---------------------------------------------------------------

    private static void handleHealthCheck(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, HEALTH_RESPONSE.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(HEALTH_RESPONSE);
        }
    }
}
