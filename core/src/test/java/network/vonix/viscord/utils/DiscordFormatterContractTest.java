package network.vonix.viscord.utils;

import network.vonix.viscord.core.ImportBoundaryTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordFormatterContractTest {

    @Test
    void javaExecProbeStillHolds() {
        DiscordFormatterTest.assertContract();
    }

    @Test
    void stripAndDetectFormattingCodes() {
        assertEquals("hello", DiscordFormatter.stripFormatting("&ahello&r"));
        assertTrue(DiscordFormatter.hasFormatting("§nunderline"));
        assertFalse(DiscordFormatter.hasFormatting("no codes"));
    }

    @Test
    void oneTwentyOneCommonDoesNotVendorFormatter() {
        Path commonUtils = ImportBoundaryTest.repoRoot().resolve(
                "viscord-1.21.1-fabric-neoforge-template/common/src/main/java/network/vonix/viscord/utils/DiscordFormatter.java");
        assertFalse(Files.exists(commonUtils), "1.21.1 common must depend on core DiscordFormatter");
    }

    @Test
    void twentySixDependsOnCoreFormatter() throws IOException {
        Path vendor = ImportBoundaryTest.repoRoot().resolve(
                "viscord-1.26.1.2-neoforge-target/src/main/java/network/vonix/viscord/utils/DiscordFormatter.java");
        assertFalse(Files.exists(vendor), "26.1.2 must use core DiscordFormatter instead of a vendor copy");
        String build = Files.readString(ImportBoundaryTest.repoRoot().resolve(
                "viscord-1.26.1.2-neoforge-target/build.gradle"));
        assertTrue(build.contains("core/src/main/java"),
                "26.1.2 must compile core sources so ChatPrefixFilter and DiscordFormatter resolve");
    }
}
