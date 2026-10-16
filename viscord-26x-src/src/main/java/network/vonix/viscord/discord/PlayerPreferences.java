package network.vonix.viscord.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.toml.ViscordConfigToml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player preferences for Discord features.
 */
public class PlayerPreferences {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path preferencesFile;
    private final Map<UUID, PlayerPreference> preferences;

    public PlayerPreferences(Path configDir) throws IOException {
        this.preferencesFile = configDir.resolve("viscord-preferences.json");
        this.preferences = new ConcurrentHashMap<>();
        loadPreferences();
    }

    /**
     * Check if a player has server system message filtering enabled.
     * Returns false by default (show all system messages).
     */
    public boolean hasServerSystemMessagesFiltered(UUID playerUuid) {
        PlayerPreference pref = preferences.get(playerUuid);
        if (pref != null) {
            return pref.filterServerSystemMessages;
        }
        return false;
    }

    /**
     * Set whether a player wants to filter server system messages
     * (startup, shutdown, player list embeds).
     */
    public void setServerSystemMessagesFiltered(UUID playerUuid, boolean filtered) {
        PlayerPreference pref = preferences.computeIfAbsent(playerUuid, k -> new PlayerPreference());
        pref.filterServerSystemMessages = filtered;
        savePreferences();
    }

    /**
     * Check if a player has server message filtering enabled.
     * Returns false by default (show all messages).
     */
    public boolean hasServerMessagesFiltered(UUID playerUuid) {
        PlayerPreference pref = preferences.get(playerUuid);
        if (pref != null) {
            return pref.filterServerMessages;
        }
        return false;
    }

    /**
     * Set whether a player wants to filter server messages.
     */
    public void setServerMessagesFiltered(UUID playerUuid, boolean filtered) {
        PlayerPreference pref = preferences.computeIfAbsent(playerUuid, k -> new PlayerPreference());
        pref.filterServerMessages = filtered;
        savePreferences();
    }

    /**
     * Check if a player has event message filtering enabled.
     * Returns false by default (show all events).
     */
    public boolean hasEventsFiltered(UUID playerUuid) {
        PlayerPreference pref = preferences.get(playerUuid);
        if (pref != null) {
            return pref.filterEvents;
        }
        return false;
    }

    /**
     * Set whether a player wants to filter event messages (achievements,
     * join/leave).
     */
    public void setEventsFiltered(UUID playerUuid, boolean filtered) {
        PlayerPreference pref = preferences.computeIfAbsent(playerUuid, k -> new PlayerPreference());
        pref.filterEvents = filtered;
        savePreferences();
    }

    /**
     * Load preferences from file.
     */
    private void loadPreferences() throws IOException {
        if (!Files.exists(preferencesFile)) {
            Viscord.LOGGER.info("Discord player preferences file not found, creating new one");
            savePreferences();
            return;
        }

        try {
            String json = Files.readString(preferencesFile);
            JsonObject root = GSON.fromJson(json, JsonObject.class);

            if (root != null && root.has("players")) {
                JsonObject playersObj = root.getAsJsonObject("players");
                for (String uuidStr : playersObj.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        JsonObject prefObj = playersObj.getAsJsonObject(uuidStr);
                        PlayerPreference pref = new PlayerPreference();

                        if (prefObj.has("filterServerSystemMessages")) {
                            pref.filterServerSystemMessages = prefObj.get("filterServerSystemMessages").getAsBoolean();
                        }
                        if (prefObj.has("filterServerMessages")) {
                            pref.filterServerMessages = prefObj.get("filterServerMessages").getAsBoolean();
                        }
                        if (prefObj.has("filterEvents")) {
                            pref.filterEvents = prefObj.get("filterEvents").getAsBoolean();
                        }

                        preferences.put(uuid, pref);
                    } catch (IllegalArgumentException e) {
                        Viscord.LOGGER.warn("Invalid UUID in Discord preferences file: {}", uuidStr);
                    }
                }
            }

            Viscord.LOGGER.info("Loaded Discord preferences for {} players", preferences.size());
        } catch (Exception e) {
            Viscord.LOGGER.error("Failed to load Discord player preferences", e);
        }
    }

    /**
     * Save preferences to file.
     */
    private void savePreferences() {
        JsonObject root = new JsonObject();
        JsonObject playersObj = new JsonObject();
        for (Map.Entry<UUID, PlayerPreference> entry : preferences.entrySet()) {
            JsonObject prefObj = new JsonObject();
            prefObj.addProperty("filterServerSystemMessages", entry.getValue().filterServerSystemMessages);
            prefObj.addProperty("filterServerMessages", entry.getValue().filterServerMessages);
            prefObj.addProperty("filterEvents", entry.getValue().filterEvents);
            playersObj.add(entry.getKey().toString(), prefObj);
        }
        root.add("players", playersObj);
        String json = GSON.toJson(root);
        Viscord.ASYNC_EXECUTOR.submit(() -> {
            try {
                Files.writeString(preferencesFile, json);
                if (ViscordConfigToml.General.DEBUG.get())
                    Viscord.LOGGER.debug("Saved Discord player preferences to file");
            } catch (IOException e) {
                Viscord.LOGGER.error("Failed to save Discord player preferences", e);
            }
        });
    }

    /**
     * Internal class to hold player preferences.
     */
    private static class PlayerPreference {
        boolean filterServerSystemMessages = false;
        boolean filterServerMessages = false;
        boolean filterEvents = false;
    }
}
