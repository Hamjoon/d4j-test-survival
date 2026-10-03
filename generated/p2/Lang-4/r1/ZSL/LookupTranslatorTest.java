/*
 * JUnit 4 test suite for {@link org.apache.commons.lang3.text.translate.LookupTranslator}.
 *
 * The tests cover:
 *  1. Construction with {@code null} and empty lookup tables.
 *  2. Correct calculation of the internal {@code shortest} and {@code longest} values.
 *  3. Simple key/value translation.
 *  4. Greedy (longest‑match) behaviour.
 *  5. Fallback to a shorter key when the longest key cannot be matched because of
 *     input length.
 *  6. Behaviour when a key that does not implement content‑based {@code equals/hashCode}
 *     (e.g., {@code StringBuilder}) is used.
 *  7. Multiple translations within the same input string.
 *
 * The helper method {@code translateWhole} mimics the behaviour of
 * {@code CharSequenceTranslator.translate(CharSequence)} – it repeatedly calls the
 * package‑private {@code translate(CharSequence, int, Writer)} method.
 */

package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.HashMap;

import org.junit.Test;

/**
 * Test class for {@link LookupTranslator}.
 */
public class LookupTranslatorTest {

    /**
     * Utility that applies a {@link LookupTranslator} to an entire string,
     * emulating {@code CharSequenceTranslator.translate(CharSequence)}.
     */
    private static String translateWhole(LookupTranslator translator, String input) throws IOException {
        StringWriter out = new StringWriter(input.length() * 2);
        int pos = 0;
        while (pos < input.length()) {
            int consumed = translator.translate(input, pos, out);
            if (consumed == 0) {
                // No translation – copy the original character.
                out.write(input.charAt(pos));
                pos++;
            } else {
                // Translation occured – advance by the number of consumed characters.
                pos += consumed;
            }
        }
        return out.toString();
    }

    @Test
    public void testConstructorWithNullLookupDoesNothing() throws IOException {
        LookupTranslator lt = new LookupTranslator(null);
        // With no entries the translator must leave the input untouched.
        String input = "anything";
        assertEquals(input, translateWhole(lt, input));

        // Verify internal state via reflection – shortest should be MAX_VALUE,
        // longest should be 0.
        try {
            Field shortestField = LookupTranslator.class.getDeclaredField("shortest");
            Field longestField  = LookupTranslator.class.getDeclaredField("longest");
            shortestField.setAccessible(true);
            longestField.setAccessible(true);
            assertEquals(Integer.MAX_VALUE, shortestField.getInt(lt));
            assertEquals(0, longestField.getInt(lt));
        } catch (ReflectiveOperationException e) {
            fail("Reflection error: " + e);
        }
    }

    @Test
    public void testConstructorWithEmptyLookupDoesNothing() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[0][]);
        String input = "xyz";
        assertEquals(input, translateWhole(lt, input));

        // Internal fields should be same as the null case.
        try {
            Field shortestField = LookupTranslator.class.getDeclaredField("shortest");
            Field longestField  = LookupTranslator.class.getDeclaredField("longest");
            shortestField.setAccessible(true);
            longestField.setAccessible(true);
            assertEquals(Integer.MAX_VALUE, shortestField.getInt(lt));
            assertEquals(0, longestField.getInt(lt));
        } catch (ReflectiveOperationException e) {
            fail("Reflection error: " + e);
        }
    }

    @Test
    public void testSimpleTranslation() throws IOException {
        // Single entry: "a" -> "A"
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"a", "A"}
        });

        assertEquals("A", translateWhole(lt, "a"));
        assertEquals("Abc", translateWhole(lt, "abc"));
        assertEquals("bc", translateWhole(lt, "bc")); // 'a' not present
    }

    @Test
    public void testGreedyLongestMatch() throws IOException {
        // Two overlapping keys: "ab" -> "X", "abc" -> "Y"
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"ab", "X"},
            {"abc", "Y"}
        });

        // Input "abc" should match the longer key "abc" and produce "Y"
        assertEquals("Y", translateWhole(lt, "abc"));

        // Input "abx" should match the shorter key "ab" (since "abc" not present)
        assertEquals("Xx", translateWhole(lt, "abx"));
    }

    @Test
    public void testFallbackWhenLongestExceedsInputLength() throws IOException {
        // Keys of length 2 and 3
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"ab", "X"},
            {"abc", "Y"}
        });

        // Input length is only 2; longest (3) cannot be tried, but the
        // translator must still match the 2‑character key.
        assertEquals("Xc", translateWhole(lt, "abc"));
        // Input "ab" (exact length 2) should translate via the 2‑char key.
        assertEquals("X", translateWhole(lt, "ab"));
    }

    @Test
    public void testKeyWithNonStringCharSequenceNotMatched() throws IOException {
        // Use a StringBuilder as the key – it does NOT have content‑based equals()
        // and therefore will not match a String produced by subSequence().
        CharSequence key = new StringBuilder("foo");
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {key, "bar"}
        });

        // Because the runtime key is a StringBuilder, looking up with a String will fail.
        assertEquals("foo", translateWhole(lt, "foo"));
        // Ensure that a normal String key works as a control.
        LookupTranslator lt2 = new LookupTranslator(new CharSequence[][]{
            {"foo", "bar"}
        });
        assertEquals("bar", translateWhole(lt2, "foo"));
    }

    @Test
    public void testMultipleTranslationsInSingleString() throws IOException {
        // Map a few HTML entities.
        LookupTranslator htmlEscaper = new LookupTranslator(new CharSequence[][]{
            {"<", "&lt;"},
            {">", "&gt;"},
            {"&", "&amp;"},
            {"\"", "&quot;"}
        });

        String input = "a < b && c > d \"quote\"";
        String expected = "a &lt; b &amp;&amp; c &gt; d &quot;quote&quot;";
        assertEquals(expected, translateWhole(htmlEscaper, input));
    }

    @Test
    public void testShortestAndLongestValuesViaReflection() {
        // Prepare a lookup with keys of lengths 1, 3, and 5.
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"x", "X"},
            {"xyz", "XYZ"},
            {"12345", "NUM"}
        });

        try {
            Field shortestField = LookupTranslator.class.getDeclaredField("shortest");
            Field longestField  = LookupTranslator.class.getDeclaredField("longest");
            shortestField.setAccessible(true);
            longestField.setAccessible(true);
            int shortest = shortestField.getInt(lt);
            int longest  = longestField.getInt(lt);
            assertEquals(1, shortest);
            assertEquals(5, longest);
        } catch (ReflectiveOperationException e) {
            fail("Could not access private fields: " + e);
        }
    }

    @Test
    public void testTranslateReturnsZeroWhenNoMatch() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"abc", "X"}
        });

        StringWriter out = new StringWriter();
        // Position 0: input is "z", not matching any key
        int consumed = lt.translate("zab", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());

        // Ensure that the higher level translateWhole copies the original character.
        assertEquals("zab", translateWhole(lt, "zab"));
    }

    @Test
    public void testTranslateDoesNotModifyWriterOnZeroMatch() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"hello", "hi"}
        });

        StringWriter out = new StringWriter();
        out.write('X'); // pre‑populate writer
        int consumed = lt.translate("world", 0, out);
        assertEquals(0, consumed);
        // Writer must still contain the pre‑written character only.
        assertEquals("X", out.toString());
    }
}
