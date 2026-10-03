package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

/**
 * Comprehensive test suite for {@code LookupTranslator}.
 */
public class LookupTranslatorTest {

    /* --------------------------------------------------------------
     * Helper writer that deliberately throws an IOException.
     * -------------------------------------------------------------- */
    private static class ThrowingWriter extends Writer {
        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            throw new IOException("forced");
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

    /* --------------------------------------------------------------
     * 1. Basic translation – single‑character mapping.
     * -------------------------------------------------------------- */
    @Test
    public void testBasicSingleCharTranslation() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"a", "b"}
        });
        StringWriter out = new StringWriter();
        int consumed = lt.translate("a", 0, out);
        assertEquals("Should consume one character", 1, consumed);
        assertEquals("b", out.toString());
    }

    /* --------------------------------------------------------------
     * 2. Greedy (longest match) behaviour.
     *    Both "a" and "ab" are defined – the longer key must win.
     * -------------------------------------------------------------- */
    @Test
    public void testGreedyLongestMatch() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"a", "X"},
                {"ab", "Y"}
        });
        StringWriter out = new StringWriter();
        int consumed = lt.translate("ab", 0, out);
        assertEquals("Should consume two characters (longest match)", 2, consumed);
        assertEquals("Y", out.toString());
    }

    /* --------------------------------------------------------------
     * 3. No match when input is shorter than the shortest key.
     * -------------------------------------------------------------- */
    @Test
    public void testNoMatchWhenInputTooShort() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"abc", "Z"}
        });
        StringWriter out = new StringWriter();
        int consumed = lt.translate("ab", 0, out);
        assertEquals("Should not consume any characters", 0, consumed);
        assertEquals("", out.toString());
    }

    /* --------------------------------------------------------------
     * 4. Constructor with null lookup array – should behave as empty map.
     * -------------------------------------------------------------- */
    @Test
    public void testConstructorWithNullLookup() throws IOException {
        LookupTranslator lt = new LookupTranslator((CharSequence[][]) null);
        StringWriter out = new StringWriter();
        int consumed = lt.translate("anything", 0, out);
        assertEquals("Empty translator must not consume characters", 0, consumed);
        assertEquals("", out.toString());
    }

    /* --------------------------------------------------------------
     * 5. Constructor with empty var‑args – also results in an empty map.
     * -------------------------------------------------------------- */
    @Test
    public void testConstructorWithEmptyLookup() throws IOException {
        LookupTranslator lt = new LookupTranslator();
        StringWriter out = new StringWriter();
        int consumed = lt.translate("test", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    /* --------------------------------------------------------------
     * 6. Null input to {@code translate} – should throw NPE.
     * -------------------------------------------------------------- */
    @Test(expected = NullPointerException.class)
    public void testTranslateNullInput() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"a", "b"}
        });
        lt.translate(null, 0, new StringWriter());
    }

    /* --------------------------------------------------------------
     * 7. Index beyond input length – should return 0 and not modify writer.
     * -------------------------------------------------------------- */
    @Test
    public void testIndexBeyondInputLength() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"a", "b"}
        });
        StringWriter out = new StringWriter();
        int consumed = lt.translate("abc", 5, out); // index > length
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    /* --------------------------------------------------------------
     * 8. Writer that throws IOException – the exception must propagate.
     * -------------------------------------------------------------- */
    @Test(expected = IOException.class)
    public void testIOExceptionPropagation() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"a", "b"}
        });
        Writer throwingWriter = new ThrowingWriter();
        lt.translate("a", 0, throwingWriter);
    }

    /* --------------------------------------------------------------
     * 9. Multiple entries with same key – later entry overwrites earlier.
     * -------------------------------------------------------------- */
    @Test
    public void testDuplicateKeyOverrides() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"key", "first"},
                {"key", "second"} // should replace "first"
        });
        StringWriter out = new StringWriter();
        int consumed = lt.translate("key", 0, out);
        assertEquals(3, consumed);
        assertEquals("second", out.toString());
    }

    /* --------------------------------------------------------------
     * 10. Verify that the translator respects the supplied index
     *     (partial translation inside a larger string).
     * -------------------------------------------------------------- */
    @Test
    public void testTranslateFromMiddleOfInput() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"bc", "X"}
        });
        StringWriter out = new StringWriter();
        int consumed = lt.translate("abcde", 1, out); // start at 'b'
        assertEquals(2, consumed);
        assertEquals("X", out.toString());
    }
}