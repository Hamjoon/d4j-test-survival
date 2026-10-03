###Test START##
```java
/**
 * JUnit 4 test suite for {@link org.apache.commons.lang3.text.translate.CharSequenceTranslator}.
 *
 * The tests cover:
 * <ul>
 *   <li>Null handling for both {@code translate(CharSequence)} and {@code translate(CharSequence, Writer)}</li>
 *   <li>Correct translation of simple characters and surrogate pairs</li>
 *   <li>Behaviour when a translator returns {@code 0} (fallback copy)</li>
 *   <li>Exception handling for a {@code null} Writer</li>
 *   <li>The {@code with(...)} merger functionality</li>
 *   <li>The static {@code hex(int)} utility</li>
 * </ul>
 *
 * Because {@code CharSequenceTranslator} is abstract, a minimal concrete implementation
 * {@code SimpleTranslator} is defined inside the test class.  It translates:
 * <ul>
 *   <li>'a' → "A"</li>
 *   <li>'b' → "B"</li>
 *   <li>the surrogate pair representing U+1F600 (😀) → "[SMILE]"</li>
 * </ul>
 *
 * The translator deliberately returns {@code 0} for any other character, which forces the
 * base class to copy the original code point to the output.
 */
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

import org.junit.Test;

/**
 * Test cases for {@link CharSequenceTranslator}.
 */
public class CharSequenceTranslatorTest {

    /**
     * Minimal concrete translator used for the tests.
     * <p>
     * It translates:
     * <ul>
     *   <li>'a' → "A"</li>
     *   <li>'b' → "B"</li>
     *   <li>U+1F600 (😀) → "[SMILE]"</li>
     * </ul>
     * For any other character it returns {@code 0} so the base class copies the input.
     */
    private static class SimpleTranslator extends CharSequenceTranslator {

        private static final int SMILEY_CODEPOINT = 0x1F600; // 😀

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == 'a') {
                out.write('A');
                return 1;
            }
            if (cp == 'b') {
                out.write('B');
                return 1;
            }
            if (cp == SMILEY_CODEPOINT) {
                out.write("[SMILE]");
                // The smiley is a surrogate pair → consumes 2 char units
                return Character.charCount(cp);
            }
            // No translation – signal to base class to copy the original code point
            return 0;
        }
    }

    private final CharSequenceTranslator translator = new SimpleTranslator();

    /* -------------------------------------------------------------
     *  Tests for translate(CharSequence)
     * ------------------------------------------------------------- */

    @Test
    public void testTranslate_NullInput_ReturnsNull() {
        assertNull("translate(null) must return null", translator.translate((CharSequence) null));
    }

    @Test
    public void testTranslate_SimpleString() {
        String input = "abc";
        // expected: 'a'→"A", 'b'→"B", 'c' copied unchanged
        String expected = "ABc";
        assertEquals(expected, translator.translate(input));
    }

    @Test
    public void testTranslate_WithSurrogatePair() {
        // Build a string containing the 😀 emoji (U+1F600)
        String input = "x\uD83D\uDE00y"; // 'x' + smiley + 'y'
        // Expected: x unchanged, smiley → "[SMILE]", y unchanged
        String expected = "x[SMILE]y";
        assertEquals(expected, translator.translate(input));
    }

    @Test
    public void testTranslate_MixedCharacters() {
        String input = "ab\uD83D\uDE00c";
        // a→A, b→B, 😀→[SMILE], c unchanged
        String expected = "AB[SMILE]c";
        assertEquals(expected, translator.translate(input));
    }

    /* -------------------------------------------------------------
     *  Tests for translate(CharSequence, Writer)
     * ------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testTranslateToWriter_NullWriter_Throws() throws IOException {
        translator.translate("test", (Writer) null);
    }

    @Test
    public void testTranslateToWriter_NullInput_WritesNothing() throws IOException {
        StringWriter sw = new StringWriter();
        translator.translate((CharSequence) null, sw);
        assertEquals("", sw.toString());
    }

    @Test
    public void testTranslateToWriter_UsesProvidedWriter() throws IOException {
        StringWriter sw = new StringWriter();
        translator.translate("ab", sw);
        assertEquals("AB", sw.toString());
    }

    @Test
    public void testTranslateToWriter_SurrogatePairConsumedCorrectly() throws IOException {
        StringWriter sw = new StringWriter();
        String input = "\uD83D\uDE00"; // 😀 (single surrogate pair)
        translator.translate(input, sw);
        assertEquals("[SMILE]", sw.toString());
    }

    @Test
    public void testTranslateToWriter_FallbackCopyBehaviour() throws IOException {
        StringWriter sw = new StringWriter();
        // 'z' is not handled by SimpleTranslator → fallback copy should write 'z'
        translator.translate("z", sw);
        assertEquals("z", sw.toString());
    }

    /* -------------------------------------------------------------
     *  Tests for with(...) merger functionality
     * ------------------------------------------------------------- */

    @Test
    public void testWith_MergesTranslatorsInOrder() {
        // Translator that changes 'c' → "C"
        CharSequenceTranslator cToC = new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (input.charAt(index) == 'c') {
                    out.write('C');
                    return 1;
                }
                return 0;
            }
        };

        // Merge: first our SimpleTranslator (a,b,smiley) then cToC
        CharSequenceTranslator merged = translator.with(cToC);

        // Input contains a, b, c and the smiley
        String input = "abc\uD83D\uDE00";
        // Expected: a→A, b→B, c→C, smiley→[SMILE]
        String expected = "ABC[SMILE]";
        assertEquals(expected, merged.translate(input));
    }

    @Test
    public void testWith_EmptyArrayReturnsSameInstanceWrappedInAggregate() {
        CharSequenceTranslator merged = translator.with();
        assertNotNull(merged);
        // The merged translator should still translate 'a' → "A"
        assertEquals("A", merged.translate("a"));
    }

    /* -------------------------------------------------------------
     *  Tests for static hex(int) utility
     * ------------------------------------------------------------- */

    @Test
    public void testHex_BasicValues() {
        assertEquals("0", CharSequenceTranslator.hex(0));
        assertEquals("7F", CharSequenceTranslator.hex(0x7F));
        assertEquals("80", CharSequenceTranslator.hex(0x80));
        assertEquals("FF", CharSequenceTranslator.hex(0xFF));
        assertEquals("100", CharSequenceTranslator.hex(0x100));
    }

    @Test
    public void testHex_UpperCaseAndLocaleIndependence() {
        Locale defaultLocale = Locale.getDefault();
        try {
            // Switch to a locale that uses commas for decimal separator to ensure
            // the method does not depend on default locale.
            Locale.setDefault(Locale.GERMANY);
            assertEquals("1F600", CharSequenceTranslator.hex(0x1F600));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testHex_NegativeCodepoint() {
        // The method does not guard against negative values; it simply uses
        // Integer.toHexString, which yields a two's‑complement representation.
        assertEquals("FFFFFFFF", CharSequenceTranslator.hex(-1));
    }
}
```
###Test END##