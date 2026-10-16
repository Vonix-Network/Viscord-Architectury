package network.vonix.viscord.neoforge;

import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;
import network.vonix.viscord.platform.ViscordPlatform;

/** Small explicit adapter so shared code never imports NeoForge loader APIs. */
public final class ViscordNeoForgePlatform {
    private ViscordNeoForgePlatform() {
    }

    public static void install() {
        Path config = FMLPaths.CONFIGDIR.get();
        ViscordPlatform.install(config);
    }
}
