package network.vonix.viscord.fabric.mixin;

import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.chat.ChatPrefixFilter;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import network.vonix.viscord.discord.DiscordManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric chat capture. Mixin injection is the loader boundary; the
 * prefix/filter decision is {@link ChatPrefixFilter#shouldForward}.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"))
    private void viscord$onHandleChat(ServerboundChatPacket packet, CallbackInfo ci) {
        try {
            String rawMessage = packet.message();
            DiscordManager manager = DiscordManager.getInstance();
            if (manager.isRunning()
                    && ChatPrefixFilter.shouldForward(
                            ViscordConfigToml.Filters.Chat.ENABLED.get(),
                            ViscordConfigToml.Filters.Chat.PREFIX.get(),
                            rawMessage)) {
                manager.sendChatMessage(player.getName().getString(), rawMessage, player.getStringUUID());
            }
        } catch (Exception e) {
            Viscord.LOGGER.error("[Viscord] Error in chat mixin", e);
        }
    }
}
