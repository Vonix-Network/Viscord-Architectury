package network.vonix.viscord.utils;

public final class DiscordFormatterTest {
    public static void main(String[] args) {
        assertContract();
        System.out.println("DiscordFormatterTest: PASS");
    }

    static void assertContract() {
        if (!"hello".equals(DiscordFormatter.stripFormatting("§ahello"))) throw new AssertionError("formatting not stripped");
        if (!DiscordFormatter.hasFormatting("&lbold")) throw new AssertionError("formatting not detected");
        if (DiscordFormatter.hasFormatting("plain")) throw new AssertionError("plain text misdetected");
        if (!"**bold**".equals(DiscordFormatter.convertToDiscordFormatting("§lbold"))) {
            throw new AssertionError("bold conversion: " + DiscordFormatter.convertToDiscordFormatting("§lbold"));
        }
        if (DiscordFormatter.convertToDiscordFormatting(null) != null) throw new AssertionError("null should pass through");
        if (!"".equals(DiscordFormatter.convertToDiscordFormatting(""))) throw new AssertionError("empty should pass through");
        if (!"§lbold§r".equals(DiscordFormatter.convertDiscordToMinecraftFormatting("**bold**"))) {
            throw new AssertionError("discord to minecraft bold");
        }
    }
}
