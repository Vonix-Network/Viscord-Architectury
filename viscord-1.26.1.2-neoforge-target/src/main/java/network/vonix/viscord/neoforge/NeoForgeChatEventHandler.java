package network.vonix.viscord.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.neoforge.ChatForwarder;

/** Native NeoForge server-chat adapter for the 26.1.2 lane. */
public final class NeoForgeChatEventHandler {
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onServerChat(ServerChatEvent event) {
        try {
            ServerPlayer player = event.getPlayer();
            String rawMessage = event.getMessage().getString();
            ChatForwarder.forwardChat(player, rawMessage);
        } catch (Exception e) {
            Viscord.LOGGER.error("[Viscord] Error in NeoForgeChatEventHandler", e);
        }
    }
}
