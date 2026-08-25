package network.vonix.viscord.discord;

import network.vonix.viscord.Viscord;
import org.javacord.api.entity.message.embed.Embed;
import java.util.Set;

/**
 * Detector class for identifying event embeds (join, leave, death) from Discord messages.
 * Uses footer-based detection with event keywords to distinguish event embeds from regular embeds.
 */
public class EventEmbedDetector {

    /**
     * Enum representing the different types of events that can be detected.
     */
    public enum EventType {
        JOIN("joined", "join"),
        LEAVE("left", "leave"),
        DEATH("death", "died"),
        UNKNOWN(null, null);

        private final String actionVerb;
        private final String footerKeyword;

        EventType(String actionVerb, String footerKeyword) {
            this.actionVerb = actionVerb;
            this.footerKeyword = footerKeyword;
        }

        public String getActionVerb() {
            return actionVerb;
        }

        public String getFooterKeyword() {
            return footerKeyword;
        }
    }

    /**
     * Keywords used to identify event embeds in footer text.
     */
    private static final Set<String> EVENT_FOOTER_KEYWORDS = Set.of(
        "join", "leave", "death"
    );

    /**
     * Determines if a Discord embed is an event embed (join/leave/death).
     *
     * @param embed The Discord embed to analyze
     * @return true if the embed is identified as an event embed, false otherwise
     */
    public boolean isEventEmbed(Embed embed) {
        if (embed == null) {
            return false;
        }

        try {
            return hasEventFooter(embed) || hasEventTitle(embed);
        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Error during event embed detection: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Determines the event type from embed content.
     *
     * @param embed The Discord embed to analyze
     * @return The corresponding EventType, defaults to UNKNOWN if not recognized
     */
    public EventType getEventType(Embed embed) {
        if (embed == null) {
            return EventType.UNKNOWN;
        }

        try {
            // Check footer first for event type
            if (embed.getFooter().isPresent()) {
                String footerText = embed.getFooter().get().getText()
                        .map(String::toLowerCase)
                        .orElse("");

                if (footerText.contains("join")) {
                    return EventType.JOIN;
                } else if (footerText.contains("leave")) {
                    return EventType.LEAVE;
                } else if (footerText.contains("death")) {
                    return EventType.DEATH;
                }
            }

            // Check title for event type indicators
            if (embed.getTitle().isPresent()) {
                String title = embed.getTitle().get().toLowerCase();

                if (title.contains("joined") || title.contains("join")) {
                    return EventType.JOIN;
                } else if (title.contains("left") || title.contains("leave")) {
                    return EventType.LEAVE;
                } else if (title.contains("died") || title.contains("death")) {
                    return EventType.DEATH;
                }
            }

            return EventType.UNKNOWN;

        } catch (Exception e) {
            Viscord.LOGGER.warn("[Discord] Error determining event type: {}", e.getMessage());
            return EventType.UNKNOWN;
        }
    }

    /**
     * Checks if the embed footer contains event-related keywords.
     */
    private boolean hasEventFooter(Embed embed) {
        try {
            if (!embed.getFooter().isPresent()) {
                return false;
            }

            String footerText = embed.getFooter().get().getText()
                    .map(String::toLowerCase)
                    .orElse("");

            return EVENT_FOOTER_KEYWORDS.stream()
                    .anyMatch(keyword -> footerText.contains(keyword));

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the embed title contains event-related keywords.
     */
    private boolean hasEventTitle(Embed embed) {
        try {
            if (!embed.getTitle().isPresent()) {
                return false;
            }

            String title = embed.getTitle().get().toLowerCase();
            String strippedTitle = title.replaceAll("[^a-zA-Z ]", "").trim();

            return strippedTitle.contains("player joined") ||
                   strippedTitle.contains("player left") ||
                   strippedTitle.contains("player died");

        } catch (Exception e) {
            return false;
        }
    }
}
