/**
 * JUnit 4 test suite for {@link org.apache.commons.lang3.text.translate.NumericEntityUnescaper}.
 *
 * The tests cover:
 * 1. All public methods (constructor & {@code translate}).
 * 2. Normal behaviour for decimal and hexadecimal numeric entities.
 * 3. Edge‑cases such as missing semicolon, non‑numeric content, incomplete prefixes,
 *    very large code points and I/O failures.
 *
 * The test class is completely self‑contained and uses only the JDK and JUnit 4.
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

    // -----------------------------------------------------------------------
    // 1.  Public API extraction (for documentation purpose)
    // -----------------------------------------------------------------------
    // The class declares the following public members:
    //   • public NumericEntityUnescaper()                // default constructor
    //   • public int translate(CharSequence input,
    //                         int index,
    //                         Writer out) throws IOException
    //
    // All other methods are inherited from {@link CharSequenceTranslator}.

    // -----------------------------------------------------------------------
    // 2.  Basic functional tests
    // -----------------------------------------------------------------------

    /**
     * Helper that invokes the translator at the first character of {@code input}
     * and returns the number of consumed characters.
     */
    private int translate(NumericEntityUnescaper unescaper,
                          CharSequence input,
                          Writer out) throws IOException {
        return unescaper.translate(input, 0, out);
    }

    @Test
    public void testConstructorIsPublic() {
        // The default constructor must be accessible.
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        assertNotNull(unescaper);
    }

    @Test
    public void testTranslateDecimalEntity() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = translate(unescaper, "&#65;", out);

        // &#65; is the character 'A'
        assertEquals("A", out.toString());
        assertEquals(5, consumed); // '&' '#' '6' '5' ';'
    }

    @Test
    public void testTranslateHexEntityLowercase() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = translate(unescaper, "&#x41;", out);

        assertEquals("A", out.toString());
        assertEquals(6, consumed); // '&' '#' 'x' '4' '1' ';'
    }

    @Test
    public void testTranslateHexEntityUppercase() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = translate(unescaper, "&#X41;", out);

        assertEquals("A", out.toString());
        assertEquals(6, consumed);
    }

    @Test
    public void testTranslateNonEntityReturnsZero() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = translate(unescaper, "&amp;", out);

        // No translation should happen
        assertEquals("", out.toString());
        assertEquals(0, consumed);
    }

    @Test
    public void testTranslateInvalidNumberFormat() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = translate(unescaper, "&#abc;", out);

        // NumberFormatException is caught internally and 0 is returned
        assertEquals("", out.toString());
        assertEquals(0, consumed);
    }

    // -----------------------------------------------------------------------
    // 3.  Edge‑case and exceptional‑path tests
    // -----------------------------------------------------------------------

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testTranslateMissingSemicolon() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        // No terminating ';' – the while‑loop runs off the end of the string
        translate(unescaper, "&#65", out);
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testTranslateIncompletePrefix() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        // Only '&' present, the code tries to read input.charAt(index+1)
        translate(unescaper, "&", out);
    }

    @Test
    public void testTranslateLargeUnicodeCodePoint() throws IOException {
        // U+1F600 (GRINNING FACE) → decimal 128512, hex 1F600
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = translate(unescaper, "&#x1F600;", out);

        assertEquals("\uD83D\uDE00", out.toString()); // surrogate pair
        assertEquals(9, consumed); // '&' '#' 'x' '1' 'F' '6' '0' '0' ';'
    }

    @Test
    public void testTranslateWriterThrowsIOException() {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        Writer failingWriter = new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("forced failure");
            }

            @Override
            public void flush() throws IOException { }

            @Override
            public void close() throws IOException { }
        };

        try {
            unescaper.translate("&#65;", 0, failingWriter);
            fail("Expected IOException to be propagated");
        } catch (IOException ex) {
            assertEquals("forced failure", ex.getMessage());
        }
    }

    @Test
    public void testTranslateWithIndexNotAtStart() throws IOException {
        // Verify that the method works when the entity does not start at 0.
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        String input = "prefix &#66; suffix";
        // The entity starts at index 7 (character '&')
        int consumed = unescaper.translate(input, 7, out);

        assertEquals("B", out.toString());
        // Length of entity = 5 (&#66;)
        assertEquals(5, consumed);
    }

    @Test
    public void testTranslateZeroLengthNumericPart() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        // "&#;" – there is no numeric part; NumberFormatException should be caught
        int consumed = translate(unescaper, "&#;", out);

        assertEquals("", out.toString());
        assertEquals(0, consumed);
    }
}