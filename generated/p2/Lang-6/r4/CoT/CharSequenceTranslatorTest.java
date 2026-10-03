package org.apache.commons.lang3.text.translate;

import org.junit.Test;
import org.junit.Assert;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * JUnit 4 test cases for {@link CharSequenceTranslator}.
 *
 * The tests cover:
 * <ul>
 *   <li>All public methods (signatures listed in the comment block).</li>
 *   <li>Basic functionality of each method.</li>
 *   <li>Edge‑cases such as {@code null} arguments, empty strings, surrogate pairs,
 *       and illegal arguments.</li>
 *   <li>Exception handling for the {@code Writer} overload.</li>
 *   <li>Behaviour of the {@code with(...)} merger and the static {@code hex(...)} helper.</li>
 * </ul>
 */
public class CharSequenceTranslatorTest {

    /* ----------------------------------------------------------------------
     *  Public method signatures of CharSequenceTranslator (for reference)
     * ----------------------------------------------------------------------
     *
     * public abstract int translate(CharSequence input, int index, Writer out)
     *        throws IOException;
     *
     * public final String translate(CharSequence input);
     *
     * public final void translate(CharSequence input, Writer out)
     *        throws IOException;
     *
     * public final CharSequenceTranslator with(CharSequenceTranslator... translators);
     *
     * public static String hex(int codepoint);
     *
     * ---------------------------------------------------------------------- */

    /**
     * Simple translator that replaces the character 'x' with the string "y".
     * For any other character it returns {@code 0} so that the default
     * implementation writes the original character.
     */
    private static class SimpleReplaceXTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            char ch = input.charAt(index);
            if (ch == 'x') {
                out.write('y');
                return 1; // consumed one code point
            }
            return 0; // let the base class write the original character
        }
    }

    /**
     * No‑op translator – never consumes any characters.
     * The base class will therefore write the input unchanged.
     */
    private static class NoOpTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) {
            return 0;
        }
    }

    /**
     * Translator that replaces the Unicode code‑point U+1F600 (😀) with "[SMILE]".
     */
    private static class EmojiTranslator extends CharSequenceTranslator {
        private static final int SMILEY = 0x1F600; // 😀
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == SMILEY) {
                out.write("[SMILE]");
                // The code point may be represented by a surrogate pair (2 chars)
                return Character.charCount(cp);
            }
            return 0;
        }
    }

    /**
     * Translator that replaces the character 'a' with "A".
     */
    private static class ReplaceATranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            if (input.charAt(index) == 'a') {
                out.write('A');
                return 1;
            }
            return 0;
        }
    }

    /**
     * Translator that replaces the character 'b' with "B".
     */
    private static class ReplaceBTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            if (input.charAt(index) == 'b') {
                out.write('B');
                return 1;
            }
            return 0;
        }
    }

    /* ----------------------------------------------------------------------
     *  Basic functionality tests
     * ---------------------------------------------------------------------- */

    @Test
    public void testTranslateString_NullInput_ReturnsNull() {
        CharSequenceTranslator translator = new NoOpTranslator();
        Assert.assertNull("translate(null) should return null", translator.translate((CharSequence) null));
    }

    @Test
    public void testTranslateString_EmptyInput_ReturnsEmptyString() {
        CharSequenceTranslator translator = new NoOpTranslator();
        Assert.assertEquals("Empty input must yield empty output",
                "", translator.translate(""));
    }

    @Test
    public void testTranslateString_SimpleReplacement() {
        CharSequenceTranslator translator = new SimpleReplaceXTranslator();
        String input = "axcxdx";
        String expected = "aycydy";
        Assert.assertEquals("Simple 'x' → 'y' replacement failed",
                expected, translator.translate(input));
    }

    @Test
    public void testTranslateWriter_NullWriter_ThrowsIllegalArgumentException() {
        CharSequenceTranslator translator = new NoOpTranslator();
        try {
            translator.translate("test", (Writer) null);
            Assert.fail("Expected IllegalArgumentException when Writer is null");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (IOException e) {
            Assert.fail("Did not expect IOException");
        }
    }

    @Test
    public void testTranslateWriter_SimpleReplacement() throws IOException {
        CharSequenceTranslator translator = new SimpleReplaceXTranslator();
        StringWriter writer = new StringWriter();
        translator.translate("axc", writer);
        Assert.assertEquals("Writer overload should produce same result as translate(String)",
                "ayc", writer.toString());
    }

    @Test
    public void testTranslateWriter_NoOpLeavesInputUnchanged() throws IOException {
        CharSequenceTranslator translator = new NoOpTranslator();
        StringWriter writer = new StringWriter();
        String input = "Hello World!";
        translator.translate(input, writer);
        Assert.assertEquals("NoOp translator must write the original input unchanged",
                input, writer.toString());
    }

    @Test
    public void testTranslateWriter_SurrogatePairReplacement() throws IOException {
        CharSequenceTranslator translator = new EmojiTranslator();
        StringWriter writer = new StringWriter();
        // Input contains the 😀 emoji (represented by a surrogate pair)
        String input = "Smile: \uD83D\uDE00!";
        translator.translate(input, writer);
        Assert.assertEquals("Emoji should be replaced with [SMILE]",
                "Smile: [SMILE]!", writer.toString());
    }

    @Test
    public void testWith_MergesTranslatorsInCorrectOrder() {
        CharSequenceTranslator aTranslator = new ReplaceATranslator();
        CharSequenceTranslator bTranslator = new ReplaceBTranslator();

        // Merge aTranslator with bTranslator (a first, then b)
        CharSequenceTranslator merged = aTranslator.with(bTranslator);

        String input = "ababa";
        String expected = "ABAB A".replace(" ", ""); // "ABAB A" -> "ABAB A"? Actually each 'a'->'A', each 'b'->'B'
        expected = "ABAB A".replace(" ", ""); // simplifies to "ABAB A"? Let's compute directly:
        // input: a b a b a
        // after merge: A B A B A => "ABABA"
        expected = "ABABA";

        Assert.assertEquals("Merged translator should apply both replacements in order",
                expected, merged.translate(input));
    }

    @Test
    public void testWith_NoAdditionalTranslators_ReturnsSameBehaviour() {
        CharSequenceTranslator original = new SimpleReplaceXTranslator();
        CharSequenceTranslator merged = original.with(); // no extra translators

        String input = "xax";
        String expected = "yay"; // same as original behaviour
        Assert.assertEquals("with() with zero arguments must behave like the original translator",
                expected, merged.translate(input));
    }

    @Test
    public void testHex_UpperCaseConversion() {
        Assert.assertEquals("FF, uppercase expected", "FF", CharSequenceTranslator.hex(255));
        Assert.assertEquals("1F600 for 😀 code point", "1F600", CharSequenceTranslator.hex(0x1F600));
    }

    @Test
    public void testHex_NegativeCodepoint() {
        // The method does not forbid negative values; it should simply prepend a minus sign.
        Assert.assertEquals("-1A", CharSequenceTranslator.hex(-26));
    }

    /* ----------------------------------------------------------------------
     *  Additional edge‑case tests for the core translate(CharSequence,Writer)
     * ---------------------------------------------------------------------- */

    @Test
    public void testTranslateWriter_MultipleCodePointsConsumed() throws IOException {
        // Translator that consumes two characters at once (e.g., "ab" → "Z")
        CharSequenceTranslator doubleConsumer = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (index + 1 < input.length()
                        && input.charAt(index) == 'a'
                        && input.charAt(index + 1) == 'b') {
                    out.write('Z');
                    return 2; // consumed two code points
                }
                return 0;
            }
        };
        StringWriter writer = new StringWriter();
        doubleConsumer.translate("ababc", writer);
        // Expected: "Z" for first "ab", then "a" (unchanged), then "b" (unchanged), then "c"
        Assert.assertEquals("Zabc", writer.toString());
    }

    @Test
    public void testTranslateWriter_HandlesFullSurrogatePairAdvancement() throws IOException {
        // Translator that consumes the whole surrogate pair and replaces it with "*"
        CharSequenceTranslator surrogateConsumer = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                int cp = Character.codePointAt(input, index);
                if (Character.isSupplementaryCodePoint(cp)) {
                    out.write('*');
                    return Character.charCount(cp); // 2 chars
                }
                return 0;
            }
        };
        StringWriter writer = new StringWriter();
        // Input: "A😀B" (A + surrogate pair + B)
        String input = "A\uD83D\uDE00B";
        surrogateConsumer.translate(input, writer);
        Assert.assertEquals("A*B", writer.toString());
    }

}
