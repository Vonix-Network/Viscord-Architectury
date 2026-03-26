package network.vonix.viscord.discord;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Properties 3 and 4: JSON payload correctness for WebhookClient and FluxerWebhookClient.
 * Uses test subclasses that override sendJson to capture the payload without making real HTTP calls.
 */
class WebhookClientPayloadTest {

    // ── Test subclass for WebhookClient ──────────────────────────────────────

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

    // ── Test subclass for FluxerWebhookClient ─────────────────────────────────

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

    // ── WebhookClient payload tests (Property 4) ─────────────────────────────

    @Test
    void webhookClient_avatarUrl_alwaysPresent_whenNonEmpty() {
        CapturingWebhookClient client = new CapturingWebhookClient();
        client.sendMessage("Alice", "https://example.com/avatar.png", "Hello");

        assertNotNull(client.captured, "sendJson should have been called");
        assertTrue(client.captured.has("avatar_url"), "avatar_url must be present when avatarUrl is non-empty");
        assertEquals("https://example.com/avatar.png", client.captured.get("avatar_url").getAsString());
        assertEquals("Alice", client.captured.get("username").getAsString());
        assertEquals("Hello", client.captured.get("content").getAsString());
    }

    @Test
    void webhookClient_avatarUrl_presentEvenWhenEmpty() {
        CapturingWebhookClient client = new CapturingWebhookClient();
        client.sendMessage("Bob", "", "Hi there");

        assertNotNull(client.captured, "sendJson should have been called");
        // WebhookClient always sets avatar_url (even empty string)
        assertTrue(client.captured.has("avatar_url"), "avatar_url must always be present in WebhookClient payload");
        assertEquals("Bob", client.captured.get("username").getAsString());
        assertEquals("Hi there", client.captured.get("content").getAsString());
    }

    // ── FluxerWebhookClient payload tests (Property 3) ───────────────────────

    @Test
    void fluxerWebhookClient_iconUrl_presentWhenAvatarUrlNonEmpty() {
        CapturingFluxerWebhookClient client = new CapturingFluxerWebhookClient();
        client.sendMessage("Charlie", "https://example.com/icon.png", "Hey");

        assertNotNull(client.captured, "sendJson should have been called");
        assertTrue(client.captured.has("icon_url"), "icon_url must be present when avatarUrl is non-empty");
        assertEquals("https://example.com/icon.png", client.captured.get("icon_url").getAsString());
        assertTrue(client.captured.has("username"));
        assertTrue(client.captured.has("text"));
    }

    @Test
    void fluxerWebhookClient_iconUrl_absentWhenAvatarUrlEmpty() {
        CapturingFluxerWebhookClient client = new CapturingFluxerWebhookClient();
        client.sendMessage("Dave", "", "Yo");

        assertNotNull(client.captured, "sendJson should have been called");
        assertFalse(client.captured.has("icon_url"), "icon_url must be absent when avatarUrl is empty");
        assertTrue(client.captured.has("username"));
        assertTrue(client.captured.has("text"));
    }
}
