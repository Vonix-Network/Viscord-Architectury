package network.vonix.viscord.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.discord.DiscordEventHandler;

@Mod(Viscord.MOD_ID)
public final class ViscordNeoForge {
    public ViscordNeoForge(IEventBus modBus) {
        Viscord.init();
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new NeoForgeChatEventHandler());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        DiscordEventHandler.registerCommands(event.getDispatcher());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        Viscord.onServerStarted(event.getServer());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        Viscord.onServerStopping();
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Viscord.onPlayerJoin(event.getEntity());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Viscord.onPlayerQuit(event.getEntity());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        Viscord.onLivingDeath(event);
    }
}
