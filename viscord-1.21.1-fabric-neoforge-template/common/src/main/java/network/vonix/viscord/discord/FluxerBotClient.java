package network.vonix.viscord.discord;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketCloseCode;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
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
import network.vonix.viscord.config.toml.ViscordConfigToml;

/**
 * A WebSocket client for connecting to Fluxer.app's gateway.
 * Handles bot status updates AND receiving chat messages via Gateway.
 */
public class FluxerBotClient {

    private static final String GATEWAY_URL = "wss://gateway.fluxer.app/?v=1&encoding=json";
    
    private WebSocket webSocket;
    private String token;
    private volatile boolean connected = false;
    private volatile boolean authenticated = false;
    private MessageHandler messageHandler;
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Fluxer-Scheduler");
        t.setDaemon(true);
        return t;
    });
    
    private final AtomicInteger sequenceNumber = new AtomicInteger(0);
    private String sessionId;
    
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_DELAY_MS = 60000; // 60s max backoff
    private static final int MAX_RECONNECT_ATTEMPTS = 10; // Stop trying after this many consecutive failures
    
    // Track the connect future to complete it only when READY is received
    private volatile CompletableFuture<Void> connectFuture;

    public interface MessageHandler {
        void onMessage(String username, String message, String avatarUrl);
    }

    private Runnable onReadyCallback;

    public void setOnReadyCallback(Runnable callback) {
        this.onReadyCallback = callback;
    }
    
    public FluxerBotClient() {
    }
    
    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    public CompletableFuture<Void> connect(String token) {
        if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE") || token.equals("YOUR_FLUXER_API_KEY")) {
            Viscord.LOGGER.warn("[Fluxer Bot] Token not configured.");
            return CompletableFuture.completedFuture(null);
        }
        
        if (connected || (webSocket != null && webSocket.isOpen())) {
            Viscord.LOGGER.warn("[Fluxer Bot] Already connected or connecting, skipping.");
            return CompletableFuture.completedFuture(null);
        }

        this.token = token;
        this.connectFuture = new CompletableFuture<>();
        doConnect();
        return connectFuture;
    }

    private void doConnect() {
        Viscord.LOGGER.info("[Fluxer Bot] Connecting to gateway...");

        try {
            if (webSocket != null) {
                try {
                    webSocket.disconnect();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }

            webSocket = new WebSocketFactory()
                .setConnectionTimeout(5000)
                .createSocket(GATEWAY_URL)
                .addListener(new WebSocketAdapter() {
                    @Override
                    public void onConnected(WebSocket websocket, java.util.Map<String, java.util.List<String>> headers) {
                        Viscord.LOGGER.info("[Fluxer Bot] WebSocket TCP connected. Waiting for Hello...");
                        // Don't set connected = true yet - wait for READY
                    }

                    @Override
                    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame,
                            WebSocketFrame clientCloseFrame, boolean closedByServer) {
                        int closeCode = (serverCloseFrame != null) ? serverCloseFrame.getCloseCode() : 
                                        (clientCloseFrame != null) ? clientCloseFrame.getCloseCode() : -1;
                        String closeReason = (serverCloseFrame != null) ? serverCloseFrame.getCloseReason() : 
                                             (clientCloseFrame != null) ? clientCloseFrame.getCloseReason() : "Unknown";
                        handleDisconnect(closedByServer, closeCode, closeReason, null);
                    }



                    @Override
                    public void onTextMessage(WebSocket websocket, String text) {
                        handleMessage(text);
                    }

                    @Override
                    public void onError(WebSocket websocket, WebSocketException cause) {
                        Viscord.LOGGER.error("[Fluxer Bot] WebSocket error: {}", cause.getMessage());
                        // Don't complete the future here - let onDisconnected handle it
                    }
                });

            // Connect asynchronously
            webSocket.connectAsynchronously();
            
        } catch (IOException e) {
            Viscord.LOGGER.error("[Fluxer Bot] Failed to create WebSocket", e);
            if (connectFuture != null && !connectFuture.isDone()) {
                connectFuture.completeExceptionally(e);
            }
            scheduleReconnect();
        }
    }

    private void handleDisconnect(boolean closedByServer, int closeCode, String closeReason, WebSocketException exception) {
        boolean wasConnected = connected;
        boolean wasAuthenticated = authenticated;
        connected = false;
        authenticated = false;
        
        // Log the disconnect reason with close code
        if (closeCode > 0) {
            Viscord.LOGGER.info("[Fluxer Bot] WebSocket disconnected. Code: {}, Reason: {}, Closed by server: {}", 
                closeCode, closeReason != null ? closeReason : "N/A", closedByServer);
        } else {
            Viscord.LOGGER.info("[Fluxer Bot] WebSocket disconnected. Closed by server: {}", closedByServer);
        }
        
        stopHeartbeating();
        
        // If this was an explicit disconnect (token set to null), don't reconnect
        if (token == null) {
            Viscord.LOGGER.info("[Fluxer Bot] Not reconnecting - explicit disconnect requested.");
            return;
        }
        
        // Handle specific close codes
        switch (closeCode) {
            case 1000: // Normal closure
                Viscord.LOGGER.info("[Fluxer Bot] Normal closure (1000). Reconnecting if not intended...");
                // Only reconnect if we didn't initiate this disconnect
                if (!closedByServer && wasConnected) {
                    scheduleReconnect();
                } else if (wasConnected) {
                    // Server closed normally - might be maintenance, try to reconnect
                    scheduleReconnect();
                }
                break;
                
            case 1001: // Going away
            case 1006: // Abnormal closure / connection lost
                Viscord.LOGGER.warn("[Fluxer Bot] Connection lost (code {}). Scheduling reconnect...", closeCode);
                scheduleReconnect();
                break;
                
            case 4001: // Unknown opcode
            case 4002: // Decode error
            case 4003: // Not authenticated
            case 4005: // Already authenticated
            case 4008: // Rate limited
                Viscord.LOGGER.error("[Fluxer Bot] Protocol error (code {}). Scheduling reconnect...", closeCode);
                scheduleReconnect();
                break;
                
            case 4004: // Authentication failed
                Viscord.LOGGER.error("[Fluxer Bot] Authentication failed (4004). Token is invalid. Not reconnecting.");
                // Clear token to prevent further reconnection attempts
                token = null;
                if (connectFuture != null && !connectFuture.isDone()) {
                    connectFuture.completeExceptionally(new RuntimeException("Authentication failed - invalid token"));
                }
                break;
                
            case 4007: // Invalid seq
            case 4009: // Session timed out
                Viscord.LOGGER.warn("[Fluxer Bot] Session error (code {}). Will attempt to reconnect with fresh session.", closeCode);
                sessionId = null; // Clear session to force fresh identify
                sequenceNumber.set(0);
                scheduleReconnect();
                break;
                
            case 4010: // Invalid shard
            case 4011: // Sharding required
            case 4012: // Invalid API version
            case 4013: // Invalid intent(s)
            case 4014: // Disallowed intent(s)
                Viscord.LOGGER.error("[Fluxer Bot] Fatal configuration error (code {}). Not reconnecting.", closeCode);
                token = null; // Stop reconnection attempts
                if (connectFuture != null && !connectFuture.isDone()) {
                    connectFuture.completeExceptionally(new RuntimeException("Fatal gateway error: " + closeCode));
                }
                break;
                
            default:
                // Unknown close code or no close code (connection loss)
                if (closeCode < 0) {
                    Viscord.LOGGER.warn("[Fluxer Bot] Connection lost without close code. Scheduling reconnect...");
                } else {
                    Viscord.LOGGER.warn("[Fluxer Bot] Unexpected close code: {}. Scheduling reconnect...", closeCode);
                }
                if (token != null) {
                    scheduleReconnect();
                }
                break;
        }
        
        // Complete the future exceptionally if it hasn't completed yet
        if (connectFuture != null && !connectFuture.isDone() && closeCode != 1000) {
            connectFuture.completeExceptionally(new RuntimeException("Connection closed with code: " + closeCode));
        }
    }

    private void scheduleReconnect() {
        if (token == null) {
            Viscord.LOGGER.info("[Fluxer Bot] Not scheduling reconnect - no token available.");
            return;
        }
        
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Viscord.LOGGER.error("[Fluxer Bot] Maximum reconnection attempts ({}) reached. Giving up.", MAX_RECONNECT_ATTEMPTS);
            token = null; // Stop trying
            return;
        }

        int delay = Math.min((int) Math.pow(2, reconnectAttempts) * 2000, MAX_RECONNECT_DELAY_MS);
        reconnectAttempts++;
        
        Viscord.LOGGER.info("[Fluxer Bot] Scheduling reconnect attempt {}/{} in {} ms...", 
            reconnectAttempts, MAX_RECONNECT_ATTEMPTS, delay);
        
        scheduler.schedule(this::doConnect, delay, TimeUnit.MILLISECONDS);
    }

    private void handleMessage(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            
            if (!json.has("op") || json.get("op").isJsonNull()) {
                Viscord.LOGGER.debug("[Fluxer Bot] Received payload without op code, ignoring");
                return;
            }
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
                    if (!json.has("t") || json.get("t").isJsonNull()) break;
                    String t = json.get("t").getAsString();
                    if ("READY".equals(t)) {
                        JsonObject readyData = json.getAsJsonObject("d");
                        sessionId = readyData.get("session_id").getAsString();
                        JsonObject user = readyData.getAsJsonObject("user");
                        Viscord.LOGGER.info("[Fluxer Bot] Authenticated successfully as {}#{}", 
                            user.get("username").getAsString(), 
                            user.get("discriminator").getAsString());
                        
                        // Now we're truly connected and ready
                        connected = true;
                        authenticated = true;
                        reconnectAttempts = 0; // Reset backoff on successful connection
                        
                        // Complete the future now that we're fully ready
                        if (connectFuture != null && !connectFuture.isDone()) {
                            connectFuture.complete(null);
                        }
                        // Fire ready callback (e.g. for immediate status update)
                        if (onReadyCallback != null) {
                            try { onReadyCallback.run(); } catch (Exception ex) {
                                Viscord.LOGGER.error("[Fluxer Bot] onReadyCallback error", ex);
                            }
                        }
                    } else if ("RESUMED".equals(t)) {
                        Viscord.LOGGER.info("[Fluxer Bot] Session resumed successfully.");
                        connected = true;
                        authenticated = true;
                        reconnectAttempts = 0;
                        if (onReadyCallback != null) {
                            try { onReadyCallback.run(); } catch (Exception ex) {
                                Viscord.LOGGER.error("[Fluxer Bot] onReadyCallback error on resume", ex);
                            }
                        }
                    } else if ("MESSAGE_CREATE".equals(t)) {
                        handleMessageCreate(json.getAsJsonObject("d"));
                    }
                    break;
                    
                case 1: // Heartbeat request
                    sendHeartbeat();
                    break;
                    
                case 9: // Invalid session
                    Viscord.LOGGER.warn("[Fluxer Bot] Invalid session received. Will reconnect.");
                    sessionId = null;
                    connected = false;
                    authenticated = false;
                    scheduleReconnect();
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
            
            if (ViscordConfigToml.General.DEBUG.get()) {
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
        properties.addProperty("os", "linux");
        properties.addProperty("browser", "viscord-bot");
        properties.addProperty("device", "viscord-bot");
        d.add("properties", properties);

        JsonObject presence = new JsonObject();
        presence.addProperty("status", "online");
        presence.addProperty("afk", false);
        presence.add("activities", new JsonArray());
        presence.add("since", com.google.gson.JsonNull.INSTANCE);
        d.add("presence", presence);
        
        // If we have a session ID, try to resume instead of identify
        if (sessionId != null && !sessionId.isEmpty()) {
            Viscord.LOGGER.debug("[Fluxer Bot] Sending Resume payload for session {}...", sessionId);
            JsonObject resume = new JsonObject();
            resume.addProperty("op", 6);
            JsonObject r = new JsonObject();
            r.addProperty("token", token);
            r.addProperty("session_id", sessionId);
            r.addProperty("seq", sequenceNumber.get());
            resume.add("d", r);
            webSocket.sendText(resume.toString());
        } else {
            // GUILD_MESSAGES (1<<9 = 512) to receive MESSAGE_CREATE events
            // MESSAGE_CONTENT (1<<15 = 32768) to read message text
            d.addProperty("intents", (1 << 9) | (1 << 15));
            identify.add("d", d);
            webSocket.sendText(identify.toString());
        }
    }

    private java.util.concurrent.ScheduledFuture<?> heartbeatTask;

    private synchronized void startHeartbeating(int interval) {
        stopHeartbeating(); // Ensure no duplicates
        
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (webSocket != null && webSocket.isOpen()) {
                sendHeartbeat();
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopHeartbeating() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
            heartbeatTask = null;
        }
    }

    private void sendHeartbeat() {
        if (webSocket == null || !webSocket.isOpen()) return;
        
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

    public void updateStatus(String status) {
        if (!authenticated || webSocket == null || !webSocket.isOpen()) {
            Viscord.LOGGER.warn("[Fluxer Bot] Cannot update status - not connected (authenticated={}, wsOpen={})", 
                authenticated, webSocket != null && webSocket.isOpen());
            return;
        }

        Viscord.LOGGER.info("[Fluxer Bot] Sending status update: {}", status);
        
        try {
            JsonObject presence = new JsonObject();
            presence.addProperty("op", 3);
            
            JsonObject d = new JsonObject();
            d.add("since", com.google.gson.JsonNull.INSTANCE);
            
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
            try {
                if (webSocket.isOpen()) {
                    webSocket.sendClose(WebSocketCloseCode.NORMAL, "Client disconnecting");
                }
            } catch (Exception e) {
                // Ignore close errors
            }
            try {
                webSocket.disconnect();
            } catch (Exception e) {
                // Ignore disconnect errors
            }
            webSocket = null;
        }
        connected = false;
        authenticated = false;
        
        // Complete any pending future
        if (connectFuture != null && !connectFuture.isDone()) {
            connectFuture.completeExceptionally(new RuntimeException("Explicitly disconnected"));
            connectFuture = null;
        }
        
        // Shutdown scheduler on explicit disconnect
        if (oldToken == null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    public boolean isConnected() {
        return connected && authenticated && webSocket != null && webSocket.isOpen();
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
                    if (ViscordConfigToml.General.DEBUG.get()) {
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
