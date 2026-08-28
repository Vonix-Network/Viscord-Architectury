package network.vonix.viscord.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestedCellContractTest {

    @Test
    void oneTwentyOneInitDoesNotSleepWaitingForConfig() throws IOException {
        Path init = ImportBoundaryTest.repoRoot().resolve(
                "viscord-1.21.1-fabric-neoforge-template/common/src/main/java/network/vonix/viscord/Viscord.java");
        String source = Files.readString(init);
        assertFalse(source.contains("Thread.sleep("),
                "1.21.1 Viscord.init must not mask async NightConfig writes with Thread.sleep");
    }

    @Test
    void loaderChatPathsHonorCorePrefixFilter() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String fabric = Files.readString(root.resolve(
                "viscord-1.21.1-fabric-neoforge-template/fabric/src/main/java/network/vonix/viscord/fabric/mixin/ServerGamePacketListenerMixin.java"));
        String neo121 = Files.readString(root.resolve(
                "viscord-1.21.1-fabric-neoforge-template/neoforge/src/main/java/network/vonix/viscord/neoforge/NeoForgeChatEventHandler.java"));
        String neo261 = Files.readString(root.resolve(
                "viscord-1.26.1.2-neoforge-target/src/main/java/network/vonix/viscord/neoforge/ChatForwarder.java"));
        assertTrue(fabric.contains("ChatPrefixFilter.shouldForward"), "1.21.1 Fabric mixin must call core ChatPrefixFilter");
        assertTrue(neo121.contains("ChatPrefixFilter.shouldForward"), "1.21.1 NeoForge handler must call core ChatPrefixFilter");
        assertTrue(neo261.contains("ChatPrefixFilter.shouldForward"), "26.1.2 ChatForwarder must call core ChatPrefixFilter");
        String fabricGradle = Files.readString(root.resolve("viscord-1.21.1-fabric-neoforge-template/fabric/build.gradle"));
        String neoGradle = Files.readString(root.resolve("viscord-1.21.1-fabric-neoforge-template/neoforge/build.gradle"));
        assertTrue(fabricGradle.contains("implementation project(':core')"),
                "1.21.1 Fabric must compile against :core; namedElements of :common is non-transitive");
        assertTrue(neoGradle.contains("implementation project(':core')"),
                "1.21.1 NeoForge must compile against :core; namedElements of :common is non-transitive");
        assertFalse(Files.exists(root.resolve(
                "viscord-1.26.1.2-neoforge-target/src/main/java/network/vonix/viscord/neoforge/mixin/ServerGamePacketListenerMixin.java")),
                "do not copy the 1.21.1 chat mixin onto 26.1.2");
    }

    @Test
    void nightConfigVersionIsAlignedOnRequestedCells() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String common = Files.readString(root.resolve("viscord-1.21.1-fabric-neoforge-template/common/build.gradle"));
        String fabric = Files.readString(root.resolve("viscord-1.21.1-fabric-neoforge-template/fabric/build.gradle"));
        String neo = Files.readString(root.resolve("viscord-1.21.1-fabric-neoforge-template/neoforge/build.gradle"));
        String neo261 = Files.readString(root.resolve("viscord-1.26.1.2-neoforge-target/build.gradle"));
        assertTrue(common.contains("night-config:toml:3.8.3"));
        assertTrue(common.contains("night-config:core:3.8.3"));
        assertFalse(common.contains("night-config:toml:3.6.7"));
        assertTrue(fabric.contains("shadowBundle 'com.electronwill.night-config:toml:3.8.3'"));
        assertTrue(neo.contains("shadowBundle 'com.electronwill.night-config:toml:3.8.3'"));
        assertTrue(neo261.contains("night-config:toml:3.8.3"));
        assertTrue(neo261.contains("night-config:core:3.8.3"));
    }

    @Test
    void kotlinIsNestedOnlyWhereOkioJvmIsNested() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String fabric = Files.readString(root.resolve("viscord-1.21.1-fabric-neoforge-template/fabric/build.gradle"));
        String neo = Files.readString(root.resolve("viscord-1.21.1-fabric-neoforge-template/neoforge/build.gradle"));
        String neo261 = Files.readString(root.resolve("viscord-1.26.1.2-neoforge-target/build.gradle"));
        String notes = Files.readString(root.resolve("viscord-1.26.1.2-neoforge-target/PORT_NOTES.md"));
        assertTrue(fabric.contains("okio-jvm:3.9.0"));
        assertTrue(neo.contains("okio-jvm:3.9.0"));
        assertFalse(fabric.contains("kotlin-stdlib:1.9.25"),
                "do not copy the 26.1.2 jarJar kotlin pin onto 1.21.1 Fabric shadow");
        assertFalse(neo.contains("kotlin-stdlib:1.9.25"),
                "do not copy the 26.1.2 jarJar kotlin pin onto 1.21.1 NeoForge shadow");
        assertTrue(neo261.contains("okio-jvm:3.9.0"));
        assertTrue(neo261.contains("kotlin-stdlib:1.9.25"),
                "26.1.2 jarJar must nest kotlin-stdlib because okio-jvm needs Intrinsics");
        assertTrue(notes.contains("kotlin-stdlib") && notes.toLowerCase().contains("okio"),
                "PORT_NOTES must document why kotlin-stdlib is nested on 26.1.2");
        assertFalse(notes.contains("Kotlin stdlib is intentionally not bundled"));
    }

    @Test
    void publicMetadataIdentityIsAligned() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String fabric = Files.readString(root.resolve(
                "viscord-1.21.1-fabric-neoforge-template/fabric/src/main/resources/fabric.mod.json"));
        String neo121 = Files.readString(root.resolve(
                "viscord-1.21.1-fabric-neoforge-template/neoforge/src/main/resources/META-INF/neoforge.mods.toml"));
        String neo261 = Files.readString(root.resolve(
                "viscord-1.26.1.2-neoforge-target/src/main/resources/META-INF/neoforge.mods.toml"));
        for (String meta : new String[]{fabric, neo121, neo261}) {
            assertTrue(meta.contains("viscord"));
            assertTrue(meta.contains("Vonix Network"));
            assertTrue(meta.contains("MIT"));
            assertFalse(meta.contains("Me!"));
            assertFalse(meta.contains("Insert License Here"));
            assertFalse(meta.contains("CC0-1.0"));
            assertFalse(meta.contains("fabric-example-mod"));
            assertFalse(meta.contains("another-mod"));
            assertFalse(meta.contains("ExampleMod"));
        }
        assertTrue(fabric.contains("\"id\": \"viscord\""));
        assertTrue(fabric.contains("\"environment\": \"server\""));
        assertTrue(fabric.contains("network.vonix.viscord.fabric.ViscordFabric"));
        assertTrue(fabric.contains("architectury"));
        assertTrue(neo121.contains("modId = \"viscord\""));
        assertTrue(neo121.contains("architectury"));
        assertTrue(neo261.contains("modId = \"viscord\""));
        assertFalse(neo261.contains("architectury"));
        assertFalse(Files.exists(root.resolve(
                "viscord-1.21.1-fabric-neoforge-template/fabric/src/main/java/network/vonix/viscord/fabric/client/ExampleModFabricClient.java")));
    }

    @Test
    void twentySixCompilesCoreContractsFromSiblingCore() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String build = Files.readString(root.resolve("viscord-1.26.1.2-neoforge-target/build.gradle"));
        assertTrue(build.contains("../core/src/main/java"),
                "26.1.2 must compile ChatPrefixFilter/DiscordFormatter from sibling core/");
        assertFalse(build.contains("../../core/src/main/java"),
                " ../../core resolves to candidates-r4k/core, which does not exist");
        assertFalse(Files.exists(root.resolve(
                "viscord-1.26.1.2-neoforge-target/src/main/java/network/vonix/viscord/utils/DiscordFormatter.java")),
                "26.1.2 must not vendor DiscordFormatter");
        assertTrue(Files.isRegularFile(root.resolve(
                "core/src/main/java/network/vonix/viscord/utils/DiscordFormatter.java")));
        assertTrue(Files.isRegularFile(root.resolve(
                "core/src/main/java/network/vonix/viscord/chat/ChatPrefixFilter.java")));
    }

    @Test
    void historicalTemplatesRemainDormant() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        assertTrue(Files.isDirectory(root.resolve("viscord-1.18.2-fabric-forge-template")));
        assertTrue(Files.isDirectory(root.resolve("viscord-1.19.2-fabric-forge-template")));
        assertTrue(Files.isDirectory(root.resolve("viscord-1.20.1-fabric-forge-template")));
        String fabric118 = Files.readString(root.resolve(
                "viscord-1.18.2-fabric-forge-template/fabric/src/main/resources/fabric.mod.json"));
        assertTrue(fabric118.contains("Me!"), "1.18.2 must stay untouched in this slice");
    }
}
