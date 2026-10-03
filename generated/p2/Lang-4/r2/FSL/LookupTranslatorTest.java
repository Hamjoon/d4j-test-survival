package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Test;

/**
 * Unit tests for {@link LookupTranslator}.
 *
 * <p>These tests exercise the constructor logic (including handling of {@code null},
 * empty and duplicate entries), the greedy longest‑match algorithm, handling of
 * different {@link CharSequence} implementations as keys, and the behaviour of the
 * {@link #translate(CharSequence, int, java.io.Writer)} method when called directly.</p>
 */
public class LookupTranslatorTest {

    /**
     * Simple one‑to‑one translation.
     */
    @Test
    public void testSimpleLookup() {
        LookupTranslator translator = new LookupTranslator(new CharSequence[][]{
                {"a", "b"}
        });
        assertEquals("b", translator.translate("a"));
        assertEquals("bc", translator.translate("ac"));
    }

    /**
     * The translator must prefer the longest possible match (greedy algorithm).
     */
    @Test
    public void testGreedyLongestMatch() {
        LookupTranslator translator = new LookupTranslator(new CharSequence[][]{
                {"ab", "X"},
                {"a", "Y"}
        });

        // exact match on the longer key
        assertEquals("X", translator.translate("ab"));

        // shorter key when longer one is not present
        assertEquals("Yc", translator.translate("ac"));

        // longer key should win even when it is followed by other characters
        assertEquals("Xd", translator.translate("abd"));
    }

    /**
     * When no translation is found the original characters must be emitted unchanged.
     */
    @Test
    public void testNoMatchLeavesInputUnchanged() {
        LookupTranslator translator = new LookupTranslator(new CharSequence[][]{
                {"foo", "bar"}
        });
        assertEquals("hello", translator.translate("hello"));
        assertEquals("fo", translator.translate("fo"));
    }

    /**
     * Supplying {@code null} as the lookup table must not cause NPEs.
     * The translator should behave as a no‑op translator.
     */
    @Test
    public void testNullLookupTable() {
        LookupTranslator translator = new LookupTranslator(null);
        assertEquals("any string", translator.translate("any string"));
    }

    /**
     * Supplying an empty lookup array must also behave as a no‑op translator.
     */
    @Test
    public void testEmptyLookupTable() {
        LookupTranslator translator = new LookupTranslator(new CharSequence[0][]);
        assertEquals("", translator.translate(""));
        assertEquals("abc", translator.translate("abc"));
    }

    /**
     * Keys are converted to {@link String} internally, therefore any {@link CharSequence}
     * implementation should work as a key.
     */
    @Test
    public void testKeyAsDifferentCharSequenceImplementation() {
        StringBuilder keyBuilder = new StringBuilder("key");
        StringBuilder valueBuilder = new StringBuilder("val");

        LookupTranslator translator = new LookupTranslator(new CharSequence[][]{
                {keyBuilder, valueBuilder}
        });

        assertEquals("val", translator.translate("key"));
        assertEquals("valXYZ", translator.translate("keyXYZ"));
    }

    /**
     * If the same key appears more than once, the later entry must overwrite the earlier one.
     */
    @Test
    public void testDuplicateKeyOverridesPrevious() {
        LookupTranslator translator = new LookupTranslator(new CharSequence[][]{
                {"dup", "first"},
                {"dup", "second"}
        });
        assertEquals("second", translator.translate("dup"));
    }

    /**
     * Direct invocation of {@link #translate(CharSequence, int, java.io.Writer)}
     * should return the number of consumed characters and write the translated form to the writer.
     */
    @Test
    public void testTranslateMethodDirectly() throws IOException {
        LookupTranslator translator = new LookupTranslator(new CharSequence[][]{
                {"ab", "X"},
                {"a", "Y"},
                {"bc", "Z"}
        });

        StringWriter out = new StringWriter();
        int consumed = translator.translate("cabd", 1, out); // starts at character 'a' (index 1)

        // The longest matching key at index 1 is "ab" → "X"
        assertEquals(2, consumed);
        assertEquals("X", out.toString());

        // Verify that the rest of the string is unchanged when processed normally
        assertEquals("cXd", translator.translate("cabd"));
    }

    /**
     * Edge case: when the remaining characters are fewer than the longest key,
     * the translator must still attempt matches down to the shortest key length.
     */
    @Test
    public void testBoundaryWithShortRemainingInput() {
        LookupTranslator translator = new LookupTranslator(new CharSequence[][]{
                {"abcd", "W"},
                {"ab", "X"},
                {"a", "Y"}
        });

        // Input length is 3; longest key length is 4, but only "ab" and "a" can match.
        assertEquals("Xc", translator.translate("abc"));
        assertEquals("Ybc", translator.translate("abc".substring(0, 2) + "c"));
    }

    /**
     * Verify that the translator correctly handles Unicode surrogate pairs and other
     * multi‑character sequences.
     */
    @Test
    public void testUnicodeSequences() {
        // The Greek mu character (µ) followed by 's' should be mapped to "microseconds"
        LookupTranslator translator = new LookupTranslator(new CharSequence[][]{
                {"µs", "microseconds"}
        });

        assertEquals("microseconds", translator.translate("µs"));
        assertEquals("microseconds per second", translator.translate("µs per second"));
    }
}
