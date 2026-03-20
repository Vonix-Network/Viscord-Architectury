package network.vonix.viscord.discord;

import java.util.Objects;

/**
 * Data class representing extracted advancement information from Discord embeds.
 * Contains player name, advancement title, description, and type with validation
 * to ensure all required fields are present and non-empty.
 * 
 * Requirements: 2.1, 2.2, 2.3, 6.4
 */
public class AdvancementData {
    private final String playerName;
    private final String advancementTitle;
    private final String advancementDescription;
    private final AdvancementType type;
    
    /**
     * Creates a new AdvancementData instance with validation.
     * 
     * @param playerName The name of the player who earned the advancement
     * @param advancementTitle The title of the advancement
     * @param advancementDescription The description of the advancement
     * @param type The type of advancement (NORMAL, GOAL, or CHALLENGE)
     * @throws IllegalArgumentException if any parameter is null or empty (for strings)
     */
    public AdvancementData(String playerName, String advancementTitle, 
                          String advancementDescription, AdvancementType type) {
        this.playerName = validateString(playerName, "playerName");
        this.advancementTitle = validateString(advancementTitle, "advancementTitle");
        this.advancementDescription = validateString(advancementDescription, "advancementDescription");
        this.type = Objects.requireNonNull(type, "type cannot be null");
    }
    
    /**
     * Validates that a string parameter is not null or empty.
     * 
     * @param value The string value to validate
     * @param fieldName The name of the field for error messages
     * @return The validated string value
     * @throws IllegalArgumentException if the value is null or empty
     */
    private String validateString(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
    
    /**
     * Gets the player name.
     * 
     * @return The player name, guaranteed to be non-null and non-empty
     */
    public String getPlayerName() {
        return playerName;
    }
    
    /**
     * Gets the advancement title.
     * 
     * @return The advancement title, guaranteed to be non-null and non-empty
     */
    public String getAdvancementTitle() {
        return advancementTitle;
    }
    
    /**
     * Gets the advancement description.
     * 
     * @return The advancement description, guaranteed to be non-null and non-empty
     */
    public String getAdvancementDescription() {
        return advancementDescription;
    }
    
    /**
     * Gets the advancement type.
     * 
     * @return The advancement type, guaranteed to be non-null
     */
    public AdvancementType getType() {
        return type;
    }
    
    /**
     * Returns a string representation of this advancement data.
     * 
     * @return A string representation including all fields
     */
    @Override
    public String toString() {
        return "AdvancementData{" +
                "playerName='" + playerName + '\'' +
                "advancementTitle='" + advancementTitle + '\'' +
                "advancementDescription='" + advancementDescription + '\'' +
                "type=" + type +
                '}';
    }
    
    /**
     * Checks equality based on all fields.
     * 
     * @param obj The object to compare with
     * @return true if all fields are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AdvancementData that = (AdvancementData) obj;
        return Objects.equals(playerName, that.playerName) &&
               Objects.equals(advancementTitle, that.advancementTitle) &&
               Objects.equals(advancementDescription, that.advancementDescription) &&
               type == that.type;
    }
    
    /**
     * Generates hash code based on all fields.
     * 
     * @return The hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(playerName, advancementTitle, advancementDescription, type);
    }
}
