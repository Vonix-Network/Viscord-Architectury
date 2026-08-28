package network.vonix.viscord.neoforge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the 1.21.1 NeoForge JPMS split: viscord must not export
 * {@code com.electronwill.nightconfig.*} while still nesting NightConfig 3.8.3.
 */
class NightConfigPackagingTest {

    @Test
    void packagedJarDoesNotExportUnrelocatedNightConfig() throws IOException {
        Path jar = packagedJar();
        assertTrue(Files.isRegularFile(jar), "packaged NeoForge jar must exist: " + jar);

        List<String> unrelocated = new ArrayList<>();
        boolean relocatedCore = false;
        boolean relocatedToml = false;
        boolean relocatedSerde = false;
        boolean manager = false;
        byte[] managerBytes = null;

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("com/electronwill/nightconfig/") && name.endsWith(".class")) {
                    unrelocated.add(name);
                }
                if ("network/vonix/viscord/shadow/nightconfig/core/Config.class".equals(name)) {
                    relocatedCore = true;
                }
                if ("network/vonix/viscord/shadow/nightconfig/toml/TomlFormat.class".equals(name)) {
                    relocatedToml = true;
                }
                if ("network/vonix/viscord/shadow/nightconfig/core/serde/ObjectSerializer.class".equals(name)) {
                    relocatedSerde = true;
                }
                if ("network/vonix/viscord/config/toml/TomlConfigManager.class".equals(name)) {
                    manager = true;
                    managerBytes = zip.getInputStream(zip.getEntry(name)).readAllBytes();
                }
            }
        }

        assertTrue(unrelocated.isEmpty(),
                "viscord must not export com.electronwill.nightconfig (JPMS collision with NeoForge): "
                        + unrelocated);
        assertTrue(relocatedCore, "NightConfig core must remain nested under the relocated package");
        assertTrue(relocatedToml, "NightConfig toml must remain nested under the relocated package");
        assertTrue(relocatedSerde, "NightConfig serde must remain nested under the relocated package");
        assertTrue(manager, "TomlConfigManager must be packaged");
        assertNotNull(managerBytes);
        String latin1 = new String(managerBytes, StandardCharsets.ISO_8859_1);
        assertFalse(
                latin1.contains("com/electronwill/nightconfig")
                        || latin1.contains("com.electronwill.nightconfig"),
                "TomlConfigManager bytecode must not reference unrelocated NightConfig");
        assertTrue(
                latin1.contains("network/vonix/viscord/shadow/nightconfig"),
                "TomlConfigManager bytecode must reference relocated NightConfig");
    }

    private static Path packagedJar() {
        String prop = System.getProperty("viscord.neoforge.packagedJar");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        return Path.of("build/libs/viscord-neoforge-2.0.0-common.1.jar");
    }
}
