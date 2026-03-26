package network.vonix.viscord.discord;

import network.vonix.viscord.Viscord;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.event.message.MessageCreateEvent;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Handles the persistent Bot connection via Javacord.
 * Used for receiving messages, events, and updating status.
 */
public class BotClient {

    private DiscordApi api;
    private String token;
    private String channelId;
    private Consumer<MessageCreateEvent> messageHandler;

    public BotClient() {
        // Initialize in disconnected state
    }

    public void setMessageHandler(Consumer<MessageCreateEvent> handler) {
        this.messageHandler = handler;
    }

    public CompletableFuture<Void> connect(String token, String channelId) {
        this.token = token;
        this.channelId = channelId;
        return connect();
    }

    private CompletableFuture<Void> connect() {
        if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            Viscord.LOGGER.warn("Bot token not configured.");
            return CompletableFuture.completedFuture(null);
        }
        
        // Prevent double connection
        if (api != null) {
            Viscord.LOGGER.warn("[Discord] Bot already connected, skipping duplicate connect.");
            return CompletableFuture.completedFuture(null);
        }

        Viscord.LOGGER.info("Connecting to Discord...");

        return new DiscordApiBuilder()
                .setToken(token)
                .setAllIntentsExcept(Intent.GUILD_PRESENCES, Intent.GUILD_MEMBERS)
                .login()
                .thenAccept(this::onConnected)
                .exceptionally(throwable -> {
                    Viscord.LOGGER.error("Failed to connect to Discord", throwable);
                    return null;
                });
    }

    private void onConnected(DiscordApi api) {
        this.api = api;
        Viscord.LOGGER.info("Connected as {}", api.getYourself().getDiscriminatedName());

        // Register Listeners
        api.addMessageCreateListener(event -> {
            if (messageHandler != null) {
                // Ignore self
                if (event.getMessageAuthor().isYourself())
                    return;

                messageHandler.accept(event);
            }
        });
    }

    public void updateStatus(String status) {
        if (api != null) {
            api.updateActivity(ActivityType.PLAYING, status);
            api.updateStatus(org.javacord.api.entity.user.UserStatus.ONLINE);
        }
    }

    public void disconnect() {
        if (api != null) {
            api.disconnect();
            api = null;
        }
    }

    public CompletableFuture<org.javacord.api.entity.message.Message> sendEmbed(String channelId,
            com.google.gson.JsonObject embedJson) {
        if (api == null) {
            Viscord.LOGGER.warn("[Discord] Cannot send embed - API is null (bot not connected)");
            return CompletableFuture.completedFuture(null);
        }
        
        Viscord.LOGGER.info("[Discord] Attempting to send embed to channel ID: {}", channelId);

        return api.getTextChannelById(channelId).map(channel -> {
            org.javacord.api.entity.message.embed.EmbedBuilder embed = new org.javacord.api.entity.message.embed.EmbedBuilder();

            if (embedJson.has("title"))
                embed.setTitle(embedJson.get("title").getAsString());
            if (embedJson.has("description"))
                embed.setDescription(embedJson.get("description").getAsString());
            if (embedJson.has("color"))
                embed.setColor(new java.awt.Color(embedJson.get("color").getAsInt()));

            if (embedJson.has("fields")) {
                com.google.gson.JsonArray fields = embedJson.getAsJsonArray("fields");
                for (com.google.gson.JsonElement fieldElem : fields) {
                    com.google.gson.JsonObject field = fieldElem.getAsJsonObject();
                    embed.addField(
                            field.get("name").getAsString(),
                            field.get("value").getAsString(),
                            field.has("inline") && field.get("inline").getAsBoolean());
                }
            }

            if (embedJson.has("footer")) {
                com.google.gson.JsonObject footer = embedJson.getAsJsonObject("footer");
                embed.setFooter(footer.get("text").getAsString());
            }

            if (embedJson.has("thumbnail")) {
                com.google.gson.JsonObject thumbnail = embedJson.getAsJsonObject("thumbnail");
                embed.setThumbnail(thumbnail.get("url").getAsString());
            }

            // Set timestamp to now
            embed.setTimestampToNow();

            return channel.sendMessage(embed);
        }).orElse(CompletableFuture.completedFuture(null));
    }

    public boolean isConnected() {
        return api != null;
    }
}
