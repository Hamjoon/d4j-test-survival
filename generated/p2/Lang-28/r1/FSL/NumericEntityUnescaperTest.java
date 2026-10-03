package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

/**
 * Unit tests for {@link NumericEntityUnescaper}.
 */
public class NumericEntityUnescaperTest {

    private final NumericEntityUnescaper unescaper = new NumericEntityUnescaper();

    /** Helper that invokes the translator at position 0. */
    private int translate(String input, Writer out) throws IOException {
        return unescaper.translate(input, 0, out);
    }

    /** Helper that invokes the translator at an arbitrary index. */
    private int translateAt(String input, int index, Writer out) throws IOException {
        return unescaper.translate(input, index, out);
    }

    @Test
    public void testDecimalEntityUnescaped() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = translate("&#65;", out);
        assertEquals("&#65; should be translated to 'A'", 5, consumed);
        assertEquals("A", out.toString());
    }

    @Test
    public void testHexEntityUnescapedLowercaseX() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = translate("&#x41;", out);
        assertEquals("&#x41; should be translated to 'A'", 6, consumed);
        assertEquals("A", out.toString());
    }

    @Test
    public void testHexEntityUnescapedUppercaseX() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = translate("&#X41;", out);
        assertEquals("&#X41; should be translated to 'A'", 6, consumed);
        assertEquals("A", out.toString());
    }

    @Test
    public void testEntityAtNonZeroIndex() throws IOException {
        String input = "foo&#66;bar";
        StringWriter out = new StringWriter();
        int consumed = translateAt(input, 3, out); // index 3 points to '&'
        assertEquals("Entity starting at index 3 should be translated", 5, consumed);
        assertEquals("B", out.toString());
    }

    @Test
    public void testNonEntityReturnsZero() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = translate("abc", out);
        assertEquals("Non‑entity should result in 0 characters consumed", 0, consumed);
        assertEquals("Output must stay empty", "", out.toString());
    }

    @Test
    public void testEntityMissingHashReturnsZero() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = translate("&65;", out);
        assertEquals("Missing '#', should not be translated", 0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testInvalidDecimalNumberReturnsZero() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = translate("&#;", out); // empty number
        assertEquals("Empty numeric part must cause no translation", 0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testInvalidHexNumberReturnsZero() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = translate("&#xG;", out); // 'G' is not a hex digit
        assertEquals("Invalid hex number must cause no translation", 0, consumed);
        assertEquals("", out.toString());
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testMissingSemicolonThrowsException() throws IOException {
        StringWriter out = new StringWriter();
        // No terminating ';' – the while‑loop runs off the end of the string
        translate("&#65", out);
    }

    @Test
    public void testMultipleEntitiesOnlyFirstProcessedPerCall() throws IOException {
        String input = "&#65;&#66;";
        StringWriter out = new StringWriter();

        // First call processes the entity at index 0
        int firstConsumed = translateAt(input, 0, out);
        assertEquals(5, firstConsumed);
        assertEquals("A", out.toString());

        // Second call processes the next entity starting at index 5
        int secondConsumed = translateAt(input, 5, out);
        assertEquals(5, secondConsumed);
        assertEquals("AB", out.toString());
    }

    @Test
    public void testEntityWithHighCodePoint() throws IOException {
        // Unicode code point for 😀 (U+1F600) – should be written as a surrogate pair
        StringWriter out = new StringWriter();
        int consumed = translate("&#128512;", out);
        assertEquals(9, consumed); // & # 1 2 8 5 1 2 ;
        assertEquals("\uD83D\uDE00", out.toString()); // surrogate pair for 😀
    }
}
