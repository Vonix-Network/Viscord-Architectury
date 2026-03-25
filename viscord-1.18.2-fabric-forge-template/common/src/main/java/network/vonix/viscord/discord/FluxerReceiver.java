package network.vonix.viscord.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.toml.ViscordConfigToml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * HTTP server to receive incoming webhook messages from Fluxer.
 * Starts on a configurable port and listens for POST requests.
 */
public class FluxerReceiver implements HttpHandler {

    private HttpServer server;
    private final int port;
    private final String path;
    private final MessageHandler messageHandler;

    public interface MessageHandler {
        void onFluxerMessage(String username, String message, String avatarUrl);
    }

    private java.util.concurrent.ExecutorService executorService;

    public FluxerReceiver(int port, String path, MessageHandler messageHandler) {
        this.port = port;
        this.path = path.startsWith("/") ? path : "/" + path;
        this.messageHandler = messageHandler;
    }

    public void start() throws IOException {
        if (server != null) {
            Viscord.LOGGER.warn("[Fluxer] Receiver already started");
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(path, this);
        
        // Use a cached thread pool for incoming requests to handle load properly without bottlenecking
        executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Fluxer-Receiver-Worker");
            t.setDaemon(true); // Ensure JVM can exit if this thread is running
            return t;
        });
        
        server.setExecutor(executorService);
        server.start();

        Viscord.LOGGER.info("[Fluxer] HTTP server started on port {} at path {}", port, path);
    }

    public void stop() {
        if (server != null) {
            server.stop(0); // instant stop
            server = null;
        }
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        Viscord.LOGGER.info("[Fluxer] HTTP server stopped cleanly");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String remoteAddress = exchange.getRemoteAddress().toString();

        if (ViscordConfigToml.General.DEBUG.get()) {
            Viscord.LOGGER.debug("[Fluxer] Received {} request from {}", method, remoteAddress);
        }

        // Only accept POST requests
        if (!"POST".equalsIgnoreCase(method)) {
            sendResponse(exchange, 405, "Method Not Allowed - Use POST");
            return;
        }

        try {
            // Read request body
            String body = readBody(exchange);
            
            if (ViscordConfigToml.General.DEBUG.get()) {
                Viscord.LOGGER.debug("[Fluxer] Received webhook payload: {}", body);
            }

            // Parse JSON payload
            JsonElement jsonElement = JsonParser.parseString(body);
            if (!jsonElement.isJsonObject()) {
                sendResponse(exchange, 400, "Invalid JSON - Expected object");
                return;
            }

            JsonObject json = jsonElement.getAsJsonObject();

            // Extract message data (Fluxer webhook format)
            String username = getStringOrDefault(json, "username", "Fluxer");
            String message = getStringOrDefault(json, "message", getStringOrDefault(json, "content", ""));
            String avatarUrl = getStringOrDefault(json, "avatar_url", null);

            if (message.isEmpty()) {
                sendResponse(exchange, 400, "Missing message content");
                return;
            }

            // Handle the message
            if (messageHandler != null) {
                messageHandler.onFluxerMessage(username, message, avatarUrl);
            }

            // Send success response
            sendResponse(exchange, 200, "OK");

            if (ViscordConfigToml.General.DEBUG.get()) {
                Viscord.LOGGER.debug("[Fluxer] Message processed from {}", username);
            }

        } catch (Exception e) {
            Viscord.LOGGER.error("[Fluxer] Error processing webhook", e);
            sendResponse(exchange, 500, "Internal Server Error");
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] response = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private String getStringOrDefault(JsonObject json, String key, String defaultValue) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return defaultValue;
    }

    public boolean isRunning() {
        return server != null;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }
}
