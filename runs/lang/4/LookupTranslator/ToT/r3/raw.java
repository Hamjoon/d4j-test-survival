###Test START##
```java
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.HashMap;

import org.junit.Test;

/**
 * Comprehensive JUnit‑4 test suite for {@link LookupTranslator}.
 *
 * <p>The tests cover:</p>
 * <ul>
 *   <li>Construction – handling of {@code null} and non‑null lookup tables,
 *       correct computation of {@code shortest} and {@code longest}.</li>
 *   <li>Translation – greedy matching, partial matches, no‑match scenarios,
 *       behaviour when the supplied index is at or beyond the end of the input,
 *       and handling of {@code null} input.</li>
 *   <li>Edge cases – overlapping keys, keys that are not {@link String}
 *       instances, and empty lookup tables.</li>
 * </ul>
 */
public class LookupTranslatorTest {

    /* --------------------------------------------------------------------- */
    /*  Helper methods                                                       */
    /* --------------------------------------------------------------------- */

    /**
     * Retrieves a private {@code int} field from {@link LookupTranslator}
     * using reflection.
     */
    private int getPrivateIntField(LookupTranslator lt, String fieldName) throws Exception {
        Field f = LookupTranslator.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.getInt(lt);
    }

    /**
     * Executes a full translation of {@code input} using the supplied
     * {@link LookupTranslator}. The method mimics the behaviour of the
     * higher‑level {@code CharSequenceTranslator.translate(CharSequence)}.
     */
    private String translateWholeString(LookupTranslator lt, CharSequence input) throws IOException {
        StringWriter out = new StringWriter();
        int i = 0;
        while (i < input.length()) {
            int consumed = lt.translate(input, i, out);
            if (consumed == 0) {               // no mapping – copy original char
                out.write(input.charAt(i));
                i++;
            } else {
                i += consumed;
            }
        }
        return out.toString();
    }

    /* --------------------------------------------------------------------- */
    /*  Constructor tests                                                    */
    /* --------------------------------------------------------------------- */

    @Test
    public void testConstructorWithNullLookup() throws Exception {
        LookupTranslator lt = new LookupTranslator(null);
        // When lookup is null, shortest should stay at MAX_VALUE and longest at 0
        assertEquals(Integer.MAX_VALUE, getPrivateIntField(lt, "shortest"));
        assertEquals(0, getPrivateIntField(lt, "longest"));
    }

    @Test
    public void testConstructorWithEmptyLookupArray() throws Exception {
        LookupTranslator lt = new LookupTranslator(new CharSequence[0][]);
        assertEquals(Integer.MAX_VALUE, getPrivateIntField(lt, "shortest"));
        assertEquals(0, getPrivateIntField(lt, "longest"));
    }

    @Test
    public void testConstructorComputesShortestAndLongest() throws Exception {
        CharSequence[][] lookup = {
                {"a", "A"},
                {"ab", "AB"},
                {"abcde", "ABCDE"},
                {"xyz", "XYZ"}
        };
        LookupTranslator lt = new LookupTranslator(lookup);
        assertEquals(1, getPrivateIntField(lt, "shortest"));   // "a"
        assertEquals(5, getPrivateIntField(lt, "longest"));    // "abcde"
    }

    @Test
    public void testConstructorWithNonStringKeyDoesNotBreak() throws Exception {
        // Using a StringBuilder as a key – the constructor stores it as‑is.
        CharSequence key = new StringBuilder("key");
        CharSequence[][] lookup = { { key, "VALUE" } };
        LookupTranslator lt = new LookupTranslator(lookup);
        // Internally the key is the StringBuilder instance, not a String.
        // The translator will therefore *not* match a String sub‑sequence.
        String result = translateWholeString(lt, "key");
        assertEquals("key", result); // unchanged because of mismatched key type
    }

    /* --------------------------------------------------------------------- */
    /*  translate(...) tests                                                 */
    /* --------------------------------------------------------------------- */

    @Test
    public void testGreedyMatchPrefersLongestKey() throws IOException {
        CharSequence[][] lookup = {
                {"ab", "X"},
                {"abc", "Y"}   // longer key – should win
        };
        LookupTranslator lt = new LookupTranslator(lookup);
        StringWriter out = new StringWriter();
        int consumed = lt.translate("abc", 0, out);
        assertEquals(3, consumed);                // whole "abc" consumed
        assertEquals("Y", out.toString());       // longer match wins
    }

    @Test
    public void testPartialMatchWhenLongerKeyFails() throws IOException {
        CharSequence[][] lookup = {
                {"ab", "X"},
                {"abc", "Y"}
        };
        LookupTranslator lt = new LookupTranslator(lookup);
        StringWriter out = new StringWriter();
        int consumed = lt.translate("abx", 0, out);
        assertEquals(2, consumed);                // "ab" matched
        assertEquals("X", out.toString());
    }

    @Test
    public void testNoMatchReturnsZeroAndWritesNothing() throws IOException {
        CharSequence[][] lookup = {
                {"foo", "FOO"},
                {"bar", "BAR"}
        };
        LookupTranslator lt = new LookupTranslator(lookup);
        StringWriter out = new StringWriter();
        int consumed = lt.translate("baz", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testTranslateAtEndOfInputReturnsZero() throws IOException {
        CharSequence[][] lookup = { {"a", "A"} };
        LookupTranslator lt = new LookupTranslator(lookup);
        StringWriter out = new StringWriter();
        // index == input length – nothing to translate
        int consumed = lt.translate("a", 1, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test(expected = NullPointerException.class)
    public void testTranslateWithNullInputThrowsNPE() throws IOException {
        CharSequence[][] lookup = { {"a", "A"} };
        LookupTranslator lt = new LookupTranslator(lookup);
        lt.translate(null, 0, new StringWriter());
    }

    @Test
    public void testFullStringTranslationWithMixedMatches() throws IOException {
        CharSequence[][] lookup = {
                {"&", "&amp;"},
                {"<", "&lt;"},
                {">", "&gt;"},
                {"\"", "&quot;"},
                {"abc", "XYZ"}
        };
        LookupTranslator lt = new LookupTranslator(lookup);
        String input = "a&b<c>\"abc\"";
        // Expected: a &amp; b &lt; c &gt; &quot; XYZ &quot;
        String expected = "a&amp;b&lt;c&gt;&quot;XYZ&quot;";
        assertEquals(expected, translateWholeString(lt, input));
    }

    @Test
    public void testOverlappingKeysDoGreedySelection() throws IOException {
        CharSequence[][] lookup = {
                {"ab", "AB"},
                {"abc", "ABC"},
                {"abcd", "ABCD"}
        };
        LookupTranslator lt = new LookupTranslator(lookup);
        // Input contains the longest possible key at the start
        assertEquals("ABCD", translateWholeString(lt, "abcd"));
        // Input contains a medium key in the middle
        assertEquals("XYZAB", translateWholeString(lt, "xyzabc"));
        // Input contains only the shortest key
        assertEquals("AB", translateWholeString(lt, "ab"));
    }

    @Test
    public void testLookupMapIsImmutableFromOutside() throws IOException, Exception {
        CharSequence[][] lookup = { {"a", "A"} };
        LookupTranslator lt = new LookupTranslator(lookup);
        // Try to modify the internal map via reflection – should not affect translation
        Field mapField = LookupTranslator.class.getDeclaredField("lookupMap");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        HashMap<CharSequence, CharSequence> internalMap = (HashMap<CharSequence, CharSequence>) mapField.get(lt);
        internalMap.put("b", "B"); // external tampering

        // The translator should still behave as if only "a" → "A" existed
        assertEquals("A", translateWholeString(lt, "a"));
        assertEquals("b", translateWholeString(lt, "b")); // unchanged because map was not consulted
    }
}
```
###Test END##