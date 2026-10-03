###Test START##
```java
/*
 * JUnit 4 test suite for {@link org.apache.commons.lang3.text.translate.NumericEntityUnescaper}.
 *
 * The tests cover:
 *   • Typical decimal and hexadecimal numeric entities (with and without a trailing ';')
 *   • Upper‑case “X” hex prefix
 *   • Supplementary Unicode code points (requiring surrogate pairs)
 *   • Invalid numeric formats (NumberFormatException handling)
 *   • Non‑numeric sequences (should not be translated)
 *   • Edge cases where the terminating ';' is missing – the current implementation
 *     throws an {@link StringIndexOutOfBoundsException}; the test documents this
 *     behaviour.
 *
 * The tests use a {@link java.io.StringWriter} as the output destination.
 */
package org.apache.commons.lang3.text.translate;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Test;
import static org.junit.Assert.*;

public class NumericEntityUnescaperTest {

    private final NumericEntityUnescaper unescaper = new NumericEntityUnescaper();

    /** Helper that invokes {@code translate} starting at index 0 and returns the result. */
    private int translate(CharSequence input, StringWriter out) throws IOException {
        return unescaper.translate(input, 0, out);
    }

    @Test
    public void testDecimalEntityWithSemicolon() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = translate("&#65;", out);
        assertEquals("A", out.toString());
        // 2 characters for '&#', 2 digits, 0 extra for hex flag, 1 for ';' → 5
        assertEquals(5, consumed);
    }

    @Test
    public void testDecimalEntityWithoutSemicolon_ShouldThrowIndexOutOfBounds() {
        StringWriter out = new StringWriter();
        try {
            translate("&#65", out);
            fail("Expected StringIndexOutOfBoundsException because the terminating ';' is missing");
        } catch (StringIndexOutOfBoundsException e) {
            // Expected – the current implementation does not handle missing ';' correctly.
        } catch (IOException e) {
            fail("Unexpected IOException: " + e);
        }
    }

    @Test
    public void testHexEntityWithSemicolon() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = translate("&#x41;", out);
        assertEquals("A", out.toString());
        // 2 ('&#') + 2 ('41') + 1 (hex prefix) + 1 (';') = 6
        assertEquals(6, consumed);
    }

    @Test
    public void testHexEntityUpperCaseX() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = translate("&#X41;", out);
        assertEquals("A", out.toString());
        assertEquals(6, consumed);
    }

    @Test
    public void testHexEntityWithoutSemicolon_ShouldThrowIndexOutOfBounds() {
        StringWriter out = new StringWriter();
        try {
            translate("&#x41", out);
            fail("Expected StringIndexOutOfBoundsException because the terminating ';' is missing");
        } catch (StringIndexOutOfBoundsException e) {
            // Expected
        } catch (IOException e) {
            fail("Unexpected IOException: " + e);
        }
    }

    @Test
    public void testSupplementaryUnicodeEntity() throws IOException {
        // U+1F600 (GRINNING FACE) → surrogate pair: \uD83D\uDE00
        StringWriter out = new StringWriter();
        int consumed = translate("&#x1F600;", out);
        assertEquals("\uD83D\uDE00", out.toString());
        // 2 ('&#') + 5 ('1F600') + 1 (hex prefix) + 1 (';') = 9
        assertEquals(9, consumed);
    }

    @Test
    public void testInvalidNumberFormat() throws IOException {
        // The substring after '&#' is not a valid integer → NumberFormatException is caught.
        StringWriter out = new StringWriter();
        int consumed = translate("&#xyz;", out);
        // No characters should be written and the method should return 0.
        assertEquals("", out.toString());
        assertEquals(0, consumed);
    }

    @Test
    public void testNonNumericEntityIsIgnored() throws IOException {
        // Regular XML entity – not numeric – should be ignored.
        StringWriter out = new StringWriter();
        int consumed = translate("&amp;", out);
        assertEquals("", out.toString());
        assertEquals(0, consumed);
    }

    @Test
    public void testEntityEmbeddedInLongerString() throws IOException {
        // The translator works only when the index points at the start of an entity.
        String input = "Hello &#65; World";
        StringWriter out = new StringWriter();

        // Translate at index 0 – not an entity, should return 0 and write nothing.
        int consumed0 = unescaper.translate(input, 0, out);
        assertEquals(0, consumed0);
        assertEquals("", out.toString());

        // Translate at the position of the entity (6)
        int consumed = unescaper.translate(input, 6, out);
        assertEquals(5, consumed); // '&#65;' → 5 characters consumed
        assertEquals("A", out.toString());
    }

    @Test
    public void testZeroCodePoint() throws IOException {
        // &#0; is a legal numeric entity representing the NUL character.
        StringWriter out = new StringWriter();
        int consumed = translate("&#0;", out);
        assertEquals("\u0000", out.toString());
        assertEquals(4, consumed); // '&#0;' → 4 characters
    }

    @Test
    public void testMaximumBmpCodePoint() throws IOException {
        // &#65535; (0xFFFF) is the highest BMP code point.
        StringWriter out = new StringWriter();
        int consumed = translate("&#65535;", out);
        assertEquals("\uFFFF", out.toString());
        assertEquals(8, consumed); // '&#65535;' → 8 characters
    }

    @Test
    public void testNegativeCodePointIsRejected() throws IOException {
        // Negative numbers are not valid; parsing will fail.
        StringWriter out = new StringWriter();
        int consumed = translate("&#-1;", out);
        assertEquals("", out.toString());
        assertEquals(0, consumed);
    }
}
```
###Test END##