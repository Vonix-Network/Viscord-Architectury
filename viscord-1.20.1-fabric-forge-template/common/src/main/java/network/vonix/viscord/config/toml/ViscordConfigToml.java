package network.vonix.viscord.config.toml;

/**
 * Viscord TOML Configuration accessor.
 * Provides typed access to all configuration values.
 * 
 * Usage: ViscordConfigToml.GENERAL.enabled.get()
 */
public class ViscordConfigToml {

    // Prevent instantiation
    private ViscordConfigToml() {}

    // ==================== GENERAL SECTION ====================
    public static class General {
        public static final ConfigValue<Boolean> ENABLED = new ConfigValue<>("general.enabled", false);
        public static final ConfigValue<String> PLATFORM = new ConfigValue<>("general.platform", "discord");
        public static final ConfigValue<Boolean> DEBUG = new ConfigValue<>("general.debug", false);
    }

    // ==================== TRIDIRECTIONAL SECTION ====================
    public static class Tridirectional {
        public static final ConfigValue<Boolean> ENABLED = new ConfigValue<>("general.tridirectional.enabled", false);
        public static final ConfigValue<Boolean> DISCORD_TO_FLUXER = new ConfigValue<>("general.tridirectional.discord_to_fluxer", true);
        public static final ConfigValue<Boolean> FLUXER_TO_DISCORD = new ConfigValue<>("general.tridirectional.fluxer_to_discord", true);
        public static final ConfigValue<Boolean> SHOW_SOURCE = new ConfigValue<>("general.tridirectional.show_source", true);
    }

    // ==================== DISCORD SECTION ====================
    public static class Discord {
        public static final ConfigValue<String> BOT_TOKEN = new ConfigValue<>("discord.bot_token", "YOUR_BOT_TOKEN_HERE");
        public static final ConfigValue<String> CHANNEL_ID = new ConfigValue<>("discord.channel_id", "YOUR_CHANNEL_ID_HERE");
        public static final ConfigValue<String> WEBHOOK_URL = new ConfigValue<>("discord.webhook_url", "");
        public static final ConfigValue<String> INVITE_URL = new ConfigValue<>("discord.invite_url", "");

        public static class Events {
            public static final ConfigValue<String> CHANNEL_ID = new ConfigValue<>("discord.events.channel_id", "");
        }
    }

    // ==================== FLUXER SECTION ====================
    public static class Fluxer {
        public static final ConfigValue<String> BOT_TOKEN = new ConfigValue<>("fluxer.bot_token", "YOUR_FLUXER_BOT_TOKEN");
        public static final ConfigValue<String> CHANNEL_ID = new ConfigValue<>("fluxer.channel_id", "YOUR_FLUXER_CHANNEL_ID");
        public static final ConfigValue<String> EVENT_CHANNEL_ID = new ConfigValue<>("fluxer.event_channel_id", "");
        public static final ConfigValue<String> WEBHOOK_URL = new ConfigValue<>("fluxer.webhook_url", "");
    }

    // ==================== SERVER SECTION ====================
    public static class Server {
        public static final ConfigValue<String> PREFIX = new ConfigValue<>("server.prefix", "[MC]");
        public static final ConfigValue<String> NAME = new ConfigValue<>("server.name", "Minecraft Server");
        public static final ConfigValue<String> AVATAR_URL = new ConfigValue<>("server.avatar_url", "");
    }

    // ==================== MESSAGES SECTION ====================
    public static class Messages {
        public static final ConfigValue<String> DISCORD_TO_MINECRAFT = new ConfigValue<>("messages.discord_to_minecraft", "[Discord] {username}: {message}");
        public static final ConfigValue<String> MINECRAFT_TO_DISCORD = new ConfigValue<>("messages.minecraft_to_discord", "{message}");
        public static final ConfigValue<String> WEBHOOK_USERNAME = new ConfigValue<>("messages.webhook_username", "{prefix} {username}");
        public static final ConfigValue<Boolean> USE_DISPLAY_NAME = new ConfigValue<>("messages.use_display_name", true);

        public static class Events {
            public static final ConfigValue<Boolean> JOIN = new ConfigValue<>("messages.events.join", true);
            public static final ConfigValue<Boolean> LEAVE = new ConfigValue<>("messages.events.leave", true);
            public static final ConfigValue<Boolean> DEATH = new ConfigValue<>("messages.events.death", true);
            public static final ConfigValue<Boolean> ADVANCEMENT = new ConfigValue<>("messages.events.advancement", true);
        }
    }

    // ==================== FILTERS SECTION ====================
    public static class Filters {
        public static final ConfigValue<Boolean> IGNORE_BOTS = new ConfigValue<>("filters.ignore_bots", true);
        public static final ConfigValue<Boolean> IGNORE_WEBHOOKS = new ConfigValue<>("filters.ignore_webhooks", true);
        public static final ConfigValue<String> TRUSTED_BOT_IDS = new ConfigValue<>("filters.trusted_bot_ids", "");
        public static final ConfigValue<Boolean> FILTER_BY_PREFIX = new ConfigValue<>("filters.filter_by_prefix", true);
        public static final ConfigValue<Boolean> SHOW_OTHER_SERVER_EVENTS = new ConfigValue<>("filters.show_other_server_events", true);

        public static class Chat {
            public static final ConfigValue<Boolean> ENABLED = new ConfigValue<>("filters.chat.enabled", false);
            public static final ConfigValue<String> PREFIX = new ConfigValue<>("filters.chat.prefix", "!");
        }
    }

    // ==================== BOT STATUS SECTION ====================
    public static class BotStatus {
        public static final ConfigValue<Boolean> ENABLED = new ConfigValue<>("bot_status.enabled", true);
        public static final ConfigValue<String> FORMAT = new ConfigValue<>("bot_status.format", "Online: {online}/{max}");
    }

    // ==================== ACCOUNT LINKING SECTION ====================
    public static class AccountLinking {
        public static final ConfigValue<Boolean> ENABLED = new ConfigValue<>("account_linking.enabled", true);
        public static final ConfigValue<Integer> CODE_EXPIRY = new ConfigValue<>("account_linking.code_expiry", 300);
    }

    // ==================== DISCORD/FLUXER BOT-SIDE RATE LIMITING ====================
    // Sliding 60-second windows. A value of 0 disables the corresponding bucket.
    public static class DiscordRateLimit {
        /** Per-Discord-user /link attempts per 60s. Default 3 — brute-forcing a 6-digit code at this rate is infeasible. */
        public static final ConfigValue<Integer> LINK_PER_USER_PER_MIN  = new ConfigValue<>("discord_rate_limit.link_per_user_per_min", 3);
        /** Channel-wide /link attempts per 60s across ALL users. Default 30 — caps coordinated brute force. */
        public static final ConfigValue<Integer> LINK_GLOBAL_PER_MIN    = new ConfigValue<>("discord_rate_limit.link_global_per_min", 30);
        /** Per-user !list invocations per 60s. Default 2. */
        public static final ConfigValue<Integer> LIST_PER_USER_PER_MIN  = new ConfigValue<>("discord_rate_limit.list_per_user_per_min", 2);
        /** Channel-wide !list invocations per 60s. Default 20. */
        public static final ConfigValue<Integer> LIST_GLOBAL_PER_MIN    = new ConfigValue<>("discord_rate_limit.list_global_per_min", 20);
    }

    // ==================== CONFIG VALUE HELPER CLASS ====================
    /**
     * Typed configuration value wrapper.
     */
    public static class ConfigValue<T> {
        private final String path;
        private final T defaultValue;

        public ConfigValue(String path, T defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
        }

        /**
         * Get the current value from config.
         */
        @SuppressWarnings("unchecked")
        public T get() {
            if (TomlConfigManager.getConfig() == null) {
                return defaultValue;
            }

            Object value = TomlConfigManager.getConfig().get(path);
            if (value == null) {
                return defaultValue;
            }

            try {
                if (value instanceof Number) {
                    if (defaultValue instanceof Integer) return (T) (Integer) ((Number) value).intValue();
                    if (defaultValue instanceof Long) return (T) (Long) ((Number) value).longValue();
                    if (defaultValue instanceof Double) return (T) (Double) ((Number) value).doubleValue();
                }
                return (T) value;
            } catch (ClassCastException e) {
                return defaultValue;
            }
        }

        /**
         * Set a new value in config.
         */
        public void set(T value) {
            TomlConfigManager.set(path, value);
        }

        /**
         * Get the config path.
         */
        public String getPath() {
            return path;
        }

        /**
         * Get the default value.
         */
        public T getDefault() {
            return defaultValue;
        }
    }
}
