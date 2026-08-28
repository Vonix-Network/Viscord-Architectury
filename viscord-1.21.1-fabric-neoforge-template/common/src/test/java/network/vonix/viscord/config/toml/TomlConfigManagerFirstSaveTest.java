package network.vonix.viscord.config.toml;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomlConfigManagerFirstSaveTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        TomlConfigManager.close();
    }

    @Test
    void missingConfigExistsBeforeLoadReturns() throws Exception {
        Path toml = tempDir.resolve("viscord.toml");
        assertFalse(Files.exists(toml));
        TomlConfigManager.load(tempDir);
        assertTrue(Files.isRegularFile(toml), "first save must materialize viscord.toml before load() returns");
        assertNotNull(TomlConfigManager.getConfig());
        assertEquals("[MC]", TomlConfigManager.getString("server.prefix", ""));
        assertTrue(Files.readString(toml, StandardCharsets.UTF_8).contains("Master toggle"));
    }

    @Test
    void existingConfigIsNotOverwritten() throws Exception {
        TomlConfigManager.load(tempDir);
        TomlConfigManager.set("server.prefix", "[Survival]");
        TomlConfigManager.set("general.enabled", true);
        TomlConfigManager.save();
        TomlConfigManager.close();

        TomlConfigManager.load(tempDir);
        assertEquals("[Survival]", TomlConfigManager.getString("server.prefix", ""));
        assertTrue(TomlConfigManager.getBoolean("general.enabled", false));
    }

    @Test
    void reloadPreservesCommentsAndKeys() throws Exception {
        TomlConfigManager.load(tempDir);
        String enabledComment = TomlConfigManager.getConfig().getComment("general.enabled");
        assertNotNull(enabledComment);
        assertTrue(enabledComment.contains("Master toggle"));
        TomlConfigManager.set("server.name", "Pinned Server");
        TomlConfigManager.save();
        TomlConfigManager.close();

        TomlConfigManager.load(tempDir);
        assertEquals("Pinned Server", TomlConfigManager.getString("server.name", ""));
        assertEquals(enabledComment, TomlConfigManager.getConfig().getComment("general.enabled"));
        assertTrue(TomlConfigManager.getConfig().contains("discord.bot_token"));
        assertTrue(TomlConfigManager.getConfig().contains("filters.chat.prefix"));
        String disk = Files.readString(tempDir.resolve("viscord.toml"), StandardCharsets.UTF_8);
        assertTrue(disk.contains("Master toggle"));
        assertTrue(disk.contains("Pinned Server"));
    }

    @Test
    void jsonMigrationPreservesValues() throws Exception {
        String json = "{\"viscord\":{\"enabled\":true},\"server\":{\"prefix\":\"[Mig]\"}}";
        Files.writeString(tempDir.resolve("viscord.json"), json, StandardCharsets.UTF_8);

        TomlConfigManager.load(tempDir);

        assertTrue(Files.isRegularFile(tempDir.resolve("viscord.toml")));
        assertTrue(Files.isRegularFile(tempDir.resolve("viscord.json.backup")));
        assertFalse(Files.exists(tempDir.resolve("viscord.json")));
        assertEquals("[Mig]", TomlConfigManager.getString("server.prefix", ""));
        assertTrue(TomlConfigManager.getBoolean("general.enabled", false));
        String disk = Files.readString(tempDir.resolve("viscord.toml"), StandardCharsets.UTF_8);
        assertTrue(disk.contains("Master toggle"));
    }
}
