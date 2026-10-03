###Test START##
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.IOException;

import org.junit.Test;

/**
 * Unit tests for {@link LookupTranslator}.
 *
 * The tests cover:
 * <ul>
 *   <li>Construction with {@code null} and empty lookup tables.</li>
 *   <li>Single‑entry translation.</li>
 *   <li>Greedy (longest‑match) behavior.</li>
 *   <li>Case‑sensitivity of keys.</li>
 *   <li>Support for {@link CharSequence} values that are not {@link String}s.</li>
 *   <li>Behaviour when the input contains characters that are not present in the lookup map.</li>
 * </ul>
 */
public class LookupTranslatorTest {

    /** Helper that creates a translator from a var‑args array of key/value pairs. */
    private static LookupTranslator createTranslator(String... pairs) {
        // The API expects an array of CharSequence[2] entries.
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Even number of arguments required (key/value pairs).");
        }
        CharSequence[][] table = new CharSequence[pairs.length / 2][2];
        for (int i = 0; i < pairs.length; i += 2) {
            table[i / 2][0] = pairs[i];
            table[i / 2][1] = pairs[i + 1];
        }
        return new LookupTranslator(table);
    }

    // ------------------------------------------------------------------------
    //  Construction edge cases
    // ------------------------------------------------------------------------

    @Test
    public void testNullLookupTableLeavesTranslatorInNo‑OpState() throws IOException {
        LookupTranslator lt = new LookupTranslator((CharSequence[][]) null);
        // No entry means the translator should return the input unchanged.
        assertEquals("hello", lt.translate("hello"));
        assertEquals("", lt.translate(""));
        assertEquals("12345", lt.translate("12345"));
    }

    @Test
    public void testEmptyLookupTableLeavesTranslatorInNo‑OpState() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[0][]);
        assertEquals("world", lt.translate("world"));
        assertEquals("a", lt.translate("a"));
    }

    // ------------------------------------------------------------------------
    //  Simple single‑entry translation
    // ------------------------------------------------------------------------

    @Test
    public void testSingleEntryTranslation() throws IOException {
        LookupTranslator lt = createTranslator("a", "b");
        assertEquals("b", lt.translate("a"));
        // Characters that are not in the map are left untouched.
        assertEquals("c", lt.translate("c"));
    }

    // ------------------------------------------------------------------------
    //  Greedy (longest‑match) algorithm
    // ------------------------------------------------------------------------

    @Test
    public void testGreedyChoicePrefersLongestKey() throws IOException {
        // Two keys: "ab" (longer) and "a" (shorter). Input "ab" must translate to "X".
        LookupTranslator lt = createTranslator("ab", "X", "a", "Y");
        assertEquals("X", lt.translate("ab"));
    }

    @Test
    public void testGreedyChoiceWithOverlappingKeys() throws IOException {
        // Keys: "abc" -> "L", "ab" -> "M", "a" -> "N"
        LookupTranslator lt = createTranslator("abc", "L", "ab", "M", "a", "N");
        assertEquals("L", lt.translate("abc")); // longest match wins
        assertEquals("M", lt.translate("abx")); // "ab" matches, "a" would also match but is shorter
        assertEquals("N", lt.translate("ax"));  // only "a" matches
    }

    // ------------------------------------------------------------------------
    //  Behaviour when the remaining characters are fewer than the longest key
    // ------------------------------------------------------------------------

    @Test
    public void testPartialInputShorterThanLongestKey() throws IOException {
        // Longest key length is 3 ("xyz") but input only has two characters left.
        LookupTranslator lt = createTranslator("xyz", "Z", "xy", "Y");
        // At index 0 we have only "xy", which should match.
        assertEquals("Y", lt.translate("xy"));
        // At index 0 with single character "x" should not match anything.
        assertEquals("x", lt.translate("x"));
    }

    // ------------------------------------------------------------------------
    //  Case‑sensitivity of keys
    // ------------------------------------------------------------------------

    @Test
    public void testCaseSensitivity() throws IOException {
        LookupTranslator lt = createTranslator("A", "alpha");
        assertEquals("alpha", lt.translate("A"));
        // Lower‑case 'a' is not a key and therefore remains unchanged.
        assertEquals("a", lt.translate("a"));
    }

    // ------------------------------------------------------------------------
    //  Values may be any CharSequence (e.g., StringBuilder)
    // ------------------------------------------------------------------------

    @Test
    public void testNonStringCharSequenceValue() throws IOException {
        CharSequence value = new StringBuilder("builder");
        LookupTranslator lt = new LookupTranslator(new CharSequence[][] { { "k", value } });
        assertEquals("builder", lt.translate("k"));
        // Ensure the same CharSequence instance is used internally (no copy).
        assertSame(value, lt.translate("k")); // translate returns a new String, but the same value source is used.
    }

    // ------------------------------------------------------------------------
    //  Mixed content: map with a single entry plus surrounding characters
    // ------------------------------------------------------------------------

    @Test
    public void testTranslationWithinLargerString() throws IOException {
        // Classic HTML escape example: '&' -> "&amp;"
        LookupTranslator lt = createTranslator("&", "&amp;");
        assertEquals("a&amp;b &amp; c", lt.translate("a&b & c"));
        // No double‑escaping: the '&' produced by the first replacement is not re‑processed.
        assertEquals("&amp;amp;", lt.translate("&amp;"));
    }

    // ------------------------------------------------------------------------
    //  Verify that translate returns 0 when no mapping applies at a given index
    // ------------------------------------------------------------------------

    @Test
    public void testNoMatchReturnsZero() throws IOException {
        LookupTranslator lt = createTranslator("foo", "bar");
        // Directly invoke the protected translate method via reflection to examine the return value.
        java.lang.reflect.Method m = LookupTranslator.class.getDeclaredMethod("translate", CharSequence.class, int.class, java.io.Writer.class);
        m.setAccessible(true);
        java.io.StringWriter out = new java.io.StringWriter();

        // Index 0 contains 'x' which is not in the map, so method should return 0 and write nothing.
        int consumed = (int) m.invoke(lt, "x", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }
}
###Test END##