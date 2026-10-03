###Test START##
```java
/*
 * Unit tests for {@link org.apache.commons.lang3.text.translate.NumericEntityUnescaper}.
 *
 * These tests cover:
 * <ul>
 *   <li>Decimal numeric entities (with and without semicolon).</li>
 *   <li>Hexadecimal numeric entities (upper‑ and lower‑case).</li>
 *   <li>Entities that map to surrogate pairs (code points > 0xFFFF).</li>
 *   <li>Invalid numeric entities – non‑numeric content, missing digits, etc.</li>
 *   <li>Input that is not an entity at all.</li>
 *   <li>Correct return value (number of consumed characters).</li>
 * </ul>
 *
 * The tests use JUnit 4.
 */
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Test;

/**
 * Test suite for {@link NumericEntityUnescaper}.
 */
public class NumericEntityUnescaperTest {

    private final NumericEntityUnescaper unescaper = new NumericEntityUnescaper();

    /**
     * Helper that calls {@code translate} starting at index 0 and returns the
     * written string.
     */
    private String translate(String input, int[] consumed) throws IOException {
        StringWriter out = new StringWriter();
        int len = unescaper.translate(input, 0, out);
        if (consumed != null && consumed.length > 0) {
            consumed[0] = len;
        }
        return out.toString();
    }

    @Test
    public void testDecimalEntityWithSemicolon() throws IOException {
        int[] consumed = new int[1];
        String result = translate("&#65;", consumed);
        assertEquals("A", result);
        // '&' + '#' + digits (2) + ';' = 5 characters consumed
        assertEquals(5, consumed[0]);
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testDecimalEntityWithoutSemicolon() throws IOException {
        // The implementation loops until it finds ';', so without it we expect
        // an IndexOutOfBoundsException.
        translate("&#65", null);
    }

    @Test
    public void testHexEntityUpperCase() throws IOException {
        int[] consumed = new int[1];
        String result = translate("&#X41;", consumed);
        assertEquals("A", result);
        // '&' + '#' + 'X' + digits (2) + ';' = 6 characters consumed
        assertEquals(6, consumed[0]);
    }

    @Test
    public void testHexEntityLowerCase() throws IOException {
        int[] consumed = new int[1];
        String result = translate("&#x41;", consumed);
        assertEquals("A", result);
        // same length as upper case
        assertEquals(6, consumed[0]);
    }

    @Test
    public void testEntityBeyondBMP() throws IOException {
        // 😀 is U+1F600, which is > 0xFFFF and requires a surrogate pair.
        int[] consumed = new int[1];
        String result = translate("&#x1F600;", consumed);
        assertEquals("\uD83D\uDE00", result);
        // '&' + '#' + 'x' + digits (5) + ';' = 8 characters consumed
        assertEquals(8, consumed[0]);
    }

    @Test
    public void testInvalidNumberReturnsZeroAndWritesNothing() throws IOException {
        StringWriter out = new StringWriter();
        int len = unescaper.translate("&#xyz;", 0, out);
        // The translator catches NumberFormatException and returns 0.
        assertEquals(0, len);
        assertEquals("", out.toString());
    }

    @Test
    public void testNonEntityInputReturnsZero() throws IOException {
        StringWriter out = new StringWriter();
        int len = unescaper.translate("Hello & world", 0, out);
        assertEquals(0, len);
        assertEquals("", out.toString());
    }

    @Test
    public void testEntityNotAtStart() throws IOException {
        // The translator should only act when the index points at '&'.
        String input = "foo &#66; bar";
        StringWriter out = new StringWriter();

        // Call translate at the position of the first space (index 3)
        int len = unescaper.translate(input, 3, out);
        assertEquals(0, len);
        assertEquals("", out.toString());

        // Now translate at the correct index (4)
        int[] consumed = new int[1];
        String result = translate(input.substring(4), consumed);
        assertEquals("B", result);
        assertEquals(5, consumed[0]); // "&#66;" length
    }

    @Test
    public void testEntityWithZeroValue() throws IOException {
        // &#0; is a valid numeric entity that maps to the null character.
        int[] consumed = new int[1];
        String result = translate("&#0;", consumed);
        assertEquals("\u0000", result);
        assertEquals(5, consumed[0]);
    }

    @Test
    public void testEntityWithLeadingZeros() throws IOException {
        int[] consumed = new int[1];
        String result = translate("&#00065;", consumed);
        assertEquals("A", result);
        // '&' + '#' + digits (5) + ';' = 7 characters consumed
        assertEquals(7, consumed[0]);
    }

    @Test
    public void testHexEntityWithLeadingZeros() throws IOException {
        int[] consumed = new int[1];
        String result = translate("&#x00041;", consumed);
        assertEquals("A", result);
        // '&' + '#' + 'x' + digits (5) + ';' = 9 characters consumed
        assertEquals(9, consumed[0]);
    }

    @Test
    public void testEntityWithNegativeNumberIsRejected() throws IOException {
        // The parser uses Integer.parseInt which throws NumberFormatException
        // for a leading '-'.
        StringWriter out = new StringWriter();
        int len = unescaper.translate("&#-65;", 0, out);
        assertEquals(0, len);
        assertEquals("", out.toString());
    }

    @Test
    public void testEntityWithWhitespaceInsideIsRejected() throws IOException {
        // Whitespace is not a valid part of the numeric value.
        StringWriter out = new StringWriter();
        int len = unescaper.translate("&#6 5;", 0, out);
        assertEquals(0, len);
        assertEquals("", out.toString());
    }
}
```
###Test END##