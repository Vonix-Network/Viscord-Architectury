package network.vonix.viscord.config.simple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SimpleConfigManager {
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void load(Path path, SimpleConfigSpec spec) {
        File file = path.toFile();
        Map<String, Object> loaded = new HashMap<>();
        
        if (file.exists()) {
            try {
                Map<String, Object> json = mapper.readValue(file, Map.class);
                flatten(json, "", loaded);
            } catch (Exception e) {
                System.err.println("Failed to load config " + file.getName() + ": " + e.getMessage());
            }
        }
        
        // Update values
        for (SimpleConfigValue<?> val : spec.getValues()) {
            if (loaded.containsKey(val.getPath())) {
                val.set(loaded.get(val.getPath()));
            }
        }
        
        // Save to ensure new keys are written (and structure is correct)
        save(path, spec);
    }

    private static void save(Path path, SimpleConfigSpec spec) {
        Map<String, Object> root = new HashMap<>();
        for (SimpleConfigValue<?> val : spec.getValues()) {
            putNested(root, val.getPath(), val.get());
        }
        
        try {
            File parent = path.getParent().toFile();
            if (!parent.exists()) parent.mkdirs();
            mapper.writeValue(path.toFile(), root);
        } catch (Exception e) {
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
