package network.vonix.viscord.utils;

import java.util.regex.Pattern;

/** Pure formatting conversion shared by the loader-specific pilot. */
public final class DiscordFormatter {
    private static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("[§&][0-9a-fk-orA-FK-OR]");
    private static final String[] DISCORD_COLORS = {
        "⚫", "🟦", "🟩", "🟨", "🟥", "🟪", "🟧", "⚪", "⚫", "🔵", "🟢", "🔷", "🔴", "🟠", "🟡", "⚪"
    };
    private DiscordFormatter() {}

    public static String convertToDiscordFormatting(String message) {
        if (message == null || message.isEmpty()) return message;
        StringBuilder result = new StringBuilder();
        StringBuilder current = new StringBuilder();
        boolean formatted = false;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (i + 1 < message.length() && (c == '§' || c == '&')) {
                String replacement = process(message.charAt(++i), current);
                if (replacement != null) {
                    if (replacement.equals("RESET")) {
                        result.append(close(current));
                        current.setLength(0);
                        formatted = false;
                    } else {
                        result.append(replacement);
                        formatted = true;
                    }
                    continue;
                }
                result.append(c).append(message.charAt(i));
                continue;
            }
            result.append(c);
        }
        if (formatted) result.append(close(current));
        return result.toString();
    }

    private static String process(char code, StringBuilder current) {
        switch (Character.toLowerCase(code)) {
            case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7':
            case '8': case '9': case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
                int index = "0123456789abcdef".indexOf(Character.toLowerCase(code));
                String replacement = close(current) + DISCORD_COLORS[index] + " ";
                current.setLength(0);
                return replacement;
            case 'k': return "✨ ";
            case 'l': return toggle(current, "bold ", "**");
            case 'm': return toggle(current, "strikethrough ", "~~");
            case 'n': return toggle(current, "underline ", "__");
            case 'o': return toggle(current, "italic ", "*");
            case 'r': return "RESET";
            default: return null;
        }
    }

    private static String toggle(StringBuilder current, String name, String marker) {
        if (current.indexOf(name) != -1) return null;
        current.append(name);
        return marker;
    }

    private static String close(StringBuilder current) {
        String value = current.toString();
        StringBuilder result = new StringBuilder();
        if (value.contains("italic ")) result.append('*');
        if (value.contains("underline ")) result.append("__");
        if (value.contains("strikethrough ")) result.append("~~");
        if (value.contains("bold ")) result.append("**");
        return result.toString();
    }

    public static String stripFormatting(String message) {
        if (message == null || message.isEmpty()) return message;
        return FORMATTING_CODE_PATTERN.matcher(message).replaceAll("");
    }

    public static boolean hasFormatting(String message) {
        return message != null && !message.isEmpty() && FORMATTING_CODE_PATTERN.matcher(message).find();
    }

    public static String convertDiscordToMinecraftFormatting(String message) {
        if (message == null || message.isEmpty()) return message;
        message = message.replaceAll("\\*\\*(.+?)\\*\\*", "§l$1§r");
        message = message.replaceAll("__(.+?)__", "§n$1§r");
        message = message.replaceAll("~~(.+?)~~", "§m$1§r");
        message = message.replaceAll("\\*(.+?)\\*", "§o$1§r");
        return message.replaceAll("(?<!\\w)_(.+?)_(?!\\w)", "§o$1§r");
    }
}
