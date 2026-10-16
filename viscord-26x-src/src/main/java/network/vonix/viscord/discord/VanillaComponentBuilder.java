package network.vonix.viscord.discord;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Builder class for creating vanilla-style advancement message components.
 * Generates MutableComponent objects that match the appearance and behavior
 * of native Minecraft advancement messages with proper colors, hover effects,
 * and server prefixes.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3
 */
public class VanillaComponentBuilder {

    // Color constants for different message parts
    private static final ChatFormatting SERVER_PREFIX_COLOR = ChatFormatting.GREEN;
    private static final ChatFormatting BRACKET_COLOR = ChatFormatting.GREEN;
    private static final ChatFormatting PLAYER_NAME_COLOR = ChatFormatting.WHITE;
    private static final ChatFormatting CONNECTOR_COLOR = ChatFormatting.WHITE;

    /**
     * Builds a complete advancement message component with server prefix.
     * Creates a component that matches vanilla advancement message formatting
     * with proper colors, hover effects, and visual distinction for server prefix.
     *
     * @param data The advancement data containing player name, title, description, and type
     * @param serverPrefix The server prefix to prepend to the message
     * @return A MutableComponent formatted as a vanilla advancement message
     * @throws IllegalArgumentException if data or serverPrefix is null
     * @throws RuntimeException if component generation fails due to system errors
     */
    public MutableComponent buildAdvancementMessage(AdvancementData data, String serverPrefix) {
        if (data == null) {
            throw new IllegalArgumentException("AdvancementData cannot be null");
        }
        if (serverPrefix == null) {
            throw new IllegalArgumentException("Server prefix cannot be null");
        }

        try {
            // Create the main message component
            MutableComponent message = Component.empty();

            // Add server prefix with visual distinction
            if (!serverPrefix.trim().isEmpty()) {
                message.append(createServerPrefixComponent(serverPrefix.trim()));
                message.append(Component.literal(" "));
            }

            // Add the player name
            message.append(Component.literal(data.getPlayerName())
                    .withStyle(PLAYER_NAME_COLOR));

            // Add connector text " has made the advancement "
            message.append(Component.literal(" has made the advancement ")
                    .withStyle(CONNECTOR_COLOR));

            // Add the advancement title with type-specific color and hover text
            MutableComponent advancementComponent = Component.literal("[" + data.getAdvancementTitle() + "]")
                    .withStyle(Style.EMPTY
                            .withColor(data.getType().getColor())
                            .withHoverEvent(createHoverText(data.getAdvancementDescription())));

            message.append(advancementComponent);

            return message;

        } catch (Exception e) {
            // If component generation fails, wrap in RuntimeException with context
            throw new RuntimeException("Failed to build advancement message component for player '" +
                    data.getPlayerName() + "' and advancement '" + data.getAdvancementTitle() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Creates a server prefix component with visual distinction.
     * The prefix is formatted with brackets and gray color to distinguish
     * it from the main advancement message content.
     *
     * @param serverPrefix The server prefix text
     * @return A MutableComponent representing the server prefix
     * @throws RuntimeException if component creation fails
     */
    private MutableComponent createServerPrefixComponent(String serverPrefix) {
        try {
            MutableComponent prefixComponent = Component.empty();

            // Add opening bracket
            prefixComponent.append(Component.literal("[")
                    .withStyle(BRACKET_COLOR));

            // Add server prefix text
            prefixComponent.append(Component.literal(serverPrefix)
                    .withStyle(SERVER_PREFIX_COLOR));

            // Add closing bracket
            prefixComponent.append(Component.literal("]")
                    .withStyle(BRACKET_COLOR));

            return prefixComponent;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create server prefix component for prefix '" + serverPrefix + "': " + e.getMessage(), e);
        }
    }

    /**
     * Creates hover text component containing the advancement description.
     * The hover text provides additional information about the advancement
     * when players hover over the advancement title.
     *
     * @param description The advancement description to display on hover
     * @return A HoverEvent containing the formatted description
     * @throws RuntimeException if hover text creation fails
     */
    private HoverEvent createHoverText(String description) {
        try {
            // Create the hover text component with proper formatting
            MutableComponent hoverComponent = Component.literal(description)
                    .withStyle(ChatFormatting.WHITE);

            return new HoverEvent.ShowText(hoverComponent);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create hover text for description '" + description + "': " + e.getMessage(), e);
        }
    }

    /**
     * Gets the appropriate text color for an advancement type.
     * This method provides a way to retrieve colors for different advancement types
     * while maintaining consistency with the AdvancementType enum.
     *
     * @param type The advancement type
     * @return The TextColor corresponding to the advancement type
     */
    private TextColor getAdvancementColor(AdvancementType type) {
        return TextColor.fromLegacyFormat(type.getColor());
    }

    /**
     * Builds a simplified advancement message without server prefix.
     * Useful for cases where server identification is not needed or handled elsewhere.
     *
     * @param data The advancement data containing player name, title, description, and type
     * @return A MutableComponent formatted as a vanilla advancement message
     * @throws IllegalArgumentException if data is null
     */
    public MutableComponent buildAdvancementMessage(AdvancementData data) {
        return buildAdvancementMessage(data, "");
    }

    /**
     * Builds a simplified event message component with server prefix.
     * Creates a component in the format: [ServerPrefix] PlayerName action
     * Example: [MCSurvival] Steve joined
     *
     * @param data The event data containing player name and event type
     * @param serverPrefix The server prefix to prepend to the message
     * @return A MutableComponent formatted as a simplified event message
     * @throws IllegalArgumentException if data or serverPrefix is null
     */
    public MutableComponent buildEventMessage(EventData data, String serverPrefix) {
        if (data == null) {
            throw new IllegalArgumentException("EventData cannot be null");
        }
        if (serverPrefix == null) {
            throw new IllegalArgumentException("Server prefix cannot be null");
        }

        try {
            MutableComponent message = Component.empty();

            // Add server prefix with visual distinction
            if (!serverPrefix.trim().isEmpty()) {
                message.append(createServerPrefixComponent(serverPrefix.trim()));
                message.append(Component.literal(" "));
            }

            // Add player name with appropriate color based on event type
            ChatFormatting playerColor = getEventPlayerColor(data.getEventType());
            message.append(Component.literal(data.getPlayerName())
                    .withStyle(playerColor));

            // Add action text
            message.append(Component.literal(" " + data.getActionString())
                    .withStyle(ChatFormatting.YELLOW));

            return message;

        } catch (Exception e) {
            throw new RuntimeException("Failed to build event message component for player '" +
                    data.getPlayerName() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Gets the appropriate player name color for an event type.
     */
    private ChatFormatting getEventPlayerColor(EventEmbedDetector.EventType eventType) {
        switch (eventType) {
            case JOIN:
                return ChatFormatting.GREEN;
            case LEAVE:
                return ChatFormatting.GRAY;
            case DEATH:
                return ChatFormatting.RED;
            default:
                return ChatFormatting.WHITE;
        }
    }

    /**
     * Creates a fallback component for when event processing fails.
     *
     * @param playerName The name of the player
     * @param action The action description
     * @param serverPrefix The server prefix (can be empty)
     * @return A basic MutableComponent with minimal formatting
     */
    public MutableComponent createEventFallbackComponent(String playerName, String action, String serverPrefix) {
        try {
            MutableComponent fallback = Component.empty();

            if (serverPrefix != null && !serverPrefix.trim().isEmpty()) {
                try {
                    fallback.append(createServerPrefixComponent(serverPrefix.trim()));
                    fallback.append(Component.literal(" "));
                } catch (Exception prefixError) {
                    // Continue without prefix
                }
            }

            String safePlayerName = (playerName != null && !playerName.trim().isEmpty())
                ? playerName.trim()
                : "Someone";
            String safeAction = (action != null && !action.trim().isEmpty())
                ? action.trim()
                : "performed an action";

            fallback.append(Component.literal(safePlayerName + " " + safeAction)
                    .withStyle(ChatFormatting.YELLOW));

            return fallback;

        } catch (Exception e) {
            return Component.literal("An event occurred but could not be displayed")
                    .withStyle(ChatFormatting.YELLOW);
        }
    }

    /**
     * Creates a fallback component for when advancement processing fails.
     * This provides a basic message that indicates an advancement occurred
     * but detailed formatting could not be applied.
     * Enhanced with better null handling and error recovery.
     *
     * @param playerName The name of the player who earned the advancement
     * @param advancementTitle The title of the advancement
     * @param serverPrefix The server prefix (can be empty)
     * @return A basic MutableComponent with minimal formatting
     */
    public MutableComponent createFallbackComponent(String playerName, String advancementTitle, String serverPrefix) {
        try {
            MutableComponent fallback = Component.empty();

            // Add server prefix if provided with enhanced validation
            if (serverPrefix != null && !serverPrefix.trim().isEmpty()) {
                try {
                    fallback.append(createServerPrefixComponent(serverPrefix.trim()));
                    fallback.append(Component.literal(" "));
                } catch (Exception prefixError) {
                    // If prefix creation fails, continue without it rather than failing completely
                    // This ensures maximum fallback reliability
                }
            }

            // Add player name with null safety
            String safePlayerName = (playerName != null && !playerName.trim().isEmpty())
                ? playerName.trim()
                : "Someone";

            fallback.append(Component.literal(safePlayerName + " has made an advancement")
                    .withStyle(ChatFormatting.YELLOW));

            // Add advancement title if available with enhanced validation
            if (advancementTitle != null && !advancementTitle.trim().isEmpty()) {
                try {
                    fallback.append(Component.literal(": " + advancementTitle.trim())
                            .withStyle(ChatFormatting.WHITE));
                } catch (Exception titleError) {
                    // If title formatting fails, continue without the title
                    // This ensures the basic message still gets through
                }
            }

            return fallback;

        } catch (Exception e) {
            // Ultimate fallback - create the most basic possible component
            try {
                return Component.literal("⚠ An advancement was earned but could not be displayed")
                        .withStyle(ChatFormatting.YELLOW);
            } catch (Exception ultimateError) {
                // If even the ultimate fallback fails, return empty component
                // This should never happen, but ensures system stability
                return Component.empty();
            }
        }
    }
}
