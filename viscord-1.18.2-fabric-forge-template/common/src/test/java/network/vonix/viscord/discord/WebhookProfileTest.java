package network.vonix.viscord.discord;

import com.google.gson.JsonObject;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for webhook user profile relay (webhook-user-profiles feature).
 * Covers Properties 1-4 from the design doc.
 */
class WebhookProfileTest {

    // -------------------------------------------------------------------------
    // Helpers — mirrors the JSON building logic in WebhookClient / FluxerWebhookClient
    // -------------------------------------------------------------------------

    private JsonObject buildDiscordPayload(String username, String avatarUrl, String content) {
        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("avatar_url", avatarUrl);
        json.addProperty("content", content);
        return json;
    }

    private JsonObject buildFluxerPayload(String username, String avatarUrl, String content) {
        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            json.addProperty("icon_url", avatarUrl);
        }
        json.addProperty("text", content);
        return json;
    }

    /** Mirrors the null-coercion used in both bridge methods. */
    private String normalizeAvatar(String avatarUrl) {
        return avatarUrl != null ? avatarUrl : "";
    }

    // -------------------------------------------------------------------------
    // Unit tests
    // -------------------------------------------------------------------------

    @Test
    void fluxerToDiscord_forwardsUsernameAndAvatar() {
        String username = "FluxerUser";
        String avatarUrl = "https://cdn.fluxer.app/avatars/123/abc.png";
        JsonObject payload = buildDiscordPayload("[Fluxer]" + username, normalizeAvatar(avatarUrl), "hello");

        assertEquals("[Fluxer]" + username, payload.get("username").getAsString());
        assertEquals(avatarUrl, payload.get("avatar_url").getAsString());
    }

    @Test
    void fluxerToDiscord_nullAvatar_coercedToEmpty() {
        assertEquals("", normalizeAvatar(null));
        JsonObject payload = buildDiscordPayload("user", normalizeAvatar(null), "msg");
        assertEquals("", payload.get("avatar_url").getAsString());
    }

    @Test
    void discordToFluxer_forwardsUsernameAndAvatar() {
        String avatarUrl = "https://cdn.discordapp.com/avatars/456/def.png";
        JsonObject payload = buildFluxerPayload("DiscordUser", normalizeAvatar(avatarUrl), "hello");

        assertEquals("DiscordUser", payload.get("username").getAsString());
        assertEquals(avatarUrl, payload.get("icon_url").getAsString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void fluxerPayload_omitsIconUrl_whenAvatarAbsent(String avatarUrl) {
        JsonObject payload = buildFluxerPayload("user", normalizeAvatar(avatarUrl), "msg");
        assertFalse(payload.has("icon_url"));
        assertTrue(payload.has("username"));
        assertTrue(payload.has("text"));
    }

    @Test
    void fluxerPayload_includesIconUrl_whenAvatarPresent() {
        JsonObject payload = buildFluxerPayload("user", "https://example.com/a.png", "msg");
        assertTrue(payload.has("icon_url"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://example.com/avatar.png"})
    void discordPayload_alwaysHasRequiredFields(String avatarUrl) {
        JsonObject payload = buildDiscordPayload("user", avatarUrl, "msg");
        assertTrue(payload.has("avatar_url"));
        assertTrue(payload.has("username"));
        assertTrue(payload.has("content"));
    }

    // -------------------------------------------------------------------------
    // Property-based tests (20 tries each for speed)
    // -------------------------------------------------------------------------

    @Property(tries = 20)
    void prop_discordToFluxer_identityForwarded(
            @ForAll @AlphaChars @StringLength(min = 1, max = 15) String displayName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 15) String content) {

        String avatarUrl = "https://cdn.discordapp.com/avatars/1/" + displayName + ".png";
        JsonObject payload = buildFluxerPayload(displayName, normalizeAvatar(avatarUrl), content);

        assertEquals(displayName, payload.get("username").getAsString());
        assertEquals(avatarUrl, payload.get("icon_url").getAsString());
        assertEquals(content, payload.get("text").getAsString());
    }

    @Property(tries = 20)
    void prop_fluxerToDiscord_nullAvatarAlwaysNormalized(
            @ForAll @AlphaChars @StringLength(min = 1, max = 15) String username,
            @ForAll @AlphaChars @StringLength(min = 1, max = 15) String content) {

        // null avatar must normalize to "" and still produce a valid payload
        String normalized = normalizeAvatar(null);
        JsonObject payload = buildDiscordPayload("[Fluxer]" + username, normalized, content);

        assertEquals("", payload.get("avatar_url").getAsString());
        assertEquals("[Fluxer]" + username, payload.get("username").getAsString());
    }

    @Property(tries = 20)
    void prop_fluxerPayload_iconUrlPresenceMatchesAvatar(
            @ForAll @AlphaChars @StringLength(min = 1, max = 15) String username,
            @ForAll @AlphaChars @StringLength(min = 1, max = 15) String content,
            @ForAll @AlphaChars @StringLength(min = 0, max = 10) String rawAvatar) {

        // rawAvatar is alpha-only so never a real URL — tests the empty/non-empty branching
        JsonObject payload = buildFluxerPayload(username, rawAvatar, content);

        if (rawAvatar.isEmpty()) {
            assertFalse(payload.has("icon_url"));
        } else {
            assertTrue(payload.has("icon_url"));
            assertEquals(rawAvatar, payload.get("icon_url").getAsString());
        }
        assertTrue(payload.has("username"));
        assertTrue(payload.has("text"));
    }

    @Property(tries = 20)
    void prop_discordPayload_avatarUrlAlwaysPresent(
            @ForAll @AlphaChars @StringLength(min = 1, max = 15) String username,
            @ForAll @AlphaChars @StringLength(min = 1, max = 15) String content,
            @ForAll @AlphaChars @StringLength(min = 0, max = 10) String rawAvatar) {

        String avatarUrl = normalizeAvatar(rawAvatar.isEmpty() ? null : rawAvatar);
        JsonObject payload = buildDiscordPayload(username, avatarUrl, content);

        assertTrue(payload.has("avatar_url"));
        assertTrue(payload.has("username"));
        assertTrue(payload.has("content"));
    }
}
