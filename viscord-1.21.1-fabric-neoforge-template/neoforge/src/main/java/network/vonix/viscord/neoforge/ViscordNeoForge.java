package network.vonix.viscord.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import network.vonix.viscord.Viscord;

@Mod(Viscord.MOD_ID)
public final class ViscordNeoForge {
    public ViscordNeoForge(IEventBus modBus) {
        System.out.println("[Viscord] NeoForge entry point called!");
        // Run our common setup.
        Viscord.init();

        // Register the NeoForge chat event handler to relay player messages to Discord.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new NeoForgeChatEventHandler());
    }
}
