###Test START##
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

import org.junit.Test;

/**
 * Unit tests for {@link CharSequenceTranslator}.
 *
 * <p>These tests exercise all public and protected behaviour:
 *
 * <ul>
 *   <li>{@link CharSequenceTranslator#translate(CharSequence)}</li>
 *   <li>{@link CharSequenceTranslator#translate(CharSequence, Writer)}</li>
 *   <li>{@link CharSequenceTranslator#with(CharSequenceTranslator...)}</li>
 *   <li>{@link CharSequenceTranslator#hex(int)}</li>
 * </ul>
 *
 * <p>Because {@code CharSequenceTranslator} is abstract we provide a few concrete
 * implementations inside the test class to verify the contract of the
 * algorithm in {@code translate(CharSequence, Writer)} as well as the helper
 * methods.</p>
 */
public class CharSequenceTranslatorTest {

    /** Simple translator that replaces a single character with a given string. */
    private static final class SimpleReplaceTranslator extends CharSequenceTranslator {
        private final char target;
        private final String replacement;

        SimpleReplaceTranslator(char target, String replacement) {
            this.target = target;
            this.replacement = replacement;
        }

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            if (input.charAt(index) == target) {
                out.write(replacement);
                return 1; // consumed one codepoint
            }
            return 0; // no translation performed
        }
    }

    /** Translator that recognises a surrogate pair (emoji) and replaces it with a token. */
    private static final class EmojiTranslator extends CharSequenceTranslator {
        private final int codepointToMatch;
        private final String token;

        EmojiTranslator(int codepointToMatch, String token) {
            this.codepointToMatch = codepointToMatch;
            this.token = token;
        }

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == codepointToMatch) {
                out.write(token);
                // return the number of **code points** consumed (here 1), but the
                // calling algorithm will advance by the correct number of char units.
                return 1;
            }
            return 0;
        }
    }

    /** Writer that deliberately throws an IOException after a certain number of writes. */
    private static final class FailingWriter extends Writer {
        private final int failAfter;
        private int writeCount = 0;

        FailingWriter(int failAfter) {
            this.failAfter = failAfter;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            if (writeCount >= failAfter) {
                throw new IOException("forced failure");
            }
            writeCount++;
        }

        @Override public void flush() throws IOException {}
        @Override public void close() throws IOException {}
    }

    // -------------------------------------------------------------------------
    // Tests for translate(CharSequence) – the convenience method
    // -------------------------------------------------------------------------

    @Test
    public void testTranslateNullInputReturnsNull() {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('a', "b");
        assertNull("translate(null) must return null", translator.translate((CharSequence) null));
    }

    @Test
    public void testTranslateEmptyStringReturnsEmpty() {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('x', "y");
        assertEquals("", translator.translate(""));
    }

    @Test
    public void testTranslateSimpleReplacement() {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('a', "A");
        assertEquals("Abc", translator.translate("abc"));
        assertEquals("A A", translator.translate("a a"));
        assertEquals("xyz", translator.translate("xyz")); // no 'a' present
    }

    @Test
    public void testTranslateWithSurrogatePair() {
        // 😀  = U+1F600
        String input = "Hello \uD83D\uDE00 World";
        CharSequenceTranslator translator = new EmojiTranslator(0x1F600, "[SMILE]");
        assertEquals("Hello [SMILE] World", translator.translate(input));
    }

    @Test
    public void testTranslateUsesWriterAlgorithmWhenConsumeZero() {
        // This translator never consumes any characters, therefore the algorithm
        // must copy the original input unchanged.
        CharSequenceTranslator identity = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) {
                return 0; // indicate “nothing translated”
            }
        };
        assertEquals("unchanged", identity.translate("unchanged"));
    }

    // -------------------------------------------------------------------------
    // Tests for translate(CharSequence, Writer) – lower level API
    // -------------------------------------------------------------------------

    @Test
    public void testTranslateWriterNullWriterThrows() {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('a', "b");
        try {
            translator.translate("test", (Writer) null);
            fail("Expected IllegalArgumentException for null Writer");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (IOException e) {
            fail("Unexpected IOException: " + e);
        }
    }

    @Test
    public void testTranslateWriterNullInputDoesNothing() throws IOException {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('a', "b");
        StringWriter sw = new StringWriter();
        translator.translate((CharSequence) null, sw);
        assertEquals("", sw.toString());
    }

    @Test
    public void testTranslateWriterMultipleTranslators() throws IOException {
        CharSequenceTranslator aToA = new SimpleReplaceTranslator('a', "A");
        CharSequenceTranslator bToB = new SimpleReplaceTranslator('b', "B");
        CharSequenceTranslator combined = aToA.with(bToB); // AggregateTranslator

        StringWriter out = new StringWriter();
        combined.translate("abca", out);
        assertEquals("ABcA", out.toString());
    }

    @Test
    public void testTranslateWriterPropagatesIOException() {
        CharSequenceTranslator translator = new SimpleReplaceTranslator('x', "y");
        Writer failing = new FailingWriter(0); // will fail on first write
        try {
            translator.translate("x", failing);
            fail("Expected IOException to be propagated");
        } catch (IOException e) {
            assertEquals("forced failure", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Tests for with(...) – merging translators
    // -------------------------------------------------------------------------

    @Test
    public void testWithMergesTranslatorsInOrder() {
        CharSequenceTranslator first = new SimpleReplaceTranslator('1', "one");
        CharSequenceTranslator second = new SimpleReplaceTranslator('2', "two");
        CharSequenceTranslator merged = first.with(second);

        // “12” => first replaces ‘1’, second replaces ‘2’
        assertEquals("onetwo", merged.translate("12"));
    }

    @Test
    public void testWithAddsCurrentTranslatorAtPositionZero() {
        CharSequenceTranslator only = new SimpleReplaceTranslator('z', "Z");
        CharSequenceTranslator merged = only.with(); // no extra translators
        assertEquals("Z", merged.translate("z"));
    }

    // -------------------------------------------------------------------------
    // Tests for static hex(int) utility method
    // -------------------------------------------------------------------------

    @Test
    public void testHexZero() {
        assertEquals("0", CharSequenceTranslator.hex(0));
    }

    @Test
    public void testHexPositiveValues() {
        assertEquals("A", CharSequenceTranslator.hex(10));
        assertEquals("FF", CharSequenceTranslator.hex(255));
        assertEquals("1F600", CharSequenceTranslator.hex(0x1F600)); // 😀
        assertEquals("7FFFFFFF", CharSequenceTranslator.hex(Integer.MAX_VALUE));
    }

    @Test
    public void testHexIsUpperCaseAndLocaleIndependent() {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN); // uses commas for decimal separator, etc.
            assertEquals("ABCDEF", CharSequenceTranslator.hex(0xABCDEF));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    // -------------------------------------------------------------------------
    // Additional edge‑case tests for surrogate handling in translate(...)
    // -------------------------------------------------------------------------

    @Test
    public void testTranslateConsumesCorrectNumberOfCharsForSurrogatePair() {
        // The translator will replace the emoji (a surrogate pair) with a token.
        CharSequenceTranslator translator = new EmojiTranslator(0x1F602, "LAUGH"); // 😂

        // Input contains: "A", the emoji, and "B"
        String input = "A\uD83D\uDE02B";
        assertEquals("ALAU GHB".replace(" ", ""), translator.translate(input).replace(" ", "")); // simplified check

        // Verify the exact output
        assertEquals("ALAUGH B".replace(" ", ""), translator.translate(input));
        // The result should be "A" + "LAUGH" + "B"
        assertEquals("AL AUGH B".replace(" ", ""), translator.translate(input));
        // Properly assert expected string:
        assertEquals("ALAUGHB", translator.translate(input));
    }

    @Test
    public void testTranslateDoesNotEnterInfiniteLoopWhenTranslatorReturnsZero() {
        // Translator that never translates any character.
        CharSequenceTranslator nop = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                return 0;
            }
        };
        // Large input to ensure loop terminates.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            sb.append('x');
        }
        assertEquals(sb.toString(), nop.translate(sb.toString()));
    }
}
###Test END##