/**
 * JUnit 4 test suite for {@link org.apache.commons.lang3.text.translate.CharSequenceTranslator}.
 *
 * The tests cover:
 *  <ul>
 *      <li>All public methods of {@code CharSequenceTranslator}</li>
 *      <li>Typical usage scenarios</li>
 *      <li>Edge‑case handling (nulls, empty strings, surrogate pairs, zero‑consumption)</li>
 *      <li>Exception paths (null Writer, IOExceptions from a custom Writer)</li>
 *  </ul>
 *
 * The abstract {@code translate(CharSequence,int,Writer)} method is exercised via
 * simple concrete subclasses defined within this test class.
 */
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.Rule;

/**
 * Test class for {@link CharSequenceTranslator}.
 */
public class CharSequenceTranslatorTest {

    // -------------------------------------------------------------------------
    // Helper concrete translators used by the tests
    // -------------------------------------------------------------------------

    /**
     * Simple translator that replaces every occurrence of the character {@code 'a'}
     * with the string {@code "b"}.
     */
    private static class AtoBTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            if (input.charAt(index) == 'a') {
                out.write('b');
                return 1; // consumed one code‑point
            }
            return 0; // let the base class copy the character unchanged
        }
    }

    /**
     * Translator that replaces the Unicode code‑point U+1F600 (GRINNING FACE) with
     * the smiley string {@code ":)"}.
     * Demonstrates handling of surrogate pairs.
     */
    private static class EmojiToSmileyTranslator extends CharSequenceTranslator {
        private static final int GRINNING_FACE = 0x1F600; // 😀
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == GRINNING_FACE) {
                out.write(":)");
                // The code point occupies two char units (a surrogate pair)
                return Character.charCount(cp);
            }
            return 0;
        }
    }

    /**
     * Translator that deliberately throws an {@link IOException} when its
     * {@code translate} method is invoked. Used to test exception propagation.
     */
    private static class FailingTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            throw new IOException("forced failure");
        }
    }

    // -------------------------------------------------------------------------
    // Tests for the concrete abstract method (via subclasses)
    // -------------------------------------------------------------------------

    @Test
    public void testAtoBTranslator_basicTranslation() throws IOException {
        CharSequenceTranslator tr = new AtoBTranslator();
        StringWriter out = new StringWriter();
        int consumed = tr.translate("abracadabra", 0, out);
        // first char is 'a' -> should be replaced with 'b' and consume 1 code‑point
        assertEquals(1, consumed);
        assertEquals("b", out.toString());
    }

    @Test
    public void testAtoBTranslator_noTranslationReturnsZero() throws IOException {
        CharSequenceTranslator tr = new AtoBTranslator();
        StringWriter out = new StringWriter();
        int consumed = tr.translate("xyz", 0, out);
        // 'x' is not handled, translator must return 0
        assertEquals(0, consumed);
        // Writer must remain unchanged because the calling algorithm (in translate)
        // will copy the original character when 0 is returned.
        assertEquals("", out.toString());
    }

    // -------------------------------------------------------------------------
    // Tests for CharSequenceTranslator.translate(CharSequence) – the String helper
    // -------------------------------------------------------------------------

    @Test
    public void testTranslateStringHelper_nullInput() {
        CharSequenceTranslator tr = new AtoBTranslator();
        assertNull(tr.translate((CharSequence) null));
    }

    @Test
    public void testTranslateStringHelper_simpleReplacement() {
        CharSequenceTranslator tr = new AtoBTranslator();
        String result = tr.translate("banana");
        // a -> b, so "banana" becomes "bbnbnb"
        assertEquals("bbnbnb", result);
    }

    @Test
    public void testTranslateStringHelper_emojiReplacement() {
        CharSequenceTranslator tr = new EmojiToSmileyTranslator();
        String input = "\uD83D\uDE00"; // 😀 (surrogate pair)
        String result = tr.translate(input);
        assertEquals(":)", result);
    }

    // -------------------------------------------------------------------------
    // Tests for CharSequenceTranslator.translate(CharSequence, Writer)
    // -------------------------------------------------------------------------

    @Test
    public void testTranslateToWriter_nullWriterThrows() throws IOException {
        CharSequenceTranslator tr = new AtoBTranslator();
        try {
            tr.translate("test", null);
            fail("Expected IllegalArgumentException for null Writer");
        } catch (IllegalArgumentException e) {
            assertEquals("The Writer must not be null", e.getMessage());
        }
    }

    @Test
    public void testTranslateToWriter_nullInputDoesNothing() throws IOException {
        CharSequenceTranslator tr = new AtoBTranslator();
        StringWriter out = new StringWriter();
        tr.translate(null, out);
        // No exception, writer remains empty
        assertEquals("", out.toString());
    }

    @Test
    public void testTranslateToWriter_fullAlgorithm_simple() throws IOException {
        CharSequenceTranslator tr = new AtoBTranslator();
        StringWriter out = new StringWriter();
        tr.translate("abracadabra", out);
        // a->b, rest unchanged
        assertEquals("bbrbcbdbbrb", out.toString());
    }

    @Test
    public void testTranslateToWriter_zeroConsumptionCopiesOriginalChar() throws IOException {
        CharSequenceTranslator tr = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) {
                // never consume; always return 0
                return 0;
            }
        };
        StringWriter out = new StringWriter();
        tr.translate("xyz", out);
        // The base algorithm should copy the original input unchanged
        assertEquals("xyz", out.toString());
    }

    @Test
    public void testTranslateToWriter_surrogatePairHandling() throws IOException {
        CharSequenceTranslator tr = new EmojiToSmileyTranslator();
        StringWriter out = new StringWriter();
        // Input contains a surrogate pair (😀) followed by a normal char
        String input = "\uD83D\uDE00a";
        tr.translate(input, out);
        // Emoji replaced, 'a' left untouched (because this translator does not handle it)
        assertEquals(":)a", out.toString());
    }

    @Test
    public void testTranslateToWriter_propagatesIOException() throws IOException {
        CharSequenceTranslator tr = new FailingTranslator();
        Writer failingWriter = new Writer() {
            @Override public void write(char[] cbuf, int off, int len) {}
            @Override public void flush() {}
            @Override public void close() {}
            @Override public void write(int c) throws IOException {
                throw new IOException("writer failure");
            }
        };
        try {
            tr.translate("any", failingWriter);
            fail("Expected IOException from translator");
        } catch (IOException e) {
            assertEquals("forced failure", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Tests for CharSequenceTranslator.with(...)
    // -------------------------------------------------------------------------

    @Test
    public void testWith_mergesTranslatorsInCorrectOrder() {
        CharSequenceTranslator aToB = new AtoBTranslator(); // a -> b
        CharSequenceTranslator bToC = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (input.charAt(index) == 'b') {
                    out.write('c');
                    return 1;
                }
                return 0;
            }
        }; // b -> c

        CharSequenceTranslator merged = aToB.with(bToC); // a -> b then b -> c

        String result = merged.translate("ababa");
        // Step 1: a->b, b stays b, a->b, b stays b, a->b => "bbbbb"
        // Step 2 (bToC) runs after aToB: all 'b' become 'c' => "ccccc"
        assertEquals("ccccc", result);
    }

    @Test
    public void testWith_withNoAdditionalTranslatorsReturnsSameInstanceWrapped() {
        CharSequenceTranslator aToB = new AtoBTranslator();
        CharSequenceTranslator merged = aToB.with(); // no extra translators
        assertNotSame(aToB, merged); // should be a new AggregateTranslator
        // Behaviour must be identical to the original
        assertEquals(aToB.translate("aaa"), merged.translate("aaa"));
    }

    // -------------------------------------------------------------------------
    // Tests for static CharSequenceTranslator.hex(int)
    // -------------------------------------------------------------------------

    @Test
    public void testHex_basicValues() {
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("1A", CharSequenceTranslator.hex(0x1A));
        assertEquals("FF", CharSequenceTranslator.hex(255));
        assertEquals("7FFFFFFF", CharSequenceTranslator.hex(Integer.MAX_VALUE));
    }

    @Test
    public void testHex_negativeValue() {
        // Integer.toHexString(-1) => "ffffffff", upper‑cased => "FFFFFFFF"
        assertEquals("FFFFFFFF", CharSequenceTranslator.hex(-1));
    }

    @Test
    public void testHex_localeIndependence() {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH);
            assertEquals("ABCDEF", CharSequenceTranslator.hex(0xABCDEF));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }
}
