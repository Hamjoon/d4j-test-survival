###Test START##
```java
package org.apache.commons.lang3.text.translate;

import org.junit.Test;
import org.junit.Assert;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

/**
 * Unit tests for {@link CharSequenceTranslator}.
 *
 * The tests cover:
 * <ul>
 *   <li>All public methods (including overloads)</li>
 *   <li>Typical use‑cases</li>
 *   <li>Edge‑cases such as {@code null} inputs, empty strings, surrogate pairs, and I/O errors</li>
 *   <li>Behaviour of the {@code with()} merger and the static {@code hex()} utility</li>
 * </ul>
 */
public class CharSequenceTranslatorTest {

    /* ---------------------------------------------------------------------- */
    /* Helper concrete translators                                            */
    /* ---------------------------------------------------------------------- */

    /**
     * Simple translator that replaces every occurrence of the character 'a'
     * with the string "b". All other characters are left untouched.
     */
    private static class SimpleAtoBTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == 'a') {
                out.write('b');
                return 1; // one code‑point consumed
            }
            return 0; // signal that nothing was translated
        }
    }

    /**
     * Translator that replaces the Unicode emoji U+1F600 (😀) with the
     * literal string "[SMILE]". Demonstrates handling of surrogate pairs.
     */
    private static class EmojiTranslator extends CharSequenceTranslator {
        private static final int SMILEY = 0x1F600; // 😀
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == SMILEY) {
                out.write("[SMILE]");
                // one code‑point (the emoji) is consumed; the algorithm will
                // advance the index by the appropriate char count (2)
                return 1;
            }
            return 0;
        }
    }

    /**
     * Writer that deliberately throws an {@link IOException} on any write.
     * Used to verify that {@code translate(..., Writer)} propagates the exception.
     */
    private static class ThrowingWriter extends Writer {
        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            throw new IOException("forced I/O error");
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

    /* ---------------------------------------------------------------------- */
    /* Tests for translate(CharSequence)                                      */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testTranslateString_NullInput_ReturnsNull() {
        CharSequenceTranslator translator = new SimpleAtoBTranslator();
        Assert.assertNull(translator.translate((CharSequence) null));
    }

    @Test
    public void testTranslateString_EmptyInput_ReturnsEmpty() {
        CharSequenceTranslator translator = new SimpleAtoBTranslator();
        Assert.assertEquals("", translator.translate(""));
    }

    @Test
    public void testTranslateString_SimpleReplacement() {
        CharSequenceTranslator translator = new SimpleAtoBTranslator();
        String result = translator.translate("abracadabra");
        // every 'a' becomes 'b'
        Assert.assertEquals("bbrbcbdbbrb", result);
    }

    @Test
    public void testTranslateString_WithSurrogatePair() {
        CharSequenceTranslator translator = new EmojiTranslator();
        // Input contains the 😀 emoji (U+1F600)
        String input = "Hello " + new String(Character.toChars(0x1F600)) + " world";
        String expected = "Hello [SMILE] world";
        Assert.assertEquals(expected, translator.translate(input));
    }

    /* ---------------------------------------------------------------------- */
    /* Tests for translate(CharSequence, Writer)                              */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testTranslateWriter_NullWriter_ThrowsIllegalArgumentException() {
        CharSequenceTranslator translator = new SimpleAtoBTranslator();
        try {
            translator.translate("test", null);
            Assert.fail("Expected IllegalArgumentException for null Writer");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (IOException e) {
            Assert.fail("Did not expect IOException");
        }
    }

    @Test
    public void testTranslateWriter_NullInput_NoOutput() throws IOException {
        CharSequenceTranslator translator = new SimpleAtoBTranslator();
        StringWriter out = new StringWriter();
        translator.translate(null, out);
        Assert.assertEquals("", out.toString());
    }

    @Test
    public void testTranslateWriter_SimpleReplacement() throws IOException {
        CharSequenceTranslator translator = new SimpleAtoBTranslator();
        StringWriter out = new StringWriter();
        translator.translate("banana", out);
        Assert.assertEquals("bbnbnb", out.toString());
    }

    @Test
    public void testTranslateWriter_WithSurrogatePair() throws IOException {
        CharSequenceTranslator translator = new EmojiTranslator();
        StringWriter out = new StringWriter();
        String input = "X" + new String(Character.toChars(0x1F600)) + "Y";
        translator.translate(input, out);
        Assert.assertEquals("X[SMILE]Y", out.toString());
    }

    @Test
    public void testTranslateWriter_IOExceptionPropagation() {
        CharSequenceTranslator translator = new SimpleAtoBTranslator();
        Writer throwingWriter = new ThrowingWriter();
        try {
            translator.translate("any", throwingWriter);
            Assert.fail("Expected IOException to be propagated");
        } catch (IOException e) {
            Assert.assertEquals("forced I/O error", e.getMessage());
        }
    }

    /* ---------------------------------------------------------------------- */
    /* Tests for with(CharSequenceTranslator...)                               */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testWith_MergesTranslatorsCorrectly() {
        // First translator: a -> b
        CharSequenceTranslator aToB = new SimpleAtoBTranslator();

        // Second translator: b -> c (implemented inline)
        CharSequenceTranslator bToC = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                int cp = Character.codePointAt(input, index);
                if (cp == 'b') {
                    out.write('c');
                    return 1;
                }
                return 0;
            }
        };

        CharSequenceTranslator merged = aToB.with(bToC);
        String result = merged.translate("ababa");
        // a -> b, then b -> c => a becomes b then c, original b becomes c
        // Expected sequence: c c c c c (five characters)
        Assert.assertEquals("ccccc", result);
    }

    @Test
    public void testWith_NullArrayArgument() {
        CharSequenceTranslator translator = new SimpleAtoBTranslator();
        // Passing an empty array should still return a functional translator
        CharSequenceTranslator merged = translator.with(new CharSequenceTranslator[0]);
        Assert.assertEquals("bb", merged.translate("aa"));
    }

    /* ---------------------------------------------------------------------- */
    /* Tests for static hex(int)                                              */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testHex_UpperCaseAndLocaleIndependence() {
        // Verify a few known conversions
        Assert.assertEquals("0", CharSequenceTranslator.hex(0));
        Assert.assertEquals("1A", CharSequenceTranslator.hex(0x1A));
        Assert.assertEquals("FF", CharSequenceTranslator.hex(255));
        Assert.assertEquals("10FFFF", CharSequenceTranslator.hex(0x10FFFF));

        // Ensure the result is always upper‑case regardless of default locale
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH);
            Assert.assertEquals("ABCDEF", CharSequenceTranslator.hex(0xABCDEF));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    /* ---------------------------------------------------------------------- */
    /* Additional edge‑case tests                                             */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testTranslate_ZeroConsumedWritesOriginalChar() {
        // Translator that never consumes any input (always returns 0)
        CharSequenceTranslator nop = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) {
                return 0; // signal "no translation"
            }
        };
        String input = "xyz";
        String result = nop.translate(input);
        // The algorithm should copy the original characters unchanged
        Assert.assertEquals("xyz", result);
    }

    @Test
    public void testTranslate_MultipleCodePointsConsumed() {
        // Translator that consumes two consecutive code points and replaces them
        CharSequenceTranslator doubleConsumer = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                // If we see "ab", replace with "Z" and consume both characters
                if (index + 1 < input.length()
                        && input.charAt(index) == 'a'
                        && input.charAt(index + 1) == 'b') {
                    out.write('Z');
                    return 2; // two code points consumed
                }
                return 0;
            }
        };
        String input = "ababc";
        String expected = "Zc"; // "ab" -> Z, second "ab" -> Z, remaining "c"
        Assert.assertEquals(expected, doubleConsumer.translate(input));
    }

    @Test
    public void testTranslate_ComplexSurrogatePairPositionAdvancement() {
        // Translator that consumes a surrogate pair (emoji) and a following character
        CharSequenceTranslator complex = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                int cp = Character.codePointAt(input, index);
                // If we encounter 😀 followed by 'x', replace both with "YY"
                if (cp == 0x1F600 && index + 2 < input.length()
                        && input.charAt(index + Character.charCount(cp)) == 'x') {
                    out.write("YY");
                    // Consumed two code points: the emoji and the 'x'
                    return 2;
                }
                return 0;
            }
        };
        String emoji = new String(Character.toChars(0x1F600));
        String input = emoji + "x" + "z";
        String expected = "YYz";
        Assert.assertEquals(expected, complex.translate(input));
    }
}
```
###Test END##