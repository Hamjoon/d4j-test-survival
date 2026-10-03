###Test START##
```java
/****************************************************************************************
 *  JUnit 4 test suite for {@link org.apache.commons.lang3.text.translate.LookupTranslator}
 *
 *  The tests cover:
 *   • Construction with normal, empty and null lookup tables
 *   • Correct calculation of the shortest and longest key lengths
 *   • Greedy (longest‑match) translation behaviour
 *   • Non‑matching input handling
 *   • Edge cases where the remaining input is shorter than the longest key
 *   • Use of non‑String {@link CharSequence} objects as values
 *
 *  Author:  OpenAI ChatGPT
 *  Date:    2026‑10‑03
 ****************************************************************************************/

package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.HashMap;

import org.junit.Test;

/**
 * Test class for {@link LookupTranslator}.
 */
public class LookupTranslatorTest {

    /**
     * Helper method to invoke the {@code translate} method and capture the output.
     *
     * @param translator the translator under test
     * @param input      the input string
     * @return a {@link Result} containing the number of characters consumed and the written output
     * @throws IOException if the underlying writer throws
     */
    private Result translateAndCapture(LookupTranslator translator, String input) throws IOException {
        Writer out = new StringWriter();
        int consumed = translator.translate(input, 0, out);
        return new Result(consumed, out.toString());
    }

    /**
     * Simple container for translate results.
     */
    private static final class Result {
        final int consumed;
        final String output;

        Result(int consumed, String output) {
            this.consumed = consumed;
            this.output = output;
        }
    }

    /**
     * Test that a normal lookup table is stored correctly and that the
     * {@code shortest} and {@code longest} fields are calculated as expected.
     */
    @Test
    public void testConstructionShortestAndLongest() throws Exception {
        CharSequence[][] table = {
                {"a", "A"},
                {"ab", "AB"},
                {"abcde", "ABCDE"}
        };
        LookupTranslator lt = new LookupTranslator(table);

        // Use reflection to read the private fields
        Field shortestField = LookupTranslator.class.getDeclaredField("shortest");
        Field longestField = LookupTranslator.class.getDeclaredField("longest");
        shortestField.setAccessible(true);
        longestField.setAccessible(true);

        int shortest = (int) shortestField.get(lt);
        int longest = (int) longestField.get(lt);

        assertEquals("Shortest key length should be 1 (\"a\")", 1, shortest);
        assertEquals("Longest key length should be 5 (\"abcde\")", 5, longest);
    }

    /**
     * Verify that the translator performs a greedy (longest‑match) translation.
     */
    @Test
    public void testGreedyTranslation() throws IOException {
        CharSequence[][] table = {
                {"a", "1"},
                {"ab", "2"},
                {"abc", "3"}
        };
        LookupTranslator lt = new LookupTranslator(table);

        // Input "abc" should match the longest key "abc" → "3"
        Result r1 = translateAndCapture(lt, "abc");
        assertEquals(3, r1.consumed);
        assertEquals("3", r1.output);

        // Input "abx" – longest matching prefix is "ab" → "2"
        Result r2 = translateAndCapture(lt, "abx");
        assertEquals(2, r2.consumed);
        assertEquals("2", r2.output);
    }

    /**
     * Ensure that when no mapping exists the translator returns 0 and writes nothing.
     */
    @Test
    public void testNoMatch() throws IOException {
        CharSequence[][] table = {
                {"x", "X"},
                {"y", "Y"}
        };
        LookupTranslator lt = new LookupTranslator(table);

        Result r = translateAndCapture(lt, "z");
        assertEquals("When no key matches, consumed characters must be 0", 0, r.consumed);
        assertEquals("When no key matches, nothing should be written", "", r.output);
    }

    /**
     * Test behaviour when the remaining input is shorter than the longest key.
     */
    @Test
    public void testInputShorterThanLongestKey() throws IOException {
        CharSequence[][] table = {
                {"hello", "hi"},
                {"he", "HE"}
        };
        LookupTranslator lt = new LookupTranslator(table);

        // Input length 3, longest key length is 5 – algorithm must shrink max correctly
        Result r = translateAndCapture(lt, "hel");
        // "he" is the only possible match → "HE"
        assertEquals(2, r.consumed);
        assertEquals("HE", r.output);
    }

    /**
     * Verify that constructing the translator with {@code null} does not throw
     * and results in a no‑op translator.
     */
    @Test
    public void testNullLookupTable() throws IOException {
        LookupTranslator lt = new LookupTranslator((CharSequence[][]) null);

        // Any input should result in no translation
        Result r = translateAndCapture(lt, "anything");
        assertEquals(0, r.consumed);
        assertEquals("", r.output);
    }

    /**
     * Test that the translator can handle values that are not {@link String}
     * but any {@link CharSequence} (e.g., {@link StringBuilder}).
     */
    @Test
    public void testNonStringValue() throws IOException {
        CharSequence[][] table = {
                {"key", new StringBuilder("value")}
        };
        LookupTranslator lt = new LookupTranslator(table);

        Result r = translateAndCapture(lt, "key");
        assertEquals(3, r.consumed);
        assertEquals("value", r.output);
    }

    /**
     * Ensure that overlapping keys are resolved correctly (longest first).
     */
    @Test
    public void testOverlappingKeys() throws IOException {
        CharSequence[][] table = {
                {"ab", "AB"},
                {"abc", "ABC"},
                {"abcd", "ABCD"},
                {"a", "A"}
        };
        LookupTranslator lt = new LookupTranslator(table);

        // Full match with the longest key
        Result rFull = translateAndCapture(lt, "abcd");
        assertEquals(4, rFull.consumed);
        assertEquals("ABCD", rFull.output);

        // Partial match where only a shorter key fits
        Result rPartial = translateAndCapture(lt, "abx");
        assertEquals(2, rPartial.consumed);
        assertEquals("AB", rPartial.output);
    }

    /**
     * Verify that the internal map uses {@link String} keys – i.e., a key that is
     * a non‑String {@link CharSequence} will not be found when the lookup is
     * performed with a {@link String} sub‑sequence.
     */
    @Test
    public void testKeyMustBeStringForLookup() throws IOException {
        // Custom CharSequence that does NOT override equals/hashCode (uses Object identity)
        CharSequence customKey = new CharSequence() {
            private final String data = "custom";

            @Override public int length() { return data.length(); }
            @Override public char charAt(int index) { return data.charAt(index); }
            @Override public CharSequence subSequence(int start, int end) { return data.subSequence(start, end); }
            @Override public String toString() { return data; }
        };

        CharSequence[][] table = {
                {customKey, "FOUND"}
        };
        LookupTranslator lt = new LookupTranslator(table);

        // The input sub‑sequence will be a String, not the custom object → no match
        Result r = translateAndCapture(lt, "custom");
        assertEquals(0, r.consumed);
        assertEquals("", r.output);
    }

    /**
     * Confirm that the translator correctly handles an empty lookup array.
     */
    @Test
    public void testEmptyLookupArray() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[0][]);

        // No entries → always no translation
        Result r = translateAndCapture(lt, "anything");
        assertEquals(0, r.consumed);
        assertEquals("", r.output);
    }

    /**
     * Verify that the translator works correctly when the input contains multiple
     * possible matches and only the first (at the given index) is considered.
     */
    @Test
    public void testMultipleTranslationsSequentially() throws IOException {
        CharSequence[][] table = {
                {"ab", "X"},
                {"cd", "Y"},
                {"e", "Z"}
        };
        LookupTranslator lt = new LookupTranslator(table);

        String input = "abcde";
        StringBuilder output = new StringBuilder();
        int idx = 0;
        while (idx < input.length()) {
            int consumed = lt.translate(input, idx, new Writer() {
                @Override public void write(char[] cbuf, int off, int len) { output.append(cbuf, off, len); }
                @Override public void flush() {}
                @Override public void close() {}
            });
            if (consumed == 0) {
                // No translation – copy the original character
                output.append(input.charAt(idx));
                idx++;
            } else {
                idx += consumed;
            }
        }
        // Expected: "X" (ab) + "c" (no match) + "Y" (cd) + "Z" (e) = "XcYZ"
        assertEquals("XcYZ", output.toString());
    }
}
```
###Test END##