###Test START##
```java
/*
 * CharSequenceTranslatorTest.java
 *
 * Comprehensive JUnit4 test suite for {@link org.apache.commons.lang3.text.translate.CharSequenceTranslator}.
 *
 * The tests cover:
 *  - translate(CharSequence) handling of null, empty and regular strings.
 *  - translate(CharSequence, Writer) handling of null arguments, proper consumption logic,
 *    surrogate pair handling, and exception propagation.
 *  - with(...) merging of translators and order of execution.
 *  - static hex(int) correctness.
 *
 * The test file is self‑contained and defines simple concrete translators
 * used solely for validation.
 */

package org.apache.commons.lang3.text.translate;

import org.junit.Test;
import org.junit.Assert;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Test suite for {@link CharSequenceTranslator}.
 */
public class CharSequenceTranslatorTest {

    /**
     * Simple translator that replaces the character 'a' with "A".
     */
    private static class SimpleATranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            if (input.charAt(index) == 'a') {
                out.write('A');
                return 1; // consumed one code point
            }
            return 0; // let the base class copy the original character
        }
    }

    /**
     * Simple translator that replaces the character 'b' with "B".
     */
    private static class SimpleBTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            if (input.charAt(index) == 'b') {
                out.write('B');
                return 1;
            }
            return 0;
        }
    }

    /**
     * Translator that replaces the emoji 😀 (U+1F600) with "[SMILE]".
     */
    private static class EmojiTranslator extends CharSequenceTranslator {
        private static final int SMILE_CODEPOINT = 0x1F600; // 😀

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == SMILE_CODEPOINT) {
                out.write("[SMILE]");
                // Return the number of *code points* consumed, which for a surrogate pair is 2 chars.
                return Character.charCount(cp);
            }
            return 0;
        }
    }

    /**
     * Translator that never consumes input and never writes anything.
     * Used to test the “consumed == 0” fallback path.
     */
    private static class NoOpTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) {
            return 0; // never consumes
        }
    }

    /**
     * Translator that deliberately throws an {@link IOException}.
     */
    private static class ThrowingTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            throw new IOException("forced exception");
        }
    }

    /**
     * Writer that throws an {@link IOException} on any write operation.
     */
    private static class FailingWriter extends Writer {
        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            throw new IOException("writer failure");
        }

        @Override
        public void flush() throws IOException {
            // no‑op
        }

        @Override
        public void close() throws IOException {
            // no‑op
        }
    }

    // -------------------------------------------------------------------------
    // Tests for translate(CharSequence) – the convenience method
    // -------------------------------------------------------------------------

    @Test
    public void testTranslateString_NullInput_ReturnsNull() {
        CharSequenceTranslator translator = new SimpleATranslator();
        Assert.assertNull("translate(null) should return null", translator.translate((CharSequence) null));
    }

    @Test
    public void testTranslateString_EmptyString() {
        CharSequenceTranslator translator = new SimpleATranslator();
        Assert.assertEquals("Empty input should produce empty output", "", translator.translate(""));
    }

    @Test
    public void testTranslateString_SimpleReplacement() {
        CharSequenceTranslator translator = new SimpleATranslator();
        String input = "abracadabra";
        String expected = "AbrAcAdAbrA"; // every 'a' becomes 'A'
        Assert.assertEquals("Simple 'a' → 'A' replacement failed", expected, translator.translate(input));
    }

    @Test
    public void testTranslateString_WithSurrogatePair() {
        CharSequenceTranslator translator = new EmojiTranslator();
        // The string contains a smiley emoji (U+1F600) surrounded by normal text.
        String input = "Hello \uD83D\uDE00 World";
        String expected = "Hello [SMILE] World";
        Assert.assertEquals("Emoji should be replaced with [SMILE]", expected, translator.translate(input));
    }

    @Test
    public void testTranslateString_ConsumedZeroFallsBackToCopy() {
        CharSequenceTranslator translator = new NoOpTranslator();
        String input = "xyz";
        // NoOpTranslator never consumes; the base class should copy the original characters.
        Assert.assertEquals("When translator consumes nothing, output must equal input", input, translator.translate(input));
    }

    // -------------------------------------------------------------------------
    // Tests for translate(CharSequence, Writer)
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void testTranslateWriter_NullWriter_ThrowsException() throws IOException {
        CharSequenceTranslator translator = new SimpleATranslator();
        translator.translate("test", (Writer) null);
    }

    @Test
    public void testTranslateWriter_NullInput_WritesNothing() throws IOException {
        CharSequenceTranslator translator = new SimpleATranslator();
        StringWriter writer = new StringWriter();
        translator.translate((CharSequence) null, writer);
        Assert.assertEquals("Writer should remain empty when input is null", "", writer.toString());
    }

    @Test
    public void testTranslateWriter_SimpleReplacement() throws IOException {
        CharSequenceTranslator translator = new SimpleATranslator();
        StringWriter writer = new StringWriter();
        translator.translate("banana", writer);
        Assert.assertEquals("banana".replace('a', 'A'), writer.toString());
    }

    @Test
    public void testTranslateWriter_ConsumeSurrogatePair() throws IOException {
        CharSequenceTranslator translator = new EmojiTranslator();
        StringWriter writer = new StringWriter();
        // Input: "Start😀End"
        String input = "Start\uD83D\uDE00End";
        translator.translate(input, writer);
        Assert.assertEquals("Start[SMILE]End", writer.toString());
    }

    @Test
    public void testTranslateWriter_ConsumedZeroCopiesOriginalChar() throws IOException {
        CharSequenceTranslator translator = new NoOpTranslator();
        StringWriter writer = new StringWriter();
        translator.translate("abc", writer);
        Assert.assertEquals("abc", writer.toString());
    }

    @Test(expected = IOException.class)
    public void testTranslateWriter_TranslatorThrowsIOException() throws IOException {
        CharSequenceTranslator translator = new ThrowingTranslator();
        StringWriter writer = new StringWriter();
        translator.translate("anything", writer);
    }

    @Test(expected = IOException.class)
    public void testTranslateWriter_WriterThrowsIOException() throws IOException {
        CharSequenceTranslator translator = new SimpleATranslator();
        Writer failingWriter = new FailingWriter();
        translator.translate("a", failingWriter);
    }

    // -------------------------------------------------------------------------
    // Tests for with(...) – merging translators
    // -------------------------------------------------------------------------

    @Test
    public void testWith_MergesTwoTranslatorsInOrder() {
        CharSequenceTranslator aTranslator = new SimpleATranslator();
        CharSequenceTranslator bTranslator = new SimpleBTranslator();

        // Build a merged translator: first replace 'a' → 'A', then 'b' → 'B'.
        CharSequenceTranslator merged = aTranslator.with(bTranslator);

        String input = "ababa";
        // Expected: a→A, b→B, resulting in "ABAB A"
        String expected = "ABAB A".replaceAll(" ", ""); // actually "ABAB A" without spaces => "ABAB A"? Let's compute:
        // Input: a b a b a
        // After a->A: A b A b A
        // After b->B: A B A B A => "ABABA"
        expected = "ABABA";
        Assert.assertEquals("Merged translator should apply both replacements in order", expected, merged.translate(input));
    }

    @Test
    public void testWith_MultipleTranslatorsIncludingSurrogate() {
        CharSequenceTranslator base = new SimpleATranslator();
        CharSequenceTranslator emoji = new EmojiTranslator();

        CharSequenceTranslator merged = base.with(emoji);

        String input = "a\uD83D\uDE00b";
        // Expected: 'a' → 'A', emoji → [SMILE], 'b' unchanged (no translator for 'b')
        String expected = "A[SMILE]b";
        Assert.assertEquals("Merged translator should handle both simple and surrogate translations", expected, merged.translate(input));
    }

    @Test
    public void testWith_ArrayOrderPreserved() {
        CharSequenceTranslator first = new SimpleATranslator(); // a→A
        CharSequenceTranslator second = new SimpleBTranslator(); // b→B
        CharSequenceTranslator third = new SimpleATranslator(); // a→A again (no effect on already transformed chars)

        CharSequenceTranslator merged = first.with(second, third);

        String input = "ab";
        // Order: first (a→A), second (b→B), third (a→A) – third sees already transformed 'A' (not 'a')
        String expected = "AB";
        Assert.assertEquals("Translators should be applied in the order supplied to with()", expected, merged.translate(input));
    }

    // -------------------------------------------------------------------------
    // Tests for static hex(int) method
    // -------------------------------------------------------------------------

    @Test
    public void testHex_Zero() {
        Assert.assertEquals("0", CharSequenceTranslator.hex(0));
    }

    @Test
    public void testHex_SingleDigit() {
        Assert.assertEquals("A", CharSequenceTranslator.hex(10));
    }

    @Test
    public void testHex_MultiDigit() {
        Assert.assertEquals("1A3F", CharSequenceTranslator.hex(0x1A3F));
    }

    @Test
    public void testHex_UpperCaseEnforced() {
        // Provide a lower‑case hex representation and ensure the method returns upper case.
        Assert.assertEquals("FF", CharSequenceTranslator.hex(255));
    }

    @Test
    public void testHex_NegativeCodepoint() {
        // Even though negative code points are not typical, the method should simply convert the int.
        Assert.assertEquals("-1", CharSequenceTranslator.hex(-1));
    }
}
```
###Test END##