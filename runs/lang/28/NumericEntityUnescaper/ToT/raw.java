###Test START##
```java
/**
 * JUnit 4 test suite for {@link org.apache.commons.lang3.text.translate.NumericEntityUnescaper}.
 *
 * The tests cover:
 * <ul>
 *   <li>Typical decimal and hexadecimal numeric entities.</li>
 *   <li>Edge cases such as empty input, non‑entity characters and entities at the end of the string.</li>
 *   <li>Error handling – malformed numbers, missing terminating ';' and I/O failures.</li>
 *   <li>Verification of the return value (number of characters consumed).</li>
 * </ul>
 */
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

/**
 * Test class for {@link NumericEntityUnescaper}.
 */
public class NumericEntityUnescaperTest {

    private final NumericEntityUnescaper unescaper = new NumericEntityUnescaper();

    /**
     * Helper that calls {@code translate} starting at the given index
     * and returns the number of characters consumed.
     */
    private int translate(CharSequence input, int index, Writer out) throws IOException {
        return unescaper.translate(input, index, out);
    }

    /* -------------------------------------------------------------
     *  Normal usage tests
     * ------------------------------------------------------------- */

    @Test
    public void testDecimalEntity() throws IOException {
        String src = "abc&#65;def";
        StringWriter out = new StringWriter();

        int consumed = translate(src, 3, out); // '&' is at position 3
        assertEquals("Consumed length should match entity length", 5, consumed);
        assertEquals("Output should contain the decoded character 'A'",
                "A", out.toString());
    }

    @Test
    public void testHexEntityUpperCaseX() throws IOException {
        String src = "&#X41;";
        StringWriter out = new StringWriter();

        int consumed = translate(src, 0, out);
        // Entity length: & # X 4 1 ; = 6
        assertEquals(6, consumed);
        assertEquals("A", out.toString());
    }

    @Test
    public void testHexEntityLowerCaseX() throws IOException {
        String src = "&#x41;";
        StringWriter out = new StringWriter();

        int consumed = translate(src, 0, out);
        // Entity length: & # x 4 1 ; = 6
        assertEquals(6, consumed);
        assertEquals("A", out.toString());
    }

    @Test
    public void testMultipleEntitiesInOneString() throws IOException {
        String src = "&#65;&#x42;&#67;";
        StringWriter out = new StringWriter();

        int index = 0;
        // First entity
        int consumed = translate(src, index, out);
        assertEquals(5, consumed); // &#65;
        index += consumed;

        // Second entity
        consumed = translate(src, index, out);
        assertEquals(6, consumed); // &#x42;
        index += consumed;

        // Third entity
        consumed = translate(src, index, out);
        assertEquals(5, consumed); // &#67;
        // Verify final output
        assertEquals("ABC", out.toString());
    }

    /* -------------------------------------------------------------
     *  Edge / error handling tests
     * ------------------------------------------------------------- */

    @Test
    public void testNonEntityReturnsZero() throws IOException {
        String src = "Hello World";
        StringWriter out = new StringWriter();

        int consumed = translate(src, 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testMalformedNumberReturnsZero() throws IOException {
        String src = "&#xG1;"; // 'G' is not a hex digit
        StringWriter out = new StringWriter();

        int consumed = translate(src, 0, out);
        // The method catches NumberFormatException and returns 0
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testMissingSemicolonCausesIndexOutOfBounds() throws IOException {
        // No terminating ';' – the while‑loop runs off the end of the string
        String src = "&#65abc";
        StringWriter out = new StringWriter();

        translate(src, 0, out);
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testEntityAtStringEndWithoutSemicolon() throws IOException {
        // The string ends right after the digits
        String src = "foo&#123";
        StringWriter out = new StringWriter();

        translate(src, 3, out);
    }

    @Test
    public void testEntityWithEmptyDigitsReturnsZero() throws IOException {
        // &#; – empty numeric part
        String src = "&#;";
        StringWriter out = new StringWriter();

        int consumed = translate(src, 0, out);
        // Parsing an empty string throws NumberFormatException -> 0 returned
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testEntityWithOnlyHashAndSemicolonReturnsZero() throws IOException {
        // &#; – same as above, but explicitly checking that the method does not write anything
        String src = "&#;";
        StringWriter out = new StringWriter();

        int consumed = translate(src, 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    /* -------------------------------------------------------------
     *  I/O failure propagation test
     * ------------------------------------------------------------- */

    @Test(expected = IOException.class)
    public void testWriterIOExceptionIsPropagated() throws IOException {
        String src = "&#65;";

        Writer faultyWriter = new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("simulated write failure");
            }

            @Override
            public void flush() throws IOException { }

            @Override
            public void close() throws IOException { }
        };

        // The translate method should propagate the IOException thrown by the writer
        translate(src, 0, faultyWriter);
    }
}
```
###Test END##