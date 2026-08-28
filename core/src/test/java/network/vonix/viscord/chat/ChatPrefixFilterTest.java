package network.vonix.viscord.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPrefixFilterTest {

    @Test
    void disabledFilterAlwaysForwards() {
        assertTrue(ChatPrefixFilter.shouldForward(false, "!", "!secret"));
        assertTrue(ChatPrefixFilter.shouldForward(false, "", "hello"));
        assertTrue(ChatPrefixFilter.shouldForward(false, null, "hello"));
    }

    @Test
    void emptyPrefixForwards() {
        assertTrue(ChatPrefixFilter.shouldForward(true, "", "!secret"));
        assertTrue(ChatPrefixFilter.shouldForward(true, null, "!secret"));
    }

    @Test
    void matchingPrefixIsDropped() {
        assertFalse(ChatPrefixFilter.shouldForward(true, "!", "!hello"));
        assertFalse(ChatPrefixFilter.shouldForward(true, "!", "! hello"));
        assertFalse(ChatPrefixFilter.shouldForward(true, "[MC]", "[MC] echo"));
    }

    @ParameterizedTest
    @CsvSource({
            "'!', hello",
            "'!', hi",
            "'!', x!hello",
            "'[MC]', hello"
    })
    void nonMatchingPrefixIsForwarded(String prefix, String message) {
        assertTrue(ChatPrefixFilter.shouldForward(true, prefix, message));
    }

    @Test
    void enabledFilterDropsNullMessage() {
        assertFalse(ChatPrefixFilter.shouldForward(true, "!", null));
    }
}
