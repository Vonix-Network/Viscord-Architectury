package network.vonix.viscord.discord;

import com.google.gson.JsonObject;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for webhook payload correctness using jqwik.
 *
 * Property 1: FluxerWebhookClient always sets username and text (Slack format)
 * Property 2: WebhookClient always sets username and content; avatar_url present when non-empty
 * Property 3: icon_url in FluxerWebhookClient iff avatarUrl non-empty
 * Property 4: avatar_url in WebhookClient iff avatarUrl non-empty
 */
class WebhookProfilePBTTest {

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
        assertEquals(username, client.captured.get("username").getAsString());
        assertEquals(content, client.captured.get("content").getAsString());
    }

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

    @Property
    void property4_webhook_avatarUrl_alwaysPresent(
            @ForAll @NotEmpty String username,
            @ForAll String avatarUrl,
            @ForAll @NotEmpty String content) {

        CapturingWebhookClient client = new CapturingWebhookClient();
        client.sendMessage(username, avatarUrl, content);

        assertNotNull(client.captured);
        boolean avatarNonEmpty = avatarUrl != null && !avatarUrl.isEmpty();
        if (avatarNonEmpty) {
            assertTrue(client.captured.has("avatar_url"),
                    "avatar_url must be present when avatarUrl is non-empty");
        } else {
            assertFalse(client.captured.has("avatar_url"),
                    "avatar_url must be absent when avatarUrl is empty");
        }
    }
}
