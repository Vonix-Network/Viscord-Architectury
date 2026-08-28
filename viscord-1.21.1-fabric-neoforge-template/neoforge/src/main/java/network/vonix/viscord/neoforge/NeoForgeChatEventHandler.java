package network.vonix.viscord.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.chat.ChatPrefixFilter;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import network.vonix.viscord.discord.DiscordManager;

/**
 * NeoForge chat capture via ServerChatEvent. Prefix/filter decision is
 * {@link ChatPrefixFilter#shouldForward}; do not replace this with the Fabric mixin.
 */
public class NeoForgeChatEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onServerChat(ServerChatEvent event) {
        try {
            ServerPlayer player = event.getPlayer();
            String rawMessage = event.getMessage().getString();
            DiscordManager manager = DiscordManager.getInstance();
            if (manager.isRunning()
                    && ChatPrefixFilter.shouldForward(
                            ViscordConfigToml.Filters.Chat.ENABLED.get(),
                            ViscordConfigToml.Filters.Chat.PREFIX.get(),
                            rawMessage)) {
                manager.sendChatMessage(player.getName().getString(), rawMessage, player.getStringUUID());
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("[Viscord] Error in NeoForgeChatEventHandler", e);
        }
    }
}
