package network.vonix.viscord.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.chat.ChatForwarder;
import network.vonix.viscord.discord.DiscordEventHandler;
import network.vonix.viscord.platform.ViscordPlatform;

/** Fabric adapter for the shared 26.x source tree. */
public final class ViscordFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ViscordPlatform.install(FabricLoader.getInstance().getConfigDir());

        ServerLifecycleEvents.SERVER_STARTED.register(Viscord::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Viscord.onServerStopping());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Viscord.onPlayerJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Viscord.onPlayerQuit(handler.getPlayer()));
        ServerMessageEvents.CHAT_MESSAGE.register((message, player, bound) ->
                ChatForwarder.forwardChat(player, message.signedContent()));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                Viscord.onLivingDeath(player, source.getLocalizedDeathMessage(player).getString());
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                DiscordEventHandler.registerCommands(dispatcher));

        Viscord.init();
    }
}
