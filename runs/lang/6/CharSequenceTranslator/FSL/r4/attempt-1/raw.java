###Test START##
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

/**
 * Unit tests for {@link CharSequenceTranslator}.
 *
 * <p>These tests cover:
 * <ul>
 *   <li>the {@code translate(CharSequence)} convenience method</li>
 *   <li>the {@code translate(CharSequence, Writer)} algorithm (null handling,
 *       surrogate‑pair handling, zero‑consumption path, etc.)</li>
 *   <li>the {@code with(...)} merger functionality</li>
 *   <li>the static {@code hex(int)} helper</li>
 * </ul>
 * </p>
 */
public class CharSequenceTranslatorTest {

    /**
     * Simple translator that replaces the character {@code 'a'} with {@code 'b'}.
     */
    private static final class ReplaceATranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            if (input.charAt(index) == 'a') {
                out.write('b');
                return 1;                 // consumed one code‑point
            }
            return 0;                     // let the base class handle it
        }
    }

    /**
     * Translator that replaces the musical G‑clef (U+1D11E) surrogate pair with the
     * string {@code "G"}.
     */
    private static final class GclefTranslator extends CharSequenceTranslator {
        private static final int G_CLEF = 0x1D11E; // 𝄞

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == G_CLEF) {
                out.write('G');
                // surrogate pair length is 2 chars
                return Character.charCount(cp);
            }
            return 0;
        }
    }

    /**
     * Translator that never consumes any input – forces the fallback path that
     * writes the original characters.
     */
    private static final class NoOpTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) {
            return 0;    // never consumes
        }
    }

    @Test
    public void testTranslate_NullInput_ReturnsNull() {
        CharSequenceTranslator translator = new ReplaceATranslator();
        assertNull("translate(null) should return null", translator.translate((CharSequence) null));
    }

    @Test
    public void testTranslate_SimpleReplacement() {
        CharSequenceTranslator translator = new ReplaceATranslator();
        String result = translator.translate("abracadabra");
        // Every 'a' becomes 'b'
        assertEquals("bbrbcbdbbrb", result);
    }

    @Test
    public void testTranslate_WithWriter_NullWriter_Throws() throws IOException {
        CharSequenceTranslator translator = new ReplaceATranslator();
        try {
            translator.translate("test", null);
            fail("Expected IllegalArgumentException when Writer is null");
        } catch (IllegalArgumentException e) {
            assertEquals("The Writer must not be null", e.getMessage());
        }
    }

    @Test
    public void testTranslate_WithWriter_NullInput_WritesNothing() throws IOException {
        CharSequenceTranslator translator = new ReplaceATranslator();
        StringWriter out = new StringWriter();
        translator.translate(null, out);
        assertEquals("Writer should remain empty for null input", "", out.toString());
    }

    @Test
    public void testTranslate_WithWriter_ZeroConsumeWritesOriginalChar() throws IOException {
        CharSequenceTranslator translator = new NoOpTranslator();
        StringWriter out = new StringWriter();
        translator.translate("xyz", out);
        assertEquals("xyz", out.toString());
    }

    @Test
    public void testTranslate_WithWriter_SurrogatePairConsumedCorrectly() throws IOException {
        CharSequenceTranslator translator = new GclefTranslator();
        // String containing: 'a' + G clef (U+1D11E) + 'b'
        String input = "a" + new String(Character.toChars(0x1D11E)) + "b";
        StringWriter out = new StringWriter();
        translator.translate(input, out);
        // Expected: 'a' (unchanged) + 'G' (replacement) + 'b' (unchanged)
        assertEquals("aGb", out.toString());
    }

    @Test
    public void testTranslate_WithWriter_SurrogatePairNotConsumed_PassesThrough() throws IOException {
        // NoOpTranslator does not consume; the base class must write the surrogate pair unchanged.
        CharSequenceTranslator translator = new NoOpTranslator();
        String input = new String(Character.toChars(0x1D11E)); // just the G clef
        StringWriter out = new StringWriter();
        translator.translate(input, out);
        assertEquals(input, out.toString());
    }

    @Test
    public void testWith_MergesTwoTranslators_SequentialApplication() {
        // First translator turns 'x' -> 'y', second turns 'y' -> 'z'.
        CharSequenceTranslator first = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (input.charAt(index) == 'x') {
                    out.write('y');
                    return 1;
                }
                return 0;
            }
        };
        CharSequenceTranslator second = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (input.charAt(index) == 'y') {
                    out.write('z');
                    return 1;
                }
                return 0;
            }
        };
        CharSequenceTranslator merged = first.with(second);
        assertNotNull("Merged translator should not be null", merged);
        String result = merged.translate("x");
        // Expected flow: first maps 'x'->'y', then second maps that 'y'->'z'
        assertEquals("z", result);
    }

    @Test
    public void testHex_UpperCaseAndCorrectConversion() {
        assertEquals("FF", CharSequenceTranslator.hex(255));
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("1A2B3C", CharSequenceTranslator.hex(0x1A2B3C));
        // Test a supplementary code‑point (requires 2 chars in UTF‑16)
        assertEquals("1D11E", CharSequenceTranslator.hex(0x1D11E));
    }

    @Test
    public void testTranslate_ComplexMixedScenario() {
        // Combine a translator that replaces "ab" with "X" and one that replaces 'c' with "Y".
        CharSequenceTranslator replaceAB = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (index + 1 < input.length()
                        && input.charAt(index) == 'a'
                        && input.charAt(index + 1) == 'b') {
                    out.write('X');
                    return 2; // consumed two chars
                }
                return 0;
            }
        };
        CharSequenceTranslator replaceC = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (input.charAt(index) == 'c') {
                    out.write('Y');
                    return 1;
                }
                return 0;
            }
        };
        CharSequenceTranslator merged = replaceAB.with(replaceC);
        // Input contains overlapping patterns: "ab" + "c" => "X" + "Y"
        String result = merged.translate("abcab");
        // Expected: "X" (for first "ab") + "Y" (for "c") + "X" (for second "ab")
        assertEquals("XYX", result);
    }
}
###Test END##