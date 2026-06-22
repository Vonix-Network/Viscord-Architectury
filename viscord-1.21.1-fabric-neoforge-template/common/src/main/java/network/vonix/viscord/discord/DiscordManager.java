package network.vonix.viscord.discord;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import network.vonix.viscord.discord.platform.DiscordPlatform;
import network.vonix.viscord.discord.platform.FluxerPlatform;
import network.vonix.viscord.discord.platform.TridirectionalBridge;
import network.vonix.viscord.utils.DiscordFormatter;
import network.vonix.viscord.discord.MessageConverter;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.Embed;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javacord.api.event.message.MessageCreateEvent;

/**
 * Thin coordinator for all platform integrations.
 *
 * Owns: Minecraft message processing, player preferences, account linking,
 *       advancement debounce, and the public API surface.
 *
 * Delegates to: DiscordPlatform, FluxerPlatform, TridirectionalBridge.
 */
public class DiscordManager {

    private static volatile DiscordManager instance;

    // Platform delegates
    private final DiscordPlatform discordPlatform = new DiscordPlatform();
    private final FluxerPlatform fluxerPlatform = new FluxerPlatform();
    private volatile TridirectionalBridge bridge;

    // Minecraft-specific processing
    private final EventEmbedDetector eventDetector = new EventEmbedDetector();
    private final AdvancementEmbedDetector advancementDetector = new AdvancementEmbedDetector();
    private final EventDataExtractor eventExtractor = new EventDataExtractor();
    private final AdvancementDataExtractor advancementExtractor = new AdvancementDataExtractor();
    private final VanillaComponentBuilder componentBuilder = new VanillaComponentBuilder();
    private static final Pattern DISCORD_MARKDOWN_LINK =
        Pattern.compile("\\[([^\\]]+)]\\((https?://[^)]+)\\)");

    /** Strict format for /link &lt;code&gt;: exactly 6 ASCII digits. Anything else is rejected pre-lookup. */
    private static final Pattern LINK_CODE_FORMAT = Pattern.compile("\\d{6}");

    /** Bot-side text-trigger rate limiter (/link, !list). Lazily initialized on first use. */
    private final DiscordCommandRateLimiter rateLimiter = new DiscordCommandRateLimiter();

    // Server + sub-systems
    private volatile MinecraftServer server;
    private volatile LinkedAccountsManager linkedAccountsManager;
    private volatile PlayerPreferences playerPreferences;

    private volatile boolean running = false;

    // Coalesces bursts of scheduleStatusUpdate (e.g. mass-join) into a single update
    private final java.util.concurrent.atomic.AtomicBoolean statusUpdatePending =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    // Advancement debounce cache — bounded LRU with TTL-on-read in sendAdvancementEmbed.
    // access-order LinkedHashMap evicts least-recently-used once size exceeds 256.
    private final java.util.Map<String, Long> recentAdvancements =
        java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, Long>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Long> e) {
                return size() > 256;
            }
        });

    private DiscordManager() {}

    public static synchronized DiscordManager getInstance() {
        if (instance == null) instance = new DiscordManager();
        return instance;
    }

    /** Resets the singleton so the next getInstance() returns a fresh instance. Call before reload. */
    public static synchronized void resetInstance() {
        instance = null;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void initialize(MinecraftServer server) {
        if (!ViscordConfigToml.General.ENABLED.get()) {
            Viscord.LOGGER.info("[Viscord] Disabled in config.");
            return;
        }
        if (this.running) {
            Viscord.LOGGER.warn("[Viscord] Already initialized, skipping.");
            return;
        }
        this.server = server;
        this.running = true;

        discordPlatform.setServer(server);
        fluxerPlatform.setServer(server);

        initSubSystems();

        String platform = ViscordConfigToml.General.PLATFORM.get();
        boolean useFluxer = "fluxer".equalsIgnoreCase(platform);
        boolean useBoth = "both".equalsIgnoreCase(platform);
        boolean tridirectional = ViscordConfigToml.Tridirectional.ENABLED.get();

        if (tridirectional) {
            Viscord.LOGGER.info("[Viscord] Initializing Tridirectional Chat (Discord & Fluxer)");
            bridge = new TridirectionalBridge(discordPlatform, fluxerPlatform);
            fluxerPlatform.setMessageListener(this::onFluxerMessage);
            boolean fluxerOk = fluxerPlatform.initialize();
            if (!fluxerOk) { this.running = false; return; }
            discordPlatform.setMessageListener(this::onDiscordMessage);
            discordPlatform.initialize(false);
        } else if (useBoth) {
            Viscord.LOGGER.info("[Viscord] Initializing with Both platforms (Discord & Fluxer, no chat bridging)");
            fluxerPlatform.setMessageListener(this::onFluxerMessage);
            boolean fluxerOk = fluxerPlatform.initialize();
            if (!fluxerOk) { this.running = false; return; }
            discordPlatform.setMessageListener(this::onDiscordMessage);
            discordPlatform.initialize(false);
        } else if (useFluxer) {
            Viscord.LOGGER.info("[Viscord] Initializing with Fluxer platform");
            fluxerPlatform.setMessageListener(this::onFluxerMessage);
            boolean fluxerOk = fluxerPlatform.initialize();
            if (!fluxerOk) { this.running = false; return; }
        } else {
            Viscord.LOGGER.info("[Viscord] Initializing with Discord platform");
            discordPlatform.setMessageListener(this::onDiscordMessage);
            discordPlatform.initialize(false);
        }
    }

    private void initSubSystems() {
        Path configDir = dev.architectury.platform.Platform.getConfigFolder().resolve("viscord");
        if (!configDir.toFile().exists()) configDir.toFile().mkdirs();
        try {
            if (playerPreferences == null)
                playerPreferences = new PlayerPreferences(configDir);
            if (ViscordConfigToml.AccountLinking.ENABLED.get() && linkedAccountsManager == null)
                linkedAccountsManager = new LinkedAccountsManager(configDir);
        } catch (IOException e) {
            Viscord.LOGGER.error("[Viscord] Failed to load sub-systems", e);
        }
    }

    public void shutdown() {
        if (!running) return;
        Viscord.LOGGER.info("[Viscord] Shutting down...");
        running = false;
        try {
            String serverName = ViscordConfigToml.Server.NAME.get();

            if (usesFluxer()) {
                JsonObject embed = new JsonObject();
                EmbedFactory.createServerStatusEmbed("Server Offline", "Server is shutting down", 0xF04747, serverName, "Viscord · Server Offline").accept(embed);
                fluxerPlatform.sendEventEmbed(embed);
            }

            if (usesDiscord()) {
                CompletableFuture<?> shutdownFuture = discordPlatform.shutdown();
                if (shutdownFuture != null) {
                    try {
                        shutdownFuture.orTimeout(3, TimeUnit.SECONDS).join();
                    } catch (Exception ignored) {}
                }
            }

            fluxerPlatform.shutdown();
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Viscord] Shutdown message failed: {}", e.getMessage());
            continueShutdown();
        }
    }

    private void continueShutdown() {
        discordPlatform.shutdown();
        fluxerPlatform.shutdown();
    }

    // =========================================================================
    // Incoming message handlers
    // =========================================================================

    private void onDiscordMessage(MessageCreateEvent event) {
        if (server == null) return;
        Message message = event.getMessage();
        String msgChannelId = message.getChannel().getIdAsString();
        String mainChannelId = ViscordConfigToml.Discord.CHANNEL_ID.get();
        String evtChannelId = ViscordConfigToml.Discord.Events.CHANNEL_ID.get();

        boolean isMainChannel = isInChannelList(mainChannelId, msgChannelId);
        boolean isEventChannel = evtChannelId != null && !evtChannelId.isEmpty()
            && isInChannelList(evtChannelId, msgChannelId);

        if (!isMainChannel && !isEventChannel) return;

        // Always block messages originating from this server (webhook ID, bot ID, or prefix match)
        // regardless of IGNORE_BOTS / IGNORE_WEBHOOKS / FILTER_BY_PREFIX settings.
        if (isSelfOriginated(message)) return;

        if (message.getContent().trim().equalsIgnoreCase("!list")) {
            handleTextListCommand(event); return;
        }
        if (message.getContent().startsWith("/link ")) {
            handleLinkCommand(event); return;
        }
        if (isEventChannel && !ViscordConfigToml.Filters.SHOW_OTHER_SERVER_EVENTS.get()) return;
        boolean trusted = isTrustedAuthor(message.getAuthor());
        if (!trusted && ViscordConfigToml.Filters.IGNORE_BOTS.get() && message.getAuthor().isBotUser()) return;
        if (!trusted && ViscordConfigToml.Filters.IGNORE_WEBHOOKS.get() && message.getAuthor().isWebhook()) return;

        String authorName = resolveAuthorName(message.getAuthor());
        String content = message.getContent();

        processDiscordMessageForMinecraft(event);

        if (bridge != null && ViscordConfigToml.Tridirectional.ENABLED.get()
                && ViscordConfigToml.Tridirectional.DISCORD_TO_FLUXER.get()) {
            bridge.discordToFluxer(authorName, content, message);
        }
    }

    private void onFluxerMessage(String username, String message, String avatarUrl) {
        if (server == null) return;

        // Echo suppression via bridge cache
        if (bridge != null && bridge.checkAndSuppressEcho(username, message)) return;

        // In "both" mode, cross-server messages are read from Discord only to avoid duplicates
        if (isBoth() && isOtherServerUsername(username)) return;

        // Handle !list command from Fluxer
        if (message.trim().equalsIgnoreCase("!list")) {
            handleFluxerListCommand();
            return;
        }

        if (ViscordConfigToml.Filters.FILTER_BY_PREFIX.get()) {
            String prefix = ViscordConfigToml.Server.PREFIX.get();
            if (prefix != null && !prefix.isEmpty() && username.startsWith(prefix)) return;
        }

        String converted = DiscordFormatter.convertDiscordToMinecraftFormatting(message);
        String rawFormat = ViscordConfigToml.Messages.DISCORD_TO_MINECRAFT.get()
            .replace("{username}", username).replace("{message}", converted);
        String formatted = rawFormat.replace("[Discord]", "[Fluxer]");

        Component component = toMinecraftComponentWithLinks(formatted);
        server.execute(() -> broadcastSystemMessageRespectingFilters(component));

        if (bridge != null && ViscordConfigToml.Tridirectional.ENABLED.get()
                && ViscordConfigToml.Tridirectional.FLUXER_TO_DISCORD.get()) {
            bridge.fluxerToDiscord(username, formatted, avatarUrl);
        }
    }

    // =========================================================================
    // Discord message → Minecraft processing (unchanged logic, same file)
    // =========================================================================

    private void processDiscordMessageForMinecraft(MessageCreateEvent event) {
        if (server == null) return;
        Message message = event.getMessage();
        String msgChannelId = message.getChannel().getIdAsString();
        String mainChannelId = ViscordConfigToml.Discord.CHANNEL_ID.get();
        String evtChannelId = ViscordConfigToml.Discord.Events.CHANNEL_ID.get();

        boolean isMainChannel = isInChannelList(mainChannelId, msgChannelId);
        boolean isEventChannel = evtChannelId != null && !evtChannelId.isEmpty()
            && isInChannelList(evtChannelId, msgChannelId);

        if (!isMainChannel && !isEventChannel) return;

        if (isEventChannel && !ViscordConfigToml.Filters.SHOW_OTHER_SERVER_EVENTS.get()) return;
        boolean trusted = isTrustedAuthor(message.getAuthor());
        if (!trusted && ViscordConfigToml.Filters.IGNORE_BOTS.get() && message.getAuthor().isBotUser()) return;
        if (!trusted && ViscordConfigToml.Filters.IGNORE_WEBHOOKS.get() && message.getAuthor().isWebhook()) return;

        if (ViscordConfigToml.Filters.FILTER_BY_PREFIX.get() && (message.getAuthor().isWebhook() || message.getAuthor().isBotUser())) {
            String serverPrefix = ViscordConfigToml.Server.PREFIX.get();
            if (serverPrefix != null && !serverPrefix.isEmpty()) {
                String authorName = message.getAuthor().getDisplayName();
                if (authorName.startsWith(serverPrefix)) return;
                String webhookFormat = ViscordConfigToml.Messages.WEBHOOK_USERNAME.get();
                if (webhookFormat != null && webhookFormat.contains("{prefix}")) {
                    String expectedStart = webhookFormat.split("\\{prefix\\}", 2)[0] + serverPrefix;
                    if (authorName.startsWith(expectedStart) || authorName.startsWith(serverPrefix)) return;
                }
            }
        }

        boolean isWebhook = message.getAuthor().isWebhook();
        String authorName = resolveAuthorName(message.getAuthor());
        String content = message.getContent();

        if (!message.getEmbeds().isEmpty()) {
            for (Embed embed : message.getEmbeds()) {
                if (advancementDetector.isAdvancementEmbed(embed)) { processAdvancementEmbed(embed, event); return; }
                if (eventDetector.isEventEmbed(embed)) { processEventEmbed(embed, event); return; }
                if (isPlayerListEmbed(embed)) { processPlayerListEmbed(embed, event); return; }
            }
        }

        if (content.isEmpty() && !message.getEmbeds().isEmpty()) {
            Embed embed = message.getEmbeds().get(0);
            MutableComponent converted = convertEmbedToMinecraftComponent(embed, event);
            if (converted != null) {
                StringBuilder embedContent = new StringBuilder();
                embed.getAuthor().ifPresent(a -> embedContent.append(a.getName()).append(" "));
                embed.getTitle().ifPresent(t -> {
                    String s = t.replaceAll("[^a-zA-Z ]", "").trim();
                    if (!s.equalsIgnoreCase("Player Joined") && !s.equalsIgnoreCase("Player Left")
                            && !s.equalsIgnoreCase("Player Died")) embedContent.append(t).append(" ");
                });
                embed.getDescription().ifPresent(d -> embedContent.append(d).append(" "));
                for (org.javacord.api.entity.message.embed.EmbedField field : embed.getFields()) {
                    String fn = field.getName();
                    if ((fn.equalsIgnoreCase("Server") || fn.equalsIgnoreCase("Message")) &&
                            !embed.getTitle().map(t -> t.contains("List") || t.contains("Status")).orElse(false) &&
                            !embed.getFooter().map(f -> f.getText().map(text -> text.contains("Player List")).orElse(false)).orElse(false))
                        continue;
                    embedContent.append("[").append(fn).append(": ").append(field.getValue()).append("] ");
                }
                content = embedContent.toString().trim();
            }
        }

        MutableComponent finalComponent = Component.empty();
        if (isWebhook) {
            String cleanedContent = content;
            if (content.startsWith(authorName + ": ")) cleanedContent = content.substring(authorName.length() + 2);
            else if (content.startsWith(authorName + " ")) cleanedContent = content.substring(authorName.length() + 1);
            cleanedContent = DiscordFormatter.convertDiscordToMinecraftFormatting(cleanedContent);

            String formattedMessage;
            if (authorName.startsWith("[") && authorName.contains("]")) {
                int end = authorName.indexOf("]");
                String prefix = authorName.substring(0, end + 1);
                String remaining = authorName.substring(end + 1).trim();
                String eventChanId = ViscordConfigToml.Discord.Events.CHANNEL_ID.get();
                boolean isEvt = eventChanId != null && !eventChanId.isEmpty() && eventChanId.equals(msgChannelId);
                if (isEvt || remaining.isEmpty() || remaining.toLowerCase().contains("server"))
                    formattedMessage = "§a" + prefix + " §f" + cleanedContent;
                else
                    formattedMessage = "§a" + prefix + " §f" + remaining + "§7: §f" + cleanedContent;
            } else {
                formattedMessage = "§a[Cross-Server] §f" + authorName + "§7: §f" + cleanedContent;
            }
            finalComponent.append(toMinecraftComponentWithLinks(formattedMessage));
        } else {
            String inviteUrl = ViscordConfigToml.Discord.INVITE_URL.get();
            String convertedContent = DiscordFormatter.convertDiscordToMinecraftFormatting(content);
            String rawFormat = ViscordConfigToml.Messages.DISCORD_TO_MINECRAFT.get()
                .replace("{username}", authorName).replace("{message}", convertedContent);

            if (rawFormat.contains("[Discord]") && inviteUrl != null && !inviteUrl.isEmpty()) {
                String[] parts = rawFormat.split("\\[Discord\\]", 2);
                if (parts.length > 0 && !parts[0].isEmpty())
                    finalComponent.append(toMinecraftComponentWithLinks(parts[0]));
                finalComponent.append(Component.literal("[Discord]").setStyle(Style.EMPTY
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, inviteUrl))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to join our Discord!")))));
                if (parts.length > 1 && !parts[1].isEmpty())
                    finalComponent.append(toMinecraftComponentWithLinks(parts[1]));
            } else {
                finalComponent.append(toMinecraftComponentWithLinks(rawFormat));
            }
        }

        server.execute(() -> broadcastSystemMessageRespectingFilters(finalComponent));
    }

    // =========================================================================
    // Embed processing (Discord → Minecraft)
    // =========================================================================

    private void processEventEmbed(Embed embed, MessageCreateEvent event) {
        try {
            EventData data = eventExtractor.extractFromEmbed(embed);
            String prefix = extractServerPrefixFromAuthor(event.getMessageAuthor().getDisplayName());
            MutableComponent comp = componentBuilder.buildEventMessage(data, prefix);
            if (server != null) server.execute(() -> broadcastEventMessageRespectingFilters(comp));
        } catch (ExtractionException e) {
            Viscord.LOGGER.warn("[Discord] Failed to extract event data: {}", e.getMessage());
            handleEmbedFallback(embed, event);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error processing event embed", e);
            handleEmbedFallback(embed, event);
        }
    }

    private void processAdvancementEmbed(Embed embed, MessageCreateEvent event) {
        try {
            AdvancementData data = advancementExtractor.extractFromEmbed(embed);
            String prefix = extractServerPrefixFromAuthor(event.getMessageAuthor().getDisplayName());
            MutableComponent comp = componentBuilder.buildAdvancementMessage(data, prefix);
            if (server != null) server.execute(() -> broadcastEventMessageRespectingFilters(comp));
        } catch (ExtractionException e) {
            Viscord.LOGGER.warn("[Discord] Failed to extract advancement data: {}", e.getMessage());
            handleEmbedFallback(embed, event);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error processing advancement embed", e);
            handleEmbedFallback(embed, event);
        }
    }

    private void handleEmbedFallback(Embed embed, MessageCreateEvent event) {
        try {
            MutableComponent comp = convertEmbedToMinecraftComponent(embed, event);
            if (comp != null && server != null) {
                server.execute(() -> broadcastEventMessageRespectingFilters(comp)); return;
            }
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Embed conversion fallback failed: {}", e.getMessage());
        }
        try {
            Component fallback = MessageConverter.toMinecraft(event.getMessage());
            if (server != null) server.execute(() -> broadcastEventMessageRespectingFilters(fallback));
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] All fallback strategies failed for embed", e);
        }
    }

    private MutableComponent convertEmbedToMinecraftComponent(Embed embed, MessageCreateEvent event) {
        if (embed == null) return null;
        try {
            StringBuilder content = new StringBuilder();
            embed.getAuthor().ifPresent(a -> { if (a.getName() != null && !a.getName().trim().isEmpty()) content.append(a.getName().trim()).append(" "); });
            embed.getTitle().ifPresent(t -> { if (!t.trim().isEmpty()) content.append(t.trim()).append(" "); });
            for (org.javacord.api.entity.message.embed.EmbedField field : embed.getFields()) {
                if (field.getName() != null && field.getValue() != null && !field.getName().trim().isEmpty() && !field.getValue().trim().isEmpty())
                    content.append("[").append(field.getName().trim()).append(": ").append(field.getValue().trim()).append("] ");
            }
            embed.getDescription().ifPresent(d -> { if (!d.trim().isEmpty()) content.append(d.trim()); });
            String text = content.toString().trim();
            if (text.isEmpty()) return null;
            String authorName = resolveAuthorName(event.getMessageAuthor());
            String formatted;
            if (authorName != null && authorName.startsWith("[") && authorName.contains("]")) {
                int end = authorName.indexOf("]");
                formatted = "§a" + authorName.substring(0, end + 1) + " §f" + text;
            } else {
                formatted = "§a[" + ViscordConfigToml.Server.PREFIX.get() + "] §f" + text;
            }
            return (MutableComponent) toMinecraftComponentWithLinks(formatted);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error converting embed to component: {}", e.getMessage());
            return null;
        }
    }

    private String extractServerPrefixFromAuthor(String authorName) {
        if (authorName == null) return "Cross-Server";
        if (authorName.startsWith("[")) {
            int end = authorName.indexOf("]");
            if (end > 1) return authorName.substring(1, end);
        }
        return "Cross-Server";
    }

    private Component toMinecraftComponentWithLinks(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        Matcher matcher = DISCORD_MARKDOWN_LINK.matcher(text);
        MutableComponent result = Component.empty();
        int lastEnd = 0;
        boolean hasLink = false;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) result.append(Component.literal(text.substring(lastEnd, matcher.start())));
            result.append(Component.literal(matcher.group(1)).withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, matcher.group(2)))
                .withUnderlined(true).withColor(ChatFormatting.AQUA)));
            lastEnd = matcher.end();
            hasLink = true;
        }
        if (lastEnd < text.length()) result.append(Component.literal(text.substring(lastEnd)));
        return hasLink ? result : Component.literal(text);
    }

    private void broadcastSystemMessageRespectingFilters(Component message) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (!hasServerMessagesFiltered(p.getUUID())) p.sendSystemMessage(message, false);
    }

    private void broadcastEventMessageRespectingFilters(Component message) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (!hasEventsFiltered(p.getUUID())) p.sendSystemMessage(message, false);
    }

    private void broadcastServerSystemMessageRespectingFilters(Component message) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (!hasServerSystemMessagesFiltered(p.getUUID())) p.sendSystemMessage(message, false);
    }

    // =========================================================================
    // Public API — Minecraft → Platform sending
    // =========================================================================

    public void sendMinecraftMessage(String username, String message) {
        sendChatMessage(username, message, null);
    }

    public void sendChatMessage(String username, String message, String uuid) {
        if (!running) return;
        String prefix = ViscordConfigToml.Server.PREFIX.get();
        String cleanUsername = DiscordFormatter.stripFormatting(username);
        String realUsername = cleanUsername;
        String resolvedUuid = uuid != null ? uuid.replace("-", "") : null;
        if (server != null) {
            ServerPlayer player = null;
            if (uuid != null && !uuid.isEmpty()) {
                try { player = server.getPlayerList().getPlayer(java.util.UUID.fromString(uuid)); }
                catch (IllegalArgumentException ignored) {}
            }
            if (player == null) player = server.getPlayerList().getPlayerByName(cleanUsername);
            if (player != null) {
                realUsername = player.getName().getString();
                resolvedUuid = player.getUUID().toString().replace("-", "");
            }
        }
        String formattedUsername = ViscordConfigToml.Messages.WEBHOOK_USERNAME.get()
            .replace("{prefix}", prefix).replace("{username}", cleanUsername);
        String avatarUrl = buildAvatarUrl(realUsername, resolvedUuid);
        String formattedMessage = DiscordFormatter.convertToDiscordFormatting(message);
        if (usesFluxer()) fluxerPlatform.sendChatMessage(formattedUsername, avatarUrl, formattedMessage);
        if (usesDiscord()) discordPlatform.sendChatMessage(formattedUsername, avatarUrl, formattedMessage);
    }

    // =========================================================================
    // Public API — Event embeds
    // =========================================================================

    public void sendStartupEmbed(String serverName) {
        if (usesFluxer()) {
            JsonObject embed = new JsonObject();
            EmbedFactory.createServerStatusEmbed("Server Online", "Server is now online", 0x43B581, serverName, "Viscord · Server Online").accept(embed);
            fluxerPlatform.sendEventEmbed(embed);
        }
        if (usesDiscord()) {
            discordPlatform.sendStartupEmbed(serverName);
        }
    }

    public CompletableFuture<org.javacord.api.entity.message.Message> sendShutdownEmbed(String serverName) {
        if (usesFluxer()) {
            JsonObject embed = new JsonObject();
            EmbedFactory.createServerStatusEmbed("Server Offline", "Server is shutting down", 0xF04747, serverName, "Viscord · Server Offline").accept(embed);
            fluxerPlatform.sendEventEmbed(embed);
        }
        if (usesDiscord()) {
            return discordPlatform.sendShutdownEmbed(serverName);
        }
        return CompletableFuture.completedFuture(null);
    }

    public void sendJoinEmbed(String username, String uuid) {
        if (!isRunning()) return;
        scheduleStatusUpdate(500);
        if (!ViscordConfigToml.Messages.Events.JOIN.get()) return;
        String avatarUrl = buildAvatarUrl(username, uuid != null ? uuid.replace("-", "") : null);
        if (usesFluxer()) {
            JsonObject embed = new JsonObject();
            EmbedFactory.createPlayerEventEmbed("Player Joined", username + " joined the game", 0x5865F2, username, ViscordConfigToml.Server.NAME.get(), "Viscord · Player Join", avatarUrl).accept(embed);
            fluxerPlatform.sendEventEmbed(embed);
        }
        if (usesDiscord()) {
            discordPlatform.sendJoinEmbed(username, avatarUrl);
        }
    }

    public void sendLeaveEmbed(String username, String uuid) {
        if (!isRunning()) return;
        scheduleStatusUpdate(500);
        if (!ViscordConfigToml.Messages.Events.LEAVE.get()) return;
        String avatarUrl = buildAvatarUrl(username, uuid != null ? uuid.replace("-", "") : null);
        if (usesFluxer()) {
            JsonObject embed = new JsonObject();
            EmbedFactory.createPlayerEventEmbed("Player Left", username + " left the game", 0x99AAB5, username, ViscordConfigToml.Server.NAME.get(), "Viscord · Player Leave", avatarUrl).accept(embed);
            fluxerPlatform.sendEventEmbed(embed);
        }
        if (usesDiscord()) {
            discordPlatform.sendLeaveEmbed(username, avatarUrl);
        }
    }

    public void sendJoinEmbed(String username) { sendJoinEmbed(username, null); }
    public void sendLeaveEmbed(String username) { sendLeaveEmbed(username, null); }

    public void sendDeathEmbed(String message) {
        if (!ViscordConfigToml.Messages.Events.DEATH.get() || !isRunning()) return;
        if (usesFluxer()) {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", "Player Died");
            embed.addProperty("description", message);
            embed.addProperty("color", 0xF04747);
            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Viscord · Player Death");
            embed.add("footer", footer);
            fluxerPlatform.sendEventEmbed(embed);
        }
        if (usesDiscord())
            discordPlatform.sendDeathEmbed(message);
    }

    public void sendAdvancementEmbed(String username, String title, String desc) {
        if (!ViscordConfigToml.Messages.Events.ADVANCEMENT.get() || !isRunning()) return;
        long now = System.currentTimeMillis();
        String key = username + ":" + title;
        synchronized (recentAdvancements) {
            Long prev = recentAdvancements.get(key);
            if (prev != null && now - prev < 5000) return; // deduped
            recentAdvancements.put(key, now);
        }
        if (usesFluxer()) {
            JsonObject embed = new JsonObject();
            EmbedFactory.createAdvancementEmbed("\uD83C\uDFC6", 0xFAA61A, username, title, desc).accept(embed);
            fluxerPlatform.sendEventEmbed(embed);
        }
        if (usesDiscord())
            discordPlatform.sendAdvancementEmbed(username, title, desc);
    }

    public void sendServerStatusMessage(String title, String description, int color) {
        if (usesDiscord()) discordPlatform.sendServerStatusMessage(title, description, color);
    }

    // =========================================================================
    // Public API — Status
    // =========================================================================

    public void updateBotStatus() {
        if (server == null || !ViscordConfigToml.BotStatus.ENABLED.get()) return;
        Viscord.ASYNC_EXECUTOR.submit(() -> {
            if (discordPlatform.isConnected()) discordPlatform.pushStatus();
            if (fluxerPlatform.isConnected()) fluxerPlatform.pushStatus();
        });
    }

    public void updateStatus() { updateBotStatus(); }

    public void scheduleStatusUpdate(int delayMs) {
        if (server == null || !ViscordConfigToml.BotStatus.ENABLED.get()) return;
        // Coalesce bursts (e.g. mass-join) into a single update
        if (!statusUpdatePending.compareAndSet(false, true)) return;
        Viscord.scheduleAsync(() -> {
            statusUpdatePending.set(false);
            updateBotStatus();
        }, delayMs);
    }

    // =========================================================================
    // Public API — State
    // =========================================================================

    public boolean isRunning() {
        if (!running) return false;
        if (isFluxer()) return true;
        if (isBoth() || ViscordConfigToml.Tridirectional.ENABLED.get()) {
            return discordPlatform.isConnected() || fluxerPlatform.isConnected();
        }
        return discordPlatform.isConnected();
    }

    private boolean isFluxer() {
        return "fluxer".equalsIgnoreCase(ViscordConfigToml.General.PLATFORM.get());
    }

    private boolean isBoth() {
        return "both".equalsIgnoreCase(ViscordConfigToml.General.PLATFORM.get());
    }

    private boolean usesFluxer() {
        return isFluxer() || isBoth() || ViscordConfigToml.Tridirectional.ENABLED.get();
    }

    private boolean usesDiscord() {
        return !isFluxer() || ViscordConfigToml.Tridirectional.ENABLED.get();
    }

    // =========================================================================
    // Player Preferences
    // =========================================================================

    public void setServerSystemMessagesFiltered(UUID uuid, boolean filtered) {
        if (playerPreferences != null) playerPreferences.setServerSystemMessagesFiltered(uuid, filtered);
    }
    public boolean hasServerSystemMessagesFiltered(UUID uuid) {
        return playerPreferences != null && playerPreferences.hasServerSystemMessagesFiltered(uuid);
    }
    public void setServerMessagesFiltered(UUID uuid, boolean filtered) {
        if (playerPreferences != null) playerPreferences.setServerMessagesFiltered(uuid, filtered);
    }
    public boolean hasServerMessagesFiltered(UUID uuid) {
        return playerPreferences != null && playerPreferences.hasServerMessagesFiltered(uuid);
    }
    public void setEventsFiltered(UUID uuid, boolean filtered) {
        if (playerPreferences != null) playerPreferences.setEventsFiltered(uuid, filtered);
    }
    public boolean hasEventsFiltered(UUID uuid) {
        return playerPreferences != null && playerPreferences.hasEventsFiltered(uuid);
    }

    // =========================================================================
    // Account Linking
    // =========================================================================

    public String generateLinkCode(ServerPlayer player) {
        if (linkedAccountsManager == null || !ViscordConfigToml.AccountLinking.ENABLED.get() || !running) return null;
        return linkedAccountsManager.generateLinkCode(player.getUUID(), player.getName().getString());
    }

    public boolean unlinkAccount(UUID uuid) {
        return linkedAccountsManager != null && linkedAccountsManager.unlinkMinecraft(uuid);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String buildAvatarUrl(String username, String uuidNoDashes) {
        String identifier = (uuidNoDashes != null && !uuidNoDashes.isEmpty()) ? uuidNoDashes : username;
        return "https://minotar.net/armor/bust/" + identifier + "/100.png";
    }

    private boolean isOtherServerUsername(String username) {
        if (username == null || !username.startsWith("[")) return false;
        int end = username.indexOf("]");
        if (end < 1) return false;
        String foundPrefix = username.substring(0, end + 1);
        String myPrefix = ViscordConfigToml.Server.PREFIX.get();
        return myPrefix != null && !foundPrefix.equalsIgnoreCase(myPrefix);
    }

    /** Returns true if channelId is contained in a comma-separated config value. */
    private static boolean isInChannelList(String configValue, String channelId) {
        if (configValue == null || configValue.isEmpty() || channelId == null) return false;
        for (String id : configValue.split(",")) {
            if (id.trim().equals(channelId)) return true;
        }
        return false;
    }

    /**
     * Returns true if this message was sent by this server's own bot or webhook,
     * blocking it unconditionally to prevent Minecraft→Discord→Minecraft echo loops.
     * Checks webhook ID (extracted from webhook URL), bot ID, and server prefix pattern.
     */
    private boolean isSelfOriginated(Message message) {
        org.javacord.api.entity.message.MessageAuthor author = message.getAuthor();

        // Regular user messages are never self-originated — only bots and webhooks are checked
        if (!author.isWebhook() && !author.isBotUser()) return false;

        // Webhook ID check — matches only our configured webhook, not arbitrary webhooks
        if (author.isWebhook()) {
            String webhookUrl = ViscordConfigToml.Discord.WEBHOOK_URL.get();
            String webhookId = extractWebhookId(webhookUrl);
            if (webhookId != null && webhookId.equals(author.getIdAsString())) return true;
        }

        // Bot ID check — bot's own user ID (complements BotClient's isYourself() guard)
        if (author.isBotUser()) {
            String botId = discordPlatform.getBotUserId();
            if (botId != null && botId.equals(author.getIdAsString())) return true;
        }

        // Prefix check — applies to both bots and webhooks sent by this server
        String serverPrefix = ViscordConfigToml.Server.PREFIX.get();
        if (serverPrefix != null && !serverPrefix.isEmpty()) {
            String authorName = author.getDisplayName();
            String webhookFormat = ViscordConfigToml.Messages.WEBHOOK_USERNAME.get();
            if (webhookFormat != null && webhookFormat.contains("{prefix}")) {
                String expectedStart = webhookFormat.split("\\{prefix\\}", 2)[0] + serverPrefix;
                if (authorName.startsWith(expectedStart)) return true;
            }
            if (authorName.startsWith(serverPrefix)) return true;
        }

        return false;
    }

    /** Extracts the numeric webhook ID from a Discord webhook URL. */
    private static String extractWebhookId(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return null;
        String[] parts = webhookUrl.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("webhooks".equals(parts[i]) && parts[i + 1].matches("\\d+")) {
                return parts[i + 1];
            }
        }
        return null;
    }

    /**
     * Returns true if this author should bypass ignoreBots/ignoreWebhooks filters.
     * Matches against the trusted_bot_ids config by Discord user/webhook ID.
     */
    private static String resolveAuthorName(org.javacord.api.entity.message.MessageAuthor author) {
        if (!ViscordConfigToml.Messages.USE_DISPLAY_NAME.get()) {
            return author.getName();
        }
        return author.getDisplayName();
    }

    private static boolean isTrustedAuthor(org.javacord.api.entity.message.MessageAuthor author) {
        String trusted = ViscordConfigToml.Filters.TRUSTED_BOT_IDS.get();
        if (trusted == null || trusted.isEmpty()) return false;
        String authorId = author.getIdAsString();
        for (String id : trusted.split(",")) {
            if (id.trim().equals(authorId)) return true;
        }
        return false;
    }

    private boolean isPlayerListEmbed(Embed embed) {
        return embed.getFooter().map(f -> f.getText().map(t -> t.contains("Player List")).orElse(false)).orElse(false)
            || embed.getTitle().map(t -> t.contains("List") || t.contains("Status")).orElse(false);
    }

    private void processPlayerListEmbed(Embed embed, MessageCreateEvent event) {
        try {
            String authorName = resolveAuthorName(event.getMessageAuthor());
            String serverPrefix = extractServerPrefixFromAuthor(authorName);

            StringBuilder sb = new StringBuilder();
            sb.append("§a[").append(serverPrefix).append("] §7Players online: ");

            boolean hasPlayers = false;
            for (org.javacord.api.entity.message.embed.EmbedField field : embed.getFields()) {
                String fieldName = field.getName();
                if (fieldName != null && fieldName.toLowerCase().startsWith("players")) {
                    String value = field.getValue();
                    if (value != null && !value.isEmpty()) {
                        String names = value.replace("• ", "").replace("\n", ", ").replaceAll(", $", "").trim();
                        sb.append("§f").append(names);
                        hasPlayers = true;
                    }
                }
            }

            if (!hasPlayers) {
                embed.getDescription().ifPresent(d -> {
                    if (!d.trim().isEmpty()) sb.append("§7").append(d.trim());
                });
                if (sb.toString().endsWith("online: ")) sb.append("§7No players online");
            }

            Component comp = toMinecraftComponentWithLinks(sb.toString());
            if (server != null) server.execute(() -> broadcastSystemMessageRespectingFilters(comp));
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Failed to format player list embed: {}", e.getMessage());
            handleEmbedFallback(embed, event);
        }
    }

    private void handleTextListCommand(MessageCreateEvent event) {
        if (server == null) return;
        String discordId = event.getMessageAuthor().getIdAsString();
        if (!rateLimiter.tryConsume(DiscordCommandRateLimiter.Command.LIST, discordId)) return;
        server.execute(() -> {
            try {
                java.util.List<net.minecraft.server.level.ServerPlayer> players = server.getPlayerList().getPlayers();
                int online = players.size();
                int max = server.getPlayerList().getMaxPlayers();
                org.javacord.api.entity.message.embed.EmbedBuilder embed =
                    new org.javacord.api.entity.message.embed.EmbedBuilder()
                        .setTitle("\uD83D\uDCCB " + ViscordConfigToml.Server.NAME.get())
                        .setColor(java.awt.Color.GREEN)
                        .setFooter("Viscord \u00B7 Player List");
                if (online == 0) {
                    embed.setDescription("No players are currently online.");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < players.size(); i++) {
                        if (i > 0) sb.append("\n");
                        sb.append("\u2022 ").append(players.get(i).getName().getString());
                    }
                    embed.addField("Players " + online + "/" + max, sb.toString(), false);
                }
                Viscord.executeAsync(() -> event.getChannel().sendMessage(embed)
                    .exceptionally(t -> { Viscord.LOGGER.error("[Discord] Failed to send !list embed", t); return null; }));
            } catch (Exception e) {
                Viscord.LOGGER.error("[Discord] Error handling !list command", e);
            }
        });
    }

    /** Handles !list command received from Fluxer -- sends player list back to Fluxer event channel. */
    private void handleFluxerListCommand() {
        if (server == null) return;
        // Fluxer's onFluxerMessage signature does not surface a stable per-user id; bucket all
        // Fluxer !list calls into a shared "fluxer" key so the global cap still applies.
        if (!rateLimiter.tryConsume(DiscordCommandRateLimiter.Command.LIST, "fluxer")) return;
        server.execute(() -> {
            try {
                java.util.List<net.minecraft.server.level.ServerPlayer> players = server.getPlayerList().getPlayers();
                int online = players.size();
                int max = server.getPlayerList().getMaxPlayers();
                StringBuilder sb = new StringBuilder();
                sb.append("\uD83D\uDCCB **").append(ViscordConfigToml.Server.NAME.get()).append("** -- Players ")
                  .append(online).append("/").append(max).append("\n");
                if (online == 0) {
                    sb.append("No players are currently online.");
                } else {
                    for (net.minecraft.server.level.ServerPlayer p : players) {
                        sb.append("\u2022 ").append(p.getName().getString()).append("\n");
                    }
                }
                fluxerPlatform.sendEventMessage(sb.toString().trim());
            } catch (Exception e) {
                Viscord.LOGGER.error("[Fluxer] Error handling !list command", e);
            }
        });
    }

    private void handleLinkCommand(MessageCreateEvent event) {
        try {
            if (!ViscordConfigToml.AccountLinking.ENABLED.get()) {
                event.getChannel().sendMessage("\u274c Account linking is disabled."); return;
            }
            String discordId = event.getMessageAuthor().getIdAsString();

            // Rate limit BEFORE doing any work (defends against brute force of 6-digit code).
            // Silent on hit: replying would let an attacker measure the limit window and pace around it,
            // and would also spam the channel under attack. Log line emitted at debug level only.
            if (!rateLimiter.tryConsume(DiscordCommandRateLimiter.Command.LINK, discordId)) {
                return;
            }

            String content = event.getMessage().getContent().trim();
            String[] parts = content.split(" ", 2);
            if (parts.length < 2) {
                event.getChannel().sendMessage("\u274c Usage: `/link <code>`"); return;
            }
            if (linkedAccountsManager == null) {
                event.getChannel().sendMessage("\u274c Account linking system is not available."); return;
            }
            String code = parts[1].trim();

            // Strict pre-validation: 6 ASCII digits. Reject anything else with a single generic
            // error so we don't help an attacker enumerate (e.g. "too short" vs "invalid code").
            if (!LINK_CODE_FORMAT.matcher(code).matches()) {
                event.getChannel().sendMessage("\u274c Invalid link code. Use `/link <6-digit code>`.");
                return;
            }

            String discordUsername = event.getMessageAuthor().getDisplayName();
            LinkedAccountsManager.LinkResult result = linkedAccountsManager.verifyAndLink(code, discordId, discordUsername);
            event.getChannel().sendMessage(result.success ? "\u2705 " + result.message : "\u274c " + result.message);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error handling /link command", e);
            event.getChannel().sendMessage("\u274c An error occurred while processing your link request.");
        }
    }
}
