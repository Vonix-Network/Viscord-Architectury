package network.vonix.viscord.neoforge;

import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import network.vonix.viscord.discord.DiscordManager;

/**
 * NeoForge chat forwarding adapter for the 26.1.2 lane.
 *
 * This helper intentionally lives outside the Mixin-owned package. NeoForge's
 * Mixin classloader rejects direct references to classes under a configured
 * Mixin package, even when those classes are not listed as mixins.
 */
public final class ChatForwarder {
    private ChatForwarder() {
    }

    /**
     * Applies Viscord's configured chat filter and forwards an accepted message
     * to the active Discord/Fluxer bridge.
     */
    public static void forwardChat(ServerPlayer player, String rawMessage) {
        if (player == null || rawMessage == null) {
            return;
        }

        DiscordManager manager = DiscordManager.getInstance();
        if (!manager.isRunning() || !shouldForward(rawMessage)) {
            return;
        }

        manager.sendChatMessage(player.getName().getString(), rawMessage, player.getStringUUID());
    }

    /**
     * Returns whether a raw chat message is allowed by the configured prefix
     * filter. This is deliberately side-effect free.
     */
    public static boolean shouldForward(String rawMessage) {
        if (!ViscordConfigToml.Filters.Chat.ENABLED.get()) {
            return true;
        }

        String filterPrefix = ViscordConfigToml.Filters.Chat.PREFIX.get();
        return filterPrefix == null || filterPrefix.isEmpty() || !rawMessage.startsWith(filterPrefix);
    }
}
