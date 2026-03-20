package network.vonix.viscord.forge.mixin;

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

/**
 * Mixin to intercept chat messages and send to Discord.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;)V", at = @At("HEAD"))
    private void vonixcore$onHandleChat(ServerboundChatPacket packet, CallbackInfo ci) {
        try {
            String rawMessage = packet.getMessage();

            if (DiscordManager.getInstance().isRunning()) {
                boolean shouldSendToDiscord = true;

                if (ViscordConfig.CONFIG.enableChatFilter.get()) {
                    String filterPrefix = ViscordConfig.CONFIG.chatFilterPrefix.get();
                    if (filterPrefix != null && !filterPrefix.isEmpty() && rawMessage.startsWith(filterPrefix)) {
                        shouldSendToDiscord = false;
                    }
                }

                if (shouldSendToDiscord) {
                    String displayName = player.getName().getString();
                    DiscordManager.getInstance()
                            .sendChatMessage(displayName, rawMessage, player.getStringUUID());
                }
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("[Viscord] Error in chat mixin", e);
        }
    }
}
