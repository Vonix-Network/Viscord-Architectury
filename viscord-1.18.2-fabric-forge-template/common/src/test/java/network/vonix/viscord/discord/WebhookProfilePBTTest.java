package network.vonix.viscord.discord;

import com.google.gson.JsonObject;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for tasks 4.4–4.7 using jqwik.
 *
 * Property 1 (task 4.4): FluxerWebhookClient always sets username and text
 * Property 2 (task 4.5): WebhookClient always sets username and content; avatar_url always present
 * Property 3 (task 4.6): icon_url in FluxerWebhookClient iff avatarUrl non-empty
 * Property 4 (task 4.7): avatar_url in WebhookClient always present
 */
class WebhookProfilePBTTest {

    // ── Capturing test subclasses ─────────────────────────────────────────────

    static class CapturingWebhookClient extends WebhookClient {
        JsonObject captured;

        CapturingWebhookClient() {
            super("https://discord.com/api/webhooks/123/token");
        }

        @Override
        protected void sendJson(JsonObject json) {
            this.captured = json;
        }
    }

    static class CapturingFluxerWebhookClient extends FluxerWebhookClient {
        JsonObject captured;

        CapturingFluxerWebhookClient() {
            super("https://api.fluxer.app/v1/webhooks/123/abc-token");
        }

        @Override
        protected void sendJson(JsonObject json) {
            this.captured = json;
        }
    }

    // ── Property 1 (task 4.4) ─────────────────────────────────────────────────
    // For any non-null username and content, FluxerWebhookClient always sets username and text.
    // Validates: Requirements 1.1, 1.2, 1.3

    /**
     * **Validates: Requirements 1.1, 1.2, 1.3**
     */
    @Property
    void property1_fluxer_alwaysSetsUsernameAndText(
            @ForAll @NotEmpty String username,
            @ForAll String avatarUrl,
            @ForAll @NotEmpty String content) {

        CapturingFluxerWebhookClient client = new CapturingFluxerWebhookClient();
        client.sendMessage(username, avatarUrl, content);

        assertNotNull(client.captured, "sendJson must be called for non-empty content");
        assertTrue(client.captured.has("username"), "username must always be present");
        assertTrue(client.captured.has("text"), "text must always be present");
        assertEquals(username, client.captured.get("username").getAsString());
        assertEquals(content, client.captured.get("text").getAsString());
    }

    // ── Property 2 (task 4.5) ─────────────────────────────────────────────────
    // For any username and avatarUrl, WebhookClient always sets username and content;
    // avatar_url is always present (WebhookClient always sets it).
    // Validates: Requirements 2.1, 2.2, 2.3, 2.4

    /**
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
     */
    @Property
    void property2_webhook_alwaysSetsUsernameAndContent(
            @ForAll @NotEmpty String username,
            @ForAll String avatarUrl,
            @ForAll @NotEmpty String content) {

        CapturingWebhookClient client = new CapturingWebhookClient();
        client.sendMessage(username, avatarUrl, content);

        assertNotNull(client.captured, "sendJson must be called");
        assertTrue(client.captured.has("username"), "username must always be present");
        assertTrue(client.captured.has("content"), "content must always be present");
        assertTrue(client.captured.has("avatar_url"), "avatar_url must always be present in WebhookClient payload");
        assertEquals(username, client.captured.get("username").getAsString());
        assertEquals(content, client.captured.get("content").getAsString());
    }

    // ── Property 3 (task 4.6) ─────────────────────────────────────────────────
    // icon_url in FluxerWebhookClient payload is present iff avatarUrl is non-empty.
    // Validates: Requirements 1.4, 3.3

    /**
     * **Validates: Requirements 1.4, 3.3**
     */
    @Property
    void property3_fluxer_iconUrl_presentIffAvatarNonEmpty(
            @ForAll @NotEmpty String username,
            @ForAll String avatarUrl,
            @ForAll @NotEmpty String content) {

        CapturingFluxerWebhookClient client = new CapturingFluxerWebhookClient();
        client.sendMessage(username, avatarUrl, content);

        assertNotNull(client.captured);
        boolean avatarNonEmpty = avatarUrl != null && !avatarUrl.isEmpty();
        if (avatarNonEmpty) {
            assertTrue(client.captured.has("icon_url"),
                    "icon_url must be present when avatarUrl is non-empty");
            assertEquals(avatarUrl, client.captured.get("icon_url").getAsString());
        } else {
            assertFalse(client.captured.has("icon_url"),
                    "icon_url must be absent when avatarUrl is empty or null");
        }
    }

    // ── Property 4 (task 4.7) ─────────────────────────────────────────────────
    // avatar_url in WebhookClient payload is always present (it always sets it regardless).
    // Validates: Requirements 2.4, 3.2

    /**
     * **Validates: Requirements 2.4, 3.2**
     */
    @Property
    void property4_webhook_avatarUrl_alwaysPresent(
            @ForAll @NotEmpty String username,
            @ForAll String avatarUrl,
            @ForAll @NotEmpty String content) {

        CapturingWebhookClient client = new CapturingWebhookClient();
        client.sendMessage(username, avatarUrl, content);

        assertNotNull(client.captured);
        assertTrue(client.captured.has("avatar_url"),
                "avatar_url must always be present in WebhookClient payload regardless of value");
    }
}
