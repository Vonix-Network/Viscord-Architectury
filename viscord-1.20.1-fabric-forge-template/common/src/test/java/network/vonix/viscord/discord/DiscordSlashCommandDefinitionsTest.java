package network.vonix.viscord.discord;

import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordSlashCommandDefinitionsTest {

    @Test
    void exposesStableNativeCommandSurface() {
        Map<String, DiscordSlashCommandDefinitions.Definition> definitions =
                DiscordSlashCommandDefinitions.all().stream()
                        .collect(Collectors.toMap(DiscordSlashCommandDefinitions.Definition::name, definition -> definition));

        assertEquals(List.of("link", "list", "home", "help", "status"),
                DiscordSlashCommandDefinitions.all().stream()
                        .map(DiscordSlashCommandDefinitions.Definition::name)
                        .collect(Collectors.toList()));
        assertEquals(5, definitions.size());
        assertFalse(definitions.get("link").administratorOnly());
        assertFalse(definitions.get("home").administratorOnly());
        assertTrue(definitions.get("status").administratorOnly());
    }

    @Test
    void validatesLinkAndHomeOptionContracts() {
        SlashCommandOption linkCode = DiscordSlashCommandDefinitions.all().stream()
                .filter(definition -> definition.name().equals("link"))
                .findFirst().orElseThrow()
                .options().get(0);
        assertEquals("code", linkCode.getName());
        assertEquals(SlashCommandOptionType.STRING, linkCode.getType());
        assertTrue(linkCode.isRequired());
        assertEquals(6L, linkCode.getMinLength().orElseThrow());
        assertEquals(6L, linkCode.getMaxLength().orElseThrow());

        SlashCommandOption homeName = DiscordSlashCommandDefinitions.all().stream()
                .filter(definition -> definition.name().equals("home"))
                .findFirst().orElseThrow()
                .options().get(0);
        assertEquals("name", homeName.getName());
        assertEquals(SlashCommandOptionType.STRING, homeName.getType());
        assertTrue(homeName.isRequired());
        assertEquals(1L, homeName.getMinLength().orElseThrow());
        assertEquals(32L, homeName.getMaxLength().orElseThrow());
    }
}
