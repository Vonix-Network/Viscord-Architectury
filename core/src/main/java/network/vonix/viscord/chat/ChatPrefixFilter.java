package network.vonix.viscord.chat;

/**
 * Side-effect-free chat prefix filter shared by Fabric mixin and NeoForge
 * event/ChatForwarder adapters. Loader capture stays in loader modules.
 */
public final class ChatPrefixFilter {
    private ChatPrefixFilter() {}

    /**
     * Whether a raw in-game chat message should be forwarded to Discord.
     *
     * <p>Matches the 26.1.2 {@code ChatForwarder.shouldForward} rule:
     * disabled filter always forwards; a null/empty prefix always forwards;
     * a matching prefix is dropped.
     */
    public static boolean shouldForward(boolean filterEnabled, String filterPrefix, String rawMessage) {
        if (!filterEnabled) {
            return true;
        }
        if (rawMessage == null) {
            return false;
        }
        return filterPrefix == null || filterPrefix.isEmpty() || !rawMessage.startsWith(filterPrefix);
    }
}
