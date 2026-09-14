package network.vonix.viscord.discord;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkinRestorerAvatarResolverTest {

    @Test
    void extractsTextureHashFromBase64SkinProperty() {
        String skinRecord = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/abc123\"}}}";
        String encoded = Base64.getEncoder().encodeToString(skinRecord.getBytes(StandardCharsets.UTF_8));

        assertEquals("abc123", SkinRestorerAvatarResolver.extractTextureHash(encoded));
    }

    @Test
    void rejectsNonMinecraftTextureUrls() {
        String skinRecord = "{\"textures\":{\"SKIN\":{\"url\":\"https://example.invalid/texture/abc123\"}}}";
        String encoded = Base64.getEncoder().encodeToString(skinRecord.getBytes(StandardCharsets.UTF_8));

        assertNull(SkinRestorerAvatarResolver.extractTextureHash(encoded));
    }

    @Test
    void malformedPropertyValuesAreIgnored() {
        assertNull(SkinRestorerAvatarResolver.extractTextureHash("not-base64"));
    }

    @Test
    void missingSkinRestorerIsAQuietFallback() {
        assertNull(SkinRestorerAvatarResolver.resolve(UUID.randomUUID()));
    }
}
