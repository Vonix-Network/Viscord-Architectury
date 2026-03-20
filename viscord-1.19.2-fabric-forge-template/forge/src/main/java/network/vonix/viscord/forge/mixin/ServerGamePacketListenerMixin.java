package network.vonix.viscord.forge.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.ViscordConfig;
import network.vonix.viscord.discord.DiscordManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"))
    private void viscord$onHandleChat(ServerboundChatPacket packet, CallbackInfo ci) {
        try {
            String rawMessage = packet.message();
            if (DiscordManager.getInstance().isRunning()) {
                boolean shouldSend = true;
                if (ViscordConfig.CONFIG.enableChatFilter.get()) {
                    String filterPrefix = ViscordConfig.CONFIG.chatFilterPrefix.get();
                    if (filterPrefix != null && !filterPrefix.isEmpty() && rawMessage.startsWith(filterPrefix)) {
                        shouldSend = false;
                    }
                }
                if (shouldSend) {
                    DiscordManager.getInstance().sendChatMessage(player.getName().getString(), rawMessage, player.getStringUUID());
                }
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("[Viscord] Error in chat mixin", e);
        }
    }
}
