package network.vonix.viscord.utils;

public final class DiscordFormatterTest {
    public static void main(String[] args) {
        if (!"hello".equals(DiscordFormatter.stripFormatting("§ahello"))) throw new AssertionError("formatting not stripped");
        if (!DiscordFormatter.hasFormatting("&lbold")) throw new AssertionError("formatting not detected");
        if (DiscordFormatter.hasFormatting("plain")) throw new AssertionError("plain text misdetected");
        System.out.println("DiscordFormatterTest: PASS");
    }
}
