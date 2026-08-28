package network.vonix.viscord.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ImportBoundaryTest {

    private static final String[] CORE_FORBIDDEN = {
            "net.minecraft.",
            "net.fabricmc.",
            "net.minecraftforge.",
            "net.neoforged.",
            "dev.architectury.",
            "org.javacord."
    };

    private static final String[] COMMON_LOADER_FORBIDDEN = {
            "net.fabricmc.",
            "net.minecraftforge.",
            "net.neoforged."
    };

    @Test
    void coreMainSourcesHaveNoMinecraftLoaderOrArchitecturyImports() throws IOException {
        Path main = repoRoot().resolve("core/src/main/java");
        List<String> hits = importHits(main, CORE_FORBIDDEN);
        if (!hits.isEmpty()) {
            fail("core imported a forbidden package:\n" + String.join("\n", hits));
        }
    }

    @Test
    void versionCommonHasNoLoaderApiImports() throws IOException {
        Path common = repoRoot().resolve("viscord-1.21.1-fabric-neoforge-template/common/src/main/java");
        assertTrue(Files.isDirectory(common), "1.21.1 common sources missing");
        List<String> hits = importHits(common, COMMON_LOADER_FORBIDDEN);
        if (!hits.isEmpty()) {
            fail("version-common imported a loader API:\n" + String.join("\n", hits));
        }
    }

    public static Path repoRoot() {
        Path here = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve("settings.gradle")) && Files.exists(p.resolve("core/build.gradle"))) {
                return p;
            }
        }
        throw new IllegalStateException("Viscord repo root not resolvable from " + here);
    }

    static List<String> importHits(Path root, String[] prefixes) throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                try {
                    int lineNo = 0;
                    for (String line : Files.readAllLines(file)) {
                        lineNo++;
                        String trimmed = line.trim();
                        if (!trimmed.startsWith("import ")) continue;
                        for (String prefix : prefixes) {
                            if (trimmed.contains(prefix)) {
                                hits.add(root.relativize(file) + ":" + lineNo + " " + trimmed);
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return hits;
    }
}
