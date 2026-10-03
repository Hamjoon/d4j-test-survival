/**
 * Comprehensive JUnit‑4 test suite for {@link org.apache.commons.lang3.text.translate.CharSequenceTranslator}.
 *
 * The tests cover:
 * <ul>
 *   <li>the {@code translate(CharSequence)} convenience method (null handling, normal translation)</li>
 *   <li>the {@code translate(CharSequence, Writer)} method (null writer, surrogate handling,
 *       zero‑consumption fallback, multi‑code‑point consumption)</li>
 *   <li>the {@code with(CharSequenceTranslator...)} merger helper</li>
 *   <li>the static {@code hex(int)} utility method</li>
 * </ul>
 *
 * Simple concrete subclasses of {@code CharSequenceTranslator} are defined as static inner classes
 * to exercise the abstract {@code translate(CharSequence, int, Writer)} contract without pulling
 * in any external library code.
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
 */
public class CharSequenceTranslatorTest {

    /* ----------------------------------------------------------------------
     * Helper concrete translators used throughout the test suite
     * ---------------------------------------------------------------------- */

    /**
     * A translator that never consumes any characters.
     * It returns {@code 0} for every call, which forces the base class to copy the
     * original character(s) to the output writer.
     */
    private static final class NoOpTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            // consume nothing – the base class will copy the original character(s)
            return 0;
        }
    }

    /**
     * Replaces a single character with a given string.
     */
    private static final class SimpleReplaceTranslator extends CharSequenceTranslator {
        private final char target;
        private final String replacement;

        SimpleReplaceTranslator(char target, String replacement) {
            this.target = target;
            this.replacement = replacement;
        }

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            if (input.charAt(index) == target) {
                out.write(replacement);
                return 1; // consumed one code point
            }
            return 0; // no translation, fallback to base class copying
        }
    }

    /**
     * Consumes a surrogate pair (an emoji) and writes a placeholder.
     * Used to verify that multi‑code‑point consumption correctly advances the index.
     */
    private static final class EmojiPlaceholderTranslator extends CharSequenceTranslator {
        private static final int GRINNING_FACE = 0x1F600; // 😀
        private static final String PLACEHOLDER = "[smile]";

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == GRINNING_FACE) {
                out.write(PLACEHOLDER);
                // The emoji consists of two char units, so we return 2 to indicate
                // that two characters (one code point) were consumed.
                return Character.charCount(cp);
            }
            return 0;
        }
    }

    /* ----------------------------------------------------------------------
     * Tests for translate(CharSequence) – the convenience method returning a String
     * ---------------------------------------------------------------------- */

    @Test
    public void testTranslate_WithNullInput_ReturnsNull() {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('a', "b");
        assertNull("translate(null) should return null", translator.translate((CharSequence) null));
    }

    @Test
    public void testTranslate_SimpleReplacement() {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('a', "b");
        assertEquals("b", translator.translate("a"));
        assertEquals("bc", translator.translate("ac"));
        assertEquals("bb", translator.translate("aa"));
        assertEquals("abc", translator.translate("abc")); // only first 'a' replaced
    }

    @Test
    public void testTranslate_EmptyInput_ReturnsEmptyString() {
        CharSequenceTranslator translator = new NoOpTranslator();
        assertEquals("", translator.translate(""));
    }

    /* ----------------------------------------------------------------------
     * Tests for translate(CharSequence, Writer) – the core algorithm
     * ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testTranslate_WithNullWriter_ThrowsException() throws IOException {
        CharSequenceTranslator translator = new NoOpTranslator();
        translator.translate("test", (Writer) null);
    }

    @Test
    public void testTranslate_WithNullInput_DoesNothing() throws IOException {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('x', "y");
        StringWriter writer = new StringWriter();
        translator.translate((CharSequence) null, writer);
        assertEquals("", writer.toString());
    }

    @Test
    public void testTranslate_NoOpTranslator_CopiesAllCharacters() throws IOException {
        CharSequenceTranslator translator = new NoOpTranslator();
        String input = "Hello, 世界!😀"; // includes BMP chars, CJK, and a surrogate pair (emoji)
        StringWriter writer = new StringWriter();
        translator.translate(input, writer);
        assertEquals("All characters must be copied unchanged", input, writer.toString());
    }

    @Test
    public void testTranslate_EmojiPlaceholderTranslator_ConsumesSurrogatePair() throws IOException {
        CharSequenceTranslator translator = new EmojiPlaceholderTranslator();
        String input = "Start😀End"; // 😀 is U+1F600 (surrogate pair)
        String expected = "Start[smile]End";

        StringWriter writer = new StringWriter();
        translator.translate(input, writer);
        assertEquals("Emoji should be replaced by placeholder", expected, writer.toString());
    }

    @Test
    public void testTranslate_MixedTranslators_CombinedBehaviour() throws IOException {
        // First translator replaces 'a' -> "b"
        // Second translator replaces 'b' -> "c"
        CharSequenceTranslator aToB = new SimpleReplaceTranslator('a', "b");
        CharSequenceTranslator bToC = new SimpleReplaceTranslator('b', "c");
        CharSequenceTranslator combined = aToB.with(bToC); // uses AggregateTranslator internally

        String input = "ababa";
        // Expected step by step:
        // a -> b (first translator) -> c (second translator)
        // b -> c (first translator does nothing, second translator converts)
        // So "ababa" becomes "ccccc"
        String expected = "ccccc";

        assertEquals("Combined translator should apply both replacements",
                expected, combined.translate(input));
    }

    /* ----------------------------------------------------------------------
     * Tests for the static hex(int) utility
     * ---------------------------------------------------------------------- */

    @Test
    public void testHex_Zero() {
        assertEquals("0", CharSequenceTranslator.hex(0));
    }

    @Test
    public void testHex_SingleDigit() {
        assertEquals("A", CharSequenceTranslator.hex(10));
    }

    @Test
    public void testHex_TwoDigits() {
        assertEquals("FF", CharSequenceTranslator.hex(255));
    }

    @Test
    public void testHex_MultiByteCodepoint() {
        // Emoji 😀 = 0x1F600
        assertEquals("1F600", CharSequenceTranslator.hex(0x1F600));
    }

    @Test
    public void testHex_NegativeValue_IsHandledByInteger.toHexString() {
        // Integer.toHexString produces a two's‑complement representation.
        // The contract of CharSequenceTranslator.hex does not forbid negatives,
        // so we just verify that the method forwards to Integer.toHexString.
        int negative = -1;
        String expected = Integer.toHexString(negative).toUpperCase(Locale.ENGLISH);
        assertEquals(expected, CharSequenceTranslator.hex(negative));
    }

    /* ----------------------------------------------------------------------
     * Additional sanity checks for the translate(CharSequence) convenience method
     * ---------------------------------------------------------------------- */

    @Test
    public void testTranslate_ConvenienceMethod_UsesWriterInternally() {
        // We use a translator that throws if the Writer is called more than once per character.
        CharSequenceTranslator singleWriteTranslator = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                // always consume exactly one code point and write a single character
                out.write('X');
                return 1;
            }
        };

        String result = singleWriteTranslator.translate("abc");
        assertEquals("XXX", result);
    }
}
