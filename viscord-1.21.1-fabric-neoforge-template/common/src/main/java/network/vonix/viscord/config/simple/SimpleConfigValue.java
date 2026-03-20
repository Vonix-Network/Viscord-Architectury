package network.vonix.viscord.config.simple;

import java.util.function.Supplier;

public class SimpleConfigValue<T> implements Supplier<T> {
    private final String path;
    private final T defaultValue;
    private T value;

    public SimpleConfigValue(String path, T defaultValue) {
        this.path = path;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    @Override
    public T get() {
        return value;
    }

    public void set(Object value) {
        try {
            this.value = (T) value;
        } catch (ClassCastException e) {
            // Handle number conversions (e.g. Integer to Double)
            if (this.defaultValue instanceof Double && value instanceof Number) {
                this.value = (T) (Double) ((Number) value).doubleValue();
            } else if (this.defaultValue instanceof Float && value instanceof Number) {
                this.value = (T) (Float) ((Number) value).floatValue();
            } else if (this.defaultValue instanceof Long && value instanceof Number) {
                this.value = (T) (Long) ((Number) value).longValue();
            } else if (this.defaultValue instanceof Integer && value instanceof Number) {
                this.value = (T) (Integer) ((Number) value).intValue();
            } else {
                // Keep default if conversion fails
                System.err.println("Failed to convert config value for " + path + ": " + value + " to " + defaultValue.getClass().getSimpleName());
            }
        }
    }

    public String getPath() {
        return path;
    }

    public T getDefault() {
        return defaultValue;
    }
}
