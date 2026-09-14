package network.vonix.viscord.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.vonix.viscord.Viscord;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves SkinRestorer's saved texture into a rendered avatar URL without a
 * compile-time or metadata dependency on SkinRestorer.
 *
 * SkinRestorer is intentionally accessed only through reflection. This keeps
 * every Viscord lane safe when the optional mod is absent, while allowing the
 * compatible runtime lane to use the saved custom skin on cracked servers.
 */
public final class SkinRestorerAvatarResolver {
    private static final String SKIN_RESTORER_CLASS_NAME = "net.lionarius.skinrestorer.SkinRestorer";
    private static final Pattern TEXTURE_URL = Pattern.compile(
            "https?://textures\\.minecraft\\.net/texture/([A-Za-z0-9_-]+)");

    private SkinRestorerAvatarResolver() {
    }

    /**
     * Returns a rendered mc-heads avatar URL for a saved SkinRestorer skin, or
     * {@code null} when SkinRestorer is absent, the player has no saved skin,
     * or the saved value cannot be decoded safely.
     */
    public static String resolve(String uuidNoDashes) {
        if (uuidNoDashes == null || uuidNoDashes.isEmpty()) {
            return null;
        }

        try {
            String normalized = uuidNoDashes.length() == 32
                    ? uuidNoDashes.substring(0, 8) + "-"
                        + uuidNoDashes.substring(8, 12) + "-"
                        + uuidNoDashes.substring(12, 16) + "-"
                        + uuidNoDashes.substring(16, 20) + "-"
                        + uuidNoDashes.substring(20)
                    : uuidNoDashes;
            return resolve(UUID.fromString(normalized));
        } catch (IllegalArgumentException malformedUuid) {
            return null;
        }
    }

    public static String resolve(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }

        try {
            // Mandatory soft-dependency guard: do not resolve or reference any
            // SkinRestorer type until the optional mod class is present.
            Class<?> skinRestorerClass = Class.forName(SKIN_RESTORER_CLASS_NAME);
            Object storage = skinRestorerClass.getMethod("getSkinStorage").invoke(null);
            if (storage == null) {
                return null;
            }

            Method hasSavedSkin = storage.getClass().getMethod("hasSavedSkin", UUID.class);
            if (!Boolean.TRUE.equals(hasSavedSkin.invoke(storage, playerUuid))) {
                return null;
            }

            Object skinValue = storage.getClass().getMethod("getSkin", UUID.class).invoke(storage, playerUuid);
            String textureHash = extractTextureHashFromSkinValue(skinValue);
            return textureHash == null
                    ? null
                    : "https://mc-heads.net/avatar/" + textureHash + "/100.png";
        } catch (ClassNotFoundException absent) {
            // SkinRestorer is optional. Its absence must be indistinguishable
            // from a player without a saved custom skin.
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            // A changed/incompatible optional API must never break a join or
            // avatar generation; keep the existing fallback path available.
            Viscord.LOGGER.debug("[Viscord] SkinRestorer avatar lookup skipped: {}", failure.toString());
            return null;
        }
    }

    static String extractTextureHashFromSkinValue(Object skinValue) {
        if (skinValue == null) {
            return null;
        }

        try {
            String textureHash = extractTextureHashFromProperty(
                    skinValue.getClass().getMethod("value").invoke(skinValue));
            if (textureHash != null) {
                return textureHash;
            }
            return extractTextureHashFromProperty(
                    skinValue.getClass().getMethod("originalValue").invoke(skinValue));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    private static String extractTextureHashFromProperty(Object property) {
        if (property == null) {
            return null;
        }

        try {
            Object encodedValue = property.getClass().getMethod("value").invoke(property);
            return encodedValue instanceof String
                    ? extractTextureHash((String) encodedValue)
                    : null;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    /**
     * Decodes a Mojang textures Property value and extracts the texture hash
     * from textures.SKIN.url. Package-private for focused resolver tests.
     */
    static String extractTextureHash(String encodedPropertyValue) {
        if (encodedPropertyValue == null || encodedPropertyValue.isEmpty()) {
            return null;
        }

        try {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(encodedPropertyValue);
            } catch (IllegalArgumentException standardEncodingFailure) {
                decoded = Base64.getUrlDecoder().decode(encodedPropertyValue);
            }

            JsonElement root = JsonParser.parseString(new String(decoded, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return null;
            }

            JsonObject rootObject = root.getAsJsonObject();
            JsonObject textures = rootObject.has("textures") && rootObject.get("textures").isJsonObject()
                    ? rootObject.getAsJsonObject("textures")
                    : null;
            if (textures == null || !textures.has("SKIN") || !textures.get("SKIN").isJsonObject()) {
                return null;
            }

            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (!skin.has("url") || !skin.get("url").isJsonPrimitive()) {
                return null;
            }

            Matcher matcher = TEXTURE_URL.matcher(skin.get("url").getAsString());
            return matcher.matches() ? matcher.group(1) : null;
        } catch (RuntimeException failure) {
            return null;
        }
    }
}
