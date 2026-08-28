package network.vonix.viscord.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeIncludeGraphTest {

    @Test
    void requestedCellsResolveAsIndependentProjects() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String settings = Files.readString(root.resolve("settings.gradle"));
        String build = Files.readString(root.resolve("build.gradle"));

        assertTrue(settings.contains("include 'core'"));
        assertTrue(settings.contains("include 'common', 'fabric', 'neoforge'"));
        assertTrue(settings.contains("mc-1.21.1:common"));
        assertTrue(settings.contains("mc-1.21.1:fabric"));
        assertTrue(settings.contains("mc-1.21.1:neoforge"));
        assertTrue(settings.contains("mc-26.1.2:neoforge"));
        assertTrue(settings.contains("nativeIncludeGraph"));

        assertTrue(Files.isDirectory(root.resolve("core")));
        assertTrue(Files.isDirectory(root.resolve("viscord-1.21.1-fabric-neoforge-template/common")));
        assertTrue(Files.isDirectory(root.resolve("viscord-1.21.1-fabric-neoforge-template/fabric")));
        assertTrue(Files.isDirectory(root.resolve("viscord-1.21.1-fabric-neoforge-template/neoforge")));
        assertTrue(Files.isDirectory(root.resolve("viscord-1.26.1.2-neoforge-target")));
        assertTrue(Files.isRegularFile(root.resolve("viscord-1.26.1.2-neoforge-target/settings.gradle")));
        assertTrue(Files.isRegularFile(root.resolve("viscord-1.26.1.2-neoforge-target/gradlew")));

        assertTrue(Files.isDirectory(root.resolve("viscord-1.18.2-fabric-forge-template")));
        assertTrue(Files.isDirectory(root.resolve("viscord-1.19.2-fabric-forge-template")));
        assertTrue(Files.isDirectory(root.resolve("viscord-1.20.1-fabric-forge-template")));

        assertTrue(build.contains("verifyNativeIncludeGraph"));
        assertTrue(build.contains("architecturyCells"));
        assertFalse(build.contains("subprojects.findAll { it.name != 'core' }"));
    }
}
