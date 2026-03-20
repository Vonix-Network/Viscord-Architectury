package network.vonix.viscord.neoforge;

import net.neoforged.fml.common.Mod;

import network.vonix.viscord.Viscord;

@Mod(Viscord.MOD_ID)
public final class ViscordNeoForge {
    public ViscordNeoForge() {
        // Run our common setup.
        Viscord.init();
    }
}
