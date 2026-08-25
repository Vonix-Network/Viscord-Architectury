package network.vonix.viscord.discord;

import network.vonix.viscord.Viscord;
import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.entity.message.embed.EmbedField;

/**
 * Extractor class for parsing event information from Discord embeds.
 * Extracts player name and event details from join/leave/death embeds.
 */
public class EventDataExtractor {

    private final EventEmbedDetector detector;

    public EventDataExtractor() {
        this.detector = new EventEmbedDetector();
    }

    /**
     * Extracts event data from a Discord embed.
     *
     * @param embed The Discord embed to extract from
     * @return EventData containing extracted information
     * @throws ExtractionException if extraction fails
     */
    public EventData extractFromEmbed(Embed embed) throws ExtractionException {
        if (embed == null) {
            throw new ExtractionException("Embed cannot be null");
        }

        EventEmbedDetector.EventType eventType = detector.getEventType(embed);
        if (eventType == EventEmbedDetector.EventType.UNKNOWN) {
            throw new ExtractionException("Could not determine event type from embed");
        }

        String playerName = extractPlayerName(embed);
        if (playerName == null || playerName.trim().isEmpty()) {
            throw new ExtractionException("Could not extract player name from embed");
        }

        // For death events, try to extract the death message
        String deathMessage = null;
        if (eventType == EventEmbedDetector.EventType.DEATH) {
            deathMessage = extractDeathMessage(embed);
        }

        return new EventData(playerName, eventType, deathMessage);
    }

    /**
     * Extracts player name from embed fields or description.
     */
    private String extractPlayerName(Embed embed) {
        // Try to find player name in fields first
        for (EmbedField field : embed.getFields()) {
            String fieldName = field.getName().toLowerCase();
            if (fieldName.contains("player") || fieldName.contains("user") || fieldName.equals("name")) {
                String value = field.getValue();
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        }

        // Try embed author
        if (embed.getAuthor().isPresent()) {
            String authorName = embed.getAuthor().get().getName();
            if (authorName != null && !authorName.trim().isEmpty()) {
                return authorName.trim();
            }
        }

        // Try to extract from description
        if (embed.getDescription().isPresent()) {
            String description = embed.getDescription().get();
            String extracted = extractPlayerFromDescription(description);
            if (extracted != null) {
                return extracted;
            }
        }

        return null;
    }

    /**
     * Attempts to extract player name from description text.
     */
    private String extractPlayerFromDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }

        // Common patterns: "PlayerName joined", "PlayerName left", "PlayerName died"
        String[] words = description.split("\\s+");
        if (words.length > 0) {
            String firstWord = words[0].trim();
            // Filter out generic words
            if (!firstWord.equalsIgnoreCase("a") &&
                !firstWord.equalsIgnoreCase("the") &&
                !firstWord.equalsIgnoreCase("someone") &&
                !firstWord.isEmpty()) {
                return firstWord;
            }
        }

        return null;
    }

    /**
     * Extracts death message from embed for death events.
     */
    private String extractDeathMessage(Embed embed) {
        // Check for a message/cause field
        for (EmbedField field : embed.getFields()) {
            String fieldName = field.getName().toLowerCase();
            if (fieldName.contains("message") || fieldName.contains("cause") || fieldName.contains("death")) {
                String value = field.getValue();
                if (value != null && !value.trim().isEmpty() && !value.equals("—")) {
                    return value.trim();
                }
            }
        }

        // Try description for death message
        if (embed.getDescription().isPresent()) {
            String description = embed.getDescription().get();
            if (description != null && !description.trim().isEmpty() &&
                !description.equalsIgnoreCase("a player died")) {
                return description.trim();
            }
        }

        return null;
    }
}
