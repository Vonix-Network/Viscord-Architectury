package network.vonix.viscord.discord;

import com.google.gson.JsonObject;
import network.vonix.viscord.Viscord;
import network.vonix.viscord.config.toml.ViscordConfigToml;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles outgoing messages to Fluxer via Webhooks.
 * Used for chat messages to preserve player avatars and names.
 * Fluxer webhooks use Slack-compatible format at /webhooks/{id}/{token}/slack endpoint.
 */
public class FluxerWebhookClient {

    private static final String FLUXER_WEBHOOK_API_BASE = "https://api.fluxer.app/v1/webhooks";
    private static final Pattern WEBHOOK_URL_PATTERN = Pattern.compile("https://api\\.fluxer\\.app/v1/webhooks/(\\d+)/([a-zA-Z0-9_-]+)");
    
    private final OkHttpClient httpClient;
    private String webhookUrl;
    private String webhookId;
    private String webhookToken;

    public FluxerWebhookClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    public FluxerWebhookClient(String webhookUrl) {
        this();
        updateUrl(webhookUrl);
    }

    public void updateUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        parseWebhookUrl(webhookUrl);
    }
    
    public String getUrl() {
        return webhookUrl;
    }
    
    private void parseWebhookUrl(String url) {
        if (url == null || url.isEmpty()) {
            this.webhookId = null;
            this.webhookToken = null;
            return;
        }
        
        Matcher matcher = WEBHOOK_URL_PATTERN.matcher(url);
        if (matcher.find()) {
            this.webhookId = matcher.group(1);
            this.webhookToken = matcher.group(2);
        } else {
            Viscord.LOGGER.warn("[Fluxer Webhook] Invalid webhook URL format. Expected: https://api.fluxer.app/v1/webhooks/{id}/{token}");
            this.webhookId = null;
            this.webhookToken = null;
        }
    }

    public void sendMessage(String username, String avatarUrl, String content) {
        if (webhookId == null || webhookToken == null || content == null || content.isEmpty()) return;

        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            json.addProperty("icon_url", avatarUrl);
        }
        json.addProperty("text", content);

        sendJson(json);
    }

    public void sendEmbed(String username, String avatarUrl, JsonObject embed) {
        if (webhookId == null || webhookToken == null) return;

        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            json.addProperty("icon_url", avatarUrl);
        }
        
        // Convert Discord-style embed to Slack attachment format
        com.google.gson.JsonArray attachments = new com.google.gson.JsonArray();
        attachments.add(convertEmbedToSlackAttachment(embed));
        json.add("attachments", attachments);

        sendJson(json);
    }
    
    /**
     * Converts Discord-style embed to Slack attachment format.
     * Slack attachments have similar but slightly different field names.
     */
    private JsonObject convertEmbedToSlackAttachment(JsonObject discordEmbed) {
        JsonObject slackAttachment = new JsonObject();
        
        if (discordEmbed.has("title")) {
            slackAttachment.addProperty("title", discordEmbed.get("title").getAsString());
        }
        if (discordEmbed.has("description")) {
            slackAttachment.addProperty("text", discordEmbed.get("description").getAsString());
        }
        if (discordEmbed.has("color")) {
            // Discord color is integer, Slack uses hex string
            int color = discordEmbed.get("color").getAsInt();
            String hexColor = String.format("#%06X", color);
            slackAttachment.addProperty("color", hexColor);
        }
        if (discordEmbed.has("footer")) {
            JsonObject footer = discordEmbed.getAsJsonObject("footer");
            if (footer.has("text")) {
                slackAttachment.addProperty("footer", footer.get("text").getAsString());
            }
        }
        if (discordEmbed.has("fields")) {
            com.google.gson.JsonArray fields = discordEmbed.getAsJsonArray("fields");
            slackAttachment.add("fields", fields);
        }
        
        return slackAttachment;
    }

    protected void sendJson(JsonObject json) {
        String apiUrl = String.format("%s/%s/%s/slack", FLUXER_WEBHOOK_API_BASE, webhookId, webhookToken);
        
        Viscord.executeAsync(() -> {
            RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Viscord.LOGGER.warn("[Fluxer Webhook] Failed to send message. Code: {}", response.code());
                    if (response.body() != null) {
                        Viscord.LOGGER.debug("[Fluxer Webhook] Response: {}", response.body().string());
                    }
                } else {
                    if (ViscordConfigToml.General.DEBUG.get()) {
                        Viscord.LOGGER.debug("[Fluxer Webhook] Message sent successfully");
                    }
                }
            } catch (Exception e) {
                Viscord.LOGGER.error("[Fluxer Webhook] Error sending payload: {}", e.getMessage());
            }
        });
    }

    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
