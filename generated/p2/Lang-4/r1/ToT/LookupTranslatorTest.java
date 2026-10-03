/****************************************************************************************
 * JUnit‑4 test suite for {@link org.apache.commons.lang3.text.translate.LookupTranslator}
 *
 * The tests are written from the perspective of three “experts”.  Each expert contributes
 * one test case per public method, covering typical usage, edge‑cases and error
 * scenarios.  The suite therefore exercises:
 *
 *   • Constructor behaviour (calculation of shortest/longest, handling of null/empty
 *     lookup tables, duplicate keys, and different CharSequence implementations)
 *
 *   • {@code translate(...)} – greedy longest‑match algorithm, boundary handling,
 *     non‑matching input, and propagation of {@link IOException}
 *
 * The tests use reflection to inspect the private fields {@code shortest} and
 * {@code longest} because they are part of the contract of the translator.
 *
 * Author:  simulated “expert” collaboration
 * Version: JUnit‑4 (compatible with Java 8+)
 ****************************************************************************************/

package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

/**
 * Comprehensive test cases for {@link LookupTranslator}.
 */
public class LookupTranslatorTest {

    private LookupTranslator translator;

    /** Helper to read the private {@code shortest} field via reflection. */
    private static int getShortest(LookupTranslator lt) throws Exception {
        Field f = LookupTranslator.class.getDeclaredField("shortest");
        f.setAccessible(true);
        return f.getInt(lt);
    }

    /** Helper to read the private {@code longest} field via reflection. */
    private static int getLongest(LookupTranslator lt) throws Exception {
        Field f = LookupTranslator.class.getDeclaredField("longest");
        f.setAccessible(true);
        return f.getInt(lt);
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 1 – Constructor validation (shortest / longest calculation)
     * -------------------------------------------------------------------------
     */
    @Test
    public void testConstructorCalculatesShortestAndLongest() throws Exception {
        // lookup table with keys of length 1, 2 and 3
        CharSequence[][] table = {
                {"a", "1"},
                {"ab", "2"},
                {"abc", "3"}
        };
        translator = new LookupTranslator(table);

        assertEquals("Shortest key length should be 1", 1, getShortest(translator));
        assertEquals("Longest key length should be 3", 3, getLongest(translator));
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 2 – Translate method – greedy longest‑match behaviour
     * -------------------------------------------------------------------------
     */
    @Test
    public void testTranslateGreedyLongestMatch() throws IOException {
        CharSequence[][] table = {
                {"a", "1"},
                {"ab", "2"},
                {"abc", "3"}
        };
        translator = new LookupTranslator(table);

        StringWriter out = new StringWriter();
        int consumed = translator.translate("abc", 0, out);

        assertEquals("Translator should consume the whole longest match (3 chars)", 3, consumed);
        assertEquals("Output should be the value for the longest key", "3", out.toString());
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 3 – Translate method – fallback to shorter match when longer does not exist
     * -------------------------------------------------------------------------
     */
    @Test
    public void testTranslateFallsBackToShorterMatch() throws IOException {
        CharSequence[][] table = {
                {"a", "1"},
                {"ab", "2"},
                {"abc", "3"}
        };
        translator = new LookupTranslator(table);

        StringWriter out = new StringWriter();
        int consumed = translator.translate("abx", 0, out);

        assertEquals("Should fall back to the 'ab' entry (2 chars)", 2, consumed);
        assertEquals("Output should be the value for the 'ab' key", "2", out.toString());
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 1 – Constructor with null lookup array
     * -------------------------------------------------------------------------
     */
    @Test
    public void testConstructorWithNullLookup() throws Exception {
        translator = new LookupTranslator((CharSequence[][]) null);

        // With a null lookup the internal shortest should stay at MAX_VALUE and longest at 0
        assertEquals(Integer.MAX_VALUE, getShortest(translator));
        assertEquals(0, getLongest(translator));

        // translate must simply return 0 and not throw any exception
        StringWriter out = new StringWriter();
        int consumed = translator.translate("anything", 0, out);
        assertEquals("No translation possible, should return 0", 0, consumed);
        assertEquals("Writer must stay empty", "", out.toString());
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 2 – Empty lookup (no var‑args entries)
     * -------------------------------------------------------------------------
     */
    @Test
    public void testConstructorWithEmptyLookup() throws Exception {
        translator = new LookupTranslator(); // empty var‑args

        assertEquals(Integer.MAX_VALUE, getShortest(translator));
        assertEquals(0, getLongest(translator));

        StringWriter out = new StringWriter();
        int consumed = translator.translate("test", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 3 – Translate with no matching key
     * -------------------------------------------------------------------------
     */
    @Test
    public void testTranslateNoMatchReturnsZero() throws IOException {
        CharSequence[][] table = { {"x", "X"} };
        translator = new LookupTranslator(table);

        StringWriter out = new StringWriter();
        int consumed = translator.translate("abc", 0, out);

        assertEquals("When nothing matches, translate should return 0", 0, consumed);
        assertEquals("Writer must remain untouched when no match", "", out.toString());
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 1 – Boundary handling when input is shorter than the longest key
     * -------------------------------------------------------------------------
     */
    @Test
    public void testTranslateRespectsInputBoundary() throws IOException {
        // longest key is 4, but input length is only 3
        CharSequence[][] table = { {"abcd", "Z"} };
        translator = new LookupTranslator(table);

        StringWriter out = new StringWriter();
        int consumed = translator.translate("abc", 0, out);

        // No match because the only key is longer than the remaining input
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 2 – Propagation of IOException from the Writer
     * -------------------------------------------------------------------------
     */
    @Test(expected = IOException.class)
    public void testTranslatePropagatesIOException() throws IOException {
        CharSequence[][] table = { {"boom", "B"} };
        translator = new LookupTranslator(table);

        Writer badWriter = new Writer() {
            @Override public void write(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("forced failure");
            }
            @Override public void flush() throws IOException {}
            @Override public void close() throws IOException {}
        };

        // This call must throw the IOException defined above
        translator.translate("boom", 0, badWriter);
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 3 – Using non‑String CharSequence implementations as keys/values
     * -------------------------------------------------------------------------
     */
    @Test
    public void testLookupWithDifferentCharSequenceImplementations() throws IOException {
        // Use StringBuilder for the key and value – they are not Strings but implement CharSequence
        CharSequence key = new StringBuilder("key");
        CharSequence value = new StringBuilder("val");
        CharSequence[][] table = { { key, value } };

        translator = new LookupTranslator(table);

        StringWriter out = new StringWriter();
        int consumed = translator.translate("key", 0, out);

        assertEquals(3, consumed);
        assertEquals("val", out.toString());
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 1 – Duplicate keys – the last entry should win (Map.put overwrites)
     * -------------------------------------------------------------------------
     */
    @Test
    public void testDuplicateKeysLastOneWins() throws IOException {
        CharSequence[][] table = {
                {"dup", "first"},
                {"dup", "second"} // should overwrite the previous entry
        };
        translator = new LookupTranslator(table);

        StringWriter out = new StringWriter();
        int consumed = translator.translate("dup", 0, out);

        assertEquals(3, consumed);
        assertEquals("second", out.toString());
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 2 – Translate at a non‑zero index (partial scanning)
     * -------------------------------------------------------------------------
     */
    @Test
    public void testTranslateFromNonZeroIndex() throws IOException {
        CharSequence[][] table = {
                {"foo", "F"},
                {"bar", "B"}
        };
        translator = new LookupTranslator(table);

        StringWriter out = new StringWriter();
        // Input "xxfoobar", start at index 2 (the first 'f')
        int consumed = translator.translate("xxfoobar", 2, out);

        assertEquals(3, consumed); // matches "foo"
        assertEquals("F", out.toString());
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 3 – Verify that translate does *not* modify the writer when
     *           the matching key has an empty replacement.
     * -------------------------------------------------------------------------
     */
    @Test
    public void testTranslateWithEmptyReplacement() throws IOException {
        CharSequence[][] table = { {"empty", ""} };
        translator = new LookupTranslator(table);

        StringWriter out = new StringWriter();
        int consumed = translator.translate("empty", 0, out);

        assertEquals(5, consumed);
        assertEquals("", out.toString()); // writer stays empty
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 1 – Verify that the translator is greedy even when multiple keys
     *           share the same prefix.
     * -------------------------------------------------------------------------
     */
    @Test
    public void testGreedyWhenPrefixesOverlap() throws IOException {
        CharSequence[][] table = {
                {"a", "1"},
                {"ab", "2"},
                {"abc", "3"},
                {"abcd", "4"}
        };
        translator = new LookupTranslator(table);

        StringWriter out = new StringWriter();
        int consumed = translator.translate("abcd", 0, out);

        assertEquals(4, consumed);
        assertEquals("4", out.toString()); // longest match wins
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 2 – Verify that translate returns 0 when the remaining input
     *           length is smaller than the shortest key.
     * -------------------------------------------------------------------------
     */
    @Test
    public void testTranslateWhenRemainingShorterThanShortestKey() throws IOException {
        CharSequence[][] table = { {"xyz", "Z"} };
        translator = new LookupTranslator(table); // shortest = 3, longest = 3

        StringWriter out = new StringWriter();
        // Start at index 2 of "ab" – only 0 chars left
        int consumed = translator.translate("ab", 2, out);

        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 3 – Verify that a null input CharSequence causes a NullPointerException
     *           (the contract of CharSequenceTranslator does not guard against it).
     * -------------------------------------------------------------------------
     */
    @Test(expected = NullPointerException.class)
    public void testTranslateWithNullInputThrowsNPE() throws IOException {
        CharSequence[][] table = { {"a", "A"} };
        translator = new LookupTranslator(table);

        translator.translate(null, 0, new StringWriter());
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 1 – Verify that a null Writer causes a NullPointerException
     * -------------------------------------------------------------------------
     */
    @Test(expected = NullPointerException.class)
    public void testTranslateWithNullWriterThrowsNPE() throws IOException {
        CharSequence[][] table = { {"a", "A"} };
        translator = new LookupTranslator(table);

        translator.translate("a", 0, null);
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 2 – Verify that an out‑of‑range index throws IndexOutOfBoundsException
     * -------------------------------------------------------------------------
     */
    @Test(expected = IndexOutOfBoundsException.class)
    public void testTranslateWithInvalidIndexThrowsIOOBE() throws IOException {
        CharSequence[][] table = { {"a", "A"} };
        translator = new LookupTranslator(table);

        // index larger than input length
        translator.translate("a", 5, new StringWriter());
    }

    /**
     * -------------------------------------------------------------------------
     * Expert 3 – Verify that the internal map uses String keys (as per LANG‑882)
     * -------------------------------------------------------------------------
     */
    @Test
    public void testInternalKeyIsString() throws Exception {
        // Provide a key that is not a String (StringBuilder)
        CharSequence key = new StringBuilder("k");
        CharSequence value = "v";
        CharSequence[][] table = { { key, value } };

        translator = new LookupTranslator(table);

        // Access the private map via reflection and check the actual key type
        Field mapField = LookupTranslator.class.getDeclaredField("lookupMap");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<CharSequence, CharSequence> map = (java.util.Map<CharSequence, CharSequence>) mapField.get(translator);

        // The map should contain a String key, not the original StringBuilder instance
        assertTrue("Map should contain exactly one entry", map.size() == 1);
        CharSequence storedKey = map.keySet().iterator().next();
        assertTrue("Stored key should be a java.lang.String", storedKey instanceof String);
        assertEquals("k", storedKey.toString());

        // Normal translation must still work
        StringWriter out = new StringWriter();
        int consumed = translator.translate("k", 0, out);
        assertEquals(1, consumed);
        assertEquals("v", out.toString());
    }
}
