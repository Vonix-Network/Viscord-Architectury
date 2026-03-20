package network.vonix.viscord.config.simple;

import org.apache.commons.lang3.tuple.Pair;
import java.util.*;
import java.util.function.Function;

public class SimpleConfigBuilder {
    private final Map<String, Object> defaults = new HashMap<>();
    private final Map<String, String> comments = new HashMap<>();
    private final List<SimpleConfigValue<?>> values = new ArrayList<>();
    private final Stack<String> pathStack = new Stack<>();
    private String currentComment = null;

    public SimpleConfigBuilder comment(String... comment) {
        this.currentComment = String.join("\n", comment);
        return this;
    }

    public SimpleConfigBuilder push(String path) {
        pathStack.push(path);
        return this;
    }

    public SimpleConfigBuilder pop() {
        if (!pathStack.isEmpty()) pathStack.pop();
        return this;
    }

    private String getCurrentPath(String key) {
        if (pathStack.isEmpty()) return key;
        return String.join(".", pathStack) + "." + key;
    }

    public <T> SimpleConfigValue<T> define(String key, T defaultValue) {
        String fullPath = getCurrentPath(key);
        defaults.put(fullPath, defaultValue);
        if (currentComment != null) {
            comments.put(fullPath, currentComment);
            currentComment = null;
        }
        SimpleConfigValue<T> val = new SimpleConfigValue<>(fullPath, defaultValue);
        values.add(val);
        return val;
    }

    public <T> SimpleConfigValue<T> defineInRange(String key, T defaultValue, T min, T max) {
        // Range validation ignored for simplicity
        return define(key, defaultValue);
    }

    public SimpleConfigSpec build() {
        return new SimpleConfigSpec(defaults, comments, values);
    }

    public <T> Pair<T, SimpleConfigSpec> configure(Function<SimpleConfigBuilder, T> consumer) {
        T config = consumer.apply(this);
        return Pair.of(config, build());
    }
}
