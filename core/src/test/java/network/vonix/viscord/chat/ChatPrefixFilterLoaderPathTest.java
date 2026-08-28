package network.vonix.viscord.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Side-effect-free stand-in for the Fabric mixin and NeoForge
 * ServerChatEvent/ChatForwarder adapters. Capture stays loader-specific;
 * both paths must honor {@link ChatPrefixFilter#shouldForward}.
 */
class ChatPrefixFilterLoaderPathTest {

    static final class FakeDiscordManager {
        boolean running = true;
        final List<String> forwarded = new ArrayList<>();

        void onCapturedChat(boolean filterEnabled, String prefix, String rawMessage, String playerName) {
            if (running && ChatPrefixFilter.shouldForward(filterEnabled, prefix, rawMessage)) {
                forwarded.add(playerName + ":" + rawMessage);
            }
        }
    }

    @Test
    void fabricMixinPathCoversDisabledEmptyMatchingAndNonMatching() {
        assertMatrix("fabric-mixin");
    }

    @Test
    void neoForgeChatForwarderPathCoversDisabledEmptyMatchingAndNonMatching() {
        assertMatrix("neoforge-chat-forwarder");
    }

    private static void assertMatrix(String path) {
        FakeDiscordManager manager = new FakeDiscordManager();

        manager.onCapturedChat(false, "!", "!secret", "A");
        manager.onCapturedChat(true, "", "!secret", "B");
        manager.onCapturedChat(true, null, "!secret", "C");
        manager.onCapturedChat(true, "!", "!secret", "D");
        manager.onCapturedChat(true, "!", "hello", "E");

        assertEquals(List.of("A:!secret", "B:!secret", "C:!secret", "E:hello"), manager.forwarded, path);
        assertTrue(manager.forwarded.stream().noneMatch(s -> s.startsWith("D:")), path);
    }
}
