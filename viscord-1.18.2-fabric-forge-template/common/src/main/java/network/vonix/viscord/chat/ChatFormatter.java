package network.vonix.viscord.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MiniMessage-style chat formatter with prefix/suffix support.
 * Supports: color codes (&a, &b), hex colors (&#RRGGBB), gradients, click/hover
 * events.
 */
public class ChatFormatter {

    // Legacy color code pattern (&a, &l, etc.)
    private static final Pattern LEGACY_COLOR = Pattern.compile("&([0-9a-fk-or])");
    // Hex color pattern (&#RRGGBB or &x&R&R&G&G&B&B)
    private static final Pattern HEX_COLOR = Pattern.compile("&#([0-9A-Fa-f]{6})");
    // MiniMessage tags
    private static final Pattern MINI_TAG = Pattern.compile("<([^>]+)>");

    /**
     * Format a chat message with prefix and suffix.
     */
    public static Component formatChatMessage(ServerPlayer player, String message) {
        String playerName = player.getName().getString();
        
        net.minecraft.network.chat.MutableComponent result = Component.empty();
        
        net.minecraft.network.chat.MutableComponent nameComponent = Component.literal("<" + playerName + "> ")
                .setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.literal("§7Click to message §e" + playerName))).withClickEvent(new ClickEvent(
                                ClickEvent.Action.SUGGEST_COMMAND,
                                "/msg " + playerName + " ")));
                                
        result.append(nameComponent);
        result.append(parseColors(message));
        
        return result;
    }

    /**
     * Format display name for tab list / scoreboard.
     */
    public static Component formatDisplayName(ServerPlayer player) {
        return Component.literal(player.getName().getString());
    }

    /**
     * Parse legacy color codes and hex colors.
     */
    public static MutableComponent parseColors(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // Convert & codes to § codes (only when followed by a valid formatting code or # for hex)
        text = text.replaceAll("&([0-9a-fk-orA-FK-OR#])", "§$1");

        // Parse hex colors (&#RRGGBB -> §x§R§R§G§G§B§B)
        Matcher hexMatcher = Pattern.compile("§#([0-9A-Fa-f]{6})").matcher(text);
        StringBuffer hexResult = new StringBuffer();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            hexMatcher.appendReplacement(hexResult, replacement.toString());
        }
        hexMatcher.appendTail(hexResult);
        text = hexResult.toString();

        // Parse MiniMessage-style tags
        text = parseMiniMessageTags(text);

        return Component.literal(text);
    }

    /**
     * Parse MiniMessage-style tags to legacy codes.
     */
    private static String parseMiniMessageTags(String text) {
        // Color tags: <red>, <blue>, <#FF0000>
        text = text.replaceAll("<black>", "§0");
        text = text.replaceAll("<dark_blue>", "§1");
        text = text.replaceAll("<dark_green>", "§2");
        text = text.replaceAll("<dark_aqua>", "§3");
        text = text.replaceAll("<dark_red>", "§4");
        text = text.replaceAll("<dark_purple>", "§5");
        text = text.replaceAll("<gold>", "§6");
        text = text.replaceAll("<gray>", "§7");
        text = text.replaceAll("<dark_gray>", "§8");
        text = text.replaceAll("<blue>", "§9");
        text = text.replaceAll("<green>", "§a");
        text = text.replaceAll("<aqua>", "§b");
        text = text.replaceAll("<red>", "§c");
        text = text.replaceAll("<light_purple>", "§d");
        text = text.replaceAll("<yellow>", "§e");
        text = text.replaceAll("<white>", "§f");

        // Format tags
        text = text.replaceAll("<bold>", "§l");
        text = text.replaceAll("<b>", "§l");
        text = text.replaceAll("<italic>", "§o");
        text = text.replaceAll("<i>", "§o");
        text = text.replaceAll("<underlined>", "§n");
        text = text.replaceAll("<u>", "§n");
        text = text.replaceAll("<strikethrough>", "§m");
        text = text.replaceAll("<st>", "§m");
        text = text.replaceAll("<obfuscated>", "§k");
        text = text.replaceAll("<obf>", "§k");
        text = text.replaceAll("<reset>", "§r");
        text = text.replaceAll("<r>", "§r");

        // Hex colors: <#RRGGBB>
        Matcher hexMatch = Pattern.compile("<#([0-9A-Fa-f]{6})>").matcher(text);
        StringBuffer hexBuf = new StringBuffer();
        while (hexMatch.find()) {
            String hex = hexMatch.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            hexMatch.appendReplacement(hexBuf, replacement.toString());
        }
        hexMatch.appendTail(hexBuf);
        text = hexBuf.toString();

        // Close tags (</color>, </bold>, etc.) - just remove them
        text = text.replaceAll("</[^>]+>", "");

        return text;
    }

    /**
     * Strip all color codes from text.
     */
    public static String stripColors(String text) {
        if (text == null)
            return "";
        return text.replaceAll("§[0-9a-fk-or]", "")
                .replaceAll("§x(§[0-9A-Fa-f]){6}", "")
                .replaceAll("<[^>]+>", "");
    }

    /**
     * Create a gradient between two colors.
     */
    public static MutableComponent gradient(String text, int startColor, int endColor) {
        MutableComponent result = Component.empty();
        int length = text.length();

        for (int i = 0; i < length; i++) {
            float ratio = (float) i / Math.max(1, length - 1);
            int color = interpolateColor(startColor, endColor, ratio);

            result.append(Component.literal(String.valueOf(text.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        }

        return result;
    }

    private static int interpolateColor(int start, int end, float ratio) {
        int r1 = (start >> 16) & 0xFF;
        int g1 = (start >> 8) & 0xFF;
        int b1 = start & 0xFF;

        int r2 = (end >> 16) & 0xFF;
        int g2 = (end >> 8) & 0xFF;
        int b2 = end & 0xFF;

        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Rainbow text effect.
     */
    public static MutableComponent rainbow(String text) {
        MutableComponent result = Component.empty();
        int[] colors = { 0xFF0000, 0xFF7F00, 0xFFFF00, 0x00FF00, 0x0000FF, 0x4B0082, 0x9400D3 };
        int length = text.length();

        for (int i = 0; i < length; i++) {
            int colorIndex = (int) ((float) i / length * colors.length) % colors.length;
            result.append(Component.literal(String.valueOf(text.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(colors[colorIndex]))));
        }

        return result;
    }
}
