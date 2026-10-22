package network.vonix.viscord.discord;

import network.vonix.viscord.Viscord;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.interaction.ApplicationCommand;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.interaction.SlashCommandBuilder;
import org.javacord.api.interaction.SlashCommandUpdater;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Handles the persistent Bot connection via Javacord.
 * Used for receiving messages, events, and updating status.
 */
public class BotClient {

    private static final int DISCORD_MESSAGE_CACHE_CAPACITY = 0;
    private static final int DISCORD_MESSAGE_CACHE_STORAGE_SECONDS = 0;

    private volatile DiscordApi api;
    private String token;
    private String channelId;
    private Consumer<MessageCreateEvent> messageHandler;
    private Consumer<SlashCommandCreateEvent> slashCommandHandler;

    public BotClient() {
        // Initialize in disconnected state
    }

    public void setMessageHandler(Consumer<MessageCreateEvent> handler) {
        this.messageHandler = handler;
    }

    public void setSlashCommandHandler(Consumer<SlashCommandCreateEvent> handler) {
        this.slashCommandHandler = handler;
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
        configureMessageCache(api);

        this.api = api;
        Viscord.LOGGER.info("Connected as {}", api.getYourself().getDiscriminatedName());

        // Components V2 noise (Container=17, TextDisplay=10, etc.) is suppressed by
        // JavacordLogQuiet (see DiscordPlatform.initialize). The previous global
        // UncaughtExceptionHandler approach was a no-op because Javacord catches+logs
        // the parse exception internally instead of letting it propagate.

        // Register Listeners
        api.addMessageCreateListener(event -> {
            if (messageHandler != null) {
                // Ignore self
                if (event.getMessageAuthor().isYourself())
                    return;

                messageHandler.accept(event);
            }
        });

        api.addSlashCommandCreateListener(event -> {
            if (slashCommandHandler != null) {
                slashCommandHandler.accept(event);
            }
        });

        registerSlashCommands(api).whenComplete((ignored, error) -> {
            if (error != null) {
                Viscord.LOGGER.error("[Discord] Failed to register native application commands", error);
            } else {
                Viscord.LOGGER.info("[Discord] Native application commands synchronized.");
            }
        });
    }

    static void configureMessageCache(DiscordApi api) {
        // Viscord consumes gateway events directly and never reads Javacord's
        // historical message cache. Keep the cache bounded to prevent a busy
        // guild from retaining message objects for Javacord's default 12 hours.
        api.setMessageCacheSize(DISCORD_MESSAGE_CACHE_CAPACITY, DISCORD_MESSAGE_CACHE_STORAGE_SECONDS);
        api.setAutomaticMessageCacheCleanupEnabled(true);
        Viscord.LOGGER.info("[Discord] Javacord message cache bounded to capacity={} and storage={}s.",
                DISCORD_MESSAGE_CACHE_CAPACITY, DISCORD_MESSAGE_CACHE_STORAGE_SECONDS);
    }

    /**
     * Synchronize only Viscord-owned commands in the guilds containing the
     * configured Discord channel(s). Existing commands with the same name are
     * updated in place, which repairs stale registrations without deleting
     * commands owned by another application.
     */
    private CompletableFuture<Void> registerSlashCommands(DiscordApi local) {
        Map<Long, Server> serversById = new LinkedHashMap<>();
        if (channelId != null) {
            for (String configuredChannelId : channelId.split(",")) {
                String trimmed = configuredChannelId.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    local.getServerTextChannelById(trimmed)
                            .ifPresent(channel -> serversById.put(channel.getServer().getId(), channel.getServer()));
                } catch (RuntimeException e) {
                    Viscord.LOGGER.warn("[Discord] Could not resolve configured command channel {}: {}",
                            trimmed, e.getMessage());
                }
            }
        }

        if (serversById.isEmpty()) {
            Viscord.LOGGER.warn("[Discord] No configured Discord guild could be resolved; native commands were not registered.");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Server server : serversById.values()) {
            chain = chain.thenCompose(ignored -> synchronizeCommandsForServer(local, server));
        }
        return chain;
    }

    private CompletableFuture<Void> synchronizeCommandsForServer(DiscordApi local, Server server) {
        return local.getServerApplicationCommands(server).thenCompose(existing -> {
            long owningApplicationId = local.getYourself().getId();
            Map<String, ApplicationCommand> ownedByName = new LinkedHashMap<>();
            Set<String> foreignNames = new LinkedHashSet<>();
            for (ApplicationCommand command : existing) {
                if (command.getApplicationId() == owningApplicationId) {
                    ownedByName.putIfAbsent(command.getName(), command);
                } else {
                    foreignNames.add(command.getName());
                }
            }

            List<CompletableFuture<?>> operations = new ArrayList<>();
            for (DiscordSlashCommandDefinitions.Definition definition : DiscordSlashCommandDefinitions.all()) {
                ApplicationCommand current = ownedByName.get(definition.name());
                if (current instanceof SlashCommand slashCommand) {
                    SlashCommandUpdater updater = slashCommand.createSlashCommandUpdater()
                            .setName(definition.name())
                            .setDescription(definition.description())
                            .setEnabledInDms(false)
                            .setSlashCommandOptions(definition.options());
                    if (definition.administratorOnly()) {
                        updater.setDefaultEnabledForPermissions(org.javacord.api.entity.permission.PermissionType.ADMINISTRATOR);
                    } else {
                        updater.setDefaultEnabledForEveryone();
                    }
                    operations.add(updater.updateForServer(server));
                } else if (current == null && !foreignNames.contains(definition.name())) {
                    SlashCommandBuilder builder = DiscordSlashCommandDefinitions.builder(definition);
                    operations.add(builder.createForServer(server));
                } else if (current == null) {
                    Viscord.LOGGER.warn("[Discord] Skipping /{} because that name belongs to another application.",
                            definition.name());
                } else {
                    Viscord.LOGGER.warn("[Discord] Cannot register /{} because an existing non-slash Viscord command has that name.",
                            definition.name());
                }
            }

            return CompletableFuture.allOf(operations.toArray(new CompletableFuture<?>[0]))
                    .thenRun(() -> Viscord.LOGGER.info("[Discord] Synchronized {} native commands for guild {}.",
                            operations.size(), server.getIdAsString()));
        });
    }

    public void updateStatus(String status) {
        DiscordApi local = api;
        if (local != null) {
            local.updateActivity(ActivityType.PLAYING, status);
            local.updateStatus(org.javacord.api.entity.user.UserStatus.ONLINE);
        }
    }

    public void disconnect() {
        DiscordApi local = api;
        if (local != null) {
            api = null;
            local.disconnect();
        }
    }

    public CompletableFuture<org.javacord.api.entity.message.Message> sendEmbed(String channelId,
            com.google.gson.JsonObject embedJson) {
        DiscordApi local = api;
        if (local == null) {
            Viscord.LOGGER.warn("[Discord] Cannot send embed - API is null (bot not connected)");
            return CompletableFuture.completedFuture(null);
        }

        Viscord.LOGGER.info("[Discord] Attempting to send embed to channel ID: {}", channelId);

        java.util.Optional<org.javacord.api.entity.channel.TextChannel> channelOpt = local.getTextChannelById(channelId);
        if (!channelOpt.isPresent()) {
            Viscord.LOGGER.warn("[Discord] Cannot send embed - channel {} not found (bot may lack access)", channelId);
            return CompletableFuture.completedFuture(null);
        }
        return channelOpt.map(channel -> {
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

            try {
                return sendMessageWithGameClassLoader(channel, embed);
            } catch (RejectedExecutionException e) {
                if (Viscord.isShuttingDown()) {
                    Viscord.LOGGER.warn("[Discord] Dropping embed because the Discord executor rejected it during server shutdown");
                    return CompletableFuture.<org.javacord.api.entity.message.Message>completedFuture(null);
                }
                throw e;
            }
        }).orElse(CompletableFuture.completedFuture(null));
    }

    /**
     * Sends through Javacord with the thread context class loader pinned to the mod
     * loader.
     *
     * <p>Forge's EventSubclassTransformer resolves a class's superclass through
     * {@code Thread.currentThread().getContextClassLoader()} when it transforms mod
     * classes. Javacord loads shaded classes lazily on whatever thread performs the
     * call, so an embed sent from a pool thread whose context loader is the system
     * loader makes the transformer fail with
     * {@code ClassNotFoundException: ...shadow.javacord.api.entity.message.MessageBuilderBase}
     * and links the class outside the game module layer. Pinning the context loader
     * keeps the lookup inside the game loader.
     */
    private static CompletableFuture<org.javacord.api.entity.message.Message> sendMessageWithGameClassLoader(
            org.javacord.api.entity.channel.TextChannel channel,
            org.javacord.api.entity.message.embed.EmbedBuilder embed) {
        Thread thread = Thread.currentThread();
        ClassLoader gameLoader = BotClient.class.getClassLoader();
        ClassLoader previous = thread.getContextClassLoader();
        if (gameLoader == null || previous == gameLoader) {
            return channel.sendMessage(embed);
        }
        thread.setContextClassLoader(gameLoader);
        try {
            return channel.sendMessage(embed);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    public boolean isConnected() {
        return api != null;
    }

    /** Returns the bot's own Discord user ID, or null if not connected. */
    public String getBotUserId() {
        DiscordApi local = api;
        return local != null ? local.getYourself().getIdAsString() : null;
    }
}
