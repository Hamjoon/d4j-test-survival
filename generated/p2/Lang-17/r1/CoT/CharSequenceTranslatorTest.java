package org.apache.commons.lang3.text.translate;

import org.junit.Test;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

import static org.junit.Assert.*;

/**
 * JUnit‑4 test class for {@link CharSequenceTranslator}.
 * <p>
 * The tests cover:
 * <ul>
 *   <li>public API signatures</li>
 *   <li>normal behaviour of {@code translate(CharSequence)}</li>
 *   <li>normal behaviour of {@code translate(CharSequence,Writer)}</li>
 *   <li>edge cases: null arguments, surrogate pairs, zero‑consumption, multi‑code‑point consumption</li>
 *   <li>exception handling (illegal writer, writer I/O error)</li>
 *   <li>static helper {@code hex(int)}</li>
 *   <li>the {@code with(...)} merger factory</li>
 * </ul>
 * </p>
 */
public class CharSequenceTranslatorTest {

    /** Simple translator that replaces the character 'x' with 'y'. */
    private static class SimpleXtoYTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == 'x') {
                out.write('y');
                return 1;               // consumed one code‑point
            }
            return 0;                   // let the base class copy the original char
        }
    }

    /** Translator that consumes a surrogate pair (code‑point > 0xFFFF) and writes a placeholder. */
    private static class SurrogatePairTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (Character.isSupplementaryCodePoint(cp)) {
                out.write("[U+" + CharSequenceTranslator.hex(cp) + "]");
                // The contract is to return the number of *codepoints* consumed.
                // For a surrogate pair that is 2 code units, but 1 codepoint.
                // The surrounding algorithm iterates over codepoints, so we return 1.
                return 1;
            }
            return 0;
        }
    }

    /** Writer that deliberately throws an IOException on any write operation. */
    private static class FailingWriter extends Writer {
        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            throw new IOException("forced failure");
        }

        @Override
        public void flush() throws IOException {
            throw new IOException("forced failure");
        }

        @Override
        public void close() throws IOException {
            // no‑op
        }
    }

    private CharSequenceTranslator simpleTranslator;
    private CharSequenceTranslator surrogateTranslator;

    @Before
    public void setUp() {
        simpleTranslator = new SimpleXtoYTranslator();
        surrogateTranslator = new SurrogatePairTranslator();
    }

    // -----------------------------------------------------------------------
    // 1. Public method signatures (extracted for documentation purposes)
    // -----------------------------------------------------------------------
    // public abstract int translate(CharSequence input, int index, Writer out) throws IOException
    // public final String translate(CharSequence input)
    // public final void translate(CharSequence input, Writer out) throws IOException
    // public final CharSequenceTranslator with(CharSequenceTranslator... translators)
    // public static String hex(int codepoint)

    // -----------------------------------------------------------------------
    // 2. Basic functionality tests
    // -----------------------------------------------------------------------

    @Test
    public void testTranslateString_NullInputReturnsNull() {
        assertNull("translate(null) should return null", simpleTranslator.translate((CharSequence) null));
    }

    @Test
    public void testTranslateString_SimpleReplacement() {
        String result = simpleTranslator.translate("axbx");
        // only the first 'x' is replaced, the second remains because after replacement we have 'y'
        assertEquals("ayb", result);
    }

    @Test
    public void testTranslateWriter_NullWriterThrows() {
        try {
            simpleTranslator.translate("any", null);
            fail("Expected IllegalArgumentException for null Writer");
        } catch (IllegalArgumentException e) {
            assertEquals("The Writer must not be null", e.getMessage());
        } catch (IOException e) {
            fail("Should not have thrown IOException");
        }
    }

    @Test
    public void testTranslateWriter_NullInputDoesNothing() throws IOException {
        StringWriter out = new StringWriter();
        simpleTranslator.translate(null, out);
        assertEquals("", out.toString());
    }

    @Test
    public void testTranslateWriter_SimpleReplacement() throws IOException {
        StringWriter out = new StringWriter();
        simpleTranslator.translate("xax", out);
        // 'x' -> 'y', other chars copied
        assertEquals("yay", out.toString());
    }

    // -----------------------------------------------------------------------
    // 3. Edge‑case tests
    // -----------------------------------------------------------------------

    @Test
    public void testTranslateWriter_ZeroConsumptionFallsBackToCopy() throws IOException {
        // SimpleXtoYTranslator returns 0 for characters other than 'x',
        // therefore the base class must copy them unchanged.
        StringWriter out = new StringWriter();
        simpleTranslator.translate("abc", out);
        assertEquals("abc", out.toString());
    }

    @Test
    public void testTranslateWriter_SurrogatePairHandled() throws IOException {
        // U+1F600 (GRINNING FACE) is a surrogate pair.
        String smiley = new StringBuilder().appendCodePoint(0x1F600).toString();
        StringWriter out = new StringWriter();
        surrogateTranslator.translate(smiley, out);
        // Expected placeholder "[U+1F600]"
        assertEquals("[U+" + CharSequenceTranslator.hex(0x1F600) + "]", out.toString());
    }

    @Test
    public void testTranslateWriter_MixedSurrogateAndNormalChars() throws IOException {
        // Input: "a" + surrogate (U+1F603) + "b"
        String mixed = "a" + new StringBuilder().appendCodePoint(0x1F603).toString() + "b";
        StringWriter out = new StringWriter();
        // Chain both translators: first surrogate handler, then simple translator (which will do nothing here)
        CharSequenceTranslator combined = surrogateTranslator.with(simpleTranslator);
        combined.translate(mixed, out);
        // Expected: "a[U+1F603]b"
        assertEquals("a[U+" + CharSequenceTranslator.hex(0x1F603) + "]b", out.toString());
    }

    @Test
    public void testTranslateWriter_IOExceptionPropagated() {
        try {
            simpleTranslator.translate("any", new FailingWriter());
            fail("Expected IOException to be propagated");
        } catch (IOException e) {
            assertEquals("forced failure", e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // 4. Static helper method tests
    // -----------------------------------------------------------------------

    @Test
    public void testHex_StandardValues() {
        assertEquals("41", CharSequenceTranslator.hex('A')); // 0x41
        assertEquals("61", CharSequenceTranslator.hex('a')); // 0x61
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("10FFFF", CharSequenceTranslator.hex(0x10FFFF)); // max Unicode code point
    }

    @Test
    public void testHex_NegativeValue() {
        // The method does not guard against negative numbers – it will produce the two's‑complement hex.
        assertEquals(Integer.toHexString(-1).toUpperCase(Locale.ENGLISH), CharSequenceTranslator.hex(-1));
    }

    // -----------------------------------------------------------------------
    // 5. with(...) merger tests
    // -----------------------------------------------------------------------

    @Test
    public void testWith_ReturnsAggregateTranslator() {
        CharSequenceTranslator merged = simpleTranslator.with(surrogateTranslator);
        assertNotNull("Merged translator must not be null", merged);
        // The concrete class is AggregateTranslator (package‑private). We test by class name.
        assertTrue("Merged translator should be an AggregateTranslator",
                merged.getClass().getSimpleName().equals("AggregateTranslator"));
    }

    @Test
    public void testWith_MergedFunctionality() throws IOException {
        // Translator A: replace 'a' with '1'
        CharSequenceTranslator a = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (input.charAt(index) == 'a') {
                    out.write('1');
                    return 1;
                }
                return 0;
            }
        };
        // Translator B: replace 'b' with '2'
        CharSequenceTranslator b = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (input.charAt(index) == 'b') {
                    out.write('2');
                    return 1;
                }
                return 0;
            }
        };
        CharSequenceTranslator merged = a.with(b);
        String result = merged.translate("abc");
        // Expect 'a' -> '1', 'b' -> '2', 'c' unchanged
        assertEquals("12c", result);
    }

}
