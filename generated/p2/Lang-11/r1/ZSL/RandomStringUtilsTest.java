/****************************************************************************************
 * RandomStringUtilsTest.java
 *
 * Comprehensive JUnit‑4 test suite for {@link org.apache.commons.lang3.RandomStringUtils}.
 *
 * The tests cover:
 *   • Normal usage of all public static factory methods.
 *   • Edge cases (zero length, negative length, empty char sources).
 *   • Validation of character categories (letters, digits, ASCII range).
 *   • Correct handling of Unicode surrogate pairs (high, low and private‑high surrogates).
 *   • Behaviour when a custom {@link java.util.Random} instance is supplied.
 *
 * The test class is deliberately self‑contained and does not rely on the internal
 * static {@code Random} used by the utility class, except where the API does not allow
 * injection of a custom source of randomness.
 *
 * Author: ChatGPT (OpenAI)
 * Date:   2024‑06‑01
 ****************************************************************************************/
package org.apache.commons.lang3;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/**
 * JUnit‑4 test cases for {@link RandomStringUtils}.
 */
public class RandomStringUtilsTest {

    /*======================================================================
     * Helper Random implementation that returns a predictable sequence.
     *====================================================================*/
    private static final class PredictableRandom extends Random {
        private final int[] values;
        private int index = 0;

        PredictableRandom(int... values) {
            this.values = values.clone();
        }

        @Override
        public int nextInt(int bound) {
            // Return the next value modulo the requested bound.
            int v = values[index % values.length];
            index++;
            return Math.abs(v) % bound;
        }
    }

    /*======================================================================
     * Simple sanity checks for length and basic character categories.
     *====================================================================*/

    @Test
    public void testRandomZeroLength() {
        assertEquals("", RandomStringUtils.random(0));
        assertEquals("", RandomStringUtils.randomAscii(0));
        assertEquals("", RandomStringUtils.randomAlphabetic(0));
        assertEquals("", RandomStringUtils.randomAlphanumeric(0));
        assertEquals("", RandomStringUtils.randomNumeric(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomNegativeLength() {
        RandomStringUtils.random(-5);
    }

    @Test
    public void testRandomAsciiRange() {
        String s = RandomStringUtils.randomAscii(100);
        assertEquals(100, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("ASCII char out of range: " + (int) c,
                    c >= 32 && c <= 126);
        }
    }

    @Test
    public void testRandomAlphabeticOnlyLetters() {
        String s = RandomStringUtils.randomAlphabetic(50);
        assertEquals(50, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Non‑letter found: " + c, Character.isLetter(c));
        }
    }

    @Test
    public void testRandomNumericOnlyDigits() {
        String s = RandomStringUtils.randomNumeric(40);
        assertEquals(40, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Non‑digit found: " + c, Character.isDigit(c));
        }
    }

    @Test
    public void testRandomAlphanumericContainsBoth() {
        String s = RandomStringUtils.randomAlphanumeric(200);
        assertEquals(200, s.length());

        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            }
            if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        assertTrue("Expected at least one letter", hasLetter);
        assertTrue("Expected at least one digit", hasDigit);
    }

    @Test
    public void testRandomWithCustomCharArray() {
        char[] source = new char[] {'A', 'B', 'C', '1', '2', '3'};
        String s = RandomStringUtils.random(20, source);
        assertEquals(20, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Character not from source array", 
                       new String(source).indexOf(c) >= 0);
        }
    }

    @Test
    public void testRandomWithStringSource() {
        String source = "xyzXYZ789";
        String s = RandomStringUtils.random(30, source);
        assertEquals(30, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Character not from source string",
                       source.indexOf(c) >= 0);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomWithEmptyCharArray() {
        RandomStringUtils.random(5, new char[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomWithEmptyStringSource() {
        RandomStringUtils.random(5, "");
    }

    /*======================================================================
     * Tests for surrogate‑pair handling using the overload that accepts a
     * custom Random instance.
     *====================================================================*/

    /**
     * Forces the generator to pick a low surrogate (0xDC00‑0xDFFF) as the first
     * character of a two‑character string. The implementation should prepend a
     * matching high surrogate, resulting in a valid surrogate pair.
     */
    @Test
    public void testLowSurrogatePrependedWithHigh() {
        // We want the first random int to map to a low surrogate.
        // start = 0, end = 0x10000 (65536) => gap = 65536.
        // low surrogate range offset = 0xDC00 = 56320.
        int lowSurrogateOffset = 0xDC00;
        PredictableRandom rng = new PredictableRandom(lowSurrogateOffset);
        String result = RandomStringUtils.random(2,
                0, 0x10000, false, false, null, rng);

        assertEquals(2, result.length());

        char first = result.charAt(0);
        char second = result.charAt(1);
        // The first char must be a high surrogate (0xD800‑0xDBFF)
        assertTrue("First char should be high surrogate", first >= 0xD800 && first <= 0xDBFF);
        // The second char must be the low surrogate we forced.
        assertEquals("Second char should be the forced low surrogate",
                (char) (lowSurrogateOffset), second);
        // Verify that they form a valid pair.
        assertTrue(Character.isSurrogatePair(first, second));
    }

    /**
     * Forces the generator to pick a high surrogate (0xD800‑0xDB7F) as the first
     * character of a two‑character string. The implementation should append a
     * matching low surrogate, resulting in a valid surrogate pair.
     */
    @Test
    public void testHighSurrogateFollowedByLow() {
        // High surrogate offset = 0xD800 = 55296
        int highSurrogateOffset = 0xD800;
        PredictableRandom rng = new PredictableRandom(highSurrogateOffset);
        String result = RandomStringUtils.random(2,
                0, 0x10000, false, false, null, rng);

        assertEquals(2, result.length());

        char first = result.charAt(0);
        char second = result.charAt(1);
        // The first char must be the forced high surrogate.
        assertEquals("First char should be the forced high surrogate",
                (char) highSurrogateOffset, first);
        // The second char must be a low surrogate (0xDC00‑0xDFFF)
        assertTrue("Second char should be low surrogate", second >= 0xDC00 && second <= 0xDFFF);
        // Verify that they form a valid pair.
        assertTrue(Character.isSurrogatePair(first, second));
    }

    /**
     * Forces the generator to pick a private‑high surrogate (0xDB80‑0xDBFF). The
     * implementation is defined to skip such characters. With a count of 2 we
     * expect the result length still to be 2 because the skipped character is
     * compensated by an extra iteration.
     */
    @Test
    public void testPrivateHighSurrogateIsSkipped() {
        // Private high surrogate start = 0xDB80 = 56192
        int privateHighStart = 0xDB80;
        PredictableRandom rng = new PredictableRandom(privateHighStart);
        // request length 2; the first draw will be a private high surrogate and be ignored.
        String result = RandomStringUtils.random(2,
                0, 0x10000, false, false, null, rng);
        // The result must still be of length 2.
        assertEquals(2, result.length());
        // Ensure that none of the chars are in the private‑high range.
        for (char c : result.toCharArray()) {
            assertFalse("Result contains private high surrogate",
                    c >= 0xDB80 && c <= 0xDBFF);
        }
    }

    /*======================================================================
     * Additional corner‑case tests for the overload that accepts a char[]
     * source together with start/end bounds.
     *====================================================================*/

    @Test
    public void testRandomWithStartEndAndCharArray() {
        char[] src = new char[] {'a', 'b', 'c', '1', '2', '3'};
        // start = 2 (character 'c'), end = 5 (exclusive, i.e. up to '3')
        String s = RandomStringUtils.random(10, 2, 5, false, false, src, new Random());
        assertEquals(10, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Character out of expected slice",
                    c == 'c' || c == '1' || c == '2' || c == '3');
        }
    }

    @Test
    public void testRandomLettersNumbersFlagsWithCharArray() {
        char[] src = "ABCxyz123".toCharArray();
        // Request only letters (numbers flag false)
        String lettersOnly = RandomStringUtils.random(20, 0, src.length,
                true, false, src, new Random());
        for (char c : lettersOnly.toCharArray()) {
            assertTrue("Non‑letter returned despite letters flag", Character.isLetter(c));
        }
        // Request only numbers (letters flag false)
        String numbersOnly = RandomStringUtils.random(20, 0, src.length,
                false, true, src, new Random());
        for (char c : numbersOnly.toCharArray()) {
            assertTrue("Non‑digit returned despite numbers flag", Character.isDigit(c));
        }
    }

}
