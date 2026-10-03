package org.apache.commons.lang3.text.translate;

import org.junit.Test;
import org.junit.Before;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Comprehensive JUnit 4 tests for {@link LookupTranslator}.
 *
 * The tests cover:
 * <ul>
 *   <li>Normal construction with a lookup table.</li>
 *   <li>Construction with {@code null} or empty lookup tables.</li>
 *   <li>Greedy matching (longest key wins).</li>
 *   <li>No‑match scenarios.</li>
 *   <li>Boundary conditions on the {@code index} argument.</li>
 *   <li>Exception propagation from the {@link Writer}.</li>
 *   <li>Invalid arguments (e.g., negative index).</li>
 * </ul>
 */
public class LookupTranslatorTest {

    private static final CharSequence[][] SIMPLE_LOOKUP = {
            {"a", "A"},
            {"ab", "AB"},
            {"b", "B"},
            {"<", "&lt;"},
            {">", "&gt;"}
    };

    private LookupTranslator simpleTranslator;

    @Before
    public void setUp() {
        simpleTranslator = new LookupTranslator(SIMPLE_LOOKUP);
    }

    /**
     * Verify that the constructor correctly builds the internal map and that a
     * known key translates as expected.
     */
    @Test
    public void testConstructorWithValidLookup() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = simpleTranslator.translate("<ab>", 0, out);
        // The longest matching key at position 0 is "<"
        assertEquals(1, consumed);
        assertEquals("&lt;", out.toString());

        // Translate the remaining characters manually to ensure the map works
        out.getBuffer().setLength(0);
        consumed = simpleTranslator.translate("ab>", 1, out); // start at 'b'
        assertEquals(2, consumed); // "b>" is not a key, but "b" matches (1) then ">" matches later
        // Actually the method only consumes from the given index; here index=1 points at 'b'
        // The longest match is "b" (length 1)
        assertEquals("B", out.toString());
    }

    /**
     * When the lookup table is {@code null}, the translator should behave as a
     * no‑op (i.e., never match anything) and must not throw NPE.
     */
    @Test
    public void testConstructorWithNullLookup() throws IOException {
        LookupTranslator lt = new LookupTranslator(null);
        StringWriter out = new StringWriter();
        int consumed = lt.translate("anything", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    /**
     * An empty lookup array should also produce a no‑op translator.
     */
    @Test
    public void testConstructorWithEmptyLookup() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[0][]);
        StringWriter out = new StringWriter();
        int consumed = lt.translate("test", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    /**
     * The translator must apply a greedy algorithm: when both "a" and "ab"
     * exist, the longer key ("ab") wins.
     */
    @Test
    public void testGreedyMatching() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = simpleTranslator.translate("ab", 0, out);
        assertEquals(2, consumed);               // "ab" should be consumed
        assertEquals("AB", out.toString());       // not "A"
    }

    /**
     * When there is no matching key, the method should return {@code 0}
     * and write nothing.
     */
    @Test
    public void testNoMatchReturnsZero() throws IOException {
        StringWriter out = new StringWriter();
        int consumed = simpleTranslator.translate("xyz", 0, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    /**
     * If the supplied {@code index} points to the last character, the translator
     * must correctly handle the reduced maximum length.
     */
    @Test
    public void testTranslateWithIndexAtEnd() throws IOException {
        // Input length is 5, index = 5 (i.e., start after the last character)
        StringWriter out = new StringWriter();
        int consumed = simpleTranslator.translate("abcde", 5, out);
        assertEquals(0, consumed);
        assertEquals("", out.toString());

        // Index points to the last character which is a known key
        out.getBuffer().setLength(0);
        consumed = simpleTranslator.translate("<>", 0, out); // "<" at index 0
        assertEquals(1, consumed);
        assertEquals("&lt;", out.toString());

        out.getBuffer().setLength(0);
        consumed = simpleTranslator.translate("<>", 1, out); // ">" at index 1
        assertEquals(1, consumed);
        assertEquals("&gt;", out.toString());
    }

    /**
     * Passing a negative index should result in an {@link IndexOutOfBoundsException}
     * from {@link CharSequence#subSequence(int, int)}.
     */
    @Test(expected = IndexOutOfBoundsException.class)
    public void testTranslateWithNegativeIndex() throws IOException {
        StringWriter out = new StringWriter();
        simpleTranslator.translate("test", -1, out);
    }

    /**
     * The translator must propagate {@link IOException} from the supplied
     * {@link Writer}. We use a custom writer that always throws.
     */
    @Test(expected = IOException.class)
    public void testWriterIOExceptionPropagates() throws IOException {
        Writer throwingWriter = new Writer() {
            @Override public void write(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("forced");
            }
            @Override public void flush() throws IOException {}
            @Override public void close() throws IOException {}
        };
        simpleTranslator.translate("<", 0, throwingWriter);
    }

    /**
     * Verify that the internal shortest/longest calculations are correct when
     * the lookup contains keys of varying lengths, including a single‑character
     * key that is longer than any other key (edge of the algorithm).
     */
    @Test
    public void testShortestAndLongestBoundaries() throws IOException {
        CharSequence[][] mixed = {
                {"xyz", "X"},
                {"xy", "Y"},
                {"x", "Z"}
        };
        LookupTranslator lt = new LookupTranslator(mixed);
        StringWriter out = new StringWriter();

        // Longest key ("xyz") should be taken
        int consumed = lt.translate("xyz", 0, out);
        assertEquals(3, consumed);
        assertEquals("X", out.toString());

        // When only a shorter key matches
        out.getBuffer().setLength(0);
        consumed = lt.translate("xy", 0, out);
        assertEquals(2, consumed);
        assertEquals("Y", out.toString());

        // Single‑character match
        out.getBuffer().setLength(0);
        consumed = lt.translate("x", 0, out);
        assertEquals(1, consumed);
        assertEquals("Z", out.toString());
    }

    /**
     * Confirm that duplicate keys in the lookup array result in the last entry
     * overriding earlier ones (standard {@link HashMap} behaviour).
     */
    @Test
    public void testDuplicateKeysOverride() throws IOException {
        CharSequence[][] dup = {
                {"dup", "first"},
                {"dup", "second"}
        };
        LookupTranslator lt = new LookupTranslator(dup);
        StringWriter out = new StringWriter();
        int consumed = lt.translate("dup", 0, out);
        assertEquals(3, consumed);
        assertEquals("second", out.toString());
    }
}
