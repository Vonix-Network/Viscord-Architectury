package network.vonix.viscord.discord.platform;

import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import network.vonix.viscord.utils.DiscordFormatter;
import org.javacord.api.entity.message.Message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Handles cross-platform message relay between Discord and Fluxer.
 *
 * Owns the echo suppression cache and all relay logic.
 * Has no knowledge of Minecraft — it only talks to DiscordPlatform and FluxerPlatform.
 */
public class TridirectionalBridge {

    private final DiscordPlatform discord;
    private final FluxerPlatform fluxer;

    // Echo suppression: records fingerprints of messages we sent via webhook
    // so when the gateway echoes them back we can drop them.
    private final Map<String, Long> recentDiscordBridges = new ConcurrentHashMap<>();

    public TridirectionalBridge(DiscordPlatform discord, FluxerPlatform fluxer) {
        this.discord = discord;
        this.fluxer = fluxer;
    }

    /**
     * Returns the echo cache so FluxerPlatform's message listener can check it.
     */
    public Map<String, Long> getEchoCache() {
        return recentDiscordBridges;
    }

    // =========================================================================
    // Discord → Fluxer
    // =========================================================================

    /**
     * Bridge a Discord message to Fluxer.
     * Uses webhook if configured (preserves sender identity), otherwise bot API.
     */
    public void discordToFluxer(String authorName, String content, Message message) {
        if (!ViscordConfigToml.Tridirectional.DISCORD_TO_FLUXER.get()) return;

        boolean hasWebhook = fluxer.hasWebhook();
        if (!hasWebhook && !fluxer.isConfigured()) return;

        try {
            String avatarUrl = "";
            try {
                org.javacord.api.entity.Icon avatar = message.getAuthor().getAvatar();
                if (avatar != null) avatarUrl = avatar.getUrl().toString();
            } catch (Exception ignored) {}

            if (hasWebhook) {
                fluxer.sendWebhookMessage(authorName, avatarUrl, content, recentDiscordBridges);
            } else {
                String formatted = formatWithSource(content, "Discord", authorName);
                fluxer.sendBotMessage(fluxer.getChannelId(), formatted);
            }
            Viscord.LOGGER.debug("[Bridge] Discord→Fluxer: {}", authorName);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Bridge] Failed Discord→Fluxer", e);
        }
    }

    // =========================================================================
    // Fluxer → Discord
    // =========================================================================

    /**
     * Bridge a Fluxer message to Discord.
     * Checks echo cache first to suppress messages we sent via webhook.
     *
     * @return true if the message was suppressed as an echo
     */
    public boolean checkAndSuppressEcho(String username, String message) {
        String echoKey = username + ":" + message;
        Long bridgedAt = recentDiscordBridges.remove(echoKey);
        if (bridgedAt != null && (System.currentTimeMillis() - bridgedAt) < 5000) {
            Viscord.LOGGER.debug("[Bridge] Suppressing Fluxer echo for: {}", username);
            return true;
        }
        return false;
    }

    /**
     * Bridge a Fluxer message to Discord via webhook.
     */
    public void fluxerToDiscord(String username, String message, String avatarUrl) {
        if (!ViscordConfigToml.Tridirectional.FLUXER_TO_DISCORD.get()) return;
        if (!discord.isConfigured()) return;

        // Prevent echo: messages that originated from Discord contain [Discord] tag
        if (message.contains("[Discord]")) {
            Viscord.LOGGER.debug("[Bridge] Skipping Discord-originated echo: {}", username);
            return;
        }

        try {
            // Strip [Fluxer] prefix and username prefix from the formatted Minecraft string
            String clean = message.replaceFirst("^\\[Fluxer\\]\\s*", "");
            if (clean.matches(".*" + Pattern.quote(username) + ":\\s*.*")) {
                clean = clean.replaceFirst(".*" + Pattern.quote(username) + ":\\s*", "");
            }

            String converted = DiscordFormatter.convertToDiscordFormatting(clean);
            discord.sendWebhookMessage("[Fluxer]" + username,
                avatarUrl != null ? avatarUrl : "", converted);
            Viscord.LOGGER.debug("[Bridge] Fluxer→Discord: {}", username);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Bridge] Failed Fluxer→Discord", e);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String formatWithSource(String message, String source, String author) {
        if (ViscordConfigToml.Tridirectional.SHOW_SOURCE.get()) {
            return "[" + source + "] " + author + ": " + message;
        }
        return author + ": " + message;
    }
}
