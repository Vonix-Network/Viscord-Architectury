package network.vonix.viscord.config.simple;

import java.util.List;
import java.util.Map;

public class SimpleConfigSpec {
    private final Map<String, Object> defaults;
    private final Map<String, String> comments;
    private final List<SimpleConfigValue<?>> values;

    public SimpleConfigSpec(Map<String, Object> defaults, Map<String, String> comments, List<SimpleConfigValue<?>> values) {
        this.defaults = defaults;
        this.comments = comments;
        this.values = values;
    }

    public Map<String, Object> getDefaults() { return defaults; }
    public Map<String, String> getComments() { return comments; }
    public List<SimpleConfigValue<?>> getValues() { return values; }
}
