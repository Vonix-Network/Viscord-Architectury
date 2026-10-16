package network.vonix.viscord.platform;

import java.nio.file.Path;
import java.util.Objects;

/** Loader-neutral filesystem boundary for the 26.x shared source tree. */
public final class ViscordPlatform {
    private static Path configDirectory;

    private ViscordPlatform() {
    }

    public static void install(Path path) {
        configDirectory = Objects.requireNonNull(path, "path");
    }

    public static Path getConfigDirectory() {
        Path path = configDirectory;
        if (path == null) {
            throw new IllegalStateException("Viscord platform has not been installed");
        }
        return path;
    }
}
