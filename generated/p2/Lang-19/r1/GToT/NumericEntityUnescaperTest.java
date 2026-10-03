package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Test;

/**
 * Comprehensive JUnit‑4 test suite for {@link NumericEntityUnescaper}.
 *
 * The tests cover:
 * <ul>
 *   <li>normal decimal and hexadecimal entities (with and without ';')</li>
 *   <li>surrogate‑pair handling for code points > 0xFFFF</li>
 *   <li>invalid numeric formats</li>
 *   <li>empty entity, non‑entity input and wrong start index</li>
 *   <li>the exceptional case where the terminating ';' is missing</li>
 * </ul>
 */
public class NumericEntityUnescaperTest {

    @Test
    public void testTranslateDecimalEntity() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = unescaper.translate("&#65;", 0, out);

        assertEquals(5, consumed);                 // whole string consumed
        assertEquals("A", out.toString());          // 65 → 'A'
    }

    @Test
    public void testTranslateHexEntityUpperCase() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = unescaper.translate("&#x41;", 0, out);

        assertEquals(6, consumed);                 // '&' '#' 'x' '4' '1' ';'
        assertEquals("A", out.toString());
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testTranslateDecimalEntityMissingSemicolon() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        // No terminating ';' → loop runs off the end
        unescaper.translate("&#65", 0, out);
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testTranslateHexEntityMissingSemicolon() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        unescaper.translate("&#x41", 0, out);
    }

    @Test
    public void testTranslateSurrogatePair() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        // 0x1F600 = 😀 (grinning face)
        int consumed = unescaper.translate("&#128512;", 0, out);

        assertEquals(9, consumed);                 // '&' '#' digits ';' → 9 chars
        String result = out.toString();
        assertEquals(2, result.length());           // surrogate pair = 2 char units
        int codePoint = result.codePointAt(0);
        assertEquals(0x1F600, codePoint);
    }

    @Test
    public void testTranslateInvalidNumberFormat() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = unescaper.translate("&#xZZ;", 0, out);

        assertEquals(0, consumed);                 // parsing failed → 0
        assertEquals("", out.toString());          // nothing written
    }

    @Test
    public void testTranslateEmptyEntity() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = unescaper.translate("&#;", 0, out);

        assertEquals(0, consumed);                 // empty numeric part → 0
        assertEquals("", out.toString());
    }

    @Test
    public void testTranslateNonEntityInput() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = unescaper.translate("Hello world", 0, out);

        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testTranslateStartsAtWrongIndex() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        // Index points to 'e' (not '&')
        int consumed = unescaper.translate("Hello &#65;", 1, out);

        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }
}
