###Test START##
```java
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

/**
 * Unit tests for {@link CharSequenceTranslator}.
 *
 * The tests focus on the concrete behaviour supplied by the abstract class:
 * <ul>
 *   <li>{@link CharSequenceTranslator#translate(CharSequence)}</li>
 *   <li>{@link CharSequenceTranslator#translate(CharSequence, Writer)}</li>
 *   <li>{@link CharSequenceTranslator#with(CharSequenceTranslator...)}</li>
 *   <li>{@link CharSequenceTranslator#hex(int)}</li>
 * </ul>
 *
 * A minimal concrete implementation is provided for each test scenario.
 */
public class CharSequenceTranslatorTest {

    /**--------------------------------------------------------------------
     * Helper concrete translators used throughout the test suite.
     * -------------------------------------------------------------------*/

    /** Translator that never consumes any characters – forces the fallback path. */
    private static final CharSequenceTranslator NOOP_TRANSLATOR = new CharSequenceTranslator() {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            // claim to have done nothing – the base class will write the original char(s)
            return 0;
        }
    };

    /** Translator that replaces a single character with a string. */
    private static CharSequenceTranslator replace(final char target, final String replacement) {
        return new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) throws IOException {
                if (input.charAt(index) == target) {
                    out.write(replacement);
                    return 1; // consumed one code point
                }
                return 0; // let the base class copy the original char
            }
        };
    }

    /** Translator that consumes a surrogate pair (emoji) and writes a placeholder. */
    private static final CharSequenceTranslator EMOJI_TRANSLATOR = new CharSequenceTranslator() {
        private static final int GRINNING_FACE = 0x1F600; // 😀
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int cp = Character.codePointAt(input, index);
            if (cp == GRINNING_FACE) {
                out.write("[smile]");
                // The code point may be represented by two char units, so report the number of *code points* consumed (1)
                return 1;
            }
            return 0;
        }
    };

    /**--------------------------------------------------------------------
     * Tests for CharSequenceTranslator.translate(CharSequence)
     * -------------------------------------------------------------------*/

    @Test
    public void testTranslateNullInputReturnsNull() {
        assertNull(new CharSequenceTranslator() {
            @Override
            public int translate(CharSequence input, int index, Writer out) {
                // never called – translate(null) should short‑circuit
                fail("translate should not be invoked for null input");
                return 0;
            }
        }.translate((CharSequence) null));
    }

    @Test
    public void testTranslateUsesWriterFallbackWhenZeroConsumed() throws IOException {
        // Input contains a plain ASCII char and a surrogate pair (emoji)
        String input = "A\uD83D\uDE00B"; // A😀B
        StringWriter writer = new StringWriter();

        // NOOP_TRANSLATOR always returns 0 → the base class must write the original characters
        NOOP_TRANSLATOR.translate(input, writer);
        assertEquals("A\uD83D\uDE00B", writer.toString());
    }

    @Test
    public void testTranslateWritesReplacedCharacters() {
        CharSequenceTranslator translator = replace('x', "xyz");
        String result = translator.translate("axbx");
        // Expected: a → a (unchanged), x → xyz, b → b, x → xyz
        assertEquals("axyzbxyz", result);
    }

    @Test
    public void testTranslateHandlesSurrogatePairsCorrectly() {
        // The string contains a single emoji (grinning face) surrounded by letters
        String input = "pre\uD83D\uDE00post";
        // EMOJI_TRANSLATOR should replace the emoji with "[smile]"
        String result = EMOJI_TRANSLATOR.translate(input);
        assertEquals("pre[smile]post", result);
    }

    /**--------------------------------------------------------------------
     * Tests for CharSequenceTranslator.translate(CharSequence, Writer)
     * -------------------------------------------------------------------*/

    @Test(expected = IllegalArgumentException.class)
    public void testTranslateToWriter_NullWriterThrows() throws IOException {
        CharSequenceTranslator translator = replace('a', "A");
        translator.translate("any", (Writer) null);
    }

    @Test
    public void testTranslateToWriter_NullInputNoExceptionAndNoOutput() throws IOException {
        CharSequenceTranslator translator = replace('a', "A");
        StringWriter writer = new StringWriter();
        translator.translate(null, writer);
        assertEquals("", writer.toString()); // nothing should be written
    }

    @Test
    public void testTranslateToWriter_DelegatesToAbstractMethod() throws IOException {
        CharSequenceTranslator translator = replace('z', "ZZ");
        StringWriter writer = new StringWriter();
        translator.translate("azb", writer);
        assertEquals("aZZb", writer.toString());
    }

    @Test
    public void testTranslateToWriter_MultipleCodePointsAndSurrogates() throws IOException {
        // Combine a simple replacer with the emoji translator using `with`
        CharSequenceTranslator combined = replace('a', "A")
                .with(EMOJI_TRANSLATOR);

        String input = "a\uD83D\uDE00a"; // a😀a
        StringWriter writer = new StringWriter();
        combined.translate(input, writer);
        // a → A, 😀 → [smile], a → A
        assertEquals("A[smile]A", writer.toString());
    }

    /**--------------------------------------------------------------------
     * Tests for CharSequenceTranslator.with(...)
     * -------------------------------------------------------------------*/

    @Test
    public void testWithCreatesAggregateTranslatorInCorrectOrder() {
        CharSequenceTranslator t1 = replace('1', "ONE");
        CharSequenceTranslator t2 = replace('2', "TWO");
        CharSequenceTranslator merged = t1.with(t2); // t1 first, then t2

        // "12" -> "ONE" from t1, then "TWO" from t2
        assertEquals("ONETWO", merged.translate("12"));
    }

    @Test
    public void testWithDoesNotAffectOriginalTranslators() {
        CharSequenceTranslator original = replace('x', "X");
        CharSequenceTranslator merged = original.with(replace('y', "Y"));

        // Original should still only replace 'x'
        assertEquals("Xy", original.translate("xy"));
        // Merged should replace both
        assertEquals("XY", merged.translate("xy"));
    }

    /**--------------------------------------------------------------------
     * Tests for static CharSequenceTranslator.hex(int)
     * -------------------------------------------------------------------*/

    @Test
    public void testHexZero() {
        assertEquals("0", CharSequenceTranslator.hex(0));
    }

    @Test
    public void testHexPositiveValues() {
        assertEquals("A", CharSequenceTranslator.hex(10));
        assertEquals("FF", CharSequenceTranslator.hex(255));
        assertEquals("1F600", CharSequenceTranslator.hex(0x1F600)); // 😀
    }

    @Test
    public void testHexIsUpperCaseAndLocaleIndependent() {
        // Force a locale with different case rules, e.g., Turkish
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("DEAD", CharSequenceTranslator.hex(0xdead));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

}
```
###Test END##