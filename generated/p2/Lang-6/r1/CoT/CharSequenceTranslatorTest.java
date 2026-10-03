package org.apache.commons.lang3.text.translate;

import org.junit.Test;
import org.junit.Assert;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

/**
 * JUnit 4 test suite for {@link CharSequenceTranslator}.
 *
 * The tests cover:
 * <ul>
 *   <li>Public method signatures</li>
 *   <li>Normal behaviour of each method</li>
 *   <li>Edge‑cases such as {@code null} arguments, empty input, surrogate pairs,
 *       translators that return {@code 0}, and illegal arguments.</li>
 *   <li>Correct handling of the static {@code hex(int)} helper.</li>
 * </ul>
 */
public class CharSequenceTranslatorTest {

    /* ---------------------------------------------------------------------- */
    /* Helper concrete translators                                            */
    /* ---------------------------------------------------------------------- */

    /**
     * Simple translator that copies the current code‑point to the writer
     * and reports that it consumed exactly one Unicode code‑point
     * (which may consist of one or two {@code char}s).
     */
    private static class IdentityTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            out.write(Character.toChars(cp));
            return Character.charCount(cp);
        }
    }

    /**
     * Translator that never consumes any input.  It is used to test the
     * fallback path in {@link CharSequenceTranslator#translate(CharSequence, Writer)}.
     */
    private static class ZeroConsumeTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) {
            // deliberately consumes nothing and writes nothing
            return 0;
        }
    }

    /**
     * Translator that converts a single Unicode code‑point into its
     * upper‑case hexadecimal representation (using {@link CharSequenceTranslator#hex(int)}).
     * It consumes the whole code‑point, which means surrogate pairs are handled
     * correctly.
     */
    private static class HexTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            out.write(hex(cp));
            return Character.charCount(cp);
        }
    }

    /* ---------------------------------------------------------------------- */
    /* 1.  Public method signatures                                            */
    /* ---------------------------------------------------------------------- */

    /**
     * Verify that the class exposes the expected public methods.
     */
    @Test
    public void testPublicMethodSignatures() throws NoSuchMethodException {
        // abstract method
        Assert.assertNotNull(CharSequenceTranslator.class
                .getDeclaredMethod("translate", CharSequence.class, int.class, Writer.class));

        // final helper returning a String
        Assert.assertNotNull(CharSequenceTranslator.class
                .getDeclaredMethod("translate", CharSequence.class));

        // final helper writing to a Writer
        Assert.assertNotNull(CharSequenceTranslator.class
                .getDeclaredMethod("translate", CharSequence.class, Writer.class));

        // final with(...) method
        Assert.assertNotNull(CharSequenceTranslator.class
                .getDeclaredMethod("with", CharSequenceTranslator[].class));

        // static hex(int) method
        Assert.assertNotNull(CharSequenceTranslator.class
                .getDeclaredMethod("hex", int.class));
    }

    /* ---------------------------------------------------------------------- */
    /* 2.  Basic functionality tests                                         */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testTranslateToString_NullInput() {
        CharSequenceTranslator translator = new IdentityTranslator();
        Assert.assertNull(translator.translate((CharSequence) null));
    }

    @Test
    public void testTranslateToString_EmptyInput() {
        CharSequenceTranslator translator = new IdentityTranslator();
        Assert.assertEquals("", translator.translate(""));
    }

    @Test
    public void testTranslateToString_BasicCopy() {
        CharSequenceTranslator translator = new IdentityTranslator();
        String input = "Hello World!";
        String output = translator.translate(input);
        Assert.assertEquals(input, output);
    }

    @Test
    public void testTranslateToWriter_NullWriter() {
        CharSequenceTranslator translator = new IdentityTranslator();
        try {
            translator.translate("test", (Writer) null);
            Assert.fail("Expected IllegalArgumentException for null Writer");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("The Writer must not be null", e.getMessage());
        } catch (IOException e) {
            Assert.fail("Did not expect IOException");
        }
    }

    @Test
    public void testTranslateToWriter_NullInput() throws IOException {
        CharSequenceTranslator translator = new IdentityTranslator();
        StringWriter writer = new StringWriter();
        translator.translate((CharSequence) null, writer);
        Assert.assertEquals("", writer.toString());
    }

    @Test
    public void testTranslateToWriter_BasicCopy() throws IOException {
        CharSequenceTranslator translator = new IdentityTranslator();
        String input = "Apache Commons";
        StringWriter writer = new StringWriter();
        translator.translate(input, writer);
        Assert.assertEquals(input, writer.toString());
    }

    @Test
    public void testTranslateToWriter_ZeroConsumeFallback() throws IOException {
        // ZeroConsumeTranslator returns 0, so CharSequenceTranslator should write the original char(s)
        CharSequenceTranslator translator = new ZeroConsumeTranslator();
        String input = "abc";
        StringWriter writer = new StringWriter();
        translator.translate(input, writer);
        Assert.assertEquals(input, writer.toString());
    }

    @Test
    public void testTranslateToWriter_SurrogatePairHandling() throws IOException {
        // Use an emoji (U+1F600) which is a supplementary code point (needs a surrogate pair)
        String emoji = "\uD83D\uDE00"; // 😀
        CharSequenceTranslator translator = new HexTranslator();
        StringWriter writer = new StringWriter();
        translator.translate(emoji, writer);
        // Expected hex string is "1F600"
        Assert.assertEquals("1F600", writer.toString());
    }

    @Test
    public void testWithMethod_ReturnsAggregateTranslator() {
        CharSequenceTranslator base = new IdentityTranslator();
        CharSequenceTranslator merged = base.with(new ZeroConsumeTranslator());
        Assert.assertTrue("Merged translator should be an instance of CharSequenceTranslator",
                merged instanceof CharSequenceTranslator);
        // The concrete class is AggregateTranslator (internal to Commons Lang)
        Assert.assertTrue("Merged translator should be an AggregateTranslator",
                merged.getClass().getSimpleName().contains("AggregateTranslator"));
    }

    @Test
    public void testWithMethod_CombinedTranslation() throws IOException {
        // Base translator (Identity) copies input; ZeroConsume consumes nothing.
        // Combine them and ensure the overall behaviour is still copying.
        CharSequenceTranslator base = new IdentityTranslator();
        CharSequenceTranslator combined = base.with(new ZeroConsumeTranslator());

        String input = "Test123";
        StringWriter writer = new StringWriter();
        combined.translate(input, writer);
        Assert.assertEquals(input, writer.toString());
    }

    /* ---------------------------------------------------------------------- */
    /* 3.  Edge cases for the static hex(int) utility                         */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testHex_PositiveValues() {
        Assert.assertEquals("0", CharSequenceTranslator.hex(0));
        Assert.assertEquals("A", CharSequenceTranslator.hex(10));
        Assert.assertEquals("10", CharSequenceTranslator.hex(16));
        Assert.assertEquals("FF", CharSequenceTranslator.hex(255));
        Assert.assertEquals("1F600", CharSequenceTranslator.hex(0x1F600));
    }

    @Test
    public void testHex_NegativeValue() {
        // Integer.toHexString returns the two's‑complement representation.
        // The method does not guard against negative numbers – we just verify the behaviour.
        Assert.assertEquals(Integer.toHexString(-1).toUpperCase(Locale.ENGLISH),
                CharSequenceTranslator.hex(-1));
    }

    @Test
    public void testHex_LocaleIndependence() {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH);
            // hex must still be upper case English regardless of default locale
            Assert.assertEquals("ABCDEF", CharSequenceTranslator.hex(0xABCDEF));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }
}
