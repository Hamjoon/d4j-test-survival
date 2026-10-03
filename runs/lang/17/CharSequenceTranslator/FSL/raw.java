###Test START##
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

/**
 * Unit tests for {@link CharSequenceTranslator}.
 */
public class CharSequenceTranslatorTest {

    /** Simple translator that replaces a single character with a given string. */
    private static class SimpleReplaceTranslator extends CharSequenceTranslator {
        private final char target;
        private final String replacement;

        SimpleReplaceTranslator(char target, String replacement) {
            this.target = target;
            this.replacement = replacement;
        }

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == target) {
                out.write(replacement);
                return 1;               // consumed one code‑point
            }
            return 0;                   // not handled
        }
    }

    /** Translator that recognises a surrogate‑pair character (emoji) and replaces it. */
    private static class EmojiTranslator extends CharSequenceTranslator {
        private static final int SMILE = 0x1F600; // 😀
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == SMILE) {
                out.write("[SMILE]");
                return 1;               // one code‑point (2 chars) consumed
            }
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Tests for translate(CharSequence) – the convenience method
    // -------------------------------------------------------------------------

    @Test
    public void testTranslateConvenience_nullInput() {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('a', "b");
        assertNull("translate(null) must return null", translator.translate((CharSequence) null));
    }

    @Test
    public void testTranslateConvenience_basicReplacement() {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('a', "b");
        assertEquals("bb", translator.translate("aba"));
    }

    @Test
    public void testTranslateConvenience_surrogatePairHandling() {
        CharSequenceTranslator translator = new EmojiTranslator();
        String input = "x\uD83D\uDE00y"; // x😀y
        assertEquals("x[SMILE]y", translator.translate(input));
    }

    // -------------------------------------------------------------------------
    // Tests for translate(CharSequence, Writer)
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void testTranslateToWriter_nullWriter() throws IOException {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('a', "b");
        translator.translate("test", (Writer) null);
    }

    @Test
    public void testTranslateToWriter_nullInput() throws IOException {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('a', "b");
        StringWriter writer = new StringWriter();
        translator.translate((CharSequence) null, writer);
        assertEquals("", writer.toString()); // nothing written
    }

    @Test
    public void testTranslateToWriter_passthroughWhenNotConsumed() throws IOException {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('z', "Z");
        StringWriter writer = new StringWriter();
        translator.translate("abc", writer);
        assertEquals("abc", writer.toString());
    }

    @Test
    public void testTranslateToWriter_consumesAndAdvancesCorrectly() throws IOException {
        // a → X, emoji → [SMILE]
        CharSequenceTranslator translator = new CharSequenceTranslator() {
            private final SimpleReplaceTranslator aToX = new SimpleReplaceTranslator('a', "X");
            private final EmojiTranslator emoji = new EmojiTranslator();

            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                int consumed = aToX.translate(input, index, out);
                if (consumed != 0) {
                    return consumed;
                }
                return emoji.translate(input, index, out);
            }
        };

        String input = "a\uD83D\uDE00b"; // a😀b
        StringWriter writer = new StringWriter();
        translator.translate(input, writer);
        assertEquals("X[SMILE]b", writer.toString());
    }

    // -------------------------------------------------------------------------
    // Tests for with(...) – merging translators
    // -------------------------------------------------------------------------

    @Test
    public void testWith_mergesTranslatorsInOrder() {
        CharSequenceTranslator translatorA = new SimpleReplaceTranslator('a', "1");
        CharSequenceTranslator translatorB = new SimpleReplaceTranslator('b', "2");
        CharSequenceTranslator merged = translatorA.with(translatorB);

        // The merged instance should be an AggregateTranslator (implementation detail)
        assertTrue("Merged translator should be instance of AggregateTranslator",
                merged instanceof AggregateTranslator);

        // Verify that both replacements happen
        assertEquals("12c", merged.translate("abc"));
    }

    @Test
    public void testWith_multipleTranslators() {
        CharSequenceTranslator t1 = new SimpleReplaceTranslator('x', "X");
        CharSequenceTranslator t2 = new SimpleReplaceTranslator('y', "Y");
        CharSequenceTranslator t3 = new SimpleReplaceTranslator('z', "Z");

        CharSequenceTranslator merged = t1.with(t2, t3);
        assertEquals("XYZ", merged.translate("xyz"));
    }

    // -------------------------------------------------------------------------
    // Tests for static hex(int) method
    // -------------------------------------------------------------------------

    @Test
    public void testHex_basicValues() {
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("7F", CharSequenceTranslator.hex(0x7F));
        assertEquals("80", CharSequenceTranslator.hex(0x80));
        assertEquals("FF", CharSequenceTranslator.hex(0xFF));
        assertEquals("100", CharSequenceTranslator.hex(0x100));
    }

    @Test
    public void testHex_surrogateCodePoint() {
        // U+1F601 (😁)
        assertEquals("1F601", CharSequenceTranslator.hex(0x1F601));
    }

    @Test
    public void testHex_negativeValue() {
        // The method does not guard against negatives – it follows Integer.toHexString behaviour
        assertEquals("-1", CharSequenceTranslator.hex(-1));
    }
}
###Test END##