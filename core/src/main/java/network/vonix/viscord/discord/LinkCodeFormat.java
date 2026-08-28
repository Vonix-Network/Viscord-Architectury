package network.vonix.viscord.discord;

import java.util.regex.Pattern;

/**
 * Account-link code format shared by every Viscord cell.
 *
 * <p>Generation still happens next to the pending-link map (Minecraft/Discord
 * types). This type only pins the 6-digit decimal contract.
 */
public final class LinkCodeFormat {
    public static final int DIGITS = 6;
    public static final int RADIX = 1_000_000;
    public static final Pattern PATTERN = Pattern.compile("\\d{6}");

    private LinkCodeFormat() {}

    public static boolean isValid(String code) {
        return code != null && PATTERN.matcher(code).matches();
    }

    public static String format(int value) {
        if (value < 0 || value >= RADIX) {
            throw new IllegalArgumentException("link code out of range");
        }
        return String.format("%06d", value);
    }
}
