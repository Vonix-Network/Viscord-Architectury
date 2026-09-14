package network.vonix.viscord.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkedAccountsManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void generateVerifyUnlinkHappyPath() {
        LinkedAccountsManager manager = new LinkedAccountsManager(tempDir);
        UUID uuid = UUID.fromString("11111111-1111-1111-1111-111111111111");

        String code = manager.generateLinkCode(uuid, "Steve");
        assertNotNull(code);
        assertTrue(code.matches("\\d{6}"));
        assertFalse(manager.isLinked(uuid));

        LinkedAccountsManager.LinkResult result = manager.verifyAndLink(code, "123456789012345678", "DiscordSteve");
        assertTrue(result.success, result.message);
        assertTrue(manager.isLinked(uuid));
        LinkedAccountsManager.LinkedAccount linked = manager.getByMinecraft(uuid);
        assertNotNull(linked);
        assertEquals("DiscordSteve", linked.discordUsername);
        assertEquals("123456789012345678", linked.discordId);
        assertEquals("Steve", linked.minecraftUsername);

        assertTrue(manager.unlinkMinecraft(uuid));
        assertFalse(manager.isLinked(uuid));
        assertFalse(manager.unlinkMinecraft(uuid));
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        LinkedAccountsManager manager = new LinkedAccountsManager(tempDir);
        UUID uuid = UUID.fromString("22222222-2222-2222-2222-222222222222");

        String code = manager.generateLinkCode(uuid, "Alex");
        expirePendingCode(manager, code);

        LinkedAccountsManager.LinkResult result = manager.verifyAndLink(code, "111", "ExpiredUser");
        assertFalse(result.success);
        assertTrue(result.message.toLowerCase().contains("invalid") || result.message.toLowerCase().contains("expired"),
                result.message);
        assertFalse(manager.isLinked(uuid));
    }

    @Test
    void alreadyMinecraftLinkedIsRejected() {
        LinkedAccountsManager manager = new LinkedAccountsManager(tempDir);
        UUID uuid = UUID.fromString("33333333-3333-3333-3333-333333333333");

        String firstCode = manager.generateLinkCode(uuid, "Steve");
        assertTrue(manager.verifyAndLink(firstCode, "discord-a", "UserA").success);

        String secondCode = manager.generateLinkCode(uuid, "Steve");
        LinkedAccountsManager.LinkResult result = manager.verifyAndLink(secondCode, "discord-b", "UserB");
        assertFalse(result.success);
        assertTrue(result.message.contains("already linked"), result.message);
        assertEquals("UserA", manager.getByMinecraft(uuid).discordUsername);
        assertEquals(1, manager.getLinkedCount());
    }

    @Test
    void sameDiscordAlreadyLinkedIsRejected() {
        LinkedAccountsManager manager = new LinkedAccountsManager(tempDir);
        UUID steve = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID alex = UUID.fromString("55555555-5555-5555-5555-555555555555");

        String steveCode = manager.generateLinkCode(steve, "Steve");
        assertTrue(manager.verifyAndLink(steveCode, "shared-discord", "SharedUser").success);

        String alexCode = manager.generateLinkCode(alex, "Alex");
        LinkedAccountsManager.LinkResult result = manager.verifyAndLink(alexCode, "shared-discord", "SharedUser");
        assertFalse(result.success);
        assertTrue(result.message.contains("already linked"), result.message);
        assertTrue(manager.isLinked(steve));
        assertFalse(manager.isLinked(alex));
        assertEquals(1, manager.getLinkedCount());
    }

    @Test
    void malformedAndNonexistentCodesAreRejected() {
        LinkedAccountsManager manager = new LinkedAccountsManager(tempDir);
        UUID uuid = UUID.fromString("66666666-6666-6666-6666-666666666666");
        String realCode = manager.generateLinkCode(uuid, "Steve");

        assertRejected(manager.verifyAndLink("abcdef", "d1", "User"));
        assertRejected(manager.verifyAndLink("12345", "d1", "User"));
        assertRejected(manager.verifyAndLink("1234567", "d1", "User"));
        assertRejected(manager.verifyAndLink("", "d1", "User"));
        String nonexistent = "000000".equals(realCode) ? "000001" : "000000";
        assertRejected(manager.verifyAndLink(nonexistent, "d1", "User"));
        assertFalse(manager.isLinked(uuid));
    }

    private static void assertRejected(LinkedAccountsManager.LinkResult result) {
        assertFalse(result.success, result.message);
        assertTrue(result.message.toLowerCase().contains("invalid") || result.message.toLowerCase().contains("expired"),
                result.message);
    }

    @SuppressWarnings("unchecked")
    private static void expirePendingCode(LinkedAccountsManager manager, String code) throws Exception {
        Field pendingField = LinkedAccountsManager.class.getDeclaredField("pendingLinks");
        pendingField.setAccessible(true);
        Map<String, Object> pendingLinks = (Map<String, Object>) pendingField.get(manager);
        Object existing = pendingLinks.get(code);
        assertNotNull(existing, "pending code should exist before expiry");

        Class<?> pendingType = existing.getClass();
        Field uuidField = pendingType.getDeclaredField("minecraftUUID");
        Field nameField = pendingType.getDeclaredField("minecraftUsername");
        uuidField.setAccessible(true);
        nameField.setAccessible(true);

        Constructor<?> ctor = pendingType.getDeclaredConstructor(UUID.class, String.class, long.class);
        ctor.setAccessible(true);
        Object expired = ctor.newInstance(
                uuidField.get(existing),
                nameField.get(existing),
                System.currentTimeMillis() - 1L);
        pendingLinks.put(code, expired);
    }
}
