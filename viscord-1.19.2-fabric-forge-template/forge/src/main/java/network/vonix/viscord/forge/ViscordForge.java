package network.vonix.viscord.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import network.vonix.viscord.Viscord;

@Mod(Viscord.MOD_ID)
public final class ViscordForge {
    public ViscordForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(Viscord.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        Viscord.init();

        // Register the Forge chat event handler to relay player messages to Discord.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ForgeChatEventHandler());
    }
}
