package network.vonix.viscord.discord;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.ViscordConfig;
import dev.architectury.platform.Platform;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.Embed;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javacord.api.event.message.MessageCreateEvent;

/**
 * Main coordinator for Discord integration.
 * Delegates to BotClient (Receiving/Status), WebhookClient (Sending),
 * and helper managers for accounts and preferences.
 */
public class DiscordManager {
    private static DiscordManager instance;
    private final BotClient botClient;
    private final WebhookClient webhookClient;
    private final MessageConverter messageConverter;

    // Embed detection and processing
    private final EventEmbedDetector eventDetector = new EventEmbedDetector();
    private final AdvancementEmbedDetector advancementDetector = new AdvancementEmbedDetector();
    private final EventDataExtractor eventExtractor = new EventDataExtractor();
    private final AdvancementDataExtractor advancementExtractor = new AdvancementDataExtractor();
    private final VanillaComponentBuilder componentBuilder = new VanillaComponentBuilder();

    // Pattern for Discord markdown links
    private static final Pattern DISCORD_MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)]+)\\)");

    // Server reference
    private MinecraftServer server;

    // Sub-systems
    private LinkedAccountsManager linkedAccountsManager;
    private PlayerPreferences playerPreferences;

    private boolean running = false;
    private String eventChannelId;

    private DiscordManager() {
        this.botClient = new BotClient();
        this.webhookClient = new WebhookClient();
        this.messageConverter = new MessageConverter();
    }

    public static DiscordManager getInstance() {
        if (instance == null) {
            instance = new DiscordManager();
        }
        return instance;
    }

    public boolean isRunning() {
        return running && botClient.isConnected();
    }

    public void initialize(MinecraftServer server) {
        if (!ViscordConfig.CONFIG.enabled.get()) {
            Viscord.LOGGER.info("[Discord] Disabled in config.");
            return;
        }

        // Prevent double initialization
        if (this.running) {
            Viscord.LOGGER.warn("[Discord] Already initialized, skipping duplicate init.");
            return;
        }

        this.server = server;
        this.running = true;

        // 1. Initialize Clients
        String webhookUrl = ViscordConfig.CONFIG.webhookUrl.get();
        String botToken = ViscordConfig.CONFIG.botToken.get();
        String channelId = ViscordConfig.CONFIG.channelId.get();

        this.webhookClient.updateUrl(webhookUrl);

        // Determine event channel
        String pEventChannelId = ViscordConfig.CONFIG.eventChannelId.get();
        if (pEventChannelId != null && !pEventChannelId.isEmpty()) {
            this.eventChannelId = pEventChannelId;
            Viscord.LOGGER.info("[Discord] Using separate channel for events: {}", pEventChannelId);
        } else {
            this.eventChannelId = channelId;
            Viscord.LOGGER.info("[Discord] Using main channel for events.");
        }

        // 2. Initialize Sub-systems
        Path configDir = dev.architectury.platform.Platform.getConfigFolder();
        try {
            this.playerPreferences = new PlayerPreferences(configDir);
            if (ViscordConfig.CONFIG.enableAccountLinking.get()) {
                this.linkedAccountsManager = new LinkedAccountsManager(configDir);
            }
        } catch (IOException e) {
            Viscord.LOGGER.error("[Discord] Failed to load data managers", e);
        }

        // 3. Connect Bot
        this.botClient.setMessageHandler(this::onDiscordMessage);
        this.botClient.connect(botToken, channelId).thenRun(() -> {
            // 4. Send Startup Message (only after connection)
            sendStartupEmbed(ViscordConfig.CONFIG.serverName.get());
            // 5. Set initial bot status
            updateBotStatus();
        });

        Viscord.LOGGER.info("[Discord] Integration initialized.");
    }

    public void shutdown() {
        if (!running)
            return;

        Viscord.LOGGER.info("[Discord] Sending shutdown message...");
        try {
            sendShutdownEmbed(ViscordConfig.CONFIG.serverName.get()).get(5, TimeUnit.SECONDS);
            Viscord.LOGGER.info("[Discord] Shutdown message sent successfully");
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Failed to send shutdown message: {}", e.getMessage());
            // Don't re-throw - continue with cleanup
        }

        running = false;

        // Disconnect bot client with error handling
        if (botClient != null) {
            try {
                botClient.disconnect();
            } catch (Exception e) {
                Viscord.LOGGER.error("[Discord] Error disconnecting bot client: {}", e.getMessage());
            }
        }

        // Shutdown webhook client with error handling
        if (webhookClient != null) {
            try {
                webhookClient.shutdown();
            } catch (Exception e) {
                Viscord.LOGGER.error("[Discord] Error shutting down webhook client: {}", e.getMessage());
            }
        }
    }

    /**
     * Handles incoming messages from Discord (via BotClient).
     */
    private void onDiscordMessage(org.javacord.api.event.message.MessageCreateEvent event) {
        if (server == null)
            return;

        Message message = event.getMessage();
        String msgChannelId = message.getChannel().getIdAsString();
        String mainChannelId = ViscordConfig.CONFIG.channelId.get();
        String eventChannelId = ViscordConfig.CONFIG.eventChannelId.get();

        boolean isMainChannel = mainChannelId != null && mainChannelId.equals(msgChannelId);
        boolean isEventChannel = eventChannelId != null && !eventChannelId.isEmpty()
                && eventChannelId.equals(msgChannelId);

        // Ignore messages from other channels
        if (!isMainChannel && !isEventChannel) {
            return;
        }

        // Handle !list command early (before any filtering)
        if (message.getContent().trim().equalsIgnoreCase("!list")) {
            handleTextListCommand(event);
            return;
        }

        // If it's an event channel message, check if we should show other server events
        if (isEventChannel && !ViscordConfig.CONFIG.showOtherServerEvents.get()) {
            return;
        }

        // Filter out bots if configured
        if (ViscordConfig.CONFIG.ignoreBots.get() && message.getAuthor().isBotUser())
            return;

        // Filter out webhooks if configured
        if (ViscordConfig.CONFIG.ignoreWebhooks.get() && message.getAuthor().isWebhook())
            return;

        // Filter by prefix to prevent echoing our own messages
        if (ViscordConfig.CONFIG.filterByPrefix.get()) {
            String serverPrefix = ViscordConfig.CONFIG.serverPrefix.get();
            if (serverPrefix != null && !serverPrefix.isEmpty()) {
                String authorName = message.getAuthor().getDisplayName();
                if (authorName.startsWith(serverPrefix)) {
                    return;
                }
                String webhookFormat = ViscordConfig.CONFIG.webhookUsernameFormat.get();
                if (webhookFormat != null && webhookFormat.contains("{prefix}")) {
                    String expectedStart = webhookFormat.split("\\{prefix\\}")[0] + serverPrefix;
                    if (authorName.startsWith(expectedStart) || authorName.startsWith(serverPrefix)) {
                        return;
                    }
                }
            }
        }

        boolean isWebhook = message.getAuthor().isWebhook();
        String authorName = message.getAuthor().getDisplayName();
        String content = message.getContent();

        // Check for embeds that need special processing
        if (!message.getEmbeds().isEmpty()) {
            for (Embed embed : message.getEmbeds()) {
                // Check for advancement embeds first
                if (advancementDetector.isAdvancementEmbed(embed)) {
                    processAdvancementEmbed(embed, event);
                    return;
                }
                // Check for event embeds (join/leave/death)
                if (eventDetector.isEventEmbed(embed)) {
                    processEventEmbed(embed, event);
                    return;
                }
                // Check for Player List system embeds
                if (isPlayerListEmbed(embed)) {
                    processPlayerListEmbed(embed, event);
                    return;
                }
            }
        }

        // Generic Embed Handling: If content is empty but we have embeds, try to
        // convert them to text
        if (content.isEmpty() && !message.getEmbeds().isEmpty()) {
            // We use convertEmbedToMinecraftComponent logic but just get the text part if
            // possible
            // Or simpler: just re-use convertEmbedToMinecraftComponent to generate the
            // "content" string?
            // Actually, cleaner to just use the converter and set content
            Embed embed = message.getEmbeds().get(0);
            MutableComponent converted = convertEmbedToMinecraftComponent(embed, event);
            if (converted != null) {
                content = converted.getString(); // Approximate text representation
                // Better: Use a clearer extraction method if we want full formatting
                // preservation?
                // But 1.18.2 TextComponent logic is simple.
                // Let's manually extract text like Viscord does to be safe
                StringBuilder embedContent = new StringBuilder();
                embed.getAuthor().ifPresent(a -> embedContent.append(a.getName()).append(" "));
                embed.getTitle().ifPresent(t -> {
                    String s = t.replaceAll("[^a-zA-Z ]", "").trim();
                    if (!s.equalsIgnoreCase("Player Joined") && !s.equalsIgnoreCase("Player Left")
                            && !s.equalsIgnoreCase("Player Died")) {
                        embedContent.append(t).append(" ");
                    }
                });
                embed.getDescription().ifPresent(d -> embedContent.append(d).append(" "));
                for (org.javacord.api.entity.message.embed.EmbedField field : embed.getFields()) {
                    String fieldName = field.getName();
                    if ((fieldName.equalsIgnoreCase("Server") || fieldName.equalsIgnoreCase("Message")) &&
                            !embed.getTitle().map(t -> t.contains("List") || t.contains("Status")).orElse(false) &&
                            !embed.getFooter()
                                    .map(f -> f.getText().map(text -> text.contains("Player List")).orElse(false))
                                    .orElse(false)) {
                        continue;
                    }
                    embedContent.append("[").append(fieldName).append(": ").append(field.getValue()).append("] ");
                }
                content = embedContent.toString().trim();
            }
        }

        // Regular message processing
        if (server != null) {
            MutableComponent finalComponent = new TextComponent("");

            if (isWebhook) {
                // Cross-server webhook: special formatting WITHOUT [Discord] prefix
                // Format: [ServerPrefix] Username: message
                String displayName = authorName;
                String cleanedContent = content;

                // Remove duplicate username from content if present (webhook quirk)
                if (content.startsWith(authorName + ": ")) {
                    cleanedContent = content.substring(authorName.length() + 2);
                } else if (content.startsWith(authorName + " ")) {
                    cleanedContent = content.substring(authorName.length() + 1);
                }

                String formattedMessage;
                if (displayName.startsWith("[") && displayName.contains("]")) {
                    int endBracket = displayName.indexOf("]");
                    String serverPrefix = displayName.substring(0, endBracket + 1);
                    String remainingName = displayName.substring(endBracket + 1).trim();

                    // Check if event channel
                    String eventChanId = ViscordConfig.CONFIG.eventChannelId.get();
                    boolean isEvtChannel = eventChanId != null && !eventChanId.isEmpty()
                            && eventChanId.equals(msgChannelId);

                    if (isEvtChannel) {
                        // Event channel: [Prefix] message (name is in message)
                        formattedMessage = "§a" + serverPrefix + " §f" + cleanedContent;
                    } else {
                        // Chat: [Prefix] Name: message
                        if (remainingName.isEmpty() || remainingName.toLowerCase().contains("server")) {
                            formattedMessage = "§a" + serverPrefix + " §f" + cleanedContent;
                        } else {
                            formattedMessage = "§a" + serverPrefix + " §f" + remainingName + "§7: §f" + cleanedContent;
                        }
                    }
                } else {
                    // No bracket prefix found - treat as cross-server
                    formattedMessage = "§a[Cross-Server] §f" + authorName + "§7: §f" + cleanedContent;
                }

                finalComponent.append(toMinecraftComponentWithLinks(formattedMessage));
            } else {
                // Regular Discord user: make [Discord] clickable
                String inviteUrl = ViscordConfig.CONFIG.inviteUrl.get();
                String rawFormat = ViscordConfig.CONFIG.discordToMinecraftFormat.get()
                        .replace("{username}", authorName)
                        .replace("{message}", content);

                if (rawFormat.contains("[Discord]") && inviteUrl != null && !inviteUrl.isEmpty()) {
                    String[] parts = rawFormat.split("\\[Discord\\]", 2);

                    // Part before [Discord]
                    if (parts.length > 0 && !parts[0].isEmpty()) {
                        finalComponent.append(toMinecraftComponentWithLinks(parts[0]));
                    }

                    // Clickable [Discord] with aqua color
                    finalComponent.append(new TextComponent("[Discord]")
                            .setStyle(Style.EMPTY
                                    .withColor(TextColor.parseColor("aqua"))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, inviteUrl))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            new TextComponent("Click to join our Discord!")))));

                    // Part after [Discord]
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        finalComponent.append(toMinecraftComponentWithLinks(parts[1]));
                    }
                } else {
                    // Fallback if no [Discord] tag or no invite URL
                    finalComponent.append(toMinecraftComponentWithLinks(rawFormat));
                }
            }

            // Broadcast to server with player preference filtering
            server.execute(() -> {
                broadcastSystemMessageRespectingFilters(finalComponent);
            });
        }
    }

    /**
     * Processes an event embed (join/leave/death) and broadcasts as vanilla-style
     * message.
     */
    private void processEventEmbed(Embed embed, org.javacord.api.event.message.MessageCreateEvent event) {
        try {
            EventData data = eventExtractor.extractFromEmbed(embed);
            String serverPrefix = extractServerPrefixFromAuthor(event.getMessageAuthor().getDisplayName());

            MutableComponent eventComponent = componentBuilder.buildEventMessage(data, serverPrefix);

            if (server != null) {
                server.execute(() -> {
                    broadcastEventMessageRespectingFilters(eventComponent);
                });
                if (ViscordConfig.CONFIG.debugLogging.get()) {
                    Viscord.LOGGER.debug("[Discord] Processed event embed: {} {}",
                            data.getPlayerName(), data.getActionString());
                }
            }
        } catch (ExtractionException e) {
            Viscord.LOGGER.warn("[Discord] Failed to extract event data: {}", e.getMessage());
            // Fallback to regular embed display
            handleEmbedFallback(embed, event);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error processing event embed", e);
            handleEmbedFallback(embed, event);
        }
    }

    /**
     * Processes an advancement embed and broadcasts as vanilla-style message.
     */
    private void processAdvancementEmbed(Embed embed, org.javacord.api.event.message.MessageCreateEvent event) {
        try {
            AdvancementData data = advancementExtractor.extractFromEmbed(embed);
            String serverPrefix = extractServerPrefixFromAuthor(event.getMessageAuthor().getDisplayName());

            MutableComponent advComponent = componentBuilder.buildAdvancementMessage(data, serverPrefix);

            if (server != null) {
                server.execute(() -> {
                    broadcastEventMessageRespectingFilters(advComponent);
                });
                if (ViscordConfig.CONFIG.debugLogging.get()) {
                    Viscord.LOGGER.debug("[Discord] Processed advancement embed: {} - {}",
                            data.getPlayerName(), data.getAdvancementTitle());
                }
            }
        } catch (ExtractionException e) {
            Viscord.LOGGER.warn("[Discord] Failed to extract advancement data: {}", e.getMessage());
            handleEmbedFallback(embed, event);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error processing advancement embed", e);
            handleEmbedFallback(embed, event);
        }
    }

    /**
     * Fallback embed display when parsing fails.
     * Implements multi-strategy fallback for production stability.
     */
    private void handleEmbedFallback(Embed embed, org.javacord.api.event.message.MessageCreateEvent event) {
        // Strategy 1: Try to convert embed to readable Minecraft component
        try {
            MutableComponent convertedComponent = convertEmbedToMinecraftComponent(embed, event);
            if (convertedComponent != null && server != null) {
                server.execute(() -> {
                    broadcastEventMessageRespectingFilters(convertedComponent);
                });
                if (ViscordConfig.CONFIG.debugLogging.get()) {
                    Viscord.LOGGER.debug("[Discord] Used embed conversion fallback");
                }
                return;
            }
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Embed conversion fallback failed: {}", e.getMessage());
        }

        // Strategy 2: Ultimate fallback - use MessageConverter
        try {
            Component fallback = MessageConverter.toMinecraft(event.getMessage());
            if (server != null) {
                server.execute(() -> {
                    broadcastEventMessageRespectingFilters(fallback);
                });
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] All fallback strategies failed for embed", e);
        }
    }

    /**
     * Converts a Discord embed to a Minecraft component for fallback display.
     * Extracts author, title, fields, and description into readable format.
     */
    private MutableComponent convertEmbedToMinecraftComponent(Embed embed,
            org.javacord.api.event.message.MessageCreateEvent event) {
        if (embed == null) {
            return null;
        }

        try {
            StringBuilder content = new StringBuilder();

            // Add Author if present
            embed.getAuthor().ifPresent(author -> {
                String authorName = author.getName();
                if (authorName != null && !authorName.trim().isEmpty()) {
                    content.append(authorName.trim()).append(" ");
                }
            });

            // Add Title if present
            embed.getTitle().ifPresent(title -> {
                if (!title.trim().isEmpty()) {
                    content.append(title.trim()).append(" ");
                }
            });

            // Parse Fields
            for (org.javacord.api.entity.message.embed.EmbedField field : embed.getFields()) {
                String fieldName = field.getName();
                String fieldValue = field.getValue();
                if (fieldName != null && fieldValue != null &&
                        !fieldName.trim().isEmpty() && !fieldValue.trim().isEmpty()) {
                    content.append("[").append(fieldName.trim()).append(": ")
                            .append(fieldValue.trim()).append("] ");
                }
            }

            // Add Description
            embed.getDescription().ifPresent(desc -> {
                if (!desc.trim().isEmpty()) {
                    content.append(desc.trim());
                }
            });

            String text = content.toString().trim();
            if (text.isEmpty()) {
                return null;
            }

            // Get server prefix from author
            String authorName = event.getMessageAuthor().getDisplayName();
            String formattedMessage;
            if (authorName != null && authorName.startsWith("[") && authorName.contains("]")) {
                int endBracket = authorName.indexOf("]");
                String prefix = authorName.substring(0, endBracket + 1);
                formattedMessage = "§a" + prefix + " §f" + text;
            } else {
                String serverPrefix = ViscordConfig.CONFIG.serverPrefix.get();
                formattedMessage = "§a[" + serverPrefix + "] §f" + text;
            }

            return (MutableComponent) toMinecraftComponentWithLinks(formattedMessage);

        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error converting embed to component: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts server prefix from webhook author name (e.g., "[HomeStead]" from
     * "[HomeStead] Player").
     */
    private String extractServerPrefixFromAuthor(String authorName) {
        if (authorName == null)
            return "Cross-Server";

        // Try to extract [Prefix] format
        if (authorName.startsWith("[")) {
            int endBracket = authorName.indexOf("]");
            if (endBracket > 1) {
                return authorName.substring(1, endBracket);
            }
        }
        return "Cross-Server";
    }

    /**
     * Converts text to Minecraft component, parsing Discord markdown links.
     */
    private Component toMinecraftComponentWithLinks(String text) {
        if (text == null || text.isEmpty()) {
            return new TextComponent("");
        }

        Matcher matcher = DISCORD_MARKDOWN_LINK.matcher(text);
        MutableComponent result = new TextComponent("");
        int lastEnd = 0;
        boolean hasLink = false;

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            if (start > lastEnd) {
                String before = text.substring(lastEnd, start);
                if (!before.isEmpty()) {
                    result.append(new TextComponent(before));
                }
            }

            String label = matcher.group(1);
            String url = matcher.group(2);

            Component linkComponent = new TextComponent(label)
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                            .withUnderlined(true)
                            .withColor(ChatFormatting.AQUA));

            result.append(linkComponent);
            lastEnd = end;
            hasLink = true;
        }

        if (lastEnd < text.length()) {
            String tail = text.substring(lastEnd);
            if (!tail.isEmpty()) {
                result.append(new TextComponent(tail));
            }
        }

        if (!hasLink) {
            return new TextComponent(text);
        }

        return result;
    }

    /**
     * Broadcasts a system message to all players, respecting player message filtering preferences.
     * Players who have disabled cross-server messages will not receive this message.
     */
    private void broadcastSystemMessageRespectingFilters(Component message) {
        if (server == null) return;
        
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!hasServerMessagesFiltered(player.getUUID())) {
                player.sendMessage(message, net.minecraft.network.chat.ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
            }
        }
    }

    /**
     * Broadcasts an event message to all players, respecting player event filtering preferences.
     * Players who have disabled event messages will not receive this message.
     */
    private void broadcastEventMessageRespectingFilters(Component message) {
        if (server == null) return;
        
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!hasEventsFiltered(player.getUUID())) {
                player.sendMessage(message, net.minecraft.network.chat.ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
            }
        }
    }

    /**
     * Broadcasts a server system message to all players, respecting player server system message filtering preferences.
     * Players who have disabled server system messages (startup, shutdown, player list) will not receive this message.
     */
    private void broadcastServerSystemMessageRespectingFilters(Component message) {
        if (server == null) return;
        
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!hasServerSystemMessagesFiltered(player.getUUID())) {
                player.sendMessage(message, net.minecraft.network.chat.ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
            }
        }
    }

    // =================================================================================
    // Sending Methods (Minecraft -> Discord)
    // =================================================================================

    public void sendMinecraftMessage(String username, String message) {
        if (!running || webhookClient == null)
            return;

        String prefix = ViscordConfig.CONFIG.serverPrefix.get();
        String formattedUsername = ViscordConfig.CONFIG.webhookUsernameFormat.get()
                .replace("{prefix}", prefix)
                .replace("{username}", username);

        String avatarUrl = getAvatarUrl(username);

        webhookClient.sendMessage(formattedUsername, avatarUrl, message);
    }

    public void sendSystemMessage(String message) {
        if (!running)
            return;

        if (message.startsWith("💀")) {
            sendDeathEmbed(message);
        } else {
            // System messages often don't have a player, so use server avatar/name
            sendMinecraftMessage("Server", message);
        }
    }

    // =================================================================================
    // Embed Senders
    // =================================================================================

    private CompletableFuture<Message> sendEventEmbedInternal(Consumer<JsonObject> embedBuilder) {
        if (!running) {
            Viscord.LOGGER.debug("[Discord] Cannot send event embed - Discord not running");
            return CompletableFuture.completedFuture(null);
        }

        if (eventChannelId == null || eventChannelId.isEmpty()) {
            Viscord.LOGGER.warn("[Discord] Cannot send event embed - event channel ID not set");
            return CompletableFuture.completedFuture(null);
        }

        JsonObject embed = new JsonObject();
        embedBuilder.accept(embed);

        if (ViscordConfig.CONFIG.debugLogging.get()) {
            Viscord.LOGGER.debug("[Discord] Sending event embed to channel: {}", eventChannelId);
        }

        return botClient.sendEmbed(eventChannelId, embed).whenComplete((msg, error) -> {
            if (error != null) {
                Viscord.LOGGER.error("[Discord] Failed to send event embed to channel {}", eventChannelId, error);
            }
        });
    }

    public void sendStartupEmbed(String serverName) {
        sendEventEmbedInternal(EmbedFactory.createServerStatusEmbed(
                "Server Online",
                "Server is now online",
                0x43B581,
                serverName,
                "Viscord"));
    }

    public CompletableFuture<Message> sendShutdownEmbed(String serverName) {
        return sendEventEmbedInternal(EmbedFactory.createServerStatusEmbed(
                "Server Offline",
                "Server is shutting down",
                0xF04747,
                serverName,
                "Viscord"));
    }

    public void sendJoinEmbed(String username, String uuid) {
        if (!ViscordConfig.CONFIG.sendJoin.get())
            return;

        if (!isRunning()) {
            Viscord.LOGGER.debug("[Discord] Not sending join embed - Discord not running");
            return;
        }

        sendEventEmbedInternal(EmbedFactory.createPlayerEventEmbed(
                "Player Joined",
                username + " joined the game",
                0x5865F2,
                username,
                ViscordConfig.CONFIG.serverName.get(),
                "Join",
                getAvatarUrl(username))).whenComplete((msg, error) -> {
                    if (error != null) {
                        Viscord.LOGGER.error("[Discord] Failed to send join embed for {}", username, error);
                    } else if (ViscordConfig.CONFIG.debugLogging.get()) {
                        Viscord.LOGGER.debug("[Discord] Sent join embed for {}", username);
                    }
                });
    }

    public void sendLeaveEmbed(String username, String uuid) {
        if (!ViscordConfig.CONFIG.sendLeave.get())
            return;

        if (!isRunning()) {
            Viscord.LOGGER.debug("[Discord] Not sending leave embed - Discord not running");
            return;
        }

        sendEventEmbedInternal(EmbedFactory.createPlayerEventEmbed(
                "Player Left",
                username + " left the game",
                0x99AAB5,
                username,
                ViscordConfig.CONFIG.serverName.get(),
                "Leave",
                getAvatarUrl(username))).whenComplete((msg, error) -> {
                    if (error != null) {
                        Viscord.LOGGER.error("[Discord] Failed to send leave embed for {}", username, error);
                    } else if (ViscordConfig.CONFIG.debugLogging.get()) {
                        Viscord.LOGGER.debug("[Discord] Sent leave embed for {}", username);
                    }
                });
    }

    // Deprecated single-arg methods for compatibility if needed
    public void sendJoinEmbed(String username) {
        sendJoinEmbed(username, null);
    }

    public void sendLeaveEmbed(String username) {
        sendLeaveEmbed(username, null);
    }

    public void updateStatus() {
        updateBotStatus();
    }

    public void sendServerStatusMessage(String title, String description, int color) {
        sendEventEmbedInternal(EmbedFactory.createServerStatusEmbed(
                title,
                description,
                color,
                ViscordConfig.CONFIG.serverName.get(),
                "Viscord"));
    }

    public void sendChatMessage(String username, String message, String uuid) {
        sendMinecraftMessage(username, message);
    }

    public void sendDeathEmbed(String message) {
        if (!ViscordConfig.CONFIG.sendDeath.get())
            return;

        if (!isRunning()) {
            Viscord.LOGGER.debug("[Discord] Not sending death embed - Discord not running");
            return;
        }

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Player Died");
        embed.addProperty("description", message);
        embed.addProperty("color", 0xF04747);

        botClient.sendEmbed(eventChannelId, embed).whenComplete((msg, error) -> {
            if (error != null) {
                Viscord.LOGGER.error("[Discord] Failed to send death embed", error);
            } else if (ViscordConfig.CONFIG.debugLogging.get()) {
                Viscord.LOGGER.debug("[Discord] Sent death embed");
            }
        });
    }

    public void sendAdvancementEmbed(String username, String title, String desc) {
        if (!ViscordConfig.CONFIG.sendAdvancement.get())
            return;

        if (!isRunning()) {
            Viscord.LOGGER.debug("[Discord] Not sending advancement embed - Discord not running");
            return;
        }

        sendEventEmbedInternal(EmbedFactory.createAdvancementEmbed(
                "🏆",
                0xFAA61A,
                username,
                title,
                desc)).whenComplete((msg, error) -> {
                    if (error != null) {
                        Viscord.LOGGER.error("[Discord] Failed to send advancement embed for {}", username, error);
                    } else if (ViscordConfig.CONFIG.debugLogging.get()) {
                        Viscord.LOGGER.debug("[Discord] Sent advancement embed for {}", username);
                    }
                });
    }

    public void updateBotStatus() {
        if (botClient == null || server == null || !ViscordConfig.CONFIG.setBotStatus.get()) {
            return;
        }

        int online = server.getPlayerList().getPlayerCount();
        int max = server.getPlayerList().getMaxPlayers();
        String format = ViscordConfig.CONFIG.botStatusFormat.get();
        String status = format.replace("{online}", String.valueOf(online))
                .replace("{max}", String.valueOf(max));

        // Update status asynchronously to avoid blocking main thread
        Viscord.ASYNC_EXECUTOR.submit(() -> botClient.updateStatus(status));
    }

    /**
     * Schedules a status update after a delay (used for player join/leave events).
     * Non-blocking and thread-safe.
     */
    public void scheduleStatusUpdate(int delayMs) {
        if (botClient == null || server == null || !ViscordConfig.CONFIG.setBotStatus.get()) {
            return;
        }

        Viscord.ASYNC_EXECUTOR.submit(() -> {
            try {
                Thread.sleep(delayMs);
                updateBotStatus();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // =================================================================================
    // Player Preferences Delegation
    // =================================================================================

    public void setServerSystemMessagesFiltered(UUID playerUuid, boolean filtered) {
        if (playerPreferences != null) {
            playerPreferences.setServerSystemMessagesFiltered(playerUuid, filtered);
        }
    }

    public boolean hasServerSystemMessagesFiltered(UUID playerUuid) {
        return playerPreferences != null && playerPreferences.hasServerSystemMessagesFiltered(playerUuid);
    }

    public void setServerMessagesFiltered(UUID playerUuid, boolean filtered) {
        if (playerPreferences != null) {
            playerPreferences.setServerMessagesFiltered(playerUuid, filtered);
        }
    }

    public boolean hasServerMessagesFiltered(UUID playerUuid) {
        return playerPreferences != null && playerPreferences.hasServerMessagesFiltered(playerUuid);
    }

    public void setEventsFiltered(UUID playerUuid, boolean filtered) {
        if (playerPreferences != null) {
            playerPreferences.setEventsFiltered(playerUuid, filtered);
        }
    }

    public boolean hasEventsFiltered(UUID playerUuid) {
        return playerPreferences != null && playerPreferences.hasEventsFiltered(playerUuid);
    }

    // =================================================================================
    // Account Linking Delegation
    // =================================================================================

    public String generateLinkCode(ServerPlayer player) {
        return linkedAccountsManager != null
                ? linkedAccountsManager.generateLinkCode(player.getUUID(), player.getName().getString())
                : null;
    }

    public boolean unlinkAccount(UUID uuid) {
        return linkedAccountsManager != null && linkedAccountsManager.unlinkMinecraft(uuid);
    }

    // =================================================================================
    // Helpers & Getters
    // =================================================================================

    /**
     * Builds an embed displaying the list of online players.
     */
    private org.javacord.api.entity.message.embed.EmbedBuilder buildPlayerListEmbed() {
        java.util.List<net.minecraft.server.level.ServerPlayer> players = server.getPlayerList().getPlayers();
        int onlinePlayers = players.size();
        int maxPlayers = server.getPlayerList().getMaxPlayers();

        String serverName = ViscordConfig.CONFIG.serverName.get();

        org.javacord.api.entity.message.embed.EmbedBuilder embed = new org.javacord.api.entity.message.embed.EmbedBuilder()
                .setTitle("📋 " + serverName)
                .setColor(java.awt.Color.GREEN)
                .setFooter("Viscord · Player List");

        if (onlinePlayers == 0) {
            embed.setDescription("No players are currently online.");
        } else {
            StringBuilder playerListBuilder = new StringBuilder();
            for (int i = 0; i < players.size(); i++) {
                if (i > 0)
                    playerListBuilder.append("\n");
                playerListBuilder.append("• ").append(players.get(i).getName().getString());
            }
            embed.addField("Players " + onlinePlayers + "/" + maxPlayers, playerListBuilder.toString(), false);
        }

        return embed;
    }

    /**
     * Handles the !list text command from Discord.
     */
    private void handleTextListCommand(org.javacord.api.event.message.MessageCreateEvent event) {
        try {
            if (server == null) {
                return;
            }

            // Build and send the rich embed
            org.javacord.api.entity.message.embed.EmbedBuilder embed = buildPlayerListEmbed();
            event.getChannel().sendMessage(embed);

        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error handling !list command", e);
        }
    }

    private String getAvatarUrl(String username) {
        String url = ViscordConfig.CONFIG.avatarUrl.get().replace("{username}", username);
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(username);
            if (player != null) {
                url = url.replace("{uuid}", player.getUUID().toString().replace("-", ""));
            }
        }
        return url;
    }

    private boolean isPlayerListEmbed(Embed embed) {
        return embed.getFooter().map(f -> f.getText().map(text -> text.contains("Player List")).orElse(false))
                .orElse(false) ||
                embed.getTitle().map(t -> t.contains("List") || t.contains("Status")).orElse(false);
    }

    private void processPlayerListEmbed(Embed embed, MessageCreateEvent event) {
        try {
            // Extract Server Name logic
            String serverName = "Unknown Server";
            if (embed.getTitle().isPresent()) {
                // Title is like "📋 ServerName"
                serverName = embed.getTitle().get().replaceAll("^📋\\s*", "").trim();
            } else if (embed.getAuthor().isPresent()) {
                serverName = embed.getAuthor().get().getName();
            }

            String message;
            String description = embed.getDescription().orElse("");

            // Check if no players online (message is in description)
            if (description.contains("No players") || description.contains("no players")) {
                message = "0 Players: No players online";
            } else {
                // Players are in embed fields, not description
                // Field format: name="Players X/Y", value="• Player1\n• Player2"
                List<String> players = new ArrayList<>();
                String countInfo = "";

                for (org.javacord.api.entity.message.embed.EmbedField field : embed.getFields()) {
                    String fieldName = field.getName();
                    String fieldValue = field.getValue();

                    // Check for player list field (e.g., "Players 3/20")
                    if (fieldName != null && fieldName.startsWith("Players")) {
                        // Extract count from field name
                        countInfo = fieldName.replace("Players", "").trim();

                        // Parse player names from field value (bullet list)
                        if (fieldValue != null && !fieldValue.isEmpty()) {
                            String[] lines = fieldValue.split("\n");
                            for (String line : lines) {
                                String cleaned = line.trim()
                                        .replaceAll("^[•\\-*]\\s*", "") // Remove bullet points
                                        .trim();
                                if (!cleaned.isEmpty()) {
                                    players.add(cleaned);
                                }
                            }
                        }
                    }
                }

                if (!players.isEmpty()) {
                    String playerList = String.join(", ", players);
                    message = countInfo + " Players: " + playerList;
                } else if (!countInfo.isEmpty()) {
                    message = countInfo + " Players: No players online";
                } else {
                    // Ultimate fallback - just show description
                    message = description.isEmpty() ? "Player list unavailable" : description;
                }
            }

            String formatted = "§a[📋 " + serverName + "] §f" + message;

            if (server != null) {
                server.execute(() -> {
                    broadcastServerSystemMessageRespectingFilters(toMinecraftComponentWithLinks(formatted));
                });
            }

        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error processing player list embed", e);
        }
    }

    public MinecraftServer getServer() {
        return server;
    }
}
