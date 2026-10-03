###Test START##
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

/**
 * Comprehensive JUnit4 test suite for {@link LookupTranslator}.
 *
 * The tests cover:
 * <ul>
 *   <li>Construction with null, empty and normal lookup tables.</li>
 *   <li>Correct handling of the {@code shortest} and {@code longest} boundaries.</li>
 *   <li>Greedy (longest‑match) translation behaviour.</li>
 *   <li>Behaviour when no match is found.</li>
 *   <li>Case‑sensitivity and duplicate‑key handling.</li>
 *   <li>Effect of using a non‑String {@link CharSequence} as a key.</li>
 * </ul>
 */
public class LookupTranslatorTest {

    /** Utility method that invokes {@link LookupTranslator#translate(CharSequence, int, Writer)} */
    private static int translate(LookupTranslator lt, CharSequence input, Writer out) throws IOException {
        return lt.translate(input, 0, out);
    }

    @Test
    public void testSimpleTranslation() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"a", "b"}
        });

        StringWriter sw = new StringWriter();
        int consumed = translate(lt, "a", sw);
        assertEquals("Exactly one character should be consumed", 1, consumed);
        assertEquals("Simple mapping should replace 'a' with 'b'", "b", sw.toString());
    }

    @Test
    public void testNoMatchReturnsZeroAndWritesNothing() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"x", "y"}
        });

        StringWriter sw = new StringWriter();
        int consumed = translate(lt, "z", sw);
        assertEquals("When no mapping exists, 0 characters should be consumed", 0, consumed);
        assertEquals("Writer must remain empty when there is no match", "", sw.toString());
    }

    @Test
    public void testGreedyLongestMatch() throws IOException {
        // Two overlapping keys: "ab" (longer) and "a" (shorter)
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"ab", "LONG"},
                {"a", "SHORT"}
        });

        StringWriter sw = new StringWriter();
        int consumed = translate(lt, "ab", sw);
        assertEquals("Greedy algorithm must consume the longest key", 2, consumed);
        assertEquals("Output must correspond to the longest key", "LONG", sw.toString());
    }

    @Test
    public void testPartialMatchWhenInputShorterThanLongestKey() throws IOException {
        // Longest key length = 4, but input only 3 characters
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"abcd", "X"}
        });

        StringWriter sw = new StringWriter();
        int consumed = translate(lt, "abc", sw);
        assertEquals("When input is shorter than any key, no characters are consumed", 0, consumed);
        assertEquals("Writer must stay empty because no key fits", "", sw.toString());
    }

    @Test
    public void testConstructorWithNullLookup() throws IOException {
        LookupTranslator lt = new LookupTranslator(null);
        StringWriter sw = new StringWriter();
        int consumed = translate(lt, "anything", sw);
        assertEquals("Null lookup table must behave as empty table", 0, consumed);
        assertEquals("No output should be produced", "", sw.toString());
    }

    @Test
    public void testConstructorWithEmptyLookupArray() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[0][]);
        StringWriter sw = new StringWriter();
        int consumed = translate(lt, "foo", sw);
        assertEquals("Empty lookup array must behave like empty table", 0, consumed);
        assertEquals("Writer must stay empty", "", sw.toString());
    }

    @Test
    public void testDuplicateKeyOverridesPrevious() throws IOException {
        // The second entry for "dup" should overwrite the first one.
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"dup", "first"},
                {"dup", "second"}
        });

        StringWriter sw = new StringWriter();
        int consumed = translate(lt, "dup", sw);
        assertEquals(3, consumed);
        assertEquals("second", sw.toString());
    }

    @Test
    public void testCaseSensitivity() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"Key", "VALUE"}
        });

        StringWriter sw1 = new StringWriter();
        int consumed1 = translate(lt, "Key", sw1);
        assertEquals(3, consumed1);
        assertEquals("VALUE", sw1.toString());

        StringWriter sw2 = new StringWriter();
        int consumed2 = translate(lt, "key", sw2);
        assertEquals("Different case must not match", 0, consumed2);
        assertEquals("", sw2.toString());
    }

    @Test
    public void testNonStringCharSequenceKeyDoesNotMatch() throws IOException {
        // Using StringBuilder as the key – it will NOT match a String input because
        // StringBuilder does not override equals to compare content.
        CharSequence keyBuilder = new StringBuilder("sb");
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {keyBuilder, "RESULT"}
        });

        StringWriter sw = new StringWriter();
        int consumed = translate(lt, "sb", sw);
        assertEquals("Lookup with non‑String key must fail to match a String subsequence", 0, consumed);
        assertEquals("", sw.toString());
    }

    @Test
    public void testMultipleTranslationsInSequence() throws IOException {
        // This test simulates processing a longer input by repeatedly calling translate()
        // advancing the index manually.
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
                {"ab", "X"},
                {"c", "Y"},
                {"d", "Z"}
        });

        String input = "abcd";
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < input.length()) {
            StringWriter sw = new StringWriter();
            int consumed = lt.translate(input, index, sw);
            if (consumed == 0) {
                // No mapping – copy the original character
                result.append(input.charAt(index));
                index++;
            } else {
                result.append(sw.toString());
                index += consumed;
            }
        }
        assertEquals("Expected combined translation X Y Z", "XYZ", result.toString());
    }
}
###Test END##