package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

/**
 * Unit tests for {@link CharSequenceTranslator}.
 * <p>
 * The abstract class is exercised through a set of minimal concrete implementations
 * that allow us to verify the behaviour of the final helper methods, the {@code with}
 * combinator and the static {@code hex} utility.
 * </p>
 */
public class CharSequenceTranslatorTest {

    /** 
     * Translator that never performs a translation – it always returns {@code 0}
     * so the base class will fall‑back to copying the original character(s).
     */
    private static final class IdentityTranslator extends CharSequenceTranslator {
        @Override
        public int translate(final CharSequence input, final int index, final Writer out) {
            // Force the fallback path
            return 0;
        }
    }

    /** 
     * Simple translator that replaces the character {@code 'a'} with {@code 'x'}.
     */
    private static final class AtoXTranslator extends CharSequenceTranslator {
        @Override
        public int translate(final CharSequence input, final int index, final Writer out) throws IOException {
            if (input.charAt(index) == 'a') {
                out.write('x');
                // one code‑point consumed
                return 1;
            }
            return 0;
        }
    }

    /** 
     * Simple translator that replaces the character {@code 'b'} with {@code 'y'}.
     */
    private static final class BtoYTranslator extends CharSequenceTranslator {
        @Override
        public int translate(final CharSequence input, final int index, final Writer out) throws IOException {
            if (input.charAt(index) == 'b') {
                out.write('y');
                return 1;
            }
            return 0;
        }
    }

    /** 
     * Translator that recognises the Unicode supplementary character U+1F600 (😀)
     * and replaces it with the word {@code "SMILE"}.
     */
    private static final class SmileyTranslator extends CharSequenceTranslator {
        private static final int SMILEY_CODEPOINT = 0x1F600; // 😀

        @Override
        public int translate(final CharSequence input, final int index, final Writer out) throws IOException {
            final int cp = Character.codePointAt(input, index);
            if (cp == SMILEY_CODEPOINT) {
                out.write("SMILE");
                // one code‑point consumed (even though it consists of two char units)
                return 1;
            }
            return 0;
        }
    }

    /* ---------------------------------------------------------------------- */
    /*  Tests for the public helper translate(CharSequence) method               */
    /* ---------------------------------------------------------------------- */

    @Test
    public void translate_nullInput_returnsNull() {
        CharSequenceTranslator translator = new IdentityTranslator();
        assertNull("translate(null) must return null", translator.translate((CharSequence) null));
    }

    @Test
    public void translate_emptyString_returnsEmpty() {
        CharSequenceTranslator translator = new IdentityTranslator();
        assertEquals("Empty input should produce empty output", "", translator.translate(""));
    }

    @Test
    public void translate_identityCopiesAllCharacters() {
        CharSequenceTranslator translator = new IdentityTranslator();
        String source = "The quick brown 🦊 jumps over 13 lazy dogs.";
        assertEquals(source, translator.translate(source));
    }

    @Test
    public void translate_simpleReplacement() {
        CharSequenceTranslator translator = new AtoXTranslator();
        assertEquals("xbc", translator.translate("abc"));
        assertEquals("xyz", translator.translate("abz"));
        // characters other than 'a' must be left untouched
        assertEquals("hello", translator.translate("hello"));
    }

    @Test
    public void translate_supplementaryCharacter_isHandledCorrectly() {
        CharSequenceTranslator translator = new SmileyTranslator();
        String input = "Smile: \uD83D\uDE00!"; // "Smile: 😀!"
        String expected = "Smile: SMILE!";
        assertEquals(expected, translator.translate(input));
    }

    /* ---------------------------------------------------------------------- */
    /*  Tests for translate(CharSequence, Writer)                               */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void translate_withNullWriter_throwsException() throws IOException {
        CharSequenceTranslator translator = new IdentityTranslator();
        translator.translate("test", (Writer) null);
    }

    @Test
    public void translate_writerReceivesSameOutput_asStringVersion() throws IOException {
        CharSequenceTranslator translator = new AtoXTranslator();
        StringWriter sw = new StringWriter();
        translator.translate("banana", sw);
        assertEquals("xbxnxn", sw.toString());
    }

    @Test
    public void translate_fallbackCopiesSurrogatePairsWhenTranslatorReturnsZero() throws IOException {
        // The translator never matches – it always returns 0, forcing the fallback copy.
        CharSequenceTranslator translator = new IdentityTranslator();
        String input = "Hello \uD83D\uDE00 World"; // contains 😀 (surrogate pair)
        StringWriter sw = new StringWriter();
        translator.translate(input, sw);
        assertEquals(input, sw.toString());
    }

    /* ---------------------------------------------------------------------- */
    /*  Tests for the combinator 'with'                                         */
    /* ---------------------------------------------------------------------- */

    @Test
    public void with_combinesTranslatorsInCorrectOrder() {
        CharSequenceTranslator combined = new AtoXTranslator()
                .with(new BtoYTranslator()); // first A->X, then B->Y

        // "ab" -> first AtoX makes "xb", then BtoY makes "xy"
        assertEquals("xy", combined.translate("ab"));
        // characters not covered by any translator stay unchanged
        assertEquals("c", combined.translate("c"));
    }

    @Test
    public void with_multipleTranslatorsPreserveAllReplacements() {
        CharSequenceTranslator combined = new AtoXTranslator()
                .with(new BtoYTranslator(), new SmileyTranslator());

        // test all three behaviours together
        String source = "ab\uD83D\uDE00c"; // "ab😀c"
        String expected = "xySMILEc";
        assertEquals(expected, combined.translate(source));
    }

    /* ---------------------------------------------------------------------- */
    /*  Tests for the static hex(int) method                                    */
    /* ---------------------------------------------------------------------- */

    @Test
    public void hex_basicValues_areUpperCase() {
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("A", CharSequenceTranslator.hex(10));
        assertEquals("10", CharSequenceTranslator.hex(16));
        assertEquals("FF", CharSequenceTranslator.hex(255));
    }

    @Test
    public void hex_surrogateCodepoint_isCorrect() {
        // U+1F600 (😀) -> "1F600"
        assertEquals("1F600", CharSequenceTranslator.hex(0x1F600));
    }

    @Test
    public void hex_negativeNumber_returnsTwoComplementRepresentation() {
        // Java's Integer.toHexString uses two's complement for negatives.
        assertEquals("FFFFFFFF", CharSequenceTranslator.hex(-1));
    }

    @Test
    public void hex_consistentWithLocaleIndependentUpperCase() {
        // Force a locale that would normally produce lower‑case letters, e.g., Turkish.
        // The method explicitly uses Locale.ENGLISH, so the result must stay upper case.
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("ABCDEF", CharSequenceTranslator.hex(0xABCDEF));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }
}
