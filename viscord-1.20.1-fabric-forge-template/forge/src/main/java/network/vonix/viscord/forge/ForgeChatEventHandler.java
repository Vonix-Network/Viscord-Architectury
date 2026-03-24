package network.vonix.viscord.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.ViscordConfig;
import network.vonix.viscord.discord.DiscordManager;

public class ForgeChatEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onServerChat(ServerChatEvent event) {
        try {
            ServerPlayer player = event.getPlayer();
            String rawMessage = event.getRawText();

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
            Viscord.LOGGER.error("[Viscord] Error in ForgeChatEventHandler", e);
        }
    }
}
