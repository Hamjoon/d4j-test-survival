###Test START##
```java
/**
 * JUnit 4 test cases for {@link org.apache.commons.lang3.text.translate.LookupTranslator}.
 *
 * The tests cover:
 * <ul>
 *   <li>Construction with {@code null} and empty lookup tables.</li>
 *   <li>Correct handling of the shortest / longest key lengths (indirectly via behaviour).</li>
 *   <li>Greedy matching – the longest possible key is chosen.</li>
 *   <li>Behaviour when the input is shorter than the longest key.</li>
 *   <li>Translation of matching and non‑matching inputs.</li>
 *   <li>Repeated calls to {@code translate} to simulate a full‑string translation.</li>
 * </ul>
 */
package org.apache.commons.lang3.text.translate;

import org.junit.Test;
import org.junit.Assert;

import java.io.IOException;
import java.io.StringWriter;

/**
 * Test class for {@link LookupTranslator}.
 */
public class LookupTranslatorTest {

    /**
     * Helper method that invokes {@code translate} on the supplied translator
     * and returns both the number of characters consumed and the content written
     * to the writer.
     */
    private static class TranslateResult {
        final int consumed;
        final String output;

        TranslateResult(int consumed, String output) {
            this.consumed = consumed;
            this.output = output;
        }
    }

    private TranslateResult translate(LookupTranslator translator,
                                        CharSequence input,
                                        int index) throws IOException {
        StringWriter writer = new StringWriter();
        int consumed = translator.translate(input, index, writer);
        return new TranslateResult(consumed, writer.toString());
    }

    /** --------------------------------------------------------------------- *
     *  1. Construction edge cases – null and empty lookup tables.
     *  --------------------------------------------------------------------- */

    @Test
    public void testConstructorWithNullLookup() throws IOException {
        LookupTranslator lt = new LookupTranslator(null);
        // No mappings – any input should result in 0 characters consumed and no output.
        TranslateResult r = translate(lt, "any text", 0);
        Assert.assertEquals("Null lookup table should not consume characters", 0, r.consumed);
        Assert.assertEquals("Null lookup table should produce no output", "", r.output);
    }

    @Test
    public void testConstructorWithEmptyLookup() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[0][]);
        TranslateResult r = translate(lt, "foobar", 2);
        Assert.assertEquals("Empty lookup table should not consume characters", 0, r.consumed);
        Assert.assertEquals("Empty lookup table should produce no output", "", r.output);
    }

    /** --------------------------------------------------------------------- *
     *  2. Shortest / longest key length handling (indirect verification).
     *  --------------------------------------------------------------------- */

    @Test
    public void testShortestAndLongestLengthsViaBehaviour() throws IOException {
        // Keys of length 1, 3 and 5
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[]{"a", "A"},
                new CharSequence[]{"abc", "X"},
                new CharSequence[]{"abcde", "Y"}
        );

        // Input that matches the longest key (length 5)
        TranslateResult rLong = translate(lt, "abcde", 0);
        Assert.assertEquals("Should consume 5 characters (longest match)", 5, rLong.consumed);
        Assert.assertEquals("Longest match should produce 'Y'", "Y", rLong.output);

        // Input that matches only the middle key (length 3)
        TranslateResult rMid = translate(lt, "abcxyz", 0);
        Assert.assertEquals("Should consume 3 characters (middle length match)", 3, rMid.consumed);
        Assert.assertEquals("Middle match should produce 'X'", "X", rMid.output);

        // Input that matches only the shortest key (length 1)
        TranslateResult rShort = translate(lt, "a123", 0);
        Assert.assertEquals("Should consume 1 character (shortest match)", 1, rShort.consumed);
        Assert.assertEquals("Shortest match should produce 'A'", "A", rShort.output);
    }

    /** --------------------------------------------------------------------- *
     *  3. Greedy matching – longest possible key wins.
     *  --------------------------------------------------------------------- */

    @Test
    public void testGreedyMatchingPrefersLongestKey() throws IOException {
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[]{"ab", "ONE"},
                new CharSequence[]{"abc", "TWO"}
        );

        // Input "abc" could match "ab" (2 chars) or "abc" (3 chars). Greedy -> "abc".
        TranslateResult result = translate(lt, "abc", 0);
        Assert.assertEquals("Greedy algorithm should consume 3 characters", 3, result.consumed);
        Assert.assertEquals("Greedy algorithm should output the value for 'abc'", "TWO", result.output);
    }

    /** --------------------------------------------------------------------- *
     *  4. Behaviour when the longest key is longer than the remaining input.
     *  --------------------------------------------------------------------- */

    @Test
    public void testNoMatchWhenInputShorterThanLongestKey() throws IOException {
        // Only one key of length 4
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[]{"abcd", "MATCH"}
        );

        // Input length is 3, shorter than the key length.
        TranslateResult result = translate(lt, "abc", 0);
        Assert.assertEquals("Should not match when input is shorter than the only key", 0, result.consumed);
        Assert.assertEquals("No output should be produced", "", result.output);
    }

    /** --------------------------------------------------------------------- *
     *  5. Non‑matching input – translator should leave writer untouched.
     *  --------------------------------------------------------------------- */

    @Test
    public void testNonMatchingInputProducesNoOutput() throws IOException {
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[]{"hello", "world"},
                new CharSequence[]{"foo", "bar"}
        );

        TranslateResult result = translate(lt, "nomatch", 0);
        Assert.assertEquals("Non‑matching input must consume 0 characters", 0, result.consumed);
        Assert.assertEquals("Writer must remain empty for non‑matching input", "", result.output);
    }

    /** --------------------------------------------------------------------- *
     *  6. Multiple successive translations – simulating a full‑string translation.
     *  --------------------------------------------------------------------- */

    @Test
    public void testSequentialTranslationsCoverWholeString() throws IOException {
        // Map some HTML entities
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[]{"&", "&amp;"},
                new CharSequence[]{"<", "&lt;"},
                new CharSequence[]{">", "&gt;"}
        );

        String input = "a & b < c > d";
        StringBuilder output = new StringBuilder();

        int i = 0;
        while (i < input.length()) {
            // Attempt to translate at the current position
            TranslateResult r = translate(lt, input, i);
            if (r.consumed == 0) {
                // No translation – copy the original character
                output.append(input.charAt(i));
                i++;
            } else {
                // Translation occurred – append the translated text and skip consumed chars
                output.append(r.output);
                i += r.consumed;
            }
        }

        String expected = "a &amp; b &lt; c &gt; d";
        Assert.assertEquals("Sequential translation should replace all entities", expected, output.toString());
    }

    /** --------------------------------------------------------------------- *
     *  7. Index out of bounds – translator should safely return 0.
     *  --------------------------------------------------------------------- */

    @Test
    public void testTranslateWithIndexEqualToLengthReturnsZero() throws IOException {
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[]{"x", "y"}
        );

        // Index points exactly at the end of the string – nothing to translate.
        TranslateResult result = translate(lt, "test", 4);
        Assert.assertEquals("When index == length, translator must return 0", 0, result.consumed);
        Assert.assertEquals("No output should be produced", "", result.output);
    }

    /** --------------------------------------------------------------------- *
     *  8. Verify that the internal map uses {@code String} keys via equality.
     *  --------------------------------------------------------------------- */

    @Test
    public void testLookupMapWorksWithCharSequenceImplementations() throws IOException {
        // Use a StringBuilder as the key – it is a CharSequence but does not override hashCode().
        // The constructor stores the exact object; lookup uses the same instance, so it works.
        StringBuilder key = new StringBuilder("key");
        LookupTranslator lt = new LookupTranslator(
                new CharSequence[]{key, "value"}
        );

        // The same StringBuilder instance should translate correctly.
        TranslateResult rSameInstance = translate(lt, "key", 0);
        Assert.assertEquals("Exact same CharSequence instance should be found", 3, rSameInstance.consumed);
        Assert.assertEquals("value", rSameInstance.output);

        // A different CharSequence with the same characters should *not* match,
        // because the map key is the original object (StringBuilder) and hashCode differs.
        // This confirms the comment in the source about converting to String in newer versions.
        TranslateResult rDifferentInstance = translate(lt, new StringBuilder("key"), 0);
        Assert.assertEquals("Different CharSequence instance should not match (pre‑Lang‑3.1 behaviour)", 0, rDifferentInstance.consumed);
        Assert.assertEquals("", rDifferentInstance.output);
    }
}
```
###Test END##