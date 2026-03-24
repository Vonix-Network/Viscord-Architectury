package network.vonix.viscord.discord;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import network.vonix.viscord.Viscord;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import network.vonix.viscord.config.ViscordConfig;

/**
 * A WebSocket client for connecting to Fluxer.app's gateway.
 * Handles bot status updates AND receiving chat messages via Gateway.
 */
public class FluxerBotClient {

    private static final String GATEWAY_URL = "wss://gateway.fluxer.app/?v=10&encoding=json";
    
    private WebSocket webSocket;
    private String token;
    private boolean connected = false;
    private MessageHandler messageHandler;
    
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Fluxer-Heartbeat");
        t.setDaemon(true);
        return t;
    });
    
    private final AtomicInteger sequenceNumber = new AtomicInteger(0);
    private String sessionId;
    
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_DELAY_MS = 60000; // 60s max backoff

    public interface MessageHandler {
        void onMessage(String username, String message, String avatarUrl);
    }
    
    public FluxerBotClient() {
    }
    
    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    public CompletableFuture<Void> connect(String token) {
        if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            Viscord.LOGGER.warn("[Fluxer Bot] Token not configured.");
            return CompletableFuture.completedFuture(null);
        }
        
        if (connected || (webSocket != null && webSocket.isOpen())) {
            Viscord.LOGGER.warn("[Fluxer Bot] Already connected or connecting, skipping.");
            return CompletableFuture.completedFuture(null);
        }

        this.token = token;
        return doConnect();
    }

    private CompletableFuture<Void> doConnect() {
        Viscord.LOGGER.info("[Fluxer Bot] Connecting to gateway...");
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            if (webSocket != null) {
                webSocket.disconnect();
            }

            webSocket = new WebSocketFactory()
                .setConnectionTimeout(5000)
                .createSocket(GATEWAY_URL)
                .addListener(new WebSocketAdapter() {
                    @Override
                    public void onConnected(WebSocket websocket, java.util.Map<String, java.util.List<String>> headers) {
                        connected = true;
                        reconnectAttempts = 0; // Reset backoff
                        Viscord.LOGGER.info("[Fluxer Bot] WebSocket connected.");
                        future.complete(null);
                    }

                    public void onDisconnected(WebSocket websocket,
                            com.neovisionaries.ws.client.WebSocketFrame serverCloseFrame,
                            com.neovisionaries.ws.client.WebSocketFrame clientCloseFrame,
                            boolean closedByServer) {
                        handleDisconnect(closedByServer);
                    }

                    public void onDisconnected(WebSocket websocket, WebSocketException serverCloseException,
                            com.neovisionaries.ws.client.WebSocketFrame serverCloseFrame,
                            com.neovisionaries.ws.client.WebSocketFrame clientCloseFrame,
                            boolean closedByServer) {
                        handleDisconnect(closedByServer);
                    }

                    private void handleDisconnect(boolean closedByServer) {
                        if (!connected && webSocket == null) return; // Already handled
                        connected = false;
                        Viscord.LOGGER.info("[Fluxer Bot] WebSocket disconnected. Closed by server: {}", closedByServer);
                        stopHeartbeating();
                        if (token != null) {
                            scheduleReconnect();
                        }
                    }

                    @Override
                    public void onTextMessage(WebSocket websocket, String text) {
                        handleMessage(text);
                    }

                    @Override
                    public void onError(WebSocket websocket, WebSocketException cause) {
                        Viscord.LOGGER.error("[Fluxer Bot] WebSocket error: {}", cause.getMessage());
                        if (!future.isDone()) {
                            future.completeExceptionally(cause);
                        }
                        // onError will often precede onDisconnected, rely on onDisconnected for reconnect scheduling.
                    }
                });

            // Connect asynchronously
            webSocket.connectAsynchronously();
            
        } catch (IOException e) {
            Viscord.LOGGER.error("[Fluxer Bot] Failed to create WebSocket", e);
            future.completeExceptionally(e);
            scheduleReconnect();
        }

        return future;
    }

    private void scheduleReconnect() {
        if (token == null) return; // Explicitly disconnected

        int delay = Math.min((int) Math.pow(2, reconnectAttempts) * 2000, MAX_RECONNECT_DELAY_MS);
        reconnectAttempts++;
        
        Viscord.LOGGER.info("[Fluxer Bot] Scheduling reconnect attempt {} in {} ms...", reconnectAttempts, delay);
        
        // Use the executor to schedule the reconnection
        heartbeatExecutor.schedule(this::doConnect, delay, TimeUnit.MILLISECONDS);
    }

    private void handleMessage(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            
            int op = json.get("op").getAsInt();
            
            // Update sequence number if present
            if (json.has("s") && !json.get("s").isJsonNull()) {
                sequenceNumber.set(json.get("s").getAsInt());
            }

            switch (op) {
                case 10: // Hello
                    JsonObject d = json.getAsJsonObject("d");
                    int heartbeatInterval = d.get("heartbeat_interval").getAsInt();
                    Viscord.LOGGER.debug("[Fluxer Bot] Received Hello. Heartbeat interval: {}ms", heartbeatInterval);
                    
                    startHeartbeating(heartbeatInterval);
                    sendIdentify();
                    break;
                    
                case 11: // Heartbeat ACK
                    Viscord.LOGGER.debug("[Fluxer Bot] Heartbeat acknowledged");
                    break;
                    
                case 0: // Dispatch
                    String t = json.get("t").getAsString();
                    if ("READY".equals(t)) {
                        JsonObject readyData = json.getAsJsonObject("d");
                        sessionId = readyData.get("session_id").getAsString();
                        JsonObject user = readyData.getAsJsonObject("user");
                        Viscord.LOGGER.info("[Fluxer Bot] Authenticated successfully as {}#{}", 
                            user.get("username").getAsString(), 
                            user.get("discriminator").getAsString());
                    } else if ("MESSAGE_CREATE".equals(t)) {
                        handleMessageCreate(json.getAsJsonObject("d"));
                    }
                    break;
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("[Fluxer Bot] Error handling payload", e);
        }
    }
    
    private void handleMessageCreate(JsonObject data) {
        try {
            // Extract author info
            JsonObject author = data.getAsJsonObject("author");
            String username = author.has("global_name") && !author.get("global_name").isJsonNull() 
                ? author.get("global_name").getAsString()
                : author.get("username").getAsString();
            String avatarUrl = null;
            if (author.has("avatar") && !author.get("avatar").isJsonNull()) {
                String avatarHash = author.get("avatar").getAsString();
                String userId = author.get("id").getAsString();
                avatarUrl = "https://cdn.fluxer.app/avatars/" + userId + "/" + avatarHash + ".png";
            }
            
            // Extract message content
            String content = data.has("content") && !data.get("content").isJsonNull() 
                ? data.get("content").getAsString() 
                : "";
            
            // Skip empty messages and bot messages
            if (content.isEmpty()) return;
            if (author.has("bot") && author.get("bot").getAsBoolean()) return;
            
            if (ViscordConfig.CONFIG.debugLogging.get()) {
                Viscord.LOGGER.debug("[Fluxer Bot] Received message from {}: {}", username, content);
            }
            
            // Notify handler
            if (messageHandler != null) {
                messageHandler.onMessage(username, content, avatarUrl);
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("[Fluxer Bot] Error processing MESSAGE_CREATE", e);
        }
    }

    private void sendIdentify() {
        Viscord.LOGGER.debug("[Fluxer Bot] Sending Identify payload...");
        
        JsonObject identify = new JsonObject();
        identify.addProperty("op", 2);
        
        JsonObject d = new JsonObject();
        d.addProperty("token", token);
        
        JsonObject properties = new JsonObject();
        properties.addProperty("$os", "windows");
        properties.addProperty("$browser", "viscord");
        properties.addProperty("$device", "viscord");
        d.add("properties", properties);
        
        // Enable MESSAGE_CONTENT intent to receive message content
        // Intent bit 15 = Message Content
        d.addProperty("intents", 1 << 15);
        
        identify.add("d", d);
        
        webSocket.sendText(identify.toString());
    }

    private java.util.concurrent.ScheduledFuture<?> heartbeatTask;

    private synchronized void startHeartbeating(int interval) {
        stopHeartbeating(); // Ensure no duplicates
        
        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (webSocket != null && webSocket.isOpen()) {
                JsonObject heartbeat = new JsonObject();
                heartbeat.addProperty("op", 1);
                int seq = sequenceNumber.get();
                if (seq > 0) {
                    heartbeat.addProperty("d", seq);
                } else {
                    heartbeat.add("d", com.google.gson.JsonNull.INSTANCE);
                }
                webSocket.sendText(heartbeat.toString());
                Viscord.LOGGER.debug("[Fluxer Bot] Sent heartbeat");
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopHeartbeating() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true); // Changed to true for better thread interruption
            heartbeatTask = null;
        }
    }

    public void updateStatus(String status) {
        if (!connected || webSocket == null || !webSocket.isOpen()) {
            return;
        }

        try {
            JsonObject presence = new JsonObject();
            presence.addProperty("op", 3);
            
            JsonObject d = new JsonObject();
            d.addProperty("since", System.currentTimeMillis());
            
            JsonArray activities = new JsonArray();
            JsonObject activity = new JsonObject();
            activity.addProperty("name", status);
            activity.addProperty("type", 0); // 0 = Playing
            activities.add(activity);
            
            d.add("activities", activities);
            d.addProperty("status", "online");
            d.addProperty("afk", false);
            
            presence.add("d", d);
            
            webSocket.sendText(presence.toString());
            Viscord.LOGGER.debug("[Fluxer Bot] Updated status to: {}", status);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Fluxer Bot] Failed to update status", e);
        }
    }

    public synchronized void disconnect() {
        String oldToken = this.token;
        this.token = null; // Prevent auto-reconnect
        
        stopHeartbeating();

        if (webSocket != null) {
            webSocket.disconnect();
            webSocket = null;
        }
        connected = false;
        
        // If oldToken is null, we might be in a shutdown state
        if (oldToken == null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdownNow();
        }
    }

    public boolean isConnected() {
        return connected && webSocket != null && webSocket.isOpen();
    }
    
    /**
     * Send a message to Fluxer using the Bot REST API.
     * This is more reliable than webhooks and doesn't require port forwarding.
     * 
     * @param channelId The channel ID to send to
     * @param content The message content
     * @return CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Boolean> sendMessage(String channelId, String content) {
        if (token == null || token.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://api.fluxer.app/channels/" + channelId + "/messages");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bot " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                JsonObject payload = new JsonObject();
                payload.addProperty("content", content);
                
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(body.length);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    if (ViscordConfig.CONFIG.debugLogging.get()) {
                        Viscord.LOGGER.debug("[Fluxer Bot] Message sent successfully to channel {}", channelId);
                    }
                    return true;
                } else {
                    Viscord.LOGGER.warn("[Fluxer Bot] Failed to send message. Response code: {}", responseCode);
                    return false;
                }
            } catch (Exception e) {
                Viscord.LOGGER.error("[Fluxer Bot] Error sending message", e);
                return false;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }
}
