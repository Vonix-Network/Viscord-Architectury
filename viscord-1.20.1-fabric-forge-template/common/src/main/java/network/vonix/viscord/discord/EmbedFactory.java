package network.vonix.viscord.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Factory for creating Discord webhook embeds.
 * Reduces duplication in embed creation code.
 */
public class EmbedFactory {

    /**
     * Create a simple event embed with title, description, and color
     */
    public static java.util.function.Consumer<JsonObject> createSimpleEmbed(
            String title,
            String description,
            int color,
            String footerText) {
        return embed -> {
            embed.addProperty("title", title);
            embed.addProperty("description", description);
            embed.addProperty("color", color);
            addFooter(embed, footerText);
        };
    }

    /**
     * Create a player event embed (join/leave) with player and server fields
     */
    public static java.util.function.Consumer<JsonObject> createPlayerEventEmbed(
            String title,
            String description,
            int color,
            String playerName,
            String serverName,
            String footerText,
            String thumbnailUrl) {
        return embed -> {
            embed.addProperty("title", title);
            embed.addProperty("description", description);
            embed.addProperty("color", color);

            JsonArray fields = new JsonArray();
            addField(fields, "Player", playerName, true);
            addField(fields, "Server", serverName, true);
            embed.add("fields", fields);

            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                JsonObject thumbnail = new JsonObject();
                thumbnail.addProperty("url", thumbnailUrl);
                embed.add("thumbnail", thumbnail);
            }

            addFooter(embed, footerText);
        };
    }

    /**
     * Create an advancement embed with player, title, and description fields
     */
    public static java.util.function.Consumer<JsonObject> createAdvancementEmbed(
            String emoji,
            int color,
            String playerName,
            String advancementTitle,
            String advancementDescription) {
        return embed -> {
            embed.addProperty("title", emoji + " Advancement Made");
            embed.addProperty("description", "A player has completed an advancement.");
            embed.addProperty("color", color);

            JsonArray fields = new JsonArray();
            addField(fields, "Player", playerName, true);
            addField(fields, "Title", advancementTitle, true);
            addField(fields, "Description",
                    advancementDescription == null || advancementDescription.isBlank() ? "—" : advancementDescription,
                    false);
            embed.add("fields", fields);

            addFooter(embed, "Viscord · Advancement");
        };
    }

    /**
     * Create a server status embed (startup/shutdown)
     */
    public static java.util.function.Consumer<JsonObject> createServerStatusEmbed(
            String title,
            String description,
            int color,
            String serverName,
            String footerText) {
        return embed -> {
            embed.addProperty("title", title);
            embed.addProperty("description", description);
            embed.addProperty("color", color);

            JsonArray fields = new JsonArray();
            addField(fields, "Server", serverName == null ? "Unknown" : serverName, true);
            embed.add("fields", fields);

            addFooter(embed, footerText);
        };
    }

    // Helper methods

    private static void addField(JsonArray fields, String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        fields.add(field);
    }

    private static void addFooter(JsonObject embed, String text) {
        JsonObject footer = new JsonObject();
        footer.addProperty("text", text);
        embed.add("footer", footer);
    }
}
