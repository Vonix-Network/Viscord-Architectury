package network.vonix.viscord.config.simple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import network.vonix.viscord.Viscord;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SimpleConfigManager {
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void load(Path path, SimpleConfigSpec spec) {
        File file = path.toFile();
        Map<String, Object> loaded = new HashMap<>();
        
        Viscord.LOGGER.info("[Config] Loading config from: {}", file.getAbsolutePath());
        
        if (file.exists()) {
            try {
                Map<String, Object> json = mapper.readValue(file, Map.class);
                flatten(json, "", loaded);
                Viscord.LOGGER.info("[Config] Loaded {} values from existing config", loaded.size());
            } catch (Exception e) {
                Viscord.LOGGER.error("[Config] Failed to load config {}: {}", file.getName(), e.getMessage());
            }
        } else {
            Viscord.LOGGER.info("[Config] Config file does not exist, will create new one");
        }
        
        // Update values
        int loadedCount = 0;
        for (SimpleConfigValue<?> val : spec.getValues()) {
            if (loaded.containsKey(val.getPath())) {
                val.set(loaded.get(val.getPath()));
                loadedCount++;
            }
        }
        
        Viscord.LOGGER.info("[Config] Applied {} values from config file", loadedCount);
        
        // Save to ensure new keys are written (and structure is correct)
        save(path, spec);
    }

    public static void save(Path path, SimpleConfigSpec spec) {
        Viscord.LOGGER.info("[Config] Starting save to: {}", path.toAbsolutePath());
        Viscord.LOGGER.info("[Config] Spec has {} values to save", spec.getValues().size());
        
        if (spec.getValues().isEmpty()) {
            Viscord.LOGGER.error("[Config] No values to save - config spec is empty!");
            return;
        }
        
        Map<String, Object> root = new HashMap<>();
        for (SimpleConfigValue<?> val : spec.getValues()) {
            Viscord.LOGGER.debug("[Config] Saving value: {} = {}", val.getPath(), val.get());
            putNested(root, val.getPath(), val.get());
        }
        
        Viscord.LOGGER.info("[Config] Built config map with {} top-level keys", root.size());
        
        File file = path.toFile();
        
        try {
            // Ensure parent directories exist
            File parent = file.getParentFile();
            Viscord.LOGGER.info("[Config] Parent directory: {}", parent != null ? parent.getAbsolutePath() : "null");
            
            if (parent != null && !parent.exists()) {
                Viscord.LOGGER.info("[Config] Parent directory does not exist, creating...");
                if (!parent.mkdirs()) {
                    Viscord.LOGGER.warn("[Config] Could not create parent directories (may already exist): {}", parent.getAbsolutePath());
                } else {
                    Viscord.LOGGER.info("[Config] Created parent directories: {}", parent.getAbsolutePath());
                }
            } else if (parent != null) {
                Viscord.LOGGER.info("[Config] Parent directory already exists");
            }
            
            Viscord.LOGGER.info("[Config] About to write JSON file...");
            mapper.writeValue(file, root);
            Viscord.LOGGER.info("[Config] Successfully saved config to: {}", file.getAbsolutePath());
        } catch (IOException e) {
            Viscord.LOGGER.error("[Config] Failed to save config to {}: {}", file.getAbsolutePath(), e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void flatten(Map<String, Object> source, String prefix, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map) {
                flatten((Map<String, Object>) entry.getValue(), key, target);
            } else {
                target.put(key, entry.getValue());
            }
        }
    }
    
    private static void putNested(Map<String, Object> target, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!current.containsKey(part) || !(current.get(part) instanceof Map)) {
                current.put(part, new HashMap<>());
            }
            current = (Map<String, Object>) current.get(part);
        }
        current.put(parts[parts.length - 1], value);
    }
}
