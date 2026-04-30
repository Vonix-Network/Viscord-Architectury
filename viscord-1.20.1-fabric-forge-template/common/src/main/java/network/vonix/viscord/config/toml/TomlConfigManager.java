package network.vonix.viscord.config.toml;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.ConfigSpec;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.simple.SimpleConfigManager;
import network.vonix.viscord.config.simple.SimpleConfigSpec;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TOML Configuration Manager with automatic JSON migration.
 * Handles loading, saving, and migrating from legacy JSON configs.
 */
public class TomlConfigManager {

    private static CommentedFileConfig config;
    private static ConfigSpec spec;

    /**
     * Load or create the TOML config, migrating from JSON if necessary.
     */
    public static void load(Path configDir) {
        Path tomlPath = configDir.resolve("viscord.toml");
        Path jsonPath = configDir.resolve("viscord.json");

        // Create spec with all config entries and defaults
        spec = createConfigSpec();

        // Check if TOML already exists
        if (tomlPath.toFile().exists()) {
            Viscord.LOGGER.info("[Config] Loading TOML config from: {}", tomlPath);
            loadToml(tomlPath);
            return;
        }

        // Check for legacy JSON to migrate
        if (jsonPath.toFile().exists()) {
            Viscord.LOGGER.info("[Config] Found legacy JSON config, migrating to TOML...");
            migrateFromJson(jsonPath, tomlPath);
            return;
        }

        // No config exists, create new default
        Viscord.LOGGER.info("[Config] Creating new TOML config at: {}", tomlPath);
        createDefaultConfig(tomlPath);
    }

    /**
     * Save the current config to disk.
     */
    public static void save() {
        if (config != null) {
            config.save();
            Viscord.LOGGER.info("[Config] Saved TOML config");
        }
    }

    /**
     * Get the current config instance.
     */
    public static CommentedFileConfig getConfig() {
        return config;
    }

    /**
     * Get a string value from config.
     */
    public static String getString(String path, String defaultValue) {
        return config.getOrElse(path, defaultValue);
    }

    /**
     * Get a boolean value from config.
     */
    public static boolean getBoolean(String path, boolean defaultValue) {
        return config.getOrElse(path, defaultValue);
    }

    /**
     * Get an integer value from config.
     */
    public static int getInt(String path, int defaultValue) {
        return config.getOrElse(path, defaultValue);
    }

    /**
     * Set a value in config.
     */
    public static <T> void set(String path, T value) {
        config.set(path, value);
    }

    /**
     * Load existing TOML config.
     */
    private static void loadToml(Path path) {
        // Build without autosave first to allow spec correction
        config = CommentedFileConfig.builder(path)
                .defaultResource("/viscord-default.toml")
                .build();

        config.load();

        // Correct any incorrect values and add missing entries
        // This must happen before autosave is enabled because StampedConfig
        // doesn't support valueMap() which ConfigSpec.correct() requires
        try {
            spec.correct(config);
            config.save();
        } catch (UnsupportedOperationException e) {
            Viscord.LOGGER.warn("[Config] Could not correct config (unsupported operation), continuing with loaded values");
        }

        // Close and rebuild with autosave enabled
        config.close();
        config = CommentedFileConfig.builder(path)
                .defaultResource("/viscord-default.toml")
                .autosave()
                .build();
        config.load();

        Viscord.LOGGER.info("[Config] Loaded TOML config successfully");
    }

    /**
     * Create a new default config.
     */
    private static void createDefaultConfig(Path path) {
        config = CommentedFileConfig.builder(path)
                .autosave()
                .build();

        // Set default values based on spec
        applyDefaults();

        // Add comments
        addComments();

        config.save();
        Viscord.LOGGER.info("[Config] Created default TOML config");
    }

    /**
     * Migrate from legacy JSON format to TOML.
     */
    private static void migrateFromJson(Path jsonPath, Path tomlPath) {
        // First load the legacy JSON using the old manager
        Path legacySpecPath = Paths.get(jsonPath.toString().replace("viscord.toml", "viscord.json"));

        try {
            // Load legacy values from the old config system
            Viscord.LOGGER.info("[Config] Reading legacy JSON values...");

            // Create new TOML config
            config = CommentedFileConfig.builder(tomlPath)
                    .autosave()
                    .build();

            // Apply defaults first
            applyDefaults();

            // Read and migrate old values
            migrateOldValues(jsonPath);

            // Add comments
            addComments();

            // Save new TOML
            config.save();

            // Backup old JSON
            File backupFile = new File(jsonPath.toString() + ".backup");
            jsonPath.toFile().renameTo(backupFile);

            Viscord.LOGGER.info("[Config] Migration complete! JSON backed up to: {}", backupFile.getName());
        } catch (Exception e) {
            Viscord.LOGGER.error("[Config] Migration failed: {}", e.getMessage());
            Viscord.LOGGER.error("[Config] Creating default TOML config instead");
            createDefaultConfig(tomlPath);
        }
    }

    /**
     * Create the configuration specification with all paths and defaults.
     */
    private static ConfigSpec createConfigSpec() {
        ConfigSpec spec = new ConfigSpec();

        // [general] section
        spec.define("general.enabled", false);
        spec.define("general.platform", "discord");
        spec.define("general.debug", false);

        // [general.tridirectional] section
        spec.define("general.tridirectional.enabled", false);
        spec.define("general.tridirectional.discord_to_fluxer", true);
        spec.define("general.tridirectional.fluxer_to_discord", true);
        spec.define("general.tridirectional.show_source", true);

        // [discord] section
        spec.define("discord.bot_token", "YOUR_BOT_TOKEN_HERE");
        spec.define("discord.channel_id", "YOUR_CHANNEL_ID_HERE");
        spec.define("discord.webhook_url", "");
        spec.define("discord.webhook_id", "");
        spec.define("discord.invite_url", "");

        // [discord.events] section
        spec.define("discord.events.channel_id", "");
        spec.define("discord.events.webhook_url", "");

        // [fluxer] section
        spec.define("fluxer.bot_token", "YOUR_FLUXER_BOT_TOKEN");
        spec.define("fluxer.channel_id", "YOUR_FLUXER_CHANNEL_ID");
        spec.define("fluxer.event_channel_id", "");
        spec.define("fluxer.webhook_url", "");

        // [server] section
        spec.define("server.prefix", "[MC]");
        spec.define("server.name", "Minecraft Server");
        spec.define("server.avatar_url", "");

        // [messages] section
        spec.define("messages.discord_to_minecraft", "[Discord] {username}: {message}");
        spec.define("messages.minecraft_to_discord", "{message}");
        spec.define("messages.webhook_username", "{prefix} {username}");
        spec.define("messages.use_display_name", true);

        // [messages.events] section
        spec.define("messages.events.join", true);
        spec.define("messages.events.leave", true);
        spec.define("messages.events.death", true);
        spec.define("messages.events.advancement", true);

        // [filters] section
        spec.define("filters.ignore_bots", true);
        spec.define("filters.ignore_webhooks", true);
        spec.define("filters.trusted_bot_ids", "");
        spec.define("filters.filter_by_prefix", true);
        spec.define("filters.show_other_server_events", true);

        // [filters.chat] section
        spec.define("filters.chat.enabled", false);
        spec.define("filters.chat.prefix", "!");

        // [bot_status] section
        spec.define("bot_status.enabled", true);
        spec.define("bot_status.format", "Online: {online}/{max}");

        // [account_linking] section
        spec.define("account_linking.enabled", true);
        spec.defineInRange("account_linking.code_expiry", 300, 60, 600);

        // [advanced] section
        spec.defineInRange("advanced.queue_size", 100, 10, 1000);
        spec.defineInRange("advanced.rate_limit", 1000, 100, 5000);

        return spec;
    }

    /**
     * Apply default values to config.
     */
    private static void applyDefaults() {
        // [general] section
        config.set("general.enabled", false);
        config.set("general.platform", "discord");
        config.set("general.debug", false);

        // [general.tridirectional] section
        config.set("general.tridirectional.enabled", false);
        config.set("general.tridirectional.discord_to_fluxer", true);
        config.set("general.tridirectional.fluxer_to_discord", true);
        config.set("general.tridirectional.show_source", true);

        // [discord] section
        config.set("discord.bot_token", "YOUR_BOT_TOKEN_HERE");
        config.set("discord.channel_id", "YOUR_CHANNEL_ID_HERE");
        config.set("discord.webhook_url", "");
        config.set("discord.webhook_id", "");
        config.set("discord.invite_url", "");

        // [discord.events] section
        config.set("discord.events.channel_id", "");
        config.set("discord.events.webhook_url", "");

        // [fluxer] section
        config.set("fluxer.bot_token", "YOUR_FLUXER_BOT_TOKEN");
        config.set("fluxer.channel_id", "YOUR_FLUXER_CHANNEL_ID");
        config.set("fluxer.event_channel_id", "");
        config.set("fluxer.webhook_url", "");

        // [server] section
        config.set("server.prefix", "[MC]");
        config.set("server.name", "Minecraft Server");
        config.set("server.avatar_url", "");

        // [messages] section
        config.set("messages.discord_to_minecraft", "[Discord] {username}: {message}");
        config.set("messages.minecraft_to_discord", "{message}");
        config.set("messages.webhook_username", "{prefix} {username}");
        config.set("messages.use_display_name", true);

        // [messages.events] section
        config.set("messages.events.join", true);
        config.set("messages.events.leave", true);
        config.set("messages.events.death", true);
        config.set("messages.events.advancement", true);

        // [filters] section
        config.set("filters.ignore_bots", true);
        config.set("filters.ignore_webhooks", true);
        config.set("filters.trusted_bot_ids", "");
        config.set("filters.filter_by_prefix", true);
        config.set("filters.show_other_server_events", true);

        // [filters.chat] section
        config.set("filters.chat.enabled", false);
        config.set("filters.chat.prefix", "!");

        // [bot_status] section
        config.set("bot_status.enabled", true);
        config.set("bot_status.format", "Online: {online}/{max}");

        // [account_linking] section
        config.set("account_linking.enabled", true);
        config.set("account_linking.code_expiry", 300);

        // [advanced] section
        config.set("advanced.queue_size", 100);
        config.set("advanced.rate_limit", 1000);
    }

    /**
     * Add comments to config entries for better UX.
     */
    private static void addComments() {
        // [general] comments
        config.setComment("general", "Viscord - Bidirectional Chat Integration\n" +
                "Connect your Minecraft server to Discord or Fluxer\n" +
                "\n" +
                "Quick Start:\n" +
                "1. Set 'general.enabled' to true\n" +
                "2. Choose your platform: 'discord' or 'fluxer'\n" +
                "3. Configure your platform settings below\n" +
                "4. Restart the server");
        config.setComment("general.enabled", "Master toggle for all Viscord features");
        config.setComment("general.platform", "Chat platform to use:\n" +
                "  discord - Full Discord bot integration (webhooks + bot API)\n" +
                "  fluxer  - Fluxer bot integration (bot API + Gateway, no port forwarding needed)");
        config.setComment("general.debug", "Enable verbose debug logging for troubleshooting");

        // [general.tridirectional] comments
        config.setComment("general.tridirectional", "Tridirectional Chat Configuration\n" +
                "Settings for 3-way chat between Discord, Fluxer, and Minecraft\n" +
                "Requires both Discord and Fluxer to be configured");
        config.setComment("general.tridirectional.enabled", "Enable tridirectional chat - allows messages to flow between all three platforms");
        config.setComment("general.tridirectional.discord_to_fluxer", "Bridge Discord messages to Fluxer");
        config.setComment("general.tridirectional.fluxer_to_discord", "Bridge Fluxer messages to Discord");
        config.setComment("general.tridirectional.show_source", "Add platform tags like [Discord] or [Fluxer] to bridged messages");

        // [discord] comments
        config.setComment("discord", "Discord Configuration\n" +
                "Required only when platform is set to 'discord'\n" +
                "\n" +
                "How to get your Bot Token:\n" +
                "  1. Go to https://discord.com/developers/applications\n" +
                "  2. Create New Application -> Bot tab -> Copy Token\n" +
                "\n" +
                "IMPORTANT: Keep your bot_token secret!");
        config.setComment("discord.bot_token", "Your Discord Bot Token");
        config.setComment("discord.channel_id", "Discord Channel ID for chat messages (right-click channel -> Copy Channel ID with Developer Mode enabled)");
        config.setComment("discord.webhook_url", "Discord Webhook URL for rich Minecraft -> Discord messages (Channel Settings -> Integrations -> Webhooks)");
        config.setComment("discord.webhook_id", "Webhook ID (auto-extracted from URL if left empty)");
        config.setComment("discord.invite_url", "Discord server invite URL shown when players use /discord command");

        // [discord.events] comments
        config.setComment("discord.events", "Discord Event Configuration\n" +
                "Optional separate channel/webhook for server events (join/leave/death/advancement)\n" +
                "Leave empty to use main Discord channel");
        config.setComment("discord.events.channel_id", "Separate channel ID for event notifications");
        config.setComment("discord.events.webhook_url", "Separate webhook URL for event notifications");

        // [fluxer] comments
        config.setComment("fluxer", "Fluxer Configuration\n" +
                "Required only when platform is set to 'fluxer'\n" +
                "\n" +
                "How it works:\n" +
                "  - Bot connects to Fluxer via WebSocket Gateway (no port forwarding needed)\n" +
                "  - Messages sent using Bot API with your token and channel IDs\n" +
                "  - Events go to event channel (or main if not set)\n" +
                "\n" +
                "How to get your Bot Token:\n" +
                "  1. Go to https://fluxer.app -> Developer Portal -> Bot -> Copy Token\n" +
                "\n" +
                "IMPORTANT: Keep your bot_token secret!");
        config.setComment("fluxer.bot_token", "Your Fluxer Bot Token");
        config.setComment("fluxer.channel_id", "Fluxer Channel ID for chat messages");
        config.setComment("fluxer.event_channel_id", "Fluxer Channel ID for server events (join/leave/death/advancement). Leave empty to use main channel.");
        config.setComment("fluxer.webhook_url", "Fluxer Webhook URL (optional). For custom usernames/avatars on Minecraft -> Fluxer messages.");

        // [server] comments
        config.setComment("server", "Server Identity\n" +
                "How your server appears in bridged messages");
        config.setComment("server.prefix", "Server prefix shown in messages (e.g., [Survival], [Creative], [SMP])");
        config.setComment("server.name", "Server name for bot status and embeds");
        config.setComment("server.avatar_url", "Server avatar URL for event messages (leave empty for default)");

        // [messages] comments
        config.setComment("messages", "Message Formats\n" +
                "Customize how messages appear on each platform");
        config.setComment("messages.discord_to_minecraft", "Format for Discord/Fluxer -> Minecraft messages\n" +
                "Placeholders: {username}, {message}");
        config.setComment("messages.minecraft_to_discord", "Format for Minecraft -> Discord/Fluxer messages\n" +
                "Placeholder: {message}");
        config.setComment("messages.webhook_username", "Webhook display name format (Discord only)\n" +
                "Placeholders: {prefix}, {username}");
        config.setComment("messages.use_display_name", "Show Discord display name (server nickname) instead of username in Minecraft chat\n" +
                "  true  - use server nickname / global display name\n" +
                "  false - use the plain @username");

        // [messages.events] comments
        config.setComment("messages.events", "Event Notifications\n" +
                "Choose which server events to broadcast to Discord/Fluxer");
        config.setComment("messages.events.join", "Send player join notifications");
        config.setComment("messages.events.leave", "Send player leave notifications");
        config.setComment("messages.events.death", "Send player death messages");
        config.setComment("messages.events.advancement", "Send advancement/achievement notifications");

        // [filters] comments
        config.setComment("filters", "Chat Filters & Loop Prevention\n" +
                "Prevent message loops and filter unwanted content");
        config.setComment("filters.ignore_bots", "Ignore bot messages from Discord/Fluxer");
        config.setComment("filters.ignore_webhooks", "Ignore other webhook messages (prevents echo loops)");
        config.setComment("filters.trusted_bot_ids", "Trusted bot/webhook IDs whose messages always pass through (comma-separated)\n" +
                "Use to receive event embeds from other servers' Viscord bots\n" +
                "Right-click bot -> Copy ID in Discord (Developer Mode required)\n" +
                "Example: \"123456789,987654321\"");
        config.setComment("filters.filter_by_prefix", "Filter messages by server prefix (prevents echoing your own server's bridged messages back)");
        config.setComment("filters.show_other_server_events", "Show events from other servers in Minecraft chat (multi-server setups)");

        // [filters.chat] comments
        config.setComment("filters.chat", "Chat Filter Configuration\n" +
                "Prevent certain messages from being bridged based on prefix");
        config.setComment("filters.chat.enabled", "Enable chat filter to prevent messages starting with filter prefix from bridging");
        config.setComment("filters.chat.prefix", "Prefix to suppress from bridge relay (e.g., '!' means '!test' stays in-game only)");

        // [bot_status] comments
        config.setComment("bot_status", "Bot Status\n" +
                "Discord/Fluxer bot presence and activity configuration");
        config.setComment("bot_status.enabled", "Update bot status with live player count (shows 'Online: X/Y')");
        config.setComment("bot_status.format", "Bot status format string\n" +
                "Placeholders: {online}, {max}\n" +
                "Example: Online: {online}/{max}");

        // [account_linking] comments
        config.setComment("account_linking", "Account Linking\n" +
                "Link Minecraft and Discord accounts");
        config.setComment("account_linking.enabled", "Enable Discord account linking system");
        config.setComment("account_linking.code_expiry", "How long link codes remain valid (seconds, range: 60-600)");

        // [advanced] comments
        config.setComment("advanced", "Advanced Settings\n" +
                "Performance and debugging options");
        config.setComment("advanced.queue_size", "Message queue size (increase if losing messages during high traffic, range: 10-1000)");
        config.setComment("advanced.rate_limit", "Rate limit delay in milliseconds (minimum delay between API calls, range: 100-5000)");
    }

    /**
     * Migrate values from legacy JSON config.
     */
    private static void migrateOldValues(Path jsonPath) {
        try {
            // Use Jackson to read the old JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> jsonData = mapper.readValue(jsonPath.toFile(), java.util.Map.class);

            // Flatten the nested JSON structure
            java.util.Map<String, Object> flatData = new java.util.HashMap<>();
            flattenJson(jsonData, "", flatData);

            // Migrate [general] section
            migrateValue(flatData, "viscord.enabled", "general.enabled");
            migrateValue(flatData, "viscord.platform", "general.platform");
            migrateValue(flatData, "viscord.debug_logging", "general.debug");

            // Migrate [general.tridirectional] section
            migrateValue(flatData, "tridirectional.enabled", "general.tridirectional.enabled");
            migrateValue(flatData, "tridirectional.discord_to_fluxer", "general.tridirectional.discord_to_fluxer");
            migrateValue(flatData, "tridirectional.fluxer_to_discord", "general.tridirectional.fluxer_to_discord");
            migrateValue(flatData, "tridirectional.show_source", "general.tridirectional.show_source");

            // Migrate [discord] section
            migrateValue(flatData, "discord.bot_token", "discord.bot_token");
            migrateValue(flatData, "discord.channel_id", "discord.channel_id");
            migrateValue(flatData, "discord.webhook_url", "discord.webhook_url");
            migrateValue(flatData, "discord.webhook_id", "discord.webhook_id");
            migrateValue(flatData, "discord.invite_url", "discord.invite_url");

            // Migrate [discord.events] section
            migrateValue(flatData, "events.event_channel_id", "discord.events.channel_id");
            migrateValue(flatData, "events.event_webhook_url", "discord.events.webhook_url");

            // Migrate [fluxer] section
            migrateValue(flatData, "fluxer.bot_token", "fluxer.bot_token");
            migrateValue(flatData, "fluxer.channel_id", "fluxer.channel_id");
            migrateValue(flatData, "fluxer.event_channel_id", "fluxer.event_channel_id");
            migrateValue(flatData, "fluxer.webhook_url", "fluxer.webhook_url");

            // Migrate [server] section
            migrateValue(flatData, "server.prefix", "server.prefix");
            migrateValue(flatData, "server.name", "server.name");
            migrateValue(flatData, "server.avatar_url", "server.avatar_url");

            // Migrate [messages] section
            migrateValue(flatData, "formats.discord_to_minecraft", "messages.discord_to_minecraft");
            migrateValue(flatData, "formats.minecraft_to_discord", "messages.minecraft_to_discord");
            migrateValue(flatData, "formats.webhook_username", "messages.webhook_username");

            // Migrate [messages.events] section
            migrateValue(flatData, "events.send_join", "messages.events.join");
            migrateValue(flatData, "events.send_leave", "messages.events.leave");
            migrateValue(flatData, "events.send_death", "messages.events.death");
            migrateValue(flatData, "events.send_advancement", "messages.events.advancement");

            // Migrate [filters] section
            migrateValue(flatData, "filters.ignore_bots", "filters.ignore_bots");
            migrateValue(flatData, "filters.ignore_webhooks", "filters.ignore_webhooks");
            migrateValue(flatData, "filters.filter_by_prefix", "filters.filter_by_prefix");
            migrateValue(flatData, "filters.show_other_server_events", "filters.show_other_server_events");

            // Migrate [filters.chat] section
            migrateValue(flatData, "filters.enable_chat_filter", "filters.chat.enabled");
            migrateValue(flatData, "filters.chat_filter_prefix", "filters.chat.prefix");

            // Migrate [bot_status] section
            migrateValue(flatData, "bot.enabled", "bot_status.enabled");
            migrateValue(flatData, "bot.format", "bot_status.format");

            // Migrate [account_linking] section
            migrateValue(flatData, "linking.enabled", "account_linking.enabled");
            migrateValue(flatData, "linking.code_expiry_seconds", "account_linking.code_expiry");

            // Migrate [advanced] section
            migrateValue(flatData, "advanced.queue_size", "advanced.queue_size");
            migrateValue(flatData, "advanced.rate_limit", "advanced.rate_limit");

            Viscord.LOGGER.info("[Config] Migrated {} values from JSON", flatData.size());
        } catch (Exception e) {
            Viscord.LOGGER.error("[Config] Error reading legacy JSON: {}", e.getMessage());
        }
    }

    /**
     * Flatten nested JSON structure into dot-notation keys.
     */
    @SuppressWarnings("unchecked")
    private static void flattenJson(java.util.Map<String, Object> source, String prefix, java.util.Map<String, Object> target) {
        for (java.util.Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof java.util.Map) {
                flattenJson((java.util.Map<String, Object>) entry.getValue(), key, target);
            } else {
                target.put(key, entry.getValue());
            }
        }
    }

    /**
     * Migrate a single value from old key to new key.
     */
    private static void migrateValue(java.util.Map<String, Object> data, String oldKey, String newKey) {
        if (data.containsKey(oldKey)) {
            Object value = data.get(oldKey);
            config.set(newKey, value);
            Viscord.LOGGER.debug("[Config] Migrated: {} -> {} = {}", oldKey, newKey, value);
        }
    }
}
