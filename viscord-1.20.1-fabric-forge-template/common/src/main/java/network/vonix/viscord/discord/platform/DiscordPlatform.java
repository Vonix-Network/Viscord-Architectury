package network.vonix.viscord.discord.platform;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import network.vonix.viscord.discord.BotClient;
import network.vonix.viscord.discord.EmbedFactory;
import network.vonix.viscord.discord.WebhookClient;
import network.vonix.viscord.utils.DiscordFormatter;
import org.javacord.api.entity.message.Message;
import org.javacord.api.event.message.MessageCreateEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manages all Discord platform logic: bot connection, webhook sending,
 * event embeds, and bot status updates.
 *
 * Receives messages from Discord and forwards them to the provided MessageListener.
 * Has no knowledge of Fluxer or TridirectionalBridge.
 */
public class DiscordPlatform {

    public interface MessageListener {
        void onMessage(MessageCreateEvent event);
    }

    private final BotClient botClient = new BotClient();
    private final WebhookClient webhookClient = new WebhookClient();

    private MinecraftServer server;
    private MessageListener messageListener;
    private String eventChannelId;
    private boolean initialized = false;

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    /**
     * Initialize Discord: configure webhook, determine event channel, connect bot.
     * @param isStatusOnly if true, skip startup embed (used when Discord is secondary to Fluxer)
     */
    public void initialize(boolean isStatusOnly) {
        String webhookUrl = ViscordConfigToml.Discord.WEBHOOK_URL.get();
        String botToken = ViscordConfigToml.Discord.BOT_TOKEN.get();
        String channelId = ViscordConfigToml.Discord.CHANNEL_ID.get();

        webhookClient.updateUrl(webhookUrl);

        // Determine event channel
        String evtChannelId = ViscordConfigToml.Discord.Events.CHANNEL_ID.get();
        if (evtChannelId != null && !evtChannelId.isEmpty()) {
            this.eventChannelId = evtChannelId;
            Viscord.LOGGER.info("[Discord] Using separate channel for events: {}", evtChannelId);
        } else {
            this.eventChannelId = channelId;
            Viscord.LOGGER.info("[Discord] Using main channel for events: {}", channelId);
        }

        botClient.setMessageHandler(event -> {
            if (messageListener != null) messageListener.onMessage(event);
        });

        botClient.connect(botToken, channelId).thenRunAsync(() -> {
            if (isStatusOnly) {
                Viscord.LOGGER.info("[Discord] Bot connected for status updates only.");
            } else {
                Viscord.LOGGER.info("[Discord] Bot connected, sending startup embed to: {}", eventChannelId);
                sendStartupEmbed(ViscordConfigToml.Server.NAME.get());
            }
            pushStatus();
        }, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)).exceptionally(t -> {
            Viscord.LOGGER.error("[Discord] Failed to connect bot: {}", t.getMessage());
            return null;
        });

        Viscord.LOGGER.info("[Discord] Discord platform initialized.");
        initialized = true;
    }

    public CompletableFuture<org.javacord.api.entity.message.Message> shutdown() {
        CompletableFuture<org.javacord.api.entity.message.Message> shutdownFuture =
            sendShutdownEmbed(ViscordConfigToml.Server.NAME.get());
        // Chain disconnect after embed (or timeout) so we don't cancel the embed mid-flight,
        // but we don't block the caller.
        CompletableFuture<?> finalCleanup = shutdownFuture
            .orTimeout(3, TimeUnit.SECONDS)
            .handle((msg, t) -> {
                if (t != null) Viscord.LOGGER.warn("[Discord] Shutdown embed did not complete: {}", t.getMessage());
                try { botClient.disconnect(); } catch (Exception e) {
                    Viscord.LOGGER.error("[Discord] Error disconnecting bot: {}", e.getMessage());
                }
                try { webhookClient.shutdown(); } catch (Exception e) {
                    Viscord.LOGGER.error("[Discord] Error shutting down webhook: {}", e.getMessage());
                }
                return null;
            });
        initialized = false;
        return shutdownFuture; // caller awaits this for the embed; cleanup runs in background
    }

    // =========================================================================
    // Sending — Minecraft → Discord
    // =========================================================================

    public void sendChatMessage(String formattedUsername, String avatarUrl, String content) {
        String webhookUrl = ViscordConfigToml.Discord.WEBHOOK_URL.get();
        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            webhookClient.updateUrl(webhookUrl);
            webhookClient.sendMessage(formattedUsername, avatarUrl, content);
        }
    }

    /**
     * Send a webhook message with custom username/avatar (used for tridirectional bridging).
     */
    public void sendWebhookMessage(String username, String avatarUrl, String content) {
        String webhookUrl = ViscordConfigToml.Discord.WEBHOOK_URL.get();
        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            webhookClient.updateUrl(webhookUrl);
            webhookClient.sendMessage(username, avatarUrl, content);
        }
    }

    // =========================================================================
    // Event Embeds
    // =========================================================================

    public void sendStartupEmbed(String serverName) {
        sendEventEmbed(EmbedFactory.createServerStatusEmbed(
            "Server Online", "Server is now online", 0x43B581, serverName, "Viscord · Server Online"));
    }

    public CompletableFuture<org.javacord.api.entity.message.Message> sendShutdownEmbed(String serverName) {
        return sendEventEmbedAsync(EmbedFactory.createServerStatusEmbed(
            "Server Offline", "Server is shutting down", 0xF04747, serverName, "Viscord · Server Offline"));
    }

    public void sendJoinEmbed(String username, String avatarUrl) {
        sendEventEmbed(EmbedFactory.createPlayerEventEmbed(
            "Player Joined", username + " joined the game", 0x5865F2,
            username, ViscordConfigToml.Server.NAME.get(), "Viscord · Player Join", avatarUrl));
    }

    public void sendLeaveEmbed(String username, String avatarUrl) {
        sendEventEmbed(EmbedFactory.createPlayerEventEmbed(
            "Player Left", username + " left the game", 0x99AAB5,
            username, ViscordConfigToml.Server.NAME.get(), "Viscord · Player Leave", avatarUrl));
    }

    public void sendDeathEmbed(String message) {
        sendEventEmbed(embed -> {
            embed.addProperty("title", "Player Died");
            embed.addProperty("description", message);
            embed.addProperty("color", 0xF04747);
            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Viscord · Player Death");
            embed.add("footer", footer);
        });
    }

    public void sendAdvancementEmbed(String username, String title, String desc) {
        sendEventEmbed(EmbedFactory.createAdvancementEmbed("🏆", 0xFAA61A, username, title, desc));
    }

    public void sendServerStatusMessage(String title, String description, int color) {
        sendEventEmbed(EmbedFactory.createServerStatusEmbed(
            title, description, color, ViscordConfigToml.Server.NAME.get(), "Viscord · Server Status"));
    }

    private void sendEventEmbed(java.util.function.Consumer<JsonObject> embedBuilder) {
        if (!initialized || eventChannelId == null || eventChannelId.isEmpty()) return;
        JsonObject embed = new JsonObject();
        embedBuilder.accept(embed);
        botClient.sendEmbed(eventChannelId, embed).whenComplete((msg, error) -> {
            if (error != null) Viscord.LOGGER.error("[Discord] Failed to send event embed", error);
        });
    }

    private CompletableFuture<org.javacord.api.entity.message.Message> sendEventEmbedAsync(
            java.util.function.Consumer<JsonObject> embedBuilder) {
        if (!initialized || eventChannelId == null || eventChannelId.isEmpty())
            return CompletableFuture.completedFuture(null);
        JsonObject embed = new JsonObject();
        embedBuilder.accept(embed);
        return botClient.sendEmbed(eventChannelId, embed);
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
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    public boolean isConnected() {
        return botClient.isConnected();
    }

    /** Returns the bot's own Discord user ID, or null if not connected. */
    public String getBotUserId() {
        return botClient.getBotUserId();
    }

    public boolean isConfigured() {
        String webhookUrl = ViscordConfigToml.Discord.WEBHOOK_URL.get();
        return webhookUrl != null && !webhookUrl.isEmpty();
    }

    public String getEventChannelId() {
        return eventChannelId;
    }

    public BotClient getBotClient() {
        return botClient;
    }
}
