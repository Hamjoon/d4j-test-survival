###Test START##
```java
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

import org.junit.Test;

/**
 * JUnit4 test cases for {@link CharSequenceTranslator}.
 * <p>
 * The tests use a small concrete implementation of {@link CharSequenceTranslator}
 * that allows us to verify the behaviour of the abstract class methods:
 * <ul>
 *   <li>{@link #translate(CharSequence)}</li>
 *   <li>{@link #translate(CharSequence, Writer)}</li>
 *   <li>{@link #with(CharSequenceTranslator...)} (merging)</li>
 *   <li>{@link #hex(int)}</li>
 * </ul>
 * The concrete translators are deliberately simple but cover the
 * edge‑cases required by the contract (null handling, surrogate pairs,
 * zero‑consumption, etc.).
 * </p>
 */
public class CharSequenceTranslatorTest {

    /**
     * Simple translator that replaces the character 'a' (U+0061) with the string "b".
     * It consumes exactly one code point when the match is found and writes
     * the replacement; otherwise it returns 0 to let the base class write the
     * original character(s).
     */
    private static class SimpleATranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            if (input.charAt(index) == 'a') {
                out.write('b');
                return 1; // consumed one code point
            }
            return 0; // let the base class write the original character
        }
    }

    /**
     * Translator that consumes two code points (a surrogate pair) and writes a fixed string.
     * Used to verify that the algorithm correctly advances the index when a translator
     * consumes more than one code point.
     */
    private static class SurrogatePairTranslator extends CharSequenceTranslator {
        private static final String REPLACEMENT = "<SMILE>";

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            // Check if there are at least two code units and they form a surrogate pair
            if (index + 1 < input.length()
                    && Character.isHighSurrogate(input.charAt(index))
                    && Character.isLowSurrogate(input.charAt(index + 1))) {
                out.write(REPLACEMENT);
                // Consumed a single *code point* (the pair)
                return 1;
            }
            return 0;
        }
    }

    /**
     * Translator that always consumes the current code point and writes its hex value.
     * Used for testing the {@code with(...)} merger (order of translators).
     */
    private static class HexTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            out.write(CharSequenceTranslator.hex(cp));
            return Character.charCount(cp);
        }
    }

    // -------------------------------------------------------------------------
    // Tests for translate(CharSequence)
    // -------------------------------------------------------------------------

    @Test
    public void testTranslateString_nullInput_returnsNull() {
        CharSequenceTranslator translator = new SimpleATranslator();
        assertNull("translate(null) should return null", translator.translate((CharSequence) null));
    }

    @Test
    public void testTranslateString_simpleReplacement() {
        CharSequenceTranslator translator = new SimpleATranslator();
        String result = translator.translate("abracadabra");
        assertEquals("bbrbcbdbbrb", result);
    }

    @Test
    public void testTranslateString_noReplacement() {
        CharSequenceTranslator translator = new SimpleATranslator();
        String input = "xyz";
        assertSame("When no characters are replaced, output should equal input", input, translator.translate(input));
    }

    @Test
    public void testTranslateString_surrogatePairHandled() {
        CharSequenceTranslator translator = new SurrogatePairTranslator();
        // U+1F600 (GRINNING FACE) = surrogate pair "\uD83D\uDE00"
        String input = "\uD83D\uDE00xyz";
        String expected = "<SMILE>xyz";
        assertEquals(expected, translator.translate(input));
    }

    // -------------------------------------------------------------------------
    // Tests for translate(CharSequence, Writer)
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void testTranslateWriter_nullWriter_throwsException() throws IOException {
        CharSequenceTranslator translator = new SimpleATranslator();
        translator.translate("abc", null);
    }

    @Test
    public void testTranslateWriter_nullInput_noOutput() throws IOException {
        CharSequenceTranslator translator = new SimpleATranslator();
        StringWriter out = new StringWriter();
        translator.translate((CharSequence) null, out);
        assertEquals("", out.toString());
    }

    @Test
    public void testTranslateWriter_consumeZeroWritesOriginalChar() throws IOException {
        CharSequenceTranslator translator = new SimpleATranslator();
        StringWriter out = new StringWriter();
        translator.translate("xyz", out);
        assertEquals("xyz", out.toString());
    }

    @Test
    public void testTranslateWriter_consumeOneReplacesChar() throws IOException {
        CharSequenceTranslator translator = new SimpleATranslator();
        StringWriter out = new StringWriter();
        translator.translate("a", out);
        assertEquals("b", out.toString());
    }

    @Test
    public void testTranslateWriter_multipleCodePointsAndSurrogates() throws IOException {
        // Combine two translators: first handles surrogate pair, then simple 'a' replacement.
        CharSequenceTranslator combined = new SurrogatePairTranslator().with(new SimpleATranslator());

        // Input: surrogate pair (😀), 'a', and normal chars.
        String input = "\uD83D\uDE00aXYZ";
        StringWriter out = new StringWriter();
        combined.translate(input, out);

        // Expected: "<SMILE>" replaces 😀, 'a' -> 'b', rest unchanged.
        assertEquals("<SMILE>bXYZ", out.toString());
    }

    @Test
    public void testTranslateWriter_consumedMultipleCodePoints() throws IOException {
        CharSequenceTranslator translator = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                // Consume two code points (two BMP characters) and write a marker.
                if (index + 1 < input.length()) {
                    out.write("[AB]");
                    return 2;
                }
                return 0;
            }
        };

        StringWriter out = new StringWriter();
        translator.translate("ABCD", out);
        // Consumed AB -> [AB], then CD -> [AB]
        assertEquals("[AB][AB]", out.toString());
    }

    // -------------------------------------------------------------------------
    // Tests for with(...)
    // -------------------------------------------------------------------------

    @Test
    public void testWith_MergesTranslatorsInCorrectOrder() {
        // First translator: replace 'x' with "1"
        CharSequenceTranslator t1 = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (input.charAt(index) == 'x') {
                    out.write('1');
                    return 1;
                }
                return 0;
            }
        };
        // Second translator: replace 'y' with "2"
        CharSequenceTranslator t2 = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (input.charAt(index) == 'y') {
                    out.write('2');
                    return 1;
                }
                return 0;
            }
        };
        CharSequenceTranslator merged = t1.with(t2);
        String result = merged.translate("xyzyx");
        // Expected order: t1 handles 'x', t2 handles 'y'
        assertEquals("12z21", result);
    }

    @Test
    public void testWith_EmptyArrayReturnsOriginalTranslator() {
        CharSequenceTranslator original = new SimpleATranslator();
        CharSequenceTranslator merged = original.with();
        assertNotSame("with() with no arguments should still return a new instance", original, merged);
        // Behaviour should be identical to the original
        assertEquals(original.translate("a"), merged.translate("a"));
    }

    // -------------------------------------------------------------------------
    // Tests for hex(int)
    // -------------------------------------------------------------------------

    @Test
    public void testHex_basicValues() {
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("A", CharSequenceTranslator.hex(10));
        assertEquals("1F", CharSequenceTranslator.hex(31));
        assertEquals("FF", CharSequenceTranslator.hex(255));
        assertEquals("10", CharSequenceTranslator.hex(16));
    }

    @Test
    public void testHex_upperCaseAndLocaleIndependence() {
        // Force a non‑English default locale to ensure the method uses ENGLISH explicitly
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH);
            assertEquals("ABCDEF", CharSequenceTranslator.hex(0xABCDEF));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testHex_negativeCodepoint() {
        // Integer.toHexString produces a 2's‑complement representation; we just verify the contract.
        assertEquals(Integer.toHexString(-1).toUpperCase(Locale.ENGLISH), CharSequenceTranslator.hex(-1));
    }

    // -------------------------------------------------------------------------
    // Additional sanity test: ensure translate(CharSequence) uses StringWriter correctly
    // -------------------------------------------------------------------------

    @Test
    public void testTranslateString_usesStringWriterCapacity() {
        // The capacity is input.length() * 2; we verify that a large replacement does not overflow.
        CharSequenceTranslator translator = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                out.write("REPLACEMENT");
                return 1; // consume the original single code point
            }
        };
        String longInput = "aaaaaaaaaa"; // 10 chars
        String result = translator.translate(longInput);
        assertEquals("REPLACEMENT".repeat(10), result);
    }
}
```
###Test END##