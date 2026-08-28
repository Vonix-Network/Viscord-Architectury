package network.vonix.viscord.command;

import network.vonix.viscord.core.ImportBoundaryTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandNamesTest {

    @Test
    void rootLiteralsAreStable() {
        assertTrue(CommandNames.rootLiterals().containsAll(
                List.of(CommandNames.DISCORD, CommandNames.VISCORD, CommandNames.VONIX_DEPRECATED_ALIAS)));
    }

    @Test
    void requestedCellsRegisterTheSameRootLiterals() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        Path cell1211 = root.resolve(
                "viscord-1.21.1-fabric-neoforge-template/common/src/main/java/network/vonix/viscord/discord/DiscordEventHandler.java");
        Path cell2612 = root.resolve(
                "viscord-1.26.1.2-neoforge-target/src/main/java/network/vonix/viscord/discord/DiscordEventHandler.java");
        assertTrue(Files.isRegularFile(cell1211));
        assertTrue(Files.isRegularFile(cell2612));
        assertRegisters(Files.readString(cell1211), "1.21.1");
        assertRegisters(Files.readString(cell2612), "26.1.2");
    }

    private static void assertRegisters(String source, String cell) {
        assertTrue(source.contains("Commands.literal(\"" + CommandNames.DISCORD + "\")"), cell + " missing /discord");
        assertTrue(source.contains("Commands.literal(\"" + CommandNames.VISCORD + "\")"), cell + " missing /viscord");
        assertTrue(source.contains("Commands.literal(\"" + CommandNames.VONIX_DEPRECATED_ALIAS + "\")"), cell + " missing /vonix");
        assertTrue(source.contains("Commands.literal(\"" + CommandNames.RELOAD + "\")"), cell + " missing reload");
        assertTrue(source.contains("Commands.literal(\"" + CommandNames.LINK + "\")"), cell + " missing link");
        assertTrue(source.contains("Commands.literal(\"" + CommandNames.UNLINK + "\")"), cell + " missing unlink");
    }
}
