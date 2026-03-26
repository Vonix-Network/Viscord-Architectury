package network.vonix.viscord.discord.platform;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import network.vonix.viscord.discord.FluxerBotClient;
import network.vonix.viscord.discord.FluxerWebhookClient;
import network.vonix.viscord.utils.DiscordFormatter;

import java.util.Arrays;

/**
 * Manages all Fluxer platform logic: gateway connection, webhook sending,
 * event messages, and bot status updates.
 *
 * Receives messages from Fluxer and forwards them to the provided MessageListener.
 * Has no knowledge of Discord or TridirectionalBridge.
 */
public class FluxerPlatform {

    public interface MessageListener {
        void onMessage(String username, String message, String avatarUrl);
    }

    private final FluxerBotClient botClient = new FluxerBotClient();
    private final FluxerWebhookClient webhookClient = new FluxerWebhookClient();

    private MinecraftServer server;
    private MessageListener messageListener;
    private boolean initialized = false;
    // Separate event webhook client so event embeds go to the event channel URL if configured
    private final FluxerWebhookClient eventWebhookClient = new FluxerWebhookClient();

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    /**
     * Initialize Fluxer: validate config, connect bot, configure webhook.
     * @return true if initialization succeeded
     */
    public boolean initialize() {
        String apiKey = ViscordConfigToml.Fluxer.BOT_TOKEN.get();
        String channelId = ViscordConfigToml.Fluxer.CHANNEL_ID.get();

        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_FLUXER_API_KEY")) {
            Viscord.LOGGER.error("[Fluxer] Bot token not configured! Set fluxer.bot_token in config.");
            return false;
        }
        if (channelId == null || channelId.isEmpty() || channelId.equals("YOUR_FLUXER_CHANNEL_ID")) {
            Viscord.LOGGER.error("[Fluxer] Channel ID not configured! Set fluxer.channel_id in config.");
            return false;
        }

        // Only listen to configured channels
        botClient.setAllowedChannelIds(Arrays.asList(
            channelId,
            ViscordConfigToml.Fluxer.EVENT_CHANNEL_ID.get()
        ));

        botClient.setMessageHandler((username, message, avatarUrl) -> {
            if (messageListener != null) {
                messageListener.onMessage(username, message, avatarUrl);
            }
        });

        botClient.setOnReadyCallback(() -> {
            // Configure webhook URL immediately on READY/RESUMED
            String webhookUrl = ViscordConfigToml.Fluxer.WEBHOOK_URL.get();
            if (webhookUrl != null && !webhookUrl.isEmpty()) {
                webhookClient.updateUrl(webhookUrl);
            }
            // Set initial bot status
            if (ViscordConfigToml.BotStatus.ENABLED.get() && server != null) {
                pushStatus();
            }
        });

        botClient.connect(apiKey).thenRun(() -> {
            Viscord.LOGGER.info("[Fluxer] Bot connected successfully.");
            // Send startup notification (only in non-tridirectional mode — tridirectional
            // startup is handled by DiscordManager to avoid duplicate messages)
            if (!ViscordConfigToml.Tridirectional.ENABLED.get()) {
                sendBotMessage(getEventChannelId(),
                    "\uD83D\uDFE2 **" + ViscordConfigToml.Server.NAME.get() + "** is now online!");
            }
        }).exceptionally(ex -> {
            Viscord.LOGGER.error("[Fluxer] Failed to connect bot: {}", ex.getMessage());
            return null;
        });

        Viscord.LOGGER.info("[Fluxer] Initialized. Channel: {}, Events: {}", channelId, getEventChannelId());
        initialized = true;
        return true;
    }

    public void shutdown() {
        if (!initialized) return;
        try { botClient.disconnect(); } catch (Exception e) {
            Viscord.LOGGER.error("[Fluxer] Error disconnecting bot: {}", e.getMessage());
        }
        try { webhookClient.shutdown(); } catch (Exception e) {
            Viscord.LOGGER.error("[Fluxer] Error shutting down webhook client: {}", e.getMessage());
        }
        initialized = false;
    }

    // =========================================================================
    // Sending
    // =========================================================================

    /**
     * Send a Minecraft player chat message to Fluxer.
     * Uses webhook if configured (preserves player identity), otherwise bot API.
     */
    public void sendChatMessage(String formattedUsername, String avatarUrl, String content) {
        String webhookUrl = ViscordConfigToml.Fluxer.WEBHOOK_URL.get();
        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            if (!webhookUrl.equals(webhookClient.getUrl())) {
                webhookClient.updateUrl(webhookUrl);
            }
            webhookClient.sendMessage(formattedUsername, avatarUrl, content);
        } else {
            String channelId = ViscordConfigToml.Fluxer.CHANNEL_ID.get();
            String fluxerContent = "**" + formattedUsername + "**: " + content;
            botClient.sendMessage(channelId, fluxerContent);
        }
    }

    /**
     * Send a webhook message with custom username/avatar (used for tridirectional bridging).
     * Records echo fingerprint in the provided cache to suppress gateway echo.
     */
    public void sendWebhookMessage(String username, String avatarUrl, String content,
                                   java.util.Map<String, Long> echoCache) {
        String webhookUrl = ViscordConfigToml.Fluxer.WEBHOOK_URL.get();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            // No webhook — fall back to bot API with formatted message
            String channelId = ViscordConfigToml.Fluxer.CHANNEL_ID.get();
            botClient.sendMessage(channelId, "[Discord] " + username + ": " + content);
            return;
        }
        if (!webhookUrl.equals(webhookClient.getUrl())) {
            webhookClient.updateUrl(webhookUrl);
        }
        // Record fingerprint before sending so echo suppression is ready
        String echoKey = username + ":" + content;
        echoCache.put(echoKey, System.currentTimeMillis());
        if (echoCache.size() > 100) echoCache.clear();
        webhookClient.sendMessage(username, avatarUrl, content);
    }

    /**
     * Send a plain-text event message to the Fluxer event channel via bot API.
     */
    public void sendEventMessage(String message) {
        String channelId = getEventChannelId();
        if (channelId != null && !channelId.isEmpty()) {
            botClient.sendMessage(channelId, message);
        }
    }

    /**
     * Send a message to a specific channel via bot API.
     */
    public void sendBotMessage(String channelId, String content) {
        if (channelId != null && !channelId.isEmpty()) {
            botClient.sendMessage(channelId, content);
        }
    }

    /**
     * Build and send a plain-text event notification from an embed JSON.
     * Fluxer doesn't support rich embeds, so we format as bold text.
     */
    public void sendEventEmbed(JsonObject embed) {
        String title = embed.has("title") ? embed.get("title").getAsString() : "";
        String description = embed.has("description") ? embed.get("description").getAsString() : "";
        String msg = title.isEmpty() ? description
            : (description.isEmpty() ? "**" + title + "**" : "**" + title + "** \u2014 " + description);
        if (!msg.isEmpty()) {
            sendEventMessage(msg);
        }
    }

    // =========================================================================
    // Status
    // =========================================================================

    public void pushStatus() {
        if (server == null || !ViscordConfigToml.BotStatus.ENABLED.get()) return;
        int online = server.getPlayerList().getPlayerCount();
        int max = server.getPlayerList().getMaxPlayers();
        String fmt = ViscordConfigToml.BotStatus.FORMAT.get();
        String status = fmt.replace("{online}", String.valueOf(online))
                           .replace("{max}", String.valueOf(max));
        botClient.updateStatus(status);
        Viscord.LOGGER.info("[Fluxer] Bot status set to: {}", status);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    public boolean isConnected() {
        return botClient.isConnected();
    }

    public boolean isConfigured() {
        String token = ViscordConfigToml.Fluxer.BOT_TOKEN.get();
        String channelId = ViscordConfigToml.Fluxer.CHANNEL_ID.get();
        return token != null && !token.isEmpty() && !token.equals("YOUR_FLUXER_API_KEY")
            && channelId != null && !channelId.isEmpty() && !channelId.equals("YOUR_FLUXER_CHANNEL_ID");
    }

    public boolean hasWebhook() {
        String url = ViscordConfigToml.Fluxer.WEBHOOK_URL.get();
        return url != null && !url.isEmpty();
    }

    public String getChannelId() {
        return ViscordConfigToml.Fluxer.CHANNEL_ID.get();
    }

    public String getEventChannelId() {
        String evtId = ViscordConfigToml.Fluxer.EVENT_CHANNEL_ID.get();
        return (evtId != null && !evtId.isEmpty()) ? evtId : ViscordConfigToml.Fluxer.CHANNEL_ID.get();
    }
}
