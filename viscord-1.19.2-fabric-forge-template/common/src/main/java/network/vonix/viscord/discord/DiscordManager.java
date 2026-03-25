package network.vonix.viscord.discord;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.ViscordConfig;
import network.vonix.viscord.utils.DiscordFormatter;
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
    private FluxerReceiver fluxerReceiver;
    private final FluxerBotClient fluxerBotClient;

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
    private String originalDiscordWebhookUrl;

    private DiscordManager() {
        this.botClient = new BotClient();
        this.webhookClient = new WebhookClient();
        this.messageConverter = new MessageConverter();
        this.fluxerBotClient = new FluxerBotClient();
    }

    public static DiscordManager getInstance() {
        if (instance == null) {
            instance = new DiscordManager();
        }
        return instance;
    }

    public boolean isRunning() {
        if (!running) return false;
        String platform = ViscordConfig.CONFIG.platform.get();
        if ("fluxer".equalsIgnoreCase(platform)) {
            // Fluxer only uses webhooks, no bot connection needed, EXCEPT if bot status is enabled
            if (ViscordConfig.CONFIG.setBotStatus.get() && botClient != null) {
                return true; // We don't strictly require the bot to be connected for Fluxer to be "running", as it can still send webhooks.
            }
            return true;
        }
        return botClient.isConnected();
    }
    
    private boolean isFluxer() {
        return "fluxer".equalsIgnoreCase(ViscordConfig.CONFIG.platform.get());
    }
    
    private String getMainWebhookUrl() {
        if (isFluxer()) {
            return ViscordConfig.CONFIG.fluxerWebhookUrl.get();
        }
        return ViscordConfig.CONFIG.discordWebhookUrl.get();
    }
    
    private String getEventWebhookUrl() {
        if (isFluxer()) {
            String eventUrl = ViscordConfig.CONFIG.fluxerEventWebhookUrl.get();
            return eventUrl != null && !eventUrl.isEmpty() ? eventUrl : ViscordConfig.CONFIG.fluxerWebhookUrl.get();
        }
        return ViscordConfig.CONFIG.discordWebhookUrl.get();
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
        
        // Determine platform
        String platform = ViscordConfig.CONFIG.platform.get();
        boolean useFluxer = "fluxer".equalsIgnoreCase(platform);
        boolean tridirectional = ViscordConfig.CONFIG.enableTridirectionalChat.get();
        
        if (tridirectional) {
            Viscord.LOGGER.info("[Viscord] Initializing Tridirectional Chat (Discord & Fluxer)");
            initializeFluxer();
            initializeDiscord(false);
        } else if (useFluxer) {
            Viscord.LOGGER.info("[Viscord] Initializing with Fluxer platform");
            initializeFluxer();
        } else {
            Viscord.LOGGER.info("[Viscord] Initializing with Discord platform");
            initializeDiscord(false);
        }
    }
    
    private void initializeFluxer() {
        // 1. Initialize Fluxer webhook URLs
        String fluxerWebhookUrl = ViscordConfig.CONFIG.fluxerWebhookUrl.get();
        String fluxerEventWebhookUrl = ViscordConfig.CONFIG.fluxerEventWebhookUrl.get();
        String apiKey = ViscordConfig.CONFIG.fluxerApiKey.get();
        
        if (fluxerWebhookUrl == null || fluxerWebhookUrl.isEmpty()) {
            Viscord.LOGGER.error("[Fluxer] No webhook URL configured! Please set fluxer.webhook_url in config.");
            this.running = false;
            return;
        }
        
        this.webhookClient.updateUrl(fluxerWebhookUrl);
        
        // Store event webhook for later use (Fluxer uses separate webhooks)
        if (fluxerEventWebhookUrl != null && !fluxerEventWebhookUrl.isEmpty()) {
            this.eventChannelId = "fluxer_events"; // Marker for Fluxer event mode
        } else {
            this.eventChannelId = "fluxer_main";
        }
        
        // 2. Start Fluxer receiver for incoming messages
        int port = ViscordConfig.CONFIG.fluxerReceiverPort.get();
        String path = ViscordConfig.CONFIG.fluxerReceiverPath.get();
        
        try {
            this.fluxerReceiver = new FluxerReceiver(port, path, this::onFluxerMessage);
            this.fluxerReceiver.start();
            Viscord.LOGGER.info("[Fluxer] Receiver started on http://localhost:{}{}", port, path);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Fluxer] Failed to start HTTP receiver", e);
            this.running = false;
            return;
        }
        
        // 3. Initialize Sub-systems (player preferences work regardless of platform)
        Path configDir = dev.architectury.platform.Platform.getConfigFolder().resolve("viscord");
        // Ensure directory exists
        if (!configDir.toFile().exists()) {
            configDir.toFile().mkdirs();
        }
        try {
            this.playerPreferences = new PlayerPreferences(configDir);
            // Account linking is Discord-specific, skip for Fluxer
        } catch (IOException e) {
            Viscord.LOGGER.error("[Fluxer] Failed to load data managers", e);
        }
        
        Viscord.LOGGER.info("[Fluxer] Fluxer integration initialized with webhook and receiver.");
        
        // 4. Connect Fluxer Bot for status if enabled
        if (ViscordConfig.CONFIG.setBotStatus.get()) {
            String token = ViscordConfig.CONFIG.fluxerApiKey.get();
            if (token != null && !token.isEmpty() && !token.equals("YOUR_FLUXER_API_KEY")) {
                this.fluxerBotClient.connect(token).thenRun(() -> {
                    updateBotStatus();
                });
            } else {
                Viscord.LOGGER.warn("[Fluxer] set_bot_status is enabled but fluxer.api_key (Bot Token) is not configured!");
            }
        }
        
        // 5. Send Startup Message for Fluxer
        if (!ViscordConfig.CONFIG.enableTridirectionalChat.get()) {
            sendStartupEmbed(ViscordConfig.CONFIG.serverName.get());
        }
    }
    
    private void onFluxerMessage(String username, String message, String avatarUrl) {
        if (server == null) return;
        
        // Apply similar filtering as Discord messages
        if (ViscordConfig.CONFIG.filterByPrefix.get()) {
            String serverPrefix = ViscordConfig.CONFIG.serverPrefix.get();
            if (serverPrefix != null && !serverPrefix.isEmpty()) {
                if (username.startsWith(serverPrefix)) {
                    return;
                }
            }
        }
        
        // Format message for Minecraft
        String convertedMessage = DiscordFormatter.convertDiscordToMinecraftFormatting(message);
        String rawFormat = ViscordConfig.CONFIG.discordToMinecraftFormat.get()
                .replace("{username}", username)
                .replace("{message}", convertedMessage);
        
        // Replace [Discord] with [Fluxer] for clarity
        String formatted = rawFormat.replace("[Discord]", "[Fluxer]");
        
        Component finalComponent = toMinecraftComponentWithLinks(formatted);
        
        // Broadcast to server with player preference filtering
        server.execute(() -> {
            broadcastSystemMessageRespectingFilters(finalComponent);
        });
        
        // Tridirectional: Bridge to Discord if enabled
        if (ViscordConfig.CONFIG.enableTridirectionalChat.get() && 
            ViscordConfig.CONFIG.fluxerToDiscord.get()) {
            // Pass the formatted content to bridgeFluxerToDiscord for proper echo detection
            bridgeFluxerToDiscord(username, formatted);
        }
    }
    
    private void initializeDiscord(boolean isStatusOnly) {
        // 1. Initialize Discord Clients
        String webhookUrl = ViscordConfig.CONFIG.discordWebhookUrl.get();
        String botToken = ViscordConfig.CONFIG.discordBotToken.get();
        String channelId = ViscordConfig.CONFIG.discordChannelId.get();

        this.originalDiscordWebhookUrl = webhookUrl;
        
        // Only update webhook URL if we're not exclusively using Fluxer for webhooks
        if (!isFluxer() || ViscordConfig.CONFIG.enableTridirectionalChat.get()) {
            this.webhookClient.updateUrl(webhookUrl);
        }

        // Determine event channel
        String pEventChannelId = ViscordConfig.CONFIG.eventChannelId.get();
        if (pEventChannelId != null && !pEventChannelId.isEmpty()) {
            this.eventChannelId = pEventChannelId;
            Viscord.LOGGER.info("[Discord] Using separate channel for events: {}", pEventChannelId);
        } else {
            this.eventChannelId = channelId;
            Viscord.LOGGER.info("[Discord] Using main channel for events: {}", channelId);
        }

        // 2. Initialize Sub-systems
        Path configDir = dev.architectury.platform.Platform.getConfigFolder().resolve("viscord");
        // Ensure directory exists
        if (!configDir.toFile().exists()) {
            configDir.toFile().mkdirs();
        }
        try {
            this.playerPreferences = new PlayerPreferences(configDir);
            if (ViscordConfig.CONFIG.enableAccountLinking.get()) {
                this.linkedAccountsManager = new LinkedAccountsManager(configDir);
            }
        } catch (IOException e) {
            Viscord.LOGGER.error("[Discord] Failed to load data managers", e);
        }

        // 3. Connect Bot
        Viscord.LOGGER.info("[Discord] Attempting to connect bot with token [REDACTED] to channel: {}", channelId);
        this.botClient.setMessageHandler(this::onDiscordMessage);
        this.botClient.connect(botToken, channelId).thenRunAsync(() -> {
            // 4. Send Startup Message (only after connection and if not status-only)
            // Added 5s delay to ensure permissions are cached
            if (isStatusOnly) {
                Viscord.LOGGER.info("[Discord] Bot connected successfully for status updates only.");
            } else {
                Viscord.LOGGER.info("[Discord] Bot connected successfully, sending startup embed to channel: {}", eventChannelId);
                sendStartupEmbed(ViscordConfig.CONFIG.serverName.get());
            }
            // 5. Set initial bot status
            updateBotStatus();
        }, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)).exceptionally(throwable -> {
            Viscord.LOGGER.error("[Discord] Failed to connect bot to Discord: {}", throwable.getMessage());
            Viscord.LOGGER.error("[Discord] Please check: 1) Bot token is correct, 2) Bot is in server, 3) Channel ID is correct, 4) Bot has Message Content Intent enabled");
            return null;
        });

        Viscord.LOGGER.info("[Discord] Discord integration initialized.");
    }

    public void shutdown() {
        if (!running)
            return;

        Viscord.LOGGER.info("[Discord] Sending shutdown message...");
        try {
            // Use non-blocking async approach with timeout instead of blocking .get()
            sendShutdownEmbed(ViscordConfig.CONFIG.serverName.get())
                .orTimeout(3, TimeUnit.SECONDS)
                .whenComplete((msg, error) -> {
                    if (error != null) {
                        Viscord.LOGGER.warn("[Discord] Failed to send shutdown message: {}", error.getMessage());
                    } else {
                        Viscord.LOGGER.info("[Discord] Shutdown message sent successfully");
                    }
                    
                    // Continue cleanup after message is sent or times out
                    continueShutdown();
                });
            
            // Give a short time for the async operation to complete
            // but don't block the main thread
            Thread.sleep(100);
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Failed to send shutdown message: {}", e.getMessage());
            continueShutdown();
        }

        running = false;
    }
    
    private void continueShutdown() {
        // Stop Fluxer receiver if running
        if (fluxerReceiver != null) {
            try {
                fluxerReceiver.stop();
            } catch (Exception e) {
                Viscord.LOGGER.error("[Fluxer] Error stopping receiver: {}", e.getMessage());
            }
        }
        
        // Disconnect bot client with error handling
        if (botClient != null) {
            try {
                botClient.disconnect();
            } catch (Exception e) {
                Viscord.LOGGER.error("[Discord] Error disconnecting bot client: {}", e.getMessage());
            }
        }

        // Disconnect Fluxer bot client with error handling
        if (fluxerBotClient != null) {
            try {
                fluxerBotClient.disconnect();
            } catch (Exception e) {
                Viscord.LOGGER.error("[Fluxer] Error disconnecting Fluxer bot client: {}", e.getMessage());
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
        String mainChannelId = ViscordConfig.CONFIG.discordChannelId.get();
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

        // Handle /link command for account linking
        if (message.getContent().startsWith("/link ")) {
            handleLinkCommand(event);
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

        // Store original message for tridirectional bridging
        String authorName = message.getAuthor().getDisplayName();
        String content = message.getContent();
        
        // Process for Minecraft (existing functionality)
        processDiscordMessageForMinecraft(event);
        
        // Tridirectional: Bridge to Fluxer if enabled
        if (ViscordConfig.CONFIG.enableTridirectionalChat.get() && 
            ViscordConfig.CONFIG.discordToFluxer.get()) {
            bridgeDiscordToFluxer(authorName, content, message);
        }
    }
    
    /**
     * Bridges Discord messages to Fluxer for tridirectional chat.
     */
    private void bridgeDiscordToFluxer(String authorName, String content, Message message) {
        if (!isFluxerConfigured()) {
            return;
        }
        
        try {
            // Format message for Fluxer with source identification
            // Discord messages don't need formatting conversion as they're plain text
            String fluxerMessage = formatMessageForPlatform(content, "Discord", authorName);
            
            // Send to Fluxer via Bot API (not webhook)
            String channelId = getFluxerChannelId();
            fluxerBotClient.sendMessage(channelId, fluxerMessage);
            Viscord.LOGGER.debug("[Tridirectional] Bridged Discord message to Fluxer: {}", authorName);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Tridirectional] Failed to bridge Discord message to Fluxer", e);
        }
    }
    
    /**
     * Bridges Fluxer messages to Discord for tridirectional chat.
     */
    private void bridgeFluxerToDiscord(String username, String message) {
        if (!isDiscordConfigured()) {
            return;
        }
        
        // Prevent echo: Skip messages that originated from Discord
        // These messages have [Discord] tag from formatMessageForPlatform
        if (message.contains("[Discord]")) {
            Viscord.LOGGER.info("[Tridirectional] Skipping Discord-originated message to prevent echo loop. Message: {}", message);
            return;
        }
        
        try {
            // Convert Minecraft formatting codes to Discord markdown for Fluxer messages
            String convertedMessage = DiscordFormatter.convertToDiscordFormatting(message);
            
            // Format message for Discord with source identification
            String discordMessage = formatMessageForPlatform(convertedMessage, "Fluxer", username);
            
            // Send to Discord via webhook
            String discordWebhookUrl = ViscordConfig.CONFIG.discordWebhookUrl.get();
            if (discordWebhookUrl != null && !discordWebhookUrl.isEmpty()) {
                // Temporarily update webhook URL and send message
                String originalUrl = originalDiscordWebhookUrl;
                webhookClient.updateUrl(discordWebhookUrl);
                webhookClient.sendMessage(username, "", discordMessage);
                webhookClient.updateUrl(originalUrl); // Restore original URL
                Viscord.LOGGER.debug("[Tridirectional] Bridged Fluxer message to Discord: {}", username);
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("[Tridirectional] Failed to bridge Fluxer message to Discord", e);
        }
    }
    
    /**
     * Formats message with platform source identification.
     */
    private String formatMessageForPlatform(String message, String sourcePlatform, String authorName) {
        if (ViscordConfig.CONFIG.showPlatformSource.get()) {
            return "[" + sourcePlatform + "] " + authorName + ": " + message;
        } else {
            return authorName + ": " + message;
        }
    }
    
    /**
     * Checks if Discord is properly configured.
     */
    private boolean isDiscordConfigured() {
        String webhookUrl = ViscordConfig.CONFIG.discordWebhookUrl.get();
        return webhookUrl != null && !webhookUrl.isEmpty();
    }
    
    /**
     * Checks if Fluxer is properly configured.
     */
    private boolean isFluxerConfigured() {
        String webhookUrl = ViscordConfig.CONFIG.fluxerWebhookUrl.get();
        return webhookUrl != null && !webhookUrl.isEmpty();
    }
    
    /**
     * Processes Discord message for Minecraft (extracted from original method).
     */
    private void processDiscordMessageForMinecraft(org.javacord.api.event.message.MessageCreateEvent event) {
        if (server == null)
            return;

        Message message = event.getMessage();
        String msgChannelId = message.getChannel().getIdAsString();
        String mainChannelId = ViscordConfig.CONFIG.discordChannelId.get();
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
            Embed embed = message.getEmbeds().get(0);
            MutableComponent converted = convertEmbedToMinecraftComponent(embed, event);
            if (converted != null) {
                content = converted.getString(); // Approximate text representation

                // Manual text extraction to ensure better formatting preservation
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
            MutableComponent finalComponent = Component.empty();

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
                
                cleanedContent = DiscordFormatter.convertDiscordToMinecraftFormatting(cleanedContent);

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
                String inviteUrl = ViscordConfig.CONFIG.discordInviteUrl.get();
                String convertedContent = DiscordFormatter.convertDiscordToMinecraftFormatting(content);
                String rawFormat = ViscordConfig.CONFIG.discordToMinecraftFormat.get()
                        .replace("{username}", authorName)
                        .replace("{message}", convertedContent);

                if (rawFormat.contains("[Discord]") && inviteUrl != null && !inviteUrl.isEmpty()) {
                    String[] parts = rawFormat.split("\\[Discord\\]", 2);

                    // Part before [Discord]
                    if (parts.length > 0 && !parts[0].isEmpty()) {
                        finalComponent.append(toMinecraftComponentWithLinks(parts[0]));
                    }

                    // Clickable [Discord] with aqua color
                    finalComponent.append(Component.literal("[Discord]")
                            .setStyle(Style.EMPTY
                                    .withColor(ChatFormatting.AQUA)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, inviteUrl))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Click to join our Discord!")))));

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
            return Component.empty();
        }

        Matcher matcher = DISCORD_MARKDOWN_LINK.matcher(text);
        MutableComponent result = Component.empty();
        int lastEnd = 0;
        boolean hasLink = false;

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            if (start > lastEnd) {
                String before = text.substring(lastEnd, start);
                if (!before.isEmpty()) {
                    result.append(Component.literal(before));
                }
            }

            String label = matcher.group(1);
            String url = matcher.group(2);

            Component linkComponent = Component.literal(label)
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
                result.append(Component.literal(tail));
            }
        }

        if (!hasLink) {
            return Component.literal(text);
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
                player.sendSystemMessage(message, false);
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
                player.sendSystemMessage(message, false);
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
                player.sendSystemMessage(message, false);
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
        String webhookUrl = getMainWebhookUrl();
        
        // Convert Minecraft formatting codes to Discord markdown
        String formattedMessage = DiscordFormatter.convertToDiscordFormatting(message);
        
        // Temporarily update webhook URL if different from current
        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            webhookClient.updateUrl(webhookUrl);
            webhookClient.sendMessage(formattedUsername, avatarUrl, formattedMessage);
        }
    }
    
    public void sendMinecraftMessageToEventWebhook(String username, String message) {
        if (!running || webhookClient == null)
            return;

        String prefix = ViscordConfig.CONFIG.serverPrefix.get();
        String formattedUsername = ViscordConfig.CONFIG.webhookUsernameFormat.get()
                .replace("{prefix}", prefix)
                .replace("{username}", username);

        String avatarUrl = getAvatarUrl(username);
        String eventWebhookUrl = getEventWebhookUrl();
        
        // Convert Minecraft formatting codes to Discord markdown
        String formattedMessage = DiscordFormatter.convertToDiscordFormatting(message);
        
        // Temporarily update webhook URL if different from current
        if (eventWebhookUrl != null && !eventWebhookUrl.isEmpty()) {
            webhookClient.updateUrl(eventWebhookUrl);
            webhookClient.sendMessage(formattedUsername, avatarUrl, formattedMessage);
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

        JsonObject embed = new JsonObject();
        embedBuilder.accept(embed);

        if (isFluxer()) {
            String webhookUrl = getEventWebhookUrl();
            if (webhookUrl != null && !webhookUrl.isEmpty()) {
                String originalUrl = webhookClient.getUrl();
                webhookClient.updateUrl(webhookUrl);
                webhookClient.sendEmbed(ViscordConfig.CONFIG.serverName.get(), null, embed);
                if (originalUrl != null) {
                    webhookClient.updateUrl(originalUrl);
                }
            }
            
            // Stop here if not tridirectional
            if (!ViscordConfig.CONFIG.enableTridirectionalChat.get()) {
                return CompletableFuture.completedFuture(null);
            }
        }

        if (eventChannelId == null || eventChannelId.isEmpty()) {
            Viscord.LOGGER.warn("[Discord] Cannot send event embed - event channel ID not set");
            return CompletableFuture.completedFuture(null);
        }

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

        sendEventEmbedInternal(embed -> {
            embed.addProperty("title", "Player Died");
            embed.addProperty("description", message);
            embed.addProperty("color", 0xF04747);
        }).whenComplete((msg, error) -> {
            if (error != null) {
                Viscord.LOGGER.error("[Discord] Failed to send death embed", error);
            } else if (ViscordConfig.CONFIG.debugLogging.get()) {
                Viscord.LOGGER.debug("[Discord] Sent death embed");
            }
        });
    }

    // Cache for advancement debounce (username:title -> timestamp)
    private final java.util.Map<String, Long> recentAdvancements = new java.util.concurrent.ConcurrentHashMap<>();

    public void sendAdvancementEmbed(String username, String title, String desc) {
        if (!ViscordConfig.CONFIG.sendAdvancement.get())
            return;

        long now = System.currentTimeMillis();
        String key = username + ":" + title;
        if (recentAdvancements.containsKey(key)) {
            if (now - recentAdvancements.get(key) < 5000) { // 5 second debounce
                return;
            }
        }
        recentAdvancements.put(key, now);
        
        // Ensure cache doesn't grow indefinitely
        if (recentAdvancements.size() > 100) {
            recentAdvancements.clear();
            recentAdvancements.put(key, now);
        }

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
        if (server == null || !ViscordConfig.CONFIG.setBotStatus.get()) {
            return;
        }
        
        int online = server.getPlayerList().getPlayerCount();
        int max = server.getPlayerList().getMaxPlayers();
        String format = ViscordConfig.CONFIG.botStatusFormat.get();
        String status = format.replace("{online}", String.valueOf(online))
                              .replace("{max}", String.valueOf(max));
        
        // Update status asynchronously to avoid blocking main thread
        Viscord.ASYNC_EXECUTOR.submit(() -> {
            if (botClient != null && botClient.isConnected()) {
                botClient.updateStatus(status);
            }
            if (fluxerBotClient != null && fluxerBotClient.isConnected()) {
                fluxerBotClient.updateStatus(status);
            }
        });
    }
    
    /**
     * Schedules a status update after a delay (used for player join/leave events).
     * Non-blocking and thread-safe.
     */
    public void scheduleStatusUpdate(int delayMs) {
        if (server == null || !ViscordConfig.CONFIG.setBotStatus.get()) {
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
        if (linkedAccountsManager == null) {
            Viscord.LOGGER.error("[Viscord] Cannot generate link code - linkedAccountsManager is null. Account linking may be disabled or Discord bot not initialized.");
            return null;
        }
        
        if (!ViscordConfig.CONFIG.enableAccountLinking.get()) {
            Viscord.LOGGER.warn("[Viscord] Account linking is disabled in configuration");
            return null;
        }
        
        if (!running) {
            Viscord.LOGGER.warn("[Viscord] Cannot generate link code - Discord bot is not running. Please check bot configuration and connection.");
            return null;
        }
        
        Viscord.LOGGER.info("[Viscord] Generating link code for player: {}", player.getName().getString());
        return linkedAccountsManager.generateLinkCode(player.getUUID(), player.getName().getString());
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

    private void handleLinkCommand(org.javacord.api.event.message.MessageCreateEvent event) {
        try {
            if (!ViscordConfig.CONFIG.enableAccountLinking.get()) {
                event.getChannel().sendMessage("❌ Account linking is disabled.");
                return;
            }

            String content = event.getMessage().getContent().trim();
            String[] parts = content.split(" ", 2);
            
            if (parts.length < 2) {
                event.getChannel().sendMessage("❌ Usage: `/link <code>`\nGet a code with `/viscord discord link` in Minecraft.");
                return;
            }

            String code = parts[1].trim();
            String discordId = event.getMessageAuthor().getIdAsString();
            String discordUsername = event.getMessageAuthor().getDisplayName();

            if (linkedAccountsManager == null) {
                event.getChannel().sendMessage("❌ Account linking system is not available.");
                return;
            }

            LinkedAccountsManager.LinkResult result = linkedAccountsManager.verifyAndLink(code, discordId, discordUsername);
            
            if (result.success) {
                event.getChannel().sendMessage("✅ " + result.message);
            } else {
                event.getChannel().sendMessage("❌ " + result.message);
            }

        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error handling /link command", e);
            event.getChannel().sendMessage("❌ An error occurred while processing your link request.");
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
            if (embed.getAuthor().isPresent()) {
                serverName = embed.getAuthor().get().getName();
            } else if (embed.getTitle().isPresent()) {
                serverName = embed.getTitle().get();
            }

            // Extract content logic
            String description = embed.getDescription().orElse("");
            String message;

            if (description.contains("No players are currently online")) {
                message = "0 Players: No players online";
            } else {
                String[] lines = description.split("\n");
                String countStr = "";
                List<String> players = new ArrayList<>();

                for (String line : lines) {
                    if (line.trim().startsWith("Players")) {
                        countStr = line.trim().replace("Players", "").trim();
                    } else if (line.trim().startsWith("-")) {
                        players.add(line.trim().substring(1).trim());
                    }
                }

                if (!countStr.isEmpty()) {
                    String playerList = String.join(", ", players);
                    message = countStr + ": " + playerList;
                } else {
                    message = "Online: " + description;
                }
            }

            String formatted = "§a[" + serverName + "] §f" + message;

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
