/**
 * JUnit 4 test suite for {@link org.apache.commons.lang3.text.translate.LookupTranslator}.
 *
 * <p>This test class covers:
 * <ul>
 *   <li>Construction with various lookup tables (null, empty, normal, duplicate keys).</li>
 *   <li>Correct computation of {@code shortest} and {@code longest} boundaries.</li>
 *   <li>Greedy matching behaviour of {@link LookupTranslator#translate(CharSequence, int, Writer)}.</li>
 *   <li>Behaviour when no translation is found (returns {@code 0}).</li>
 *   <li>Edge cases such as input shorter than the shortest key, or the remaining
 *       input being shorter than {@code longest}.</li>
 *   <li>Interaction with different {@link CharSequence} implementations as keys.</li>
 *   <li>Exception handling when a {@code null} {@link Writer} is supplied.</li>
 * </ul>
 *
 * <p>All tests are written using JUnit 4 and can be run with any JUnit‑compatible runner.
 */
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

/**
 * Test class for {@link LookupTranslator}.
 */
public class LookupTranslatorTest {

    private LookupTranslator emptyTranslator;
    private LookupTranslator htmlEntityTranslator;

    /**
     * Helper method to invoke {@code translate} on a {@link LookupTranslator}
     * and return the number of characters consumed.
     *
     * @param translator the translator under test
     * @param input the input string
     * @param index index at which translation should start
     * @param out the writer that receives the output
     * @return number of characters consumed
     * @throws IOException if an I/O error occurs
     */
    private static int translate(LookupTranslator translator,
                                 CharSequence input,
                                 int index,
                                 Writer out) throws IOException {
        return translator.translate(input, index, out);
    }

    @Before
    public void setUp() {
        // 1. Empty translator – constructed with null lookup table.
        emptyTranslator = new LookupTranslator(null);

        // 2. A realistic translator for a few HTML entities.
        //    The table is deliberately unsorted to verify greedy matching.
        htmlEntityTranslator = new LookupTranslator(
                new CharSequence[]{"&", "&amp;"},
                new CharSequence[]{"<", "&lt;"},
                new CharSequence[]{">", "&gt;"},
                new CharSequence[]{"&amp;", "&amp;amp;"} // longer key that overlaps with "&"
        );
    }

    /* -----------------------------------------------------------------------
     * Construction tests
     * ---------------------------------------------------------------------*/

    @Test
    public void testConstructorWithNullLookup() {
        // No exception should be thrown and shortest/longest should stay at defaults.
        LookupTranslator lt = new LookupTranslator(null);
        // The internal fields are private; we verify behaviour via translate().
        StringWriter out = new StringWriter();
        try {
            int consumed = lt.translate("any", 0, out);
            assertEquals("With no entries, translate must consume 0 characters", 0, consumed);
            assertEquals("Output must remain unchanged", "", out.toString());
        } catch (IOException e) {
            fail("IOException should not be thrown: " + e.getMessage());
        }
    }

    @Test
    public void testConstructorWithEmptyLookupArray() {
        LookupTranslator lt = new LookupTranslator(new CharSequence[0][]);
        StringWriter out = new StringWriter();
        try {
            int consumed = lt.translate("test", 0, out);
            assertEquals(0, consumed);
            assertEquals("", out.toString());
        } catch (IOException e) {
            fail("Unexpected IOException: " + e.getMessage());
        }
    }

    @Test
    public void testConstructorWithDuplicateKeysKeepsLastValue() {
        // Duplicate key "&" – the last mapping should win.
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[]{"&", "&amp;"},
                new CharSequence[]{"&", "&amp;amp;"}
        );
        StringWriter out = new StringWriter();
        try {
            int consumed = lt.translate("&", 0, out);
            assertEquals(1, consumed);
            assertEquals("&amp;amp;", out.toString());
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    /* -----------------------------------------------------------------------
     * Basic translation tests
     * ---------------------------------------------------------------------*/

    @Test
    public void testTranslateSimpleMatch() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = htmlEntityTranslator.translate("<", 0, out);
        assertEquals(1, consumed);
        assertEquals("&lt;", out.toString());
    }

    @Test
    public void testTranslateNoMatchReturnsZero() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = htmlEntityTranslator.translate("x", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testTranslateGreedyMatchingPrefersLongestKey() throws IOException {
        // Input starts with "&amp;" – there are two possible matches:
        //   1) "&" -> "&amp;"
        //   2) "&amp;" -> "&amp;amp;"
        // Greedy algorithm must choose the longer key.
        StringWriter out = new StringWriter();
        int consumed = htmlEntityTranslator.translate("&amp;test", 0, out);
        assertEquals(5, consumed); // length of "&amp;"
        assertEquals("&amp;amp;", out.toString());
    }

    @Test
    public void testTranslateWhenRemainingInputShorterThanLongest() throws IOException {
        // longest = 5 ("&amp;"), shortest = 1 ("&","<",">")
        // Input length is 3, starting at index 0.
        // The algorithm must clamp max to 3.
        StringWriter out = new StringWriter();
        int consumed = htmlEntityTranslator.translate("&<", 0, out);
        // The first character '&' matches a 1‑char key ("&") -> "&amp;"
        assertEquals(1, consumed);
        assertEquals("&amp;", out.toString());
    }

    @Test
    public void testTranslateMultipleCallsAdvanceIndexCorrectly() throws IOException {
        // Translate a string containing several entities.
        String input = "<&>test";
        StringWriter out = new StringWriter();
        int idx = 0;
        while (idx < input.length()) {
            int consumed = htmlEntityTranslator.translate(input, idx, out);
            if (consumed == 0) {
                // No translation – copy the original character.
                out.write(input.charAt(idx));
                idx++;
            } else {
                idx += consumed;
            }
        }
        assertEquals("&lt;&amp;&gt;test", out.toString());
    }

    /* -----------------------------------------------------------------------
     * Edge‑case tests concerning CharSequence implementations
     * ---------------------------------------------------------------------*/

    @Test
    public void testLookupKeyIsStringOnly() throws IOException {
        // Build a translator with a non‑String key (StringBuilder).
        // The lookupMap uses the key object as provided; later lookups use
        // the subSequence returned by String (a String), which will not be equal
        // to a StringBuilder. Therefore the translation must fail.
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[]{new StringBuilder("abc"), "XYZ"}
        );
        StringWriter out = new StringWriter();
        int consumed = lt.translate("abc", 0, out);
        assertEquals("Translation should not succeed because the key is not a String",
                0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testLookupWithStringKeyWorks() throws IOException {
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[]{"abc", "XYZ"}
        );
        StringWriter out = new StringWriter();
        int consumed = lt.translate("abc", 0, out);
        assertEquals(3, consumed);
        assertEquals("XYZ", out.toString());
    }

    /* -----------------------------------------------------------------------
     * Exception handling tests
     * ---------------------------------------------------------------------*/

    @Test(expected = NullPointerException.class)
    public void testTranslateWithNullWriterThrowsNPE() throws IOException {
        // The method does not guard against a null Writer; a NPE is expected.
        htmlEntityTranslator.translate("<", 0, null);
    }

    @Test
    public void testTranslateWithNullInputThrowsNPE() {
        try {
            htmlEntityTranslator.translate(null, 0, new StringWriter());
            fail("Expected NullPointerException when input is null");
        } catch (NullPointerException expected) {
            // fine
        } catch (IOException e) {
            fail("Did not expect IOException: " + e.getMessage());
        }
    }

    @Test
    public void testTranslateWithNegativeIndexThrowsStringIndexOutOfBoundsException() {
        StringWriter out = new StringWriter();
        try {
            htmlEntityTranslator.translate("<test>", -1, out);
            fail("Expected StringIndexOutOfBoundsException for negative index");
        } catch (StringIndexOutOfBoundsException expected) {
            // fine
        } catch (IOException e) {
            fail("Did not expect IOException: " + e.getMessage());
        }
    }

    @Test
    public void testTranslateWithIndexBeyondLengthReturnsZero() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = htmlEntityTranslator.translate("abc", 5, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    /* -----------------------------------------------------------------------
     * Internal state verification via reflection (optional but useful)
     * ---------------------------------------------------------------------*/

    @Test
    public void testShortestAndLongestCalculatedCorrectly() throws Exception {
        // Use reflection to access the private fields.
        java.lang.reflect.Field shortestField = LookupTranslator.class.getDeclaredField("shortest");
        java.lang.reflect.Field longestField = LookupTranslator.class.getDeclaredField("longest");
        shortestField.setAccessible(true);
        longestField.setAccessible(true);

        // The htmlEntityTranslator has keys of lengths 1 ("&","<",">") and 5 ("&amp;").
        int shortest = (Integer) shortestField.get(htmlEntityTranslator);
        int longest = (Integer) longestField.get(htmlEntityTranslator);
        assertEquals(1, shortest);
        assertEquals(5, longest);
    }
}
