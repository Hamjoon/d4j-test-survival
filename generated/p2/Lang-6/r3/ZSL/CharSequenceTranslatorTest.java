/**
 * Comprehensive JUnit‑4 test suite for {@link org.apache.commons.lang3.text.translate.CharSequenceTranslator}.
 *
 * The tests cover:
 * <ul>
 *   <li>the {@code translate(CharSequence)} convenience method (null handling, empty input, normal translation, IOException wrapping)</li>
 *   <li>the {@code translate(CharSequence, Writer)} method (null writer, surrogate‑pair handling, code‑point consumption)</li>
 *   <li>the {@code with(...)} merger method (order of translators, combined effect)</li>
 *   <li>the static {@code hex(int)} utility (hex conversion, upper‑case, supplementary characters)</li>
 * </ul>
 *
 * A small concrete {@link CharSequenceTranslator} implementation is provided
 * ( {@code SimpleReplacer} ) to exercise the abstract contract.
 */
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

/**
 * Test class for {@link CharSequenceTranslator}.
 */
public class CharSequenceTranslatorTest {

    /**
     * Simple concrete translator used by many test cases.
     * <p>
     * Behaviour:
     * <ul>
     *   <li>'a' → "b"</li>
     *   <li>U+1F600 (GRINNING FACE) → "SMILE"</li>
     *   <li>otherwise → no translation (return 0)</li>
     * </ul>
     */
    private static class SimpleReplacer extends CharSequenceTranslator {
        private static final int GRINNING_FACE = 0x1F600; // 😀 (surrogate pair)

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int codepoint = Character.codePointAt(input, index);
            if (codepoint == 'a') {
                out.write('b');
                return 1; // one code point consumed
            }
            if (codepoint == GRINNING_FACE) {
                out.write("SMILE");
                return Character.charCount(codepoint); // consumes the surrogate pair
            }
            // no translation performed
            return 0;
        }
    }

    /**
     * Translator that replaces 'b' → "c".
     */
    private static class BtoCTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == 'b') {
                out.write('c');
                return 1;
            }
            return 0;
        }
    }

    /**
     * Translator that ALWAYS throws an {@link IOException}. Used to verify that
     * {@link CharSequenceTranslator#translate(CharSequence)} wraps the exception
     * into a {@link RuntimeException}.
     */
    private static class IOExceptionThrower extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            throw new IOException("forced");
        }
    }

    /* ----------------------------------------------------------------------
     * Tests for the convenience method translate(CharSequence)
     * ---------------------------------------------------------------------- */

    @Test
    public void testTranslateString_nullInput() {
        CharSequenceTranslator translator = new SimpleReplacer();
        assertNull("translate(null) must return null", translator.translate((CharSequence) null));
    }

    @Test
    public void testTranslateString_emptyInput() {
        CharSequenceTranslator translator = new SimpleReplacer();
        assertEquals("Empty input should yield empty output", "", translator.translate(""));
    }

    @Test
    public void testTranslateString_simpleReplacement() {
        CharSequenceTranslator translator = new SimpleReplacer();
        assertEquals("cbbt", translator.translate("cabt"));
        // only 'a' should become 'b', everything else stays unchanged
        assertEquals("bcd", translator.translate("acd"));
    }

    @Test
    public void testTranslateString_surrogatePairReplacement() {
        CharSequenceTranslator translator = new SimpleReplacer();
        // Input contains the Unicode grinning face emoji (U+1F600)
        String input = "test\uD83D\uDE00end";
        String expected = "testSMILEend";
        assertEquals(expected, translator.translate(input));
    }

    @Test
    public void testTranslateString_ioExceptionWrapped() {
        CharSequenceTranslator translator = new IOExceptionThrower();
        try {
            translator.translate("anything");
            fail("Expected RuntimeException because the underlying translate threw IOException");
        } catch (RuntimeException e) {
            assertTrue("Cause should be IOException", e.getCause() instanceof IOException);
            assertEquals("forced", e.getCause().getMessage());
        }
    }

    /* ----------------------------------------------------------------------
     * Tests for translate(CharSequence, Writer)
     * ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testTranslateWriter_nullWriter() throws IOException {
        CharSequenceTranslator translator = new SimpleReplacer();
        translator.translate("abc", (Writer) null);
    }

    @Test
    public void testTranslateWriter_nullInputDoesNothing() throws IOException {
        CharSequenceTranslator translator = new SimpleReplacer();
        StringWriter out = new StringWriter();
        translator.translate((CharSequence) null, out);
        assertEquals("", out.toString());
    }

    @Test
    public void testTranslateWriter_consumesCorrectNumberOfCodePoints() throws IOException {
        CharSequenceTranslator translator = new SimpleReplacer();
        String input = "a\uD83D\uDE00x"; // 'a' + emoji + 'x'
        StringWriter out = new StringWriter();
        translator.translate(input, out);
        // a -> b, emoji -> SMILE, x unchanged
        assertEquals("bSMILEx", out.toString());
    }

    @Test
    public void testTranslateWriter_noTranslationWritesOriginalChar() throws IOException {
        CharSequenceTranslator translator = new SimpleReplacer();
        String input = "xyz";
        StringWriter out = new StringWriter();
        translator.translate(input, out);
        // No character is recognized, so output must be identical
        assertEquals(input, out.toString());
    }

    /* ----------------------------------------------------------------------
     * Tests for the merger method with(...)
     * ---------------------------------------------------------------------- */

    @Test
    public void testWith_mergesTranslatorsInOrder() {
        CharSequenceTranslator aToB = new SimpleReplacer(); // 'a' → 'b'
        CharSequenceTranslator bToC = new BtoCTranslator(); // 'b' → 'c'

        CharSequenceTranslator merged = aToB.with(bToC);
        // The merged translator should first turn 'a' into 'b', then turn that 'b' into 'c'.
        assertEquals("c", merged.translate("a"));
        // Characters not affected by the first translator but matched by the second should still be transformed.
        assertEquals("c", merged.translate("b"));
        // Unrelated characters stay unchanged.
        assertEquals("d", merged.translate("d"));
    }

    @Test
    public void testWith_multipleTranslatorsChain() {
        CharSequenceTranslator aToB = new SimpleReplacer(); // a->b
        CharSequenceTranslator bToC = new BtoCTranslator(); // b->c
        CharSequenceTranslator cToD = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                int cp = Character.codePointAt(input, index);
                if (cp == 'c') {
                    out.write('d');
                    return 1;
                }
                return 0;
            }
        };

        CharSequenceTranslator chain = aToB.with(bToC, cToD);
        // 'a' → b → c → d
        assertEquals("d", chain.translate("a"));
        // Directly feeding 'b' also ends up as 'd' because the chain still processes it.
        assertEquals("d", chain.translate("b"));
        // 'c' → d
        assertEquals("d", chain.translate("c"));
        // Anything else unchanged
        assertEquals("x", chain.translate("x"));
    }

    /* ----------------------------------------------------------------------
     * Tests for the static hex(int) utility
     * ---------------------------------------------------------------------- */

    @Test
    public void testHex_basicValues() {
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("7F", CharSequenceTranslator.hex(0x7F));
        assertEquals("FF", CharSequenceTranslator.hex(255));
        assertEquals("10", CharSequenceTranslator.hex(16));
    }

    @Test
    public void testHex_upperCaseAlways() {
        assertEquals("ABCD", CharSequenceTranslator.hex(0xABCD));
        assertEquals("ABCD", CharSequenceTranslator.hex(0xabcd)); // input int is the same, output must be upper case
    }

    @Test
    public void testHex_surrogatePairCodepoint() {
        // U+1F600 (😀)
        assertEquals("1F600", CharSequenceTranslator.hex(0x1F600));
    }

    @Test
    public void testHex_negativeValue() {
        // Integer.toHexString handles negative numbers using two's complement;
        // the method under test mirrors that behaviour.
        assertEquals(Integer.toHexString(-1).toUpperCase(), CharSequenceTranslator.hex(-1));
    }
}
