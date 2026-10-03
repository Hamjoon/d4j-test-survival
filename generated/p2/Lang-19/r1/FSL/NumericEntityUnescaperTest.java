package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Test;

/**
 * Comprehensive JUnit 4 tests for {@link NumericEntityUnescaper}.
 * The tests cover:
 * <ul>
 *   <li>Decimal and hexadecimal numeric entities (with and without a terminating ';')</li>
 *   <li>Entities that map to a single UTF‑16 code unit and to a surrogate pair</li>
 *   <li>Invalid entities that trigger {@code NumberFormatException}</li>
 *   <li>Input that does not start an entity – method must return {@code 0}</li>
 *   <li>Translation starting at an arbitrary index inside a larger string</li>
 *   <li>Behaviour when the terminating ';' is missing (expect {@link StringIndexOutOfBoundsException})</li>
 * </ul>
 */
public class NumericEntityUnescaperTest {

    private final NumericEntityUnescaper unescaper = new NumericEntityUnescaper();

    /**
     * Helper that invokes {@code translate} and returns the characters written to the writer.
     */
    private static String translate(CharSequence input, int index) throws IOException {
        StringWriter out = new StringWriter();
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        int consumed = unescaper.translate(input, index, out);
        // sanity – the method should never consume a negative number of characters
        assertTrue("Consumed length must be non‑negative", consumed >= 0);
        return out.toString();
    }

    /*** 1. Simple decimal entity with terminating ';' ***/
    @Test
    public void testDecimalEntityWithSemicolon() throws IOException {
        String result = translate("&#65;", 0); // 65 decimal = 'A'
        assertEquals("A", result);
    }

    /*** 2. Decimal entity without terminating ';' (the code loops until ';' – expects OOB) ***/
    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testDecimalEntityWithoutSemicolonThrows() throws IOException {
        // No ';' present – the while‑loop will read past the end of the string.
        translate("&#66", 0);
    }

    /*** 3. Hexadecimal entity (lower‑case x) with semicolon ***/
    @Test
    public void testHexEntityLowerCase() throws IOException {
        String result = translate("&#x41;", 0); // 0x41 = 'A'
        assertEquals("A", result);
    }

    /*** 4. Hexadecimal entity (upper‑case X) with semicolon ***/
    @Test
    public void testHexEntityUpperCase() throws IOException {
        String result = translate("&#X41;", 0);
        assertEquals("A", result);
    }

    /*** 5. Hexadecimal entity without semicolon – expect OOB ***/
    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testHexEntityWithoutSemicolonThrows() throws IOException {
        translate("&#x42", 0);
    }

    /*** 6. Entity that resolves to a surrogate pair (code point > 0xFFFF) ***/
    @Test
    public void testSurrogatePairEntity() throws IOException {
        // U+1F600 = 😀 (GRINNING FACE) – decimal 128512
        String result = translate("&#128512;", 0);
        assertEquals("\uD83D\uDE00", result);
        // Verify the length is 2 (a surrogate pair)
        assertEquals(2, result.length());
    }

    /*** 7. Hexadecimal surrogate‑pair entity ***/
    @Test
    public void testHexSurrogatePairEntity() throws IOException {
        // U+1F601 = 😁 (GRINNING FACE WITH SMILING EYES) – hex 1F601
        String result = translate("&#x1F601;", 0);
        assertEquals("\uD83D\uDE01", result);
    }

    /*** 8. Invalid numeric value – non‑numeric characters after &# ***/
    @Test
    public void testInvalidNumberFormatReturnsZero() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = unescaper.translate("&#XYZ;", 0, out);
        // The method catches NumberFormatException and returns 0
        assertEquals(0, consumed);
        // Nothing should be written to the writer
        assertEquals("", out.toString());
    }

    /*** 9. Empty numeric part (e.g., "&#;") – also triggers NumberFormatException ***/
    @Test
    public void testEmptyNumericPart() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = unescaper.translate("&#;", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    /*** 10. Input that does not start an entity – method must return 0 ***/
    @Test
    public void testNonEntityInput() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = unescaper.translate("Hello & world", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    /*** 11. Translation starting at a non‑zero index inside a larger string ***/
    @Test
    public void testTranslationWithOffset() throws IOException {
        String input = "foo&#66;bar"; // '&#66;' = 'B'
        StringWriter out = new StringWriter();
        // Start at index 3 where '&' begins
        int consumed = unescaper.translate(input, 3, out);
        assertEquals(5, consumed); // '&' + '#' + '66' + ';' = 5 characters
        assertEquals("B", out.toString());

        // The surrounding characters remain untouched – we verify by concatenating manually
        String rebuilt = input.substring(0, 3) + out.toString() + input.substring(3 + consumed);
        assertEquals("fooBbar", rebuilt);
    }

    /*** 12. Verify the returned character count matches the actual consumed characters ***/
    @Test
    public void testReturnedCountMatchesConsumed() throws IOException {
        // Decimal entity
        StringWriter out1 = new StringWriter();
        int count1 = unescaper.translate("&#1234;", 0, out1);
        // 2 for '&#', 4 digits, 1 for ';' = 7
        assertEquals(7, count1);
        assertEquals("\u04D2", out1.toString()); // 1234 decimal = 'Ӓ'

        // Hex entity with leading 'x' flag
        StringWriter out2 = new StringWriter();
        int count2 = unescaper.translate("&#x1A2B;", 0, out2);
        // 2 for '&#', 1 for 'x', 4 hex digits, 1 for ';' = 8
        assertEquals(8, count2);
        assertEquals("\u1A2B", out2.toString());
    }

    /*** 13. Entity with extra characters after semicolon – translator should stop at ';' ***/
    @Test
    public void testEntityFollowedByExtraChars() throws IOException {
        String input = "&#65;XYZ";
        StringWriter out = new StringWriter();
        int consumed = unescaper.translate(input, 0, out);
        assertEquals(5, consumed); // stops at ';'
        assertEquals("A", out.toString());

        // Remaining characters stay untouched
        assertEquals("XYZ", input.substring(consumed));
    }
}
