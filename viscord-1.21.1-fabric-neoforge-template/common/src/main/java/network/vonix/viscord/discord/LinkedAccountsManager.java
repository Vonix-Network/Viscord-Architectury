package network.vonix.viscord.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.ViscordConfig;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages linked accounts between Minecraft and Discord.
 * Stores UUID -> Discord ID mappings and handles link code
 * generation/validation.
 */
public class LinkedAccountsManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String LINKED_ACCOUNTS_FILE = "viscord-links.json";

    // UUID -> LinkedAccount
    private final Map<UUID, LinkedAccount> linkedAccounts = new ConcurrentHashMap<>();

    // Pending link codes: code -> PendingLink
    private final Map<String, PendingLink> pendingLinks = new ConcurrentHashMap<>();

    private final Path dataFile;

    public LinkedAccountsManager(Path configDir) {
        this.dataFile = configDir.resolve(LINKED_ACCOUNTS_FILE);
        load();
    }

    /**
     * Generate a 6-digit link code for a player
     */
    public String generateLinkCode(UUID minecraftUUID, String minecraftUsername) {
        // Clean up expired pending links
        cleanupExpiredCodes();

        // Generate unique 6-digit code
        String code;
        do {
            code = String.format("%06d", new Random().nextInt(1000000));
        } while (pendingLinks.containsKey(code));

        // Store pending link
        long expiryTime = System.currentTimeMillis() + (ViscordConfig.CONFIG.linkCodeExpiry.get() * 1000L);
        pendingLinks.put(code, new PendingLink(minecraftUUID, minecraftUsername, expiryTime));

        Viscord.LOGGER.info("Generated link code {} for player {} ({})", code, minecraftUsername, minecraftUUID);
        return code;
    }

    /**
     * Verify and complete a link using a code
     */
    public LinkResult verifyAndLink(String code, String discordId, String discordUsername) {
        cleanupExpiredCodes();

        PendingLink pending = pendingLinks.get(code);
        if (pending == null) {
            return new LinkResult(false, "Invalid or expired link code!");
        }

        // Check if Minecraft account is already linked
        LinkedAccount existing = linkedAccounts.get(pending.minecraftUUID);
        if (existing != null) {
            pendingLinks.remove(code);
            return new LinkResult(false,
                    "This Minecraft account is already linked to Discord user " + existing.discordUsername);
        }

        // Check if Discord account is already linked to another Minecraft account
        for (LinkedAccount account : linkedAccounts.values()) {
            if (account.discordId.equals(discordId)) {
                pendingLinks.remove(code);
                return new LinkResult(false,
                        "This Discord account is already linked to Minecraft player " + account.minecraftUsername);
            }
        }

        // Create link
        LinkedAccount link = new LinkedAccount(
                pending.minecraftUUID,
                pending.minecraftUsername,
                discordId,
                discordUsername,
                System.currentTimeMillis());

        linkedAccounts.put(pending.minecraftUUID, link);
        pendingLinks.remove(code);
        save();

        Viscord.LOGGER.info("Successfully linked {} ({}) to Discord user {} ({})",
                pending.minecraftUsername, pending.minecraftUUID, discordUsername, discordId);

        return new LinkResult(true,
                "Successfully linked " + pending.minecraftUsername + " to Discord user " + discordUsername);
    }

    /**
     * Unlink a Minecraft account
     */
    public boolean unlinkMinecraft(UUID minecraftUUID) {
        LinkedAccount removed = linkedAccounts.remove(minecraftUUID);
        if (removed != null) {
            save();
            Viscord.LOGGER.info("Unlinked {} ({}) from Discord user {} ({})",
                    removed.minecraftUsername, removed.minecraftUUID, removed.discordUsername, removed.discordId);
            return true;
        }
        return false;
    }

    /**
     * Unlink a Discord account
     */
    public boolean unlinkDiscord(String discordId) {
        UUID toRemove = null;
        for (Map.Entry<UUID, LinkedAccount> entry : linkedAccounts.entrySet()) {
            if (entry.getValue().discordId.equals(discordId)) {
                toRemove = entry.getKey();
                break;
            }
        }

        if (toRemove != null) {
            linkedAccounts.remove(toRemove);
            save();
            Viscord.LOGGER.info("Unlinked Discord user {} from Minecraft account", discordId);
            return true;
        }
        return false;
    }

    /**
     * Get linked account by Minecraft UUID
     */
    public LinkedAccount getByMinecraft(UUID minecraftUUID) {
        return linkedAccounts.get(minecraftUUID);
    }

    /**
     * Get linked account by Discord ID
     */
    public LinkedAccount getByDiscord(String discordId) {
        return linkedAccounts.values().stream()
                .filter(account -> account.discordId.equals(discordId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if a Minecraft account is linked
     */
    public boolean isLinked(UUID minecraftUUID) {
        return linkedAccounts.containsKey(minecraftUUID);
    }

    /**
     * Get total number of linked accounts
     */
    public int getLinkedCount() {
        return linkedAccounts.size();
    }

    /**
     * Clean up expired link codes
     */
    private void cleanupExpiredCodes() {
        long now = System.currentTimeMillis();
        pendingLinks.entrySet().removeIf(entry -> entry.getValue().expiryTime < now);
    }

    /**
     * Save linked accounts to file
     */
    private void save() {
        try {
            Files.createDirectories(dataFile.getParent());

            try (Writer writer = new FileWriter(dataFile.toFile())) {
                Type type = new TypeToken<Map<UUID, LinkedAccount>>() {
                }.getType();
                GSON.toJson(linkedAccounts, type, writer);
            }

            if (ViscordConfig.CONFIG.debugLogging.get()) {
                Viscord.LOGGER.debug("Saved {} linked accounts to {}", linkedAccounts.size(), dataFile);
            }
        } catch (IOException e) {
            Viscord.LOGGER.error("Failed to save linked accounts!", e);
        }
    }

    /**
     * Load linked accounts from file
     */
    private void load() {
        if (!Files.exists(dataFile)) {
            Viscord.LOGGER.info("No Discord linked accounts file found, starting fresh");
            return;
        }

        try (Reader reader = new FileReader(dataFile.toFile())) {
            Type type = new TypeToken<Map<UUID, LinkedAccount>>() {
            }.getType();
            Map<UUID, LinkedAccount> loaded = GSON.fromJson(reader, type);

            if (loaded != null) {
                linkedAccounts.putAll(loaded);
                Viscord.LOGGER.info("Loaded {} linked accounts from {}", linkedAccounts.size(), dataFile);
            }
        } catch (IOException e) {
            Viscord.LOGGER.error("Failed to load linked accounts!", e);
        }
    }

    // Data classes

    public static class LinkedAccount {
        public final UUID minecraftUUID;
        public final String minecraftUsername;
        public final String discordId;
        public final String discordUsername;
        public final long linkedTimestamp;

        public LinkedAccount(UUID minecraftUUID, String minecraftUsername, String discordId, String discordUsername,
                long linkedTimestamp) {
            this.minecraftUUID = minecraftUUID;
            this.minecraftUsername = minecraftUsername;
            this.discordId = discordId;
            this.discordUsername = discordUsername;
            this.linkedTimestamp = linkedTimestamp;
        }
    }

    private static class PendingLink {
        public final UUID minecraftUUID;
        public final String minecraftUsername;
        public final long expiryTime;

        public PendingLink(UUID minecraftUUID, String minecraftUsername, long expiryTime) {
            this.minecraftUUID = minecraftUUID;
            this.minecraftUsername = minecraftUsername;
            this.expiryTime = expiryTime;
        }
    }

    public static class LinkResult {
        public final boolean success;
        public final String message;

        public LinkResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
