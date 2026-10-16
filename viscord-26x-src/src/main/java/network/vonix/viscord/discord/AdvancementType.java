package network.vonix.viscord.discord;

import net.minecraft.ChatFormatting;

/**
 * Enumeration of advancement types with their display text and color mappings.
 * Used to categorize and format different types of advancement messages from Discord.
 *
 * Requirements: 2.1, 2.2, 2.3, 6.4
 */
public enum AdvancementType {
    /**
     * Regular advancement completion
     */
    NORMAL("Advancement Made", ChatFormatting.YELLOW),

    /**
     * Goal advancement completion
     */
    GOAL("Goal Reached", ChatFormatting.YELLOW),

    /**
     * Challenge advancement completion
     */
    CHALLENGE("Challenge Complete", ChatFormatting.LIGHT_PURPLE);

    private final String displayText;
    private final ChatFormatting color;

    /**
     * Creates an advancement type with display text and color.
     *
     * @param displayText The text to display for this advancement type
     * @param color The color formatting to apply to this advancement type
     */
    AdvancementType(String displayText, ChatFormatting color) {
        this.displayText = displayText;
        this.color = color;
    }

    /**
     * Gets the display text for this advancement type.
     *
     * @return The display text
     */
    public String getDisplayText() {
        return displayText;
    }

    /**
     * Gets the color formatting for this advancement type.
     *
     * @return The ChatFormatting color
     */
    public ChatFormatting getColor() {
        return color;
    }

    /**
     * Determines the advancement type from embed title text.
     *
     * @param title The embed title to analyze
     * @return The corresponding AdvancementType, defaults to NORMAL if not recognized
     */
    public static AdvancementType fromTitle(String title) {
        if (title == null) {
            return NORMAL;
        }

        String lowerTitle = title.toLowerCase();

        if (lowerTitle.contains("challenge")) {
            return CHALLENGE;
        } else if (lowerTitle.contains("goal")) {
            return GOAL;
        } else {
            return NORMAL;
        }
    }
}
