package network.vonix.viscord.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.ViscordConfig;
import network.vonix.viscord.discord.DiscordManager;

public class NeoForgeChatEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onServerChat(ServerChatEvent event) {
        try {
            ServerPlayer player = event.getPlayer();
            String rawMessage = event.getMessage();

            DiscordManager manager = DiscordManager.getInstance();
            if (manager.isRunning()) {
                boolean shouldSend = true;

                if (ViscordConfig.CONFIG.enableChatFilter.get()) {
                    String filterPrefix = ViscordConfig.CONFIG.chatFilterPrefix.get();
                    if (filterPrefix != null && !filterPrefix.isEmpty()
                            && rawMessage.startsWith(filterPrefix)) {
                        shouldSend = false;
                    }
                }

                if (shouldSend) {
                    String displayName = player.getName().getString();
                    manager.sendChatMessage(displayName, rawMessage, player.getStringUUID());
                }
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("[Viscord] Error in NeoForgeChatEventHandler", e);
        }
    }
}
