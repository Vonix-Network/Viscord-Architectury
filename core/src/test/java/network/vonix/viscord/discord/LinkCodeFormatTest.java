package network.vonix.viscord.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkCodeFormatTest {

    @Test
    void sixDigitDecimalContract() {
        assertEquals("000000", LinkCodeFormat.format(0));
        assertEquals("000042", LinkCodeFormat.format(42));
        assertEquals("999999", LinkCodeFormat.format(999999));
        assertTrue(LinkCodeFormat.isValid("000000"));
        assertTrue(LinkCodeFormat.isValid("123456"));
        assertFalse(LinkCodeFormat.isValid("12345"));
        assertFalse(LinkCodeFormat.isValid("1234567"));
        assertFalse(LinkCodeFormat.isValid("abcdef"));
        assertFalse(LinkCodeFormat.isValid(null));
        assertFalse(LinkCodeFormat.isValid(""));
        assertThrows(IllegalArgumentException.class, () -> LinkCodeFormat.format(-1));
        assertThrows(IllegalArgumentException.class, () -> LinkCodeFormat.format(1_000_000));
    }
}
