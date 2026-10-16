package network.vonix.viscord.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.discord.DiscordEventHandler;
import network.vonix.viscord.platform.ViscordPlatform;

@Mod(Viscord.MOD_ID)
public final class ViscordNeoForge {
    public ViscordNeoForge(IEventBus modBus, ModContainer modContainer) {
        ViscordPlatform.install(FMLPaths.CONFIGDIR.get());
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new NeoForgeChatEventHandler());
        Viscord.init();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        DiscordEventHandler.registerCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        Viscord.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        Viscord.onServerStopping();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Viscord.onPlayerJoin(event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Viscord.onPlayerQuit(event.getEntity());
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Viscord.onLivingDeath(player, event.getSource().getLocalizedDeathMessage(player).getString());
        }
    }
}
