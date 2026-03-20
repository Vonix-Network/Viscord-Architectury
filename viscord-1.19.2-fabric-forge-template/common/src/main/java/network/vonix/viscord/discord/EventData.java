package network.vonix.viscord.discord;

import java.util.Objects;

/**
 * Data class representing extracted event information from Discord embeds.
 * Contains player name and event type with validation.
 */
public class EventData {
    private final String playerName;
    private final EventEmbedDetector.EventType eventType;
    private final String deathMessage; // Optional, only for death events
    
    /**
     * Creates a new EventData instance with validation.
     * 
     * @param playerName The name of the player involved in the event
     * @param eventType The type of event (JOIN, LEAVE, DEATH)
     * @throws IllegalArgumentException if playerName is null or empty, or eventType is null
     */
    public EventData(String playerName, EventEmbedDetector.EventType eventType) {
        this(playerName, eventType, null);
    }
    
    /**
     * Creates a new EventData instance with death message.
     * 
     * @param playerName The name of the player involved in the event
     * @param eventType The type of event (JOIN, LEAVE, DEATH)
     * @param deathMessage Optional death message for death events
     * @throws IllegalArgumentException if playerName is null or empty, or eventType is null
     */
    public EventData(String playerName, EventEmbedDetector.EventType eventType, String deathMessage) {
        this.playerName = validateString(playerName, "playerName");
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.deathMessage = deathMessage;
    }
    
    private String validateString(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public EventEmbedDetector.EventType getEventType() {
        return eventType;
    }
    
    public String getDeathMessage() {
        return deathMessage;
    }
    
    public boolean hasDeathMessage() {
        return deathMessage != null && !deathMessage.trim().isEmpty();
    }
    
    /**
     * Gets a formatted action string for display.
     * 
     * @return The action string (e.g., "joined", "left", "died")
     */
    public String getActionString() {
        switch (eventType) {
            case JOIN:
                return "joined";
            case LEAVE:
                return "left";
            case DEATH:
                return hasDeathMessage() ? deathMessage : "died";
            default:
                return "performed an action";
        }
    }
    
    @Override
    public String toString() {
        return "EventData{" +
                "playerName='" + playerName + '\'' +
                ", eventType=" + eventType +
                ", deathMessage='" + deathMessage + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        EventData that = (EventData) obj;
        return Objects.equals(playerName, that.playerName) &&
               eventType == that.eventType &&
               Objects.equals(deathMessage, that.deathMessage);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(playerName, eventType, deathMessage);
    }
}
