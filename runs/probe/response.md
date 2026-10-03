###Test START##
/*
 * Unit tests for {@link org.apache.commons.lang3.text.translate.NumericEntityUnescaper}.
 *
 * These tests are written for JUnit 4 and aim to cover all logical paths
 * inside the {@code translate} method, including normal operation,
 * hexadecimal handling, error handling and edge‑cases.
 */
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;

/**
 * Comprehensive test suite for {@link NumericEntityUnescaper}.
 */
public class NumericEntityUnescaperTest {

    private NumericEntityUnescaper unescaper;
    private StringWriter writer;

    @Before
    public void setUp() {
        unescaper = new NumericEntityUnescaper();
        writer = new StringWriter();
    }

    /**
     * Helper that invokes {@code translate} and returns the number of characters
     * consumed. The writer content is left in {@link #writer}.
     */
    private int translate(String input, int index) throws IOException {
        return unescaper.translate(input, index, writer);
    }

    @Test
    public void testDecimalEntity() throws IOException {
        String input = "&#65;";
        int consumed = translate(input, 0);
        assertEquals("Should consume the whole entity", 5, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testHexEntityLowerCaseX() throws IOException {
        String input = "&#x41;";
        int consumed = translate(input, 0);
        assertEquals("Should consume the whole entity", 6, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testHexEntityUpperCaseX() throws IOException {
        String input = "&#X41;";
        int consumed = translate(input, 0);
        assertEquals("Should consume the whole entity", 6, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testMultipleEntitiesOnlyFirstProcessed() throws IOException {
        String input = "&#65;&#66;";
        int consumed = translate(input, 0);
        // Only the first entity should be processed; length of first entity = 5
        assertEquals(5, consumed);
        assertEquals("A", writer.toString());

        // Reset writer and translate the second entity starting at index 5
        writer.getBuffer().setLength(0);
        consumed = translate(input, 5);
        assertEquals(5, consumed);
        assertEquals("B", writer.toString());
    }

    @Test
    public void testNonEntityStartsWithAmpersand() throws IOException {
        String input = "&notanentity;";
        int consumed = translate(input, 0);
        assertEquals("Should not treat as numeric entity", 0, consumed);
        assertEquals("", writer.toString());
    }

    @Test
    public void testInvalidNumberFormatReturnsZero() throws IOException {
        String input = "&#xyz;";
        int consumed = translate(input, 0);
        assertEquals("Invalid numeric part should result in 0", 0, consumed);
        assertEquals("Writer must stay untouched", "", writer.toString());
    }

    @Test
    public void testEmptyNumericPartReturnsZero() throws IOException {
        String input = "&#;";
        int consumed = translate(input, 0);
        assertEquals("Empty numeric part should result in 0", 0, consumed);
        assertEquals("", writer.toString());
    }

    @Test
    public void testIndexNotAtEntityReturnsZero() throws IOException {
        String input = "foo&#65;";
        int consumed = translate(input, 0); // 'f' at index 0
        assertEquals(0, consumed);
        assertEquals("", writer.toString());

        // Now start at the actual '&' character
        writer.getBuffer().setLength(0);
        consumed = translate(input, 3);
        assertEquals(5, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testEntityBeyondBMPIsTruncatedToChar() throws IOException {
        // Unicode code point U+1F600 (😀) = 0x1F600 = 128512 decimal
        String input = "&#x1F600;";
        int consumed = translate(input, 0);
        assertEquals(8, consumed); // & # x 1 F 6 0 0 ;  -> length 8
        // Writer.write(int) writes a single char, i.e. low 16 bits (0xF600)
        char expectedChar = (char) 0x1F600; // truncates to 0xF600
        assertEquals(String.valueOf(expectedChar), writer.toString());
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testMissingSemicolonThrowsException() throws IOException {
        // No terminating ';' – the while‑loop will read past the end.
        String input = "&#65";
        translate(input, 0);
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testShortInputThrowsException() throws IOException {
        // Input too short to contain '&' followed by '#'
        String input = "&";
        translate(input, 0);
    }

    @Test
    public void testEntityWithLeadingZeros() throws IOException {
        String input = "&#00065;";
        int consumed = translate(input, 0);
        assertEquals(8, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testHexEntityWithMixedCaseDigits() throws IOException {
        String input = "&#x4A;";
        int consumed = translate(input, 0);
        assertEquals(6, consumed);
        // 0x4A = 74 -> 'J'
        assertEquals("J", writer.toString());
    }
}
###Test END##