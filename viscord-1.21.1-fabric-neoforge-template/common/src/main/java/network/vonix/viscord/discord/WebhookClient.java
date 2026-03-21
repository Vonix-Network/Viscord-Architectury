package network.vonix.viscord.discord;

import com.google.gson.JsonObject;
import network.vonix.viscord.Viscord;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Handles outgoing messages to Discord via Webhooks.
 * Used for chat messages to preserve player avatars and names.
 */
public class WebhookClient {

    private final OkHttpClient httpClient;
    private String webhookUrl;

    public WebhookClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    public WebhookClient(String webhookUrl) {
        this();
        this.webhookUrl = webhookUrl;
    }

    public void updateUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }
    
    public String getWebhookUrl() {
        return this.webhookUrl;
    }

    public void sendMessage(String username, String avatarUrl, String content) {
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.isEmpty()) return;

        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("avatar_url", avatarUrl);
        json.addProperty("content", content);

        sendJson(json);
    }

    public void sendEmbed(String username, String avatarUrl, JsonObject embed) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        if (avatarUrl != null)
            json.addProperty("avatar_url", avatarUrl);

        com.google.gson.JsonArray embeds = new com.google.gson.JsonArray();
        embeds.add(embed);
        json.add("embeds", embeds);

        sendJson(json);
    }

    private void sendJson(JsonObject json) {
        Viscord.executeAsync(() -> {
            RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Viscord.LOGGER.warn("Failed to send webhook message. Code: {}", response.code());
                    if (response.body() != null) {
                        Viscord.LOGGER.debug("Response: {}", response.body().string());
                    }
                }
            } catch (Exception e) {
                Viscord.LOGGER.error("Error sending webhook payload to {}: {}", webhookUrl, e.getMessage());
            }
        });
    }

    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
