package network.vonix.viscord.config;

import network.vonix.viscord.config.simple.SimpleConfigBuilder;
import network.vonix.viscord.config.simple.SimpleConfigSpec;
import network.vonix.viscord.config.simple.SimpleConfigValue;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Discord/Fluxer integration configuration for Viscord.
 * Stored in config/viscord.json
 */
public class ViscordConfig {

        public static final SimpleConfigSpec SPEC;
        public static final ViscordConfig CONFIG;

        // Platform selection
        public final SimpleConfigValue<String> platform;
        
        // Tridirectional chat settings
        public final SimpleConfigValue<Boolean> enableTridirectionalChat;
        public final SimpleConfigValue<Boolean> discordToFluxer;
        public final SimpleConfigValue<Boolean> fluxerToDiscord;
        public final SimpleConfigValue<Boolean> showPlatformSource;
        
        // Master toggle
        public final SimpleConfigValue<Boolean> enabled;

        // Discord settings
        public final SimpleConfigValue<String> discordBotToken;
        public final SimpleConfigValue<String> discordChannelId;
        public final SimpleConfigValue<String> discordWebhookUrl;
        public final SimpleConfigValue<String> discordWebhookId;
        public final SimpleConfigValue<String> discordInviteUrl;
        
        // Fluxer settings
        public final SimpleConfigValue<String> fluxerApiKey;
        public final SimpleConfigValue<String> fluxerChannelId;
        public final SimpleConfigValue<String> fluxerEventChannelId;
        public final SimpleConfigValue<String> fluxerEventWebhookUrl;
        public final SimpleConfigValue<String> fluxerWebhookUrl;
        public final SimpleConfigValue<Integer> fluxerReceiverPort;
        public final SimpleConfigValue<String> fluxerReceiverPath;
        public final SimpleConfigValue<String> fluxerApplicationId;

        // Server identity
        public final SimpleConfigValue<String> serverPrefix;
        public final SimpleConfigValue<String> serverName;
        public final SimpleConfigValue<String> serverAvatarUrl;

        // Message formats
        public final SimpleConfigValue<String> discordToMinecraftFormat;
        public final SimpleConfigValue<String> minecraftToDiscordFormat;
        public final SimpleConfigValue<String> webhookUsernameFormat;
        public final SimpleConfigValue<String> avatarUrl;

        // Event settings
        public final SimpleConfigValue<Boolean> sendJoin;
        public final SimpleConfigValue<Boolean> sendLeave;
        public final SimpleConfigValue<Boolean> sendDeath;
        public final SimpleConfigValue<Boolean> sendAdvancement;
        public final SimpleConfigValue<String> eventChannelId;
        public final SimpleConfigValue<String> eventWebhookUrl;

        // Loop prevention
        public final SimpleConfigValue<Boolean> ignoreBots;
        public final SimpleConfigValue<Boolean> ignoreWebhooks;
        public final SimpleConfigValue<Boolean> filterByPrefix;
        public final SimpleConfigValue<Boolean> showOtherServerEvents;

        // Chat filtering
        public final SimpleConfigValue<Boolean> enableChatFilter;
        public final SimpleConfigValue<String> chatFilterPrefix;

        // Bot status
        public final SimpleConfigValue<Boolean> setBotStatus;
        public final SimpleConfigValue<String> botStatusFormat;

        // Account linking
        public final SimpleConfigValue<Boolean> enableAccountLinking;
        public final SimpleConfigValue<Integer> linkCodeExpiry;

        // Advanced
        public final SimpleConfigValue<Boolean> debugLogging;
        public final SimpleConfigValue<Integer> messageQueueSize;
        public final SimpleConfigValue<Integer> rateLimitDelay;

        static {
                Pair<ViscordConfig, SimpleConfigSpec> pair = new SimpleConfigBuilder()
                                .configure(ViscordConfig::new);
                CONFIG = pair.getLeft();
                SPEC = pair.getRight();
        }

        private ViscordConfig(SimpleConfigBuilder builder) {
                builder.comment(
                                "Viscord - Bidirectional Chat Integration",
                                "Connect your Minecraft server to Discord or Fluxer",
                                "",
                                "Quick Start:",
                                "1. Set 'enabled' to true",
                                "2. Choose your platform: 'discord' or 'fluxer'",
                                "3. Configure your platform settings below",
                                "4. Restart the server")
                                .push("viscord");

                enabled = builder.comment(
                                "Enable Viscord integration",
                                "Master toggle for all features")
                                .define("enabled", false);

                platform = builder.comment(
                                "Chat platform to use",
                                "discord - Full Discord bot integration (webhooks + bot API)",
                                "fluxer  - Fluxer bot integration (bot API + Gateway, no port forwarding needed)")
                                .define("platform", "discord");
                
                builder.pop().comment(
                                "Tridirectional Chat Configuration",
                                "Settings for 3-way chat between Discord, Fluxer, and Minecraft",
                                "Requires both Discord and Fluxer to be configured")
                                .push("tridirectional");

                enableTridirectionalChat = builder.comment(
                                "Enable tridirectional chat",
                                "Allows messages to flow between all three platforms",
                                "Discord <-> Minecraft <-> Fluxer")
                                .define("enabled", false);

                discordToFluxer = builder.comment(
                                "Bridge Discord messages to Fluxer",
                                "Messages from Discord will be sent to Fluxer")
                                .define("discord_to_fluxer", true);

                fluxerToDiscord = builder.comment(
                                "Bridge Fluxer messages to Discord",
                                "Messages from Fluxer will be sent to Discord")
                                .define("fluxer_to_discord", true);

                showPlatformSource = builder.comment(
                                "Show message source platform",
                                "Adds platform tags like [Discord] or [Fluxer] to bridged messages")
                                .define("show_source", true);
                
                builder.pop().comment(
                                "Discord Configuration",
                                "Required only when platform is set to 'discord'")
                                .push("discord");

                discordBotToken = builder.comment(
                                "Discord Bot Token",
                                "Get from: https://discord.com/developers/applications",
                                "Bot tab -> Copy Token",
                                "\nIMPORTANT: Keep this secret!")
                                .define("bot_token", "YOUR_BOT_TOKEN_HERE");

                discordChannelId = builder.comment(
                                "Discord Channel ID for chat messages",
                                "Right-click channel -> Copy Channel ID",
                                "(Enable Developer Mode in Discord settings)")
                                .define("channel_id", "YOUR_CHANNEL_ID_HERE");

                discordWebhookUrl = builder.comment(
                                "Discord Webhook URL",
                                "Channel Settings -> Integrations -> Webhooks -> Copy URL",
                                "Used to send Minecraft messages to Discord with player avatars")
                                .define("webhook_url", "");

                discordWebhookId = builder.comment(
                                "Webhook ID (optional)",
                                "Auto-extracted from URL if left empty")
                                .define("webhook_id", "");

                discordInviteUrl = builder.comment(
                                "Discord Invite URL",
                                "Shown when players use /discord command")
                                .define("invite_url", "");
                
                builder.pop().comment(
                                "Fluxer Configuration",
                                "Required only when platform is set to 'fluxer'",
                                "",
                                "How it works:",
                                "  - The bot connects to Fluxer via WebSocket Gateway (no port forwarding needed)",
                                "  - Messages are sent using the Bot API with your bot token and channel IDs",
                                "  - Events (join/leave/death) go to the event channel (or main if not set)")
                                .push("fluxer");
                
                fluxerApiKey = builder.comment(
                                "Fluxer Bot Token",
                                "Get from: https://fluxer.app -> Developer Portal -> Bot -> Copy Token",
                                "\nIMPORTANT: Keep this secret!")
                                .define("bot_token", "YOUR_FLUXER_BOT_TOKEN");
                
                fluxerChannelId = builder.comment(
                                "Fluxer Channel ID for chat messages",
                                "Right-click channel -> Copy Channel ID",
                                "(Enable Developer Mode in Fluxer settings)")
                                .define("channel_id", "YOUR_FLUXER_CHANNEL_ID");
                
                fluxerEventChannelId = builder.comment(
                                "Fluxer Channel ID for server events (optional)",
                                "Join/leave/death/advancement notifications go here",
                                "Leave empty to use the main channel_id above")
                                .define("event_channel_id", "");
                
                fluxerEventWebhookUrl = builder.comment(
                                "Fluxer Webhook URL for server events (optional)",
                                "Leave empty to use the main webhook_url above")
                                .define("event_webhook_url", "");
                
                fluxerWebhookUrl = builder.comment(
                                "Fluxer Webhook URL for sending messages",
                                "Optional: Use webhook instead of bot API for sending messages")
                                .define("webhook_url", "");

                fluxerReceiverPort = builder.comment(
                                "Fluxer HTTP Receiver Port",
                                "Port for receiving incoming messages from Fluxer")
                                .defineInRange("receiver_port", 8080, 1024, 65535);
                
                fluxerReceiverPath = builder.comment(
                                "Fluxer HTTP Receiver Path",
                                "URL path for receiving incoming messages")
                                .define("receiver_path", "/fluxer");
                
                fluxerApplicationId = builder.comment(
                                "Fluxer Application ID for bot invite",
                                "Used to generate the bot invite URL",
                                "Get from: https://fluxer.app -> Developer Portal -> Application ID")
                                .define("application_id", "YOUR_APPLICATION_ID");

                builder.pop().comment(
                                "Server Identity",
                                "How your server appears in messages")
                                .push("server");

                serverPrefix = builder.comment(
                                "Server prefix shown in messages",
                                "Example: [Survival], [Creative], [SMP]")
                                .define("prefix", "[MC]");

                serverName = builder.comment(
                                "Server name for embeds and bot messages")
                                .define("name", "Minecraft Server");

                serverAvatarUrl = builder.comment(
                                "Server avatar URL for event messages",
                                "Leave empty to use default")
                                .define("avatar_url", "");

                builder.pop().comment(
                                "Message Formats",
                                "Customize how messages appear")
                                .push("formats");

                discordToMinecraftFormat = builder.comment(
                                "Format for Discord/Fluxer -> Minecraft messages",
                                "Placeholders: {username}, {message}")
                                .define("discord_to_minecraft", "[Discord] {username}: {message}");

                minecraftToDiscordFormat = builder.comment(
                                "Format for Minecraft -> Discord/Fluxer messages",
                                "Placeholder: {message}")
                                .define("minecraft_to_discord", "{message}");

                webhookUsernameFormat = builder.comment(
                                "Webhook display name format (Discord only)",
                                "Placeholders: {prefix}, {username}")
                                .define("webhook_username", "{prefix} {username}");

                avatarUrl = builder.comment(
                                "Player avatar URL template",
                                "Placeholders: {uuid}, {username}")
                                .define("avatar_url", "https://minotar.net/armor/bust/{username}/100.png");

                builder.pop().comment(
                                "Event Notifications",
                                "Choose which events to send to Discord/Fluxer")
                                .push("events");

                sendJoin = builder.comment("Send player join notifications")
                                .define("send_join", true);

                sendLeave = builder.comment("Send player leave notifications")
                                .define("send_leave", true);

                sendDeath = builder.comment("Send player death messages")
                                .define("send_death", true);

                sendAdvancement = builder.comment("Send advancement notifications")
                                .define("send_advancement", true);

                eventChannelId = builder.comment(
                                "Separate Discord channel ID for events (optional)",
                                "Leave empty to use main Discord channel")
                                .define("event_channel_id", "");

                eventWebhookUrl = builder.comment(
                                "Separate Discord webhook URL for events (optional)",
                                "Leave empty to use main Discord webhook")
                                .define("event_webhook_url", "");

                builder.pop().comment(
                                "Chat Filters & Loop Prevention",
                                "Prevent message loops and filter content")
                                .push("filters");

                ignoreBots = builder.comment("Ignore bot messages from Discord/Fluxer")
                                .define("ignore_bots", true);

                ignoreWebhooks = builder.comment("Ignore other webhook messages")
                                .define("ignore_webhooks", true);

                filterByPrefix = builder.comment(
                                "Filter messages by server prefix",
                                "Prevents echoing your own server's bridged messages back")
                                .define("filter_by_prefix", true);

                showOtherServerEvents = builder.comment(
                                "Show events from other servers in Minecraft chat",
                                "Only applies to multi-server setups sharing a channel")
                                .define("show_other_server_events", true);

                enableChatFilter = builder.comment(
                                "Enable chat filter to prevent certain messages from being bridged",
                                "Messages starting with the filter prefix will only appear in-game")
                                .define("enable_chat_filter", false);

                chatFilterPrefix = builder.comment(
                                "Prefix to suppress from bridge relay",
                                "Example: '!' means '!test' stays in-game only")
                                .define("chat_filter_prefix", "!");

                builder.pop().comment(
                                "Bot Status",
                                "Bot presence and activity configuration")
                                .push("bot");

                setBotStatus = builder.comment(
                                "Update bot status with live player count",
                                "Shows 'Online: X/Y' in the bot's activity")
                                .define("enabled", true);

                botStatusFormat = builder.comment(
                                "Bot status format string",
                                "Placeholders: {online}, {max}",
                                "Example: Online: {online}/{max}")
                                .define("format", "Online: {online}/{max}");

                builder.pop().comment(
                                "Account Linking",
                                "Link Minecraft and Discord accounts")
                                .push("linking");

                enableAccountLinking = builder.comment("Enable Discord account linking")
                                .define("enabled", true);

                linkCodeExpiry = builder.comment(
                                "How long link codes remain valid (seconds)")
                                .defineInRange("code_expiry_seconds", 300, 60, 600);

                builder.pop().comment(
                                "Advanced Settings",
                                "Performance and debugging options")
                                .push("advanced");

                debugLogging = builder.comment("Enable verbose debug logging")
                                .define("debug_logging", false);

                messageQueueSize = builder.comment(
                                "Message queue size",
                                "Increase if losing messages during high traffic")
                                .defineInRange("queue_size", 100, 10, 1000);

                rateLimitDelay = builder.comment(
                                "Rate limit delay (milliseconds)",
                                "Minimum delay between outgoing API calls")
                                .defineInRange("rate_limit", 1000, 100, 5000);

                builder.pop();
        }
}
