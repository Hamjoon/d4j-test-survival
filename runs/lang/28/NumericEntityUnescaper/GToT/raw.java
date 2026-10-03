###Test START##
```java
/*
 * Comprehensive JUnit 4 test suite for
 * org.apache.commons.lang3.text.translate.NumericEntityUnescaper
 *
 * The suite covers:
 *  • Typical decimal and hexadecimal numeric entities
 *  • Upper‑case hex prefix, leading zeros and high‑code‑point values
 *  • Non‑entity inputs, malformed entities and out‑of‑range indexes
 *  • Exception scenarios (NumberFormatException, IndexOutOfBounds, NullPointerException)
 *
 * Author: Automated Test Generation Assistant
 */
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

public class NumericEntityUnescaperTest {

    private final NumericEntityUnescaper unescaper = new NumericEntityUnescaper();

    /**
     * Helper that invokes {@code translate} starting at the first character of {@code input}
     * and returns the written result as a String.
     */
    private String translate(String input) throws IOException {
        Writer out = new StringWriter();
        int consumed = unescaper.translate(input, 0, out);
        // If the translator did not consume anything we simply return the original string
        // (the real CharSequenceTranslator contract would fall back to other translators;
        // for our isolated tests we just return what was written).
        return ((StringWriter) out).toString();
    }

    /* ----------------------------------------------------------------------
     *  Public method signature (extracted from the source)
     * ----------------------------------------------------------------------
     *
     * public int translate(CharSequence input, int index, Writer out) throws IOException
     *
     * ---------------------------------------------------------------------- */

    /* ----------------------------------------------------------------------
     *  1. Typical use cases
     * ---------------------------------------------------------------------- */

    @Test
    public void testDecimalEntity() throws IOException {
        String result = translate("&#65;");
        assertEquals("A", result);
    }

    @Test
    public void testHexEntityLowerCase() throws IOException {
        String result = translate("&#x41;");
        assertEquals("A", result);
    }

    @Test
    public void testHexEntityUpperCaseX() throws IOException {
        String result = translate("&#X41;");
        assertEquals("A", result);
    }

    @Test
    public void testEntityWithLeadingZeros() throws IOException {
        String result = translate("&#00065;");
        assertEquals("A", result);
    }

    @Test
    public void testHexEntityWithLeadingZeros() throws IOException {
        String result = translate("&#x00041;");
        assertEquals("A", result);
    }

    @Test
    public void testHighBmpEntity() throws IOException {
        // Euro sign – U+20AC (decimal 8364, hex 20AC)
        String result = translate("&#x20AC;");
        assertEquals("\u20AC", result);
    }

    @Test
    public void testSurrogatePairEntity() throws IOException {
        // Character outside BMP: 𝄞 (U+1D11E) – not representable as a single char,
        // but writer.write(int) will write the low 16 bits. The test ensures that
        // the method does not throw and writes something (the low surrogate).
        String result = translate("&#119070;"); // decimal for 0x1D11E
        // The low 16 bits of 0x1D11E are 0xD11E, which is a low surrogate.
        assertEquals("\uD11E", result);
    }

    @Test
    public void testEntityEmbeddedInText() throws IOException {
        String result = translate("Hello &#65; World!");
        // Only the entity at the start is processed because we always start at index 0.
        // The rest of the string is not processed by this translator.
        assertEquals("A", result);
    }

    /* ----------------------------------------------------------------------
     *  2. Edge‑case / malformed inputs – expected to return 0 and write nothing
     * ---------------------------------------------------------------------- */

    @Test
    public void testNonEntityStartsWithAmpersand() throws IOException {
        Writer out = new StringWriter();
        int consumed = unescaper.translate("&notanentity;", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testMissingSemicolon() {
        // The implementation loops until it finds ';' and will eventually hit
        // a StringIndexOutOfBoundsException.
        try {
            unescaper.translate("&#65", 0, new StringWriter());
            fail("Expected StringIndexOutOfBoundsException due to missing ';'");
        } catch (StringIndexOutOfBoundsException | IOException e) {
            // Expected – either the loop runs off the end (StringIndexOutOfBounds)
            // or the writer throws IOException (unlikely here).
        }
    }

    @Test
    public void testInvalidDecimalNumber() throws IOException {
        Writer out = new StringWriter();
        int consumed = unescaper.translate("&#12a3;", 0, out);
        // NumberFormatException is caught and 0 is returned; nothing is written.
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testInvalidHexNumber() throws IOException {
        Writer out = new StringWriter();
        int consumed = unescaper.translate("&#xGHI;", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testEmptyNumericPart() throws IOException {
        Writer out = new StringWriter();
        int consumed = unescaper.translate("&#;", 0, out);
        // Parsing an empty string throws NumberFormatException -> return 0.
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testOnlyAmpersandAndHash() throws IOException {
        Writer out = new StringWriter();
        int consumed = unescaper.translate("&#", 0, out);
        // The while loop will try to read past the end and throw StringIndexOutOfBoundsException.
        // We capture it to verify that the method does not silently succeed.
        try {
            unescaper.translate("&#", 0, out);
            fail("Expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException e) {
            // Expected
        }
    }

    /* ----------------------------------------------------------------------
     *  3. Index‑related edge cases
     * ---------------------------------------------------------------------- */

    @Test
    public void testIndexAtLastCharacter() {
        // Input length is 1, index points to the only character.
        // The implementation accesses index+1, causing IndexOutOfBounds.
        try {
            unescaper.translate("a", 0, new StringWriter());
            fail("Expected StringIndexOutOfBoundsException because index+1 is out of range");
        } catch (StringIndexOutOfBoundsException | IOException e) {
            // Expected
        }
    }

    @Test
    public void testIndexBeyondStringLength() {
        try {
            unescaper.translate("abc", 5, new StringWriter());
            fail("Expected StringIndexOutOfBoundsException for index beyond length");
        } catch (StringIndexOutOfBoundsException | IOException e) {
            // Expected
        }
    }

    /* ----------------------------------------------------------------------
     *  4. Null handling – should throw NullPointerException
     * ---------------------------------------------------------------------- */

    @Test(expected = NullPointerException.class)
    public void testNullInput() throws IOException {
        unescaper.translate(null, 0, new StringWriter());
    }

    @Test(expected = NullPointerException.class)
    public void testNullWriter() throws IOException {
        unescaper.translate("&#65;", 0, null);
    }

    /* ----------------------------------------------------------------------
     *  5. Verify the returned “consumed” length matches the specification
     * ---------------------------------------------------------------------- */

    @Test
    public void testReturnedLengthDecimal() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = unescaper.translate("&#123;", 0, out);
        // 2 (&#) + (end-start) where start=2, end=5 => 3 digits + 1 (;) = 2+3+1 = 6
        assertEquals(6, consumed);
        assertEquals("{", out.toString()); // 123 decimal = '{'
    }

    @Test
    public void testReturnedLengthHex() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = unescaper.translate("&#x7B;", 0, out);
        // 2 (&#) + 1 (x) + (end-start)=2 (7B) + 1 (;) = 6
        assertEquals(6, consumed);
        assertEquals("{", out.toString());
    }

    @Test
    public void testReturnedLengthNoMatch() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = unescaper.translate("Hello", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }
}
```
###Test END##