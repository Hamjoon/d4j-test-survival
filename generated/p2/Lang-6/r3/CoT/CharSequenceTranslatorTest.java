package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

/**
 * Tests for {@link CharSequenceTranslator}.
 */
public class CharSequenceTranslatorTest {

    /** Simple translator that copies the character unchanged (consumes 1). */
    private static class IdentityTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            char[] chars = Character.toChars(Character.codePointAt(input, index));
            out.write(chars);
            return chars.length; // always consumes exactly one code‑point
        }
    }

    /** Translator that replaces the character 'a' with 'b'. */
    private static class SimpleReplaceTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == 'a') {
                out.write('b');
                return 1; // consumed the 'a'
            }
            return 0; // let the base class write the original character
        }
    }

    /* -------------------------------------------------
       1. Basic functionality tests
       ------------------------------------------------- */

    @Test
    public void testTranslateString_NullInput_ReturnsNull() {
        CharSequenceTranslator t = new IdentityTranslator();
        assertNull(t.translate((CharSequence) null));
    }

    @Test
    public void testTranslateWriter_NullWriter_ThrowsIAE() {
        CharSequenceTranslator t = new IdentityTranslator();
        try {
            t.translate("test", (Writer) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (IOException e) {
            fail("Unexpected IOException");
        }
    }

    @Test
    public void testTranslateWriter_NullInput_NoOutput() throws IOException {
        CharSequenceTranslator t = new IdentityTranslator();
        StringWriter sw = new StringWriter();
        t.translate((CharSequence) null, sw);
        assertEquals("", sw.toString());
    }

    @Test
    public void testTranslateWriter_EmptyString_NoOutput() throws IOException {
        CharSequenceTranslator t = new IdentityTranslator();
        StringWriter sw = new StringWriter();
        t.translate("", sw);
        assertEquals("", sw.toString());
    }

    /* -------------------------------------------------
       2. Edge‑case handling
       ------------------------------------------------- */

    @Test
    public void testTranslateWriter_SurrogatePair_ConsumedZero_WritesCorrectly() throws IOException {
        // U+1D11E (musical G clef) – a surrogate pair
        String surrogate = new String(Character.toChars(0x1D11E));
        CharSequenceTranslator t = new SimpleReplaceTranslator(); // never consumes → fallback writes original
        StringWriter sw = new StringWriter();
        t.translate(surrogate, sw);
        assertEquals(surrogate, sw.toString());
    }

    @Test
    public void testTranslateWriter_MultipleCodepointsConsumed() throws IOException {
        // Translator that consumes "ab" and writes "X"
        CharSequenceTranslator multi = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (index + 1 < input.length()
                    && input.charAt(index) == 'a'
                    && input.charAt(index + 1) == 'b') {
                    out.write('X');
                    return 2; // consumes both characters
                }
                return 0; // let base class write the char
            }
        };
        StringWriter sw = new StringWriter();
        multi.translate("abacus", sw);
        // Expected result: "Xacus"
        assertEquals("Xacus", sw.toString());
    }

    @Test
    public void testTranslateWriter_IOExceptionPropagation() throws IOException {
        CharSequenceTranslator t = new SimpleReplaceTranslator();
        Writer badWriter = new Writer() {
            @Override public void write(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("forced");
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        try {
            t.translate("a", badWriter);
            fail("Expected IOException");
        } catch (IOException e) {
            assertEquals("forced", e.getMessage());
        }
    }

    /* -------------------------------------------------
       3. with(...) merging tests
       ------------------------------------------------- */

    @Test
    public void testWith_NoAdditionalTranslators_ReturnsAggregateContainingOnlyThis() {
        CharSequenceTranslator base = new SimpleReplaceTranslator();
        CharSequenceTranslator merged = base.with(); // empty var‑args
        assertNotNull(merged);
        // Behaviour must still be the same as the original translator
        assertEquals("b", merged.translate("a"));
    }

    @Test
    public void testWith_MultipleTranslators_OrderedApplication() {
        // First translator: a → b
        CharSequenceTranslator first = new SimpleReplaceTranslator();

        // Second translator: b → c
        CharSequenceTranslator second = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (input.charAt(index) == 'b') {
                    out.write('c');
                    return 1;
                }
                return 0;
            }
        };

        CharSequenceTranslator merged = first.with(second);

        // a → b (first) → c (second)
        assertEquals("c", merged.translate("a"));
        // b → c (second only)
        assertEquals("c", merged.translate("b"));
        // c unchanged
        assertEquals("c", merged.translate("c"));
    }

    /* -------------------------------------------------
       4. hex(...) tests
       ------------------------------------------------- */

    @Test
    public void testHex_TypicalValues() {
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("A", CharSequenceTranslator.hex(10));
        assertEquals("FF", CharSequenceTranslator.hex(255));
        assertEquals("1D11E", CharSequenceTranslator.hex(0x1D11E));
    }

    @Test
    public void testHex_NegativeValue() {
        assertEquals("-1", CharSequenceTranslator.hex(-1));
    }

    @Test
    public void testHex_MaxInt() {
        assertEquals("7FFFFFFF", CharSequenceTranslator.hex(Integer.MAX_VALUE));
    }
}
