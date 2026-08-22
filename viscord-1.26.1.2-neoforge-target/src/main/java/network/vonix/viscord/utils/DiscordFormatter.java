package network.vonix.viscord.utils;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Utility class for converting Minecraft formatting codes to Discord markdown.
 * Supports both § codes and & codes for compatibility.
 */
public class DiscordFormatter {

    // Pattern to match Minecraft formatting codes (both § and &)
    private static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("[§&][0-9a-fk-orA-FK-OR]");

    // Pattern to match reset codes
    private static final Pattern RESET_CODE_PATTERN = Pattern.compile("[§&][rR]");

    // Discord markdown mappings
    private static final String[] MINECRAFT_COLORS = {
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"
    };

    private static final String[] DISCORD_COLORS = {
        "⚫", "🟦", "🟩", "🟨", "🟥", "🟪", "🟧", "⚪", "⚫", "🔵", "🟢", "🔷", "🔴", "🟠", "🟡", "⚪"
    };

    /**
     * Converts Minecraft formatting codes to Discord markdown formatting.
     *
     * @param message The message with Minecraft formatting codes
     * @return The message with Discord markdown formatting
     */
    public static String convertToDiscordFormatting(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        StringBuilder result = new StringBuilder();
        StringBuilder currentFormatting = new StringBuilder();
        boolean hasFormatting = false;

        // Process each character
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);

            // Check if this is a formatting code
            if (i + 1 < message.length() && (c == '§' || c == '&')) {
                char code = message.charAt(i + 1);
                String replacement = processFormattingCode(code, currentFormatting);

                if (replacement != null) {
                    // This is a valid formatting code
                    if (replacement.equals("RESET")) {
                        // Close all open formatting
                        result.append(closeAllFormatting(currentFormatting));
                        currentFormatting.setLength(0);
                        hasFormatting = false;
                    } else {
                        result.append(replacement);
                        hasFormatting = true;
                    }
                    i++; // Skip the code character
                    continue;
                }
            }

            // Regular character, just append it
            result.append(c);
        }

        // Close any remaining formatting
        if (hasFormatting) {
            result.append(closeAllFormatting(currentFormatting));
        }

        return result.toString();
    }

    /**
     * Processes a single formatting code and returns the Discord equivalent.
     */
    private static String processFormattingCode(char code, StringBuilder currentFormatting) {
        switch (Character.toLowerCase(code)) {
            case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7':
            case '8': case '9': case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
                // Colors - Discord doesn't support colors directly, so we'll use emojis as indicators
                int colorIndex = "0123456789abcdef".indexOf(Character.toLowerCase(code));
                if (colorIndex >= 0 && colorIndex < DISCORD_COLORS.length) {
                    // Close previous formatting and add color indicator
                    String result = closeAllFormatting(currentFormatting) + DISCORD_COLORS[colorIndex] + " ";
                    currentFormatting.setLength(0);
                    return result;
                }
                break;

            case 'k':
                // Obfuscated/magic - Discord doesn't support this, we'll use a sparkle emoji
                return "✨ ";

            case 'l':
                // Bold
                if (currentFormatting.indexOf("bold") == -1) {
                    currentFormatting.append("bold ");
                    return "**";
                }
                break;

            case 'm':
                // Strikethrough
                if (currentFormatting.indexOf("strikethrough") == -1) {
                    currentFormatting.append("strikethrough ");
                    return "~~";
                }
                break;

            case 'n':
                // Underline
                if (currentFormatting.indexOf("underline") == -1) {
                    currentFormatting.append("underline ");
                    return "__";
                }
                break;

            case 'o':
                // Italic
                if (currentFormatting.indexOf("italic") == -1) {
                    currentFormatting.append("italic ");
                    return "*";
                }
                break;

            case 'r':
                // Reset
                return "RESET";

            default:
                // Unknown code, treat as regular text
                return null;
        }

        return null;
    }

    /**
     * Closes all open Discord markdown formatting.
     */
    private static String closeAllFormatting(StringBuilder currentFormatting) {
        StringBuilder result = new StringBuilder();
        String formatting = currentFormatting.toString();

        // Close in reverse order (last opened, first closed)
        if (formatting.contains("italic ")) result.append("*");
        if (formatting.contains("underline ")) result.append("__");
        if (formatting.contains("strikethrough ")) result.append("~~");
        if (formatting.contains("bold ")) result.append("**");

        return result.toString();
    }

    /**
     * Removes all Minecraft formatting codes from a message.
     * Useful for platforms that don't support any formatting.
     */
    public static String stripFormatting(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        return FORMATTING_CODE_PATTERN.matcher(message).replaceAll("");
    }

    /**
     * Checks if a message contains any Minecraft formatting codes.
     */
    public static boolean hasFormatting(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        return FORMATTING_CODE_PATTERN.matcher(message).find();
    }

    /**
     * Converts Discord markdown formatting to Minecraft formatting codes.
     */
    public static String convertDiscordToMinecraftFormatting(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // Process bold (**text**)
        message = message.replaceAll("\\*\\*(.+?)\\*\\*", "§l$1§r");

        // Process underline (__text__)
        message = message.replaceAll("__(.+?)__", "§n$1§r");

        // Process strikethrough (~~text~~)
        message = message.replaceAll("~~(.+?)~~", "§m$1§r");

        // Process italic (*text*)
        message = message.replaceAll("\\*(.+?)\\*", "§o$1§r");

        // Process italic (_text_) - Use negative lookbehind/lookahead to avoid matching variable_names
        message = message.replaceAll("(?<!\\w)_(.+?)_(?!\\w)", "§o$1§r");

        return message;
    }
}
