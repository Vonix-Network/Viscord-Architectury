package network.vonix.viscord.fabric;

import net.fabricmc.api.ModInitializer;

import network.vonix.viscord.Viscord;

public final class ViscordFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        network.vonix.viscord.platform.PlatformEvents.Holder.install(new ViscordPlatformEvents());
        System.out.println("[Viscord] Fabric entry point called!");
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        Viscord.init();
    }
}
