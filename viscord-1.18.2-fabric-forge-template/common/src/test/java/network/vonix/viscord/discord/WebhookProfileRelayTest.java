package network.vonix.viscord.discord;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for webhook payload relay correctness.
 */
class WebhookProfileRelayTest {

    static class CapturingWebhookClient extends WebhookClient {
        JsonObject captured;
        int callCount = 0;

        CapturingWebhookClient(String url) {
            super(url);
        }

        @Override
        protected void sendJson(JsonObject json) {
            this.captured = json;
            this.callCount++;
        }
    }

    static class CapturingFluxerWebhookClient extends FluxerWebhookClient {
        JsonObject captured;
        int callCount = 0;

        CapturingFluxerWebhookClient(String url) {
            super(url);
        }

        @Override
        protected void sendJson(JsonObject json) {
            this.captured = json;
            this.callCount++;
        }
    }

    // ── FluxerWebhookClient: icon_url presence (Slack format) ─────────────────

    @Test
    void task4_1_fluxer_iconUrl_presentWhenAvatarNonEmpty() {
        CapturingFluxerWebhookClient client =
                new CapturingFluxerWebhookClient("https://api.fluxer.app/v1/webhooks/42/mytoken");

        client.sendMessage("Alice", "https://cdn.example.com/alice.png", "Hello Fluxer");

        assertNotNull(client.captured);
        assertTrue(client.captured.has("icon_url"),
                "icon_url must be present when avatarUrl is non-empty");
        assertEquals("https://cdn.example.com/alice.png",
                client.captured.get("icon_url").getAsString());
    }

    @Test
    void task4_1_fluxer_iconUrl_absentWhenAvatarEmpty() {
        CapturingFluxerWebhookClient client =
                new CapturingFluxerWebhookClient("https://api.fluxer.app/v1/webhooks/42/mytoken");

        client.sendMessage("Bob", "", "Hello Fluxer");

        assertNotNull(client.captured);
        assertFalse(client.captured.has("icon_url"),
                "icon_url must be absent when avatarUrl is empty");
    }

    // ── WebhookClient: avatar_url presence ────────────────────────────────────

    @Test
    void task4_2_webhook_avatarUrl_presentWhenNonEmpty() {
        CapturingWebhookClient client =
                new CapturingWebhookClient("https://discord.com/api/webhooks/99/token");

        client.sendMessage("Charlie", "https://cdn.example.com/charlie.png", "Hello Discord");

        assertNotNull(client.captured);
        assertTrue(client.captured.has("avatar_url"),
                "avatar_url must be present when avatarUrl is non-empty");
        assertEquals("https://cdn.example.com/charlie.png",
                client.captured.get("avatar_url").getAsString());
        assertEquals("Charlie", client.captured.get("username").getAsString());
        assertEquals("Hello Discord", client.captured.get("content").getAsString());
    }

    // ── Early return when not configured ──────────────────────────────────────

    @Test
    void task4_3_fluxer_noSend_whenWebhookIdNull() {
        CapturingFluxerWebhookClient client = new CapturingFluxerWebhookClient(null);

        client.sendMessage("Dave", "https://example.com/avatar.png", "Should not send");

        assertEquals(0, client.callCount,
                "sendJson must NOT be called when webhookId/webhookToken is null");
        assertNull(client.captured);
    }

    @Test
    void task4_3_fluxer_noSend_whenWebhookUrlInvalid() {
        CapturingFluxerWebhookClient client =
                new CapturingFluxerWebhookClient("https://not-fluxer.example.com/webhook");

        client.sendMessage("Eve", "", "Should not send");

        assertEquals(0, client.callCount,
                "sendJson must NOT be called when webhook URL is invalid/unconfigured");
    }

    @Test
    void task4_3_webhook_noSend_whenUrlNull() {
        CapturingWebhookClient client = new CapturingWebhookClient(null);

        client.sendMessage("Frank", "", "Should not send");

        assertEquals(0, client.callCount,
                "sendJson must NOT be called when webhookUrl is null");
    }
}
