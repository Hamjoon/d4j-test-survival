###Test START##
```java
/*
 * Unit tests for {@link org.apache.commons.lang3.text.translate.LookupTranslator}
 *
 * These tests cover:
 *  - basic translation functionality,
 *  - greedy longest‑match behavior,
 *  - handling of null or empty lookup tables,
 *  - behavior when the input is shorter than the longest key,
 *  - proper propagation of IOException,
 *  - duplicate key handling.
 *
 * The tests are written using JUnit 4.
 */
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

/**
 * Test suite for {@link LookupTranslator}.
 */
public class LookupTranslatorTest {

    /**
     * Helper method that runs {@link LookupTranslator#translate(CharSequence, int, Writer)}
     * on the whole input string and returns the translated result.
     */
    private String translateWhole(LookupTranslator lt, String input) throws IOException {
        StringWriter out = new StringWriter();
        int pos = 0;
        while (pos < input.length()) {
            int consumed = lt.translate(input, pos, out);
            if (consumed == 0) {
                // No translation – copy the current character
                out.write(input.charAt(pos));
                pos++;
            } else {
                pos += consumed;
            }
        }
        return out.toString();
    }

    /*** 1. Basic translation ***********************************************/

    @Test
    public void testBasicTranslation() throws IOException {
        // lookup: "a" → "A", "b" → "B"
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[] { "a", "A" },
                new CharSequence[] { "b", "B" });

        String result = translateWhole(lt, "abc");
        assertEquals("ABc", result);
    }

    /*** 2. Greedy longest‑match behavior ************************************/

    @Test
    public void testGreedyLongestMatch() throws IOException {
        // "ab" should win over "a" because of greedy algorithm
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[] { "ab", "X" },
                new CharSequence[] { "a", "Y" });

        String result = translateWhole(lt, "ab");
        assertEquals("X", result); // not "YA"
    }

    /*** 3. Null lookup table ***********************************************/

    @Test
    public void testNullLookupTable() throws IOException {
        LookupTranslator lt = new LookupTranslator((CharSequence[][]) null);
        // With no entries nothing should be translated
        String result = translateWhole(lt, "anything");
        assertEquals("anything", result);
    }

    /*** 4. Empty lookup table ***********************************************/

    @Test
    public void testEmptyLookupTable() throws IOException {
        LookupTranslator lt = new LookupTranslator(); // var‑arg empty
        String result = translateWhole(lt, "xyz");
        assertEquals("xyz", result);
    }

    /*** 5. Input shorter than longest key ***********************************/

    @Test
    public void testInputShorterThanLongestKey() throws IOException {
        // Only key is "abcd" → "Z"
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[] { "abcd", "Z" });

        // Input "abc" is shorter than the key; should be unchanged
        String result = translateWhole(lt, "abc");
        assertEquals("abc", result);
    }

    /*** 6. IOException propagation *******************************************/

    @Test(expected = IOException.class)
    public void testIOExceptionPropagation() throws IOException {
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[] { "x", "y" });

        Writer throwingWriter = new Writer() {
            @Override public void write(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("forced");
            }
            @Override public void flush() throws IOException {}
            @Override public void close() throws IOException {}
        };

        // This call must propagate the IOException
        lt.translate("x", 0, throwingWriter);
    }

    /*** 7. Duplicate key handling *******************************************/

    @Test
    public void testDuplicateKeyOverridesPrevious() throws IOException {
        // The second definition of "a" should overwrite the first
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[] { "a", "first" },
                new CharSequence[] { "a", "second" });

        String result = translateWhole(lt, "a");
        assertEquals("second", result);
    }

    /*** 8. Non‑String CharSequence keys ****************************************/

    @Test
    public void testNonStringCharSequenceKey() throws IOException {
        // Use StringBuilder as a key – it converts to String internally via
        // CharSequence's hashCode/equals contract (StringBuilder's does not
        // override equals, so the map stores the exact object; however the
        // implementation stores the original CharSequence, and during lookup we
        // pass a String (subSequence of a String), which is not equal.
        // This test confirms that only String keys behave correctly.
        CharSequence key = new StringBuilder("k");
        CharSequence value = "V";

        LookupTranslator lt = new LookupTranslator(
                new CharSequence[] { key, value });

        // Because the key is a StringBuilder (not a String), the lookup will fail.
        String result = translateWhole(lt, "k");
        assertEquals("k", result);
    }

    /*** 9. Verify shortest/longest internal calculations via behavior *********/

    @Test
    public void testShortestAndLongestAffectTranslation() throws IOException {
        // Keys of lengths 1, 3, and 5
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[] { "a", "1" },
                new CharSequence[] { "abc", "2" },
                new CharSequence[] { "abcde", "3" });

        // Input that contains the longest key at the beginning
        String result = translateWhole(lt, "abcde");
        assertEquals("3", result);

        // Input that contains a medium key but also a longer possible match
        // The translator should still pick the longest possible match at each step.
        result = translateWhole(lt, "abc");
        assertEquals("2", result);
    }

    /*** 10. Ensure that translate returns 0 when nothing matches *************/

    @Test
    public void testTranslateReturnsZeroWhenNoMatch() throws IOException {
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[] { "foo", "bar" });

        StringWriter out = new StringWriter();
        int consumed = lt.translate("baz", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }
}
```
###Test END##