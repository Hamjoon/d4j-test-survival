/*
 * Comprehensive JUnit‑4 test suite for {@link org.apache.commons.lang3.text.translate.CharSequenceTranslator}
 *
 * The tests are written from the perspective of three “experts” collaborating.
 * Each expert contributed one test per public method, covering typical usage,
 * edge cases and error handling.  The final file aggregates all of those ideas.
 */

package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

import org.junit.Test;

/**
 * Test class for {@link CharSequenceTranslator}.
 *
 * <p>Because {@code CharSequenceTranslator} is abstract we provide a few concrete
 * subclasses that expose the required behaviour for the tests:</p>
 *
 * <ul>
 *   <li>{@code SimpleReplaceTranslator} – replaces a single character with a
 *       string; used for normal translation tests.</li>
 *   <li>{@code ZeroConsumerTranslator} – always returns {@code 0} to trigger
 *       the default “write‑as‑is” path in {@code translate(CharSequence, Writer)}.</li>
 *   <li>{@code SurrogatePairTranslator} – consumes a surrogate pair (a single
 *       Unicode code point > 0xFFFF) and writes a placeholder.</li>
 * </ul>
 *
 * The suite verifies:
 * <ul>
 *   <li>Null handling for the {@code translate(CharSequence)} helper.</li>
 *   <li>Correct exception when the supplied {@code Writer} is {@code null}.</li>
 *   <li>Proper advancement of the input index when the abstract {@code translate}
 *       method returns the number of consumed code‑points.</li>
 *   <li>Behaviour of the {@code with(...)} merger.</li>
 *   <li>Correct hex conversion and upper‑casing.</li>
 * </ul>
 */
public class CharSequenceTranslatorTest {

    // ------------------------------------------------------------------------
    // Helper concrete translators
    // ------------------------------------------------------------------------

    /** Replaces every occurrence of {@code from} with {@code to}. */
    private static final class SimpleReplaceTranslator extends CharSequenceTranslator {
        private final char from;
        private final String to;

        SimpleReplaceTranslator(char from, String to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            if (input.charAt(index) == from) {
                out.write(to);
                return 1; // consumed exactly one code‑point
            }
            return 0; // let the base class write the original character
        }
    }

    /** Always returns 0, forcing the fallback path that writes the original char(s). */
    private static final class ZeroConsumerTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) {
            return 0; // never consumes; base algorithm will write the character(s)
        }
    }

    /**
     * Consumes a surrogate pair (a single Unicode code‑point > 0xFFFF) and
     * writes the string {@code "[U]"}.
     */
    private static final class SurrogatePairTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (Character.isSupplementaryCodePoint(cp)) {
                out.write("[U]");
                // Consumed the whole code‑point (two UTF‑16 chars)
                return Character.charCount(cp);
            }
            return 0;
        }
    }

    // ------------------------------------------------------------------------
    // 1. Tests for translate(CharSequence) – the convenience wrapper
    // ------------------------------------------------------------------------

    @Test
    public void expert1_translateString_nullInput_returnsNull() {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('a', "b");
        assertNull("translate(null) must return null", translator.translate((CharSequence) null));
    }

    @Test
    public void expert1_translateString_simpleReplacement() {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('a', "bc");
        String result = translator.translate("abracadabra");
        // every 'a' becomes "bc"
        assertEquals("bcbrcbcbcdbrbcbc", result);
    }

    // ------------------------------------------------------------------------
    // 2. Tests for translate(CharSequence, Writer) – core algorithm
    // ------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void expert2_translateWriter_nullWriter_throwsException() throws IOException {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('x', "y");
        translator.translate("test", (Writer) null);
    }

    @Test
    public void expert2_translateWriter_nullInput_isNoOp() throws IOException {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('x', "y");
        StringWriter out = new StringWriter();
        translator.translate((CharSequence) null, out);
        assertEquals("Writer must stay untouched for null input", "", out.toString());
    }

    @Test
    public void expert2_translateWriter_consumedZero_writesOriginalChar() throws IOException {
        CharSequenceTranslator translator = new ZeroConsumerTranslator();
        StringWriter out = new StringWriter();
        translator.translate("Ω", out);
        assertEquals("When consume==0 the original character must be written", "Ω", out.toString());
    }

    @Test
    public void expert2_translateWriter_surrogatePairConsumedCorrectly() throws IOException {
        // U+1F600 GRINNING FACE (😀) – represented by a surrogate pair in UTF‑16
        String smiley = new StringBuilder().appendCodePoint(0x1F600).toString();
        CharSequenceTranslator translator = new SurrogatePairTranslator();

        StringWriter out = new StringWriter();
        translator.translate(smiley, out);
        assertEquals("Surrogate pair should be replaced by [U]", "[U]", out.toString());
    }

    @Test
    public void expert2_translateWriter_multipleTranslatorsViaWith() throws IOException {
        // First translator: replace 'a' with "1"
        CharSequenceTranslator t1 = new SimpleReplaceTranslator('a', "1");
        // Second translator: replace 'b' with "2"
        CharSequenceTranslator t2 = new SimpleReplaceTranslator('b', "2");

        CharSequenceTranslator merged = t1.with(t2); // order matters: t1 then t2

        StringWriter out = new StringWriter();
        merged.translate("ababa", out);
        assertEquals("Merged translators should apply sequentially", "12121", out.toString());
    }

    // ------------------------------------------------------------------------
    // 3. Tests for with(CharSequenceTranslator...)
    // ------------------------------------------------------------------------

    @Test
    public void expert3_with_mergesTranslatorsInCorrectOrder() throws IOException {
        // Translator that upper‑cases 'x' to "X"
        CharSequenceTranslator upperX = new SimpleReplaceTranslator('x', "X");
        // Translator that replaces 'X' with "Y"
        CharSequenceTranslator replaceX = new SimpleReplaceTranslator('X', "Y");

        CharSequenceTranslator merged = upperX.with(replaceX);
        assertEquals("Y", merged.translate("x"));
        // Ensure the original order (upperX first, then replaceX) is honoured.
    }

    @Test
    public void expert3_with_emptyArray_returnsSameInstance() {
        CharSequenceTranslator t = new SimpleReplaceTranslator('z', "Z");
        CharSequenceTranslator merged = t.with(); // no extra translators
        assertSame("Calling with() with no arguments must return an AggregateTranslator containing only the original",
                t, ((AggregateTranslator) merged).getTranslators()[0]);
    }

    // ------------------------------------------------------------------------
    // 4. Tests for static hex(int) utility
    // ------------------------------------------------------------------------

    @Test
    public void expert1_hex_basicValues() {
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("A", CharSequenceTranslator.hex(10));
        assertEquals("FF", CharSequenceTranslator.hex(255));
        assertEquals("10", CharSequenceTranslator.hex(16));
    }

    @Test
    public void expert1_hex_upperCaseAndLocaleIndependence() {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH); // French uses different decimal separator, etc.
            assertEquals("ABCDEF", CharSequenceTranslator.hex(0xABCDEF));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void expert1_hex_surrogateCodePoint() {
        // Highest valid Unicode code‑point: 0x10FFFF
        assertEquals("10FFFF", CharSequenceTranslator.hex(0x10FFFF));
    }

    // ------------------------------------------------------------------------
    // Helper to expose internal translators array from AggregateTranslator for testing
    // ------------------------------------------------------------------------
    private static class AggregateTranslator extends CharSequenceTranslator {
        private final CharSequenceTranslator[] translators;

        AggregateTranslator(CharSequenceTranslator[] translators) {
            this.translators = translators;
        }

        CharSequenceTranslator[] getTranslators() {
            return translators;
        }

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            for (CharSequenceTranslator translator : translators) {
                int consumed = translator.translate(input, index, out);
                if (consumed != 0) {
                    return consumed;
                }
            }
            return 0;
        }
    }
}
