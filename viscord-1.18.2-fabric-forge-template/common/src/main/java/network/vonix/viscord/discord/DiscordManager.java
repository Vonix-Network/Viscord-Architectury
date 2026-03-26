package network.vonix.viscord.discord;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
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

    private static DiscordManager instance;

    // Platform delegates
    private final DiscordPlatform discordPlatform = new DiscordPlatform();
    private final FluxerPlatform fluxerPlatform = new FluxerPlatform();
    private TridirectionalBridge bridge;

    // Minecraft-specific processing
    private final EventEmbedDetector eventDetector = new EventEmbedDetector();
    private final AdvancementEmbedDetector advancementDetector = new AdvancementEmbedDetector();
    private final EventDataExtractor eventExtractor = new EventDataExtractor();
    private final AdvancementDataExtractor advancementExtractor = new AdvancementDataExtractor();
    private final VanillaComponentBuilder componentBuilder = new VanillaComponentBuilder();
    private static final Pattern DISCORD_MARKDOWN_LINK =
        Pattern.compile("\\[([^\\]]+)]\\((https?://[^)]+)\\)");

    // Server + sub-systems
    private MinecraftServer server;
    private LinkedAccountsManager linkedAccountsManager;
    private PlayerPreferences playerPreferences;

    private boolean running = false;

    // Advancement debounce cache
    private final java.util.Map<String, Long> recentAdvancements =
        new java.util.concurrent.ConcurrentHashMap<>();

    private DiscordManager() {}

    public static DiscordManager getInstance() {
        if (instance == null) instance = new DiscordManager();
        return instance;
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
        boolean tridirectional = ViscordConfigToml.Tridirectional.ENABLED.get();

        if (tridirectional) {
            Viscord.LOGGER.info("[Viscord] Initializing Tridirectional Chat (Discord & Fluxer)");
            bridge = new TridirectionalBridge(discordPlatform, fluxerPlatform);
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
        try {
            CompletableFuture<?> shutdownMsg = null;
            if (isFluxer()) {
                fluxerPlatform.sendEventMessage(
                    "\uD83D\uDD34 **" + ViscordConfigToml.Server.NAME.get() + "** is now offline.");
            } else {
                shutdownMsg = discordPlatform.sendShutdownEmbed(ViscordConfigToml.Server.NAME.get());
            }
            if (shutdownMsg != null) {
                shutdownMsg.orTimeout(3, TimeUnit.SECONDS)
                    .whenComplete((m, e) -> continueShutdown());
                Thread.sleep(100);
            } else {
                continueShutdown();
            }
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Viscord] Shutdown message failed: {}", e.getMessage());
            continueShutdown();
        }
        running = false;
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

        boolean isMainChannel = mainChannelId != null && mainChannelId.equals(msgChannelId);
        boolean isEventChannel = evtChannelId != null && !evtChannelId.isEmpty()
            && evtChannelId.equals(msgChannelId);

        if (!isMainChannel && !isEventChannel) return;

        if (message.getContent().trim().equalsIgnoreCase("!list")) {
            handleTextListCommand(event); return;
        }
        if (message.getContent().startsWith("/link ")) {
            handleLinkCommand(event); return;
        }
        if (isEventChannel && !ViscordConfigToml.Filters.SHOW_OTHER_SERVER_EVENTS.get()) return;
        if (ViscordConfigToml.Filters.IGNORE_BOTS.get() && message.getAuthor().isBotUser()) return;
        if (ViscordConfigToml.Filters.IGNORE_WEBHOOKS.get() && message.getAuthor().isWebhook()) return;

        String authorName = message.getAuthor().getDisplayName();
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

        boolean isMainChannel = mainChannelId != null && mainChannelId.equals(msgChannelId);
        boolean isEventChannel = evtChannelId != null && !evtChannelId.isEmpty()
            && evtChannelId.equals(msgChannelId);

        if (!isMainChannel && !isEventChannel) return;

        if (message.getContent().trim().equalsIgnoreCase("!list")) {
            handleTextListCommand(event); return;
        }
        if (isEventChannel && !ViscordConfigToml.Filters.SHOW_OTHER_SERVER_EVENTS.get()) return;
        if (ViscordConfigToml.Filters.IGNORE_BOTS.get() && message.getAuthor().isBotUser()) return;
        if (ViscordConfigToml.Filters.IGNORE_WEBHOOKS.get() && message.getAuthor().isWebhook()) return;

        if (ViscordConfigToml.Filters.FILTER_BY_PREFIX.get()) {
            String serverPrefix = ViscordConfigToml.Server.PREFIX.get();
            if (serverPrefix != null && !serverPrefix.isEmpty()) {
                String authorName = message.getAuthor().getDisplayName();
                if (authorName.startsWith(serverPrefix)) return;
                String webhookFormat = ViscordConfigToml.Messages.WEBHOOK_USERNAME.get();
                if (webhookFormat != null && webhookFormat.contains("{prefix}")) {
                    String expectedStart = webhookFormat.split("\\{prefix\\}")[0] + serverPrefix;
                    if (authorName.startsWith(expectedStart) || authorName.startsWith(serverPrefix)) return;
                }
            }
        }

        boolean isWebhook = message.getAuthor().isWebhook();
        String authorName = message.getAuthor().getDisplayName();
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

        MutableComponent finalComponent = new TextComponent("");
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
                finalComponent.append(new TextComponent("[Discord]").setStyle(Style.EMPTY
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, inviteUrl))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TextComponent("Click to join our Discord!")))));
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
            String authorName = event.getMessageAuthor().getDisplayName();
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
        if (text == null || text.isEmpty()) return new TextComponent("");
        Matcher matcher = DISCORD_MARKDOWN_LINK.matcher(text);
        MutableComponent result = new TextComponent("");
        int lastEnd = 0;
        boolean hasLink = false;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) result.append(new TextComponent(text.substring(lastEnd, matcher.start())));
            result.append(new TextComponent(matcher.group(1)).withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, matcher.group(2)))
                .withUnderlined(true).withColor(ChatFormatting.AQUA)));
            lastEnd = matcher.end();
            hasLink = true;
        }
        if (lastEnd < text.length()) result.append(new TextComponent(text.substring(lastEnd)));
        return hasLink ? result : new TextComponent(text);
    }

    private void broadcastSystemMessageRespectingFilters(Component message) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (!hasServerMessagesFiltered(p.getUUID())) p.sendMessage(message, net.minecraft.Util.NIL_UUID);
    }

    private void broadcastEventMessageRespectingFilters(Component message) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (!hasEventsFiltered(p.getUUID())) p.sendMessage(message, net.minecraft.Util.NIL_UUID);
    }

    private void broadcastServerSystemMessageRespectingFilters(Component message) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (!hasServerSystemMessagesFiltered(p.getUUID())) p.sendMessage(message, net.minecraft.Util.NIL_UUID);
    }

    // =========================================================================
    // Public API — Minecraft → Platform sending
    // =========================================================================

    public void sendMinecraftMessage(String username, String message) {
        if (!running) return;
        String prefix = ViscordConfigToml.Server.PREFIX.get();
        String formattedUsername = ViscordConfigToml.Messages.WEBHOOK_USERNAME.get()
            .replace("{prefix}", prefix).replace("{username}", username);
        String avatarUrl = getAvatarUrl(username);
        String formattedMessage = DiscordFormatter.convertToDiscordFormatting(message);
        if (isFluxer()) {
            fluxerPlatform.sendChatMessage(formattedUsername, avatarUrl, formattedMessage);
        } else {
            discordPlatform.sendChatMessage(formattedUsername, avatarUrl, formattedMessage);
        }
    }

    public void sendChatMessage(String username, String message, String uuid) {
        sendMinecraftMessage(username, message);
    }

    // =========================================================================
    // Public API — Event embeds
    // =========================================================================

    public void sendStartupEmbed(String serverName) {
        boolean tridirectional = ViscordConfigToml.Tridirectional.ENABLED.get();
        if (isFluxer() || tridirectional) {
            if (tridirectional) {
                fluxerPlatform.sendEventMessage("\uD83D\uDFE2 **" + serverName + "** is now online!");
            }
        }
        if (!isFluxer() || tridirectional) {
            discordPlatform.sendStartupEmbed(serverName);
        }
    }

    public CompletableFuture<org.javacord.api.entity.message.Message> sendShutdownEmbed(String serverName) {
        if (isFluxer()) {
            fluxerPlatform.sendEventMessage("\uD83D\uDD34 **" + serverName + "** is now offline.");
            return CompletableFuture.completedFuture(null);
        }
        return discordPlatform.sendShutdownEmbed(serverName);
    }

    public void sendJoinEmbed(String username, String uuid) {
        if (!ViscordConfigToml.Messages.Events.JOIN.get() || !isRunning()) return;
        String avatarUrl = getAvatarUrl(username);
        if (isFluxer() || ViscordConfigToml.Tridirectional.ENABLED.get()) {
            fluxerPlatform.sendEventMessage("➡ **" + username + "** joined the game");
        }
        if (!isFluxer() || ViscordConfigToml.Tridirectional.ENABLED.get()) {
            discordPlatform.sendJoinEmbed(username, avatarUrl);
        }
    }

    public void sendLeaveEmbed(String username, String uuid) {
        if (!ViscordConfigToml.Messages.Events.LEAVE.get() || !isRunning()) return;
        String avatarUrl = getAvatarUrl(username);
        if (isFluxer() || ViscordConfigToml.Tridirectional.ENABLED.get()) {
            fluxerPlatform.sendEventMessage("⬅ **" + username + "** left the game");
        }
        if (!isFluxer() || ViscordConfigToml.Tridirectional.ENABLED.get()) {
            discordPlatform.sendLeaveEmbed(username, avatarUrl);
        }
    }

    public void sendJoinEmbed(String username) { sendJoinEmbed(username, null); }
    public void sendLeaveEmbed(String username) { sendLeaveEmbed(username, null); }

    public void sendDeathEmbed(String message) {
        if (!ViscordConfigToml.Messages.Events.DEATH.get() || !isRunning()) return;
        if (isFluxer() || ViscordConfigToml.Tridirectional.ENABLED.get())
            fluxerPlatform.sendEventMessage("\u2620 " + message);
        if (!isFluxer() || ViscordConfigToml.Tridirectional.ENABLED.get())
            discordPlatform.sendDeathEmbed(message);
    }

    public void sendAdvancementEmbed(String username, String title, String desc) {
        if (!ViscordConfigToml.Messages.Events.ADVANCEMENT.get() || !isRunning()) return;
        long now = System.currentTimeMillis();
        String key = username + ":" + title;
        Long prev = recentAdvancements.merge(key, now, (existing, nv) -> (now - existing < 5000) ? existing : nv);
        if (!prev.equals(now)) return;
        if (recentAdvancements.size() > 100) { recentAdvancements.clear(); recentAdvancements.put(key, now); }
        if (isFluxer() || ViscordConfigToml.Tridirectional.ENABLED.get())
            fluxerPlatform.sendEventMessage("\uD83C\uDFC6 **" + username + "** has made the advancement **" + title + "**");
        if (!isFluxer() || ViscordConfigToml.Tridirectional.ENABLED.get())
            discordPlatform.sendAdvancementEmbed(username, title, desc);
    }

    public void sendServerStatusMessage(String title, String description, int color) {
        if (!isFluxer()) discordPlatform.sendServerStatusMessage(title, description, color);
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
        Viscord.ASYNC_EXECUTOR.submit(() -> {
            try { Thread.sleep(delayMs); updateBotStatus(); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
    }

    // =========================================================================
    // Public API — State
    // =========================================================================

    public boolean isRunning() {
        if (!running) return false;
        if (isFluxer()) return true;
        if (ViscordConfigToml.Tridirectional.ENABLED.get()) {
            return discordPlatform.isConnected() || fluxerPlatform.isConnected();
        }
        return discordPlatform.isConnected();
    }

    private boolean isFluxer() {
        return "fluxer".equalsIgnoreCase(ViscordConfigToml.General.PLATFORM.get());
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

    private String getAvatarUrl(String username) {
        String url = ViscordConfigToml.Messages.AVATAR_URL.get().replace("{username}", username);
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(username);
            if (player != null) url = url.replace("{uuid}", player.getUUID().toString().replace("-", ""));
        }
        return url;
    }

    private boolean isPlayerListEmbed(Embed embed) {
        return embed.getFooter().map(f -> f.getText().map(t -> t.contains("Player List")).orElse(false)).orElse(false)
            || embed.getTitle().map(t -> t.contains("List") || t.contains("Status")).orElse(false);
    }

    private void processPlayerListEmbed(Embed embed, MessageCreateEvent event) {
        // Delegate to fallback — player list embeds from other servers are displayed as text
        handleEmbedFallback(embed, event);
    }

    private void handleTextListCommand(MessageCreateEvent event) {
        try {
            if (server == null) return;
            java.util.List<net.minecraft.server.level.ServerPlayer> players = server.getPlayerList().getPlayers();
            int online = players.size();
            int max = server.getPlayerList().getMaxPlayers();
            org.javacord.api.entity.message.embed.EmbedBuilder embed =
                new org.javacord.api.entity.message.embed.EmbedBuilder()
                    .setTitle("\uD83D\uDCCB " + ViscordConfigToml.Server.NAME.get())
                    .setColor(java.awt.Color.GREEN)
                    .setFooter("Viscord · Player List");
            if (online == 0) {
                embed.setDescription("No players are currently online.");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < players.size(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append("• ").append(players.get(i).getName().getString());
                }
                embed.addField("Players " + online + "/" + max, sb.toString(), false);
            }
            event.getChannel().sendMessage(embed);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error handling !list command", e);
        }
    }

    private void handleLinkCommand(MessageCreateEvent event) {
        try {
            if (!ViscordConfigToml.AccountLinking.ENABLED.get()) {
                event.getChannel().sendMessage("❌ Account linking is disabled."); return;
            }
            String content = event.getMessage().getContent().trim();
            String[] parts = content.split(" ", 2);
            if (parts.length < 2) {
                event.getChannel().sendMessage("❌ Usage: `/link <code>`"); return;
            }
            if (linkedAccountsManager == null) {
                event.getChannel().sendMessage("❌ Account linking system is not available."); return;
            }
            String code = parts[1].trim();
            String discordId = event.getMessageAuthor().getIdAsString();
            String discordUsername = event.getMessageAuthor().getDisplayName();
            LinkedAccountsManager.LinkResult result = linkedAccountsManager.verifyAndLink(code, discordId, discordUsername);
            event.getChannel().sendMessage(result.success ? "✅ " + result.message : "❌ " + result.message);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Discord] Error handling /link command", e);
            event.getChannel().sendMessage("❌ An error occurred while processing your link request.");
        }
    }
}
