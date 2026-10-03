package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

import org.junit.Test;

/**
 * JUnit‑4 test suite for {@link CharSequenceTranslator}.
 *
 * The tests cover:
 *   • basic usage of the concrete translators,
 *   • all public methods of the abstract base class,
 *   • edge‑cases such as null/empty inputs, surrogate pairs,
 *   • error handling (null writer, IOException from a Writer),
 *   • the static {@code hex(int)} helper,
 *   • the {@code with(..)} merger.
 */
public class CharSequenceTranslatorTest {

    /** -----------------------------------------------------------------
     *  Helper concrete translator used in most tests.
     *  It replaces the character 'a' with the string "b".
     *  All other characters are left untouched (returns 0 → fallback).
     * ----------------------------------------------------------------- */
    private static class DummyTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            char ch = input.charAt(index);
            if (ch == 'a') {
                out.write('b');
                return 1;                     // one code‑point consumed
            }
            return 0;                         // let the base class write the original char
        }
    }

    /** -----------------------------------------------------------------
     *  Simple translator that converts every code‑point to its upper‑case
     *  representation (via {@link String#toUpperCase(Locale)}).  Used for
     *  testing the {@code with(..)} merger.
     * ----------------------------------------------------------------- */
    private static class UpperCaseTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            String upper = new String(Character.toChars(cp)).toUpperCase(Locale.ENGLISH);
            out.write(upper);
            return Character.charCount(cp);
        }
    }

    /** -----------------------------------------------------------------
     *  Writer that deliberately throws IOException on any write operation.
     * ----------------------------------------------------------------- */
    private static class FailingWriter extends Writer {
        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            throw new IOException("forced failure");
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

    // -----------------------------------------------------------------
    //  1. Tests for the static helper method: hex(int)
    // -----------------------------------------------------------------
    @Test
    public void testHexTypicalValues() {
        assertEquals("41", CharSequenceTranslator.hex(0x41));          // 'A'
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("FFFF", CharSequenceTranslator.hex(0xFFFF));
        assertEquals("1F600", CharSequenceTranslator.hex(0x1F600));   // 😀
    }

    @Test
    public void testHexNegativeValue() {
        assertEquals("-1", CharSequenceTranslator.hex(-1));
        assertEquals("-10", CharSequenceTranslator.hex(-0x10));
    }

    // -----------------------------------------------------------------
    //  2. Tests for translate(CharSequence) – the convenience method
    // -----------------------------------------------------------------
    @Test
    public void testTranslateStringNullInput() {
        DummyTranslator translator = new DummyTranslator();
        assertNull(translator.translate((CharSequence) null));
    }

    @Test
    public void testTranslateStringEmptyInput() {
        DummyTranslator translator = new DummyTranslator();
        assertEquals("", translator.translate(""));
    }

    @Test
    public void testTranslateStringSimpleReplacement() {
        DummyTranslator translator = new DummyTranslator();
        assertEquals("bcd", translator.translate("acd")); // only 'a' -> 'b'
    }

    @Test
    public void testTranslateStringSurrogatePairUnchanged() {
        DummyTranslator translator = new DummyTranslator();
        // 😀 is a surrogate pair (U+1F600)
        String input = "x\uD83D\uDE00y";
        assertEquals(input, translator.translate(input));
    }

    // -----------------------------------------------------------------
    //  3. Tests for translate(CharSequence, Writer)
    // -----------------------------------------------------------------
    @Test
    public void testTranslateWriterNullWriter() {
        DummyTranslator translator = new DummyTranslator();
        try {
            translator.translate("test", (Writer) null);
            fail("Expected IllegalArgumentException for null Writer");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (IOException e) {
            fail("Unexpected IOException");
        }
    }

    @Test
    public void testTranslateWriterNullInput() throws IOException {
        DummyTranslator translator = new DummyTranslator();
        StringWriter sw = new StringWriter();
        translator.translate((CharSequence) null, sw);
        assertEquals("", sw.toString());   // nothing written
    }

    @Test
    public void testTranslateWriterZeroConsumedPath() throws IOException {
        DummyTranslator translator = new DummyTranslator();
        StringWriter sw = new StringWriter();
        translator.translate("xyz", sw);
        // No character is 'a', so each iteration returns 0 → fallback writes original char
        assertEquals("xyz", sw.toString());
    }

    @Test
    public void testTranslateWriterWithExceptionPropagation() {
        DummyTranslator translator = new DummyTranslator();
        Writer failingWriter = new FailingWriter();
        try {
            translator.translate("a", failingWriter);
            fail("Expected IOException to be propagated");
        } catch (IOException e) {
            assertEquals("forced failure", e.getMessage());
        }
    }

    @Test
    public void testTranslateWriterSurrogatePairHandling() throws IOException {
        DummyTranslator translator = new DummyTranslator();
        StringWriter sw = new StringWriter();
        // Input contains a surrogate pair; translator never consumes it (returns 0)
        String input = "\uD83D\uDE00"; // 😀
        translator.translate(input, sw);
        assertEquals(input, sw.toString());
    }

    // -----------------------------------------------------------------
    //  4. Tests for the merger method: with(CharSequenceTranslator...)
    // -----------------------------------------------------------------
    @Test
    public void testWithMergesTranslatorsInOrder() {
        // this (Dummy) replaces 'a' -> "b"
        // UpperCaseTranslator will later turn that "b" into "B"
        CharSequenceTranslator merged = new DummyTranslator()
                .with(new UpperCaseTranslator());

        assertEquals("Bcd", merged.translate("acd")); // 'a'->'b' then uppercased to 'B'
    }

    @Test
    public void testWithNoAdditionalTranslators() {
        CharSequenceTranslator only = new DummyTranslator().with();
        assertEquals("bcd", only.translate("acd"));
    }

    @Test
    public void testWithMultipleTranslators() {
        CharSequenceTranslator merged = new DummyTranslator()
                .with(new UpperCaseTranslator(),
                      new DummyTranslator() { // second dummy that replaces 'b' with "c"
                          @Override
                          public int translate(CharSequence input, int index, Writer out) throws IOException {
                              char ch = input.charAt(index);
                              if (ch == 'b') {
                                  out.write('c');
                                  return 1;
                              }
                              return 0;
                          }
                      });

        // Order: Dummy(a->b) → UpperCase (b->B) → second Dummy (B not matched) → result "Bcd"
        assertEquals("Bcd", merged.translate("acd"));
    }

    // -----------------------------------------------------------------
    //  5. Direct test of the abstract translate(int,CharSequence,Writer) contract
    // -----------------------------------------------------------------
    @Test
    public void testAbstractTranslateReturnsConsumedCount() throws IOException {
        // Use DummyTranslator directly via the protected abstract method through the public wrapper
        DummyTranslator translator = new DummyTranslator();
        StringWriter sw = new StringWriter();
        int consumed = translator.translate("a", 0, sw);
        assertEquals(1, consumed);
        assertEquals("b", sw.toString());
    }
}
