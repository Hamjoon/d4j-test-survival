/***************************************************************************************************
 * Comprehensive JUnit‑4 test suite for {@link org.apache.commons.lang3.RandomStringUtils}.
 *
 * The tests are organized as if three independent experts contributed one test per public method,
 * then merged their work into this single file.
 *
 * The suite covers:
 *   • Basic functional verification for each overload.
 *   • Edge‑case handling (zero/negative length, empty source arrays, surrogate characters, etc.).
 *   • Exception‑throwing behaviour.
 *
 * The tests use a deterministic {@link FixedRandom} implementation to make the random selections
 * predictable where needed (especially for surrogate‑handling tests).
 *
 * Run with any JUnit‑4 runner (e.g. via Maven Surefire, IDE, or the command line).
 **************************************************************************************************/
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

/**
 * Test class for {@link RandomStringUtils}.
 */
public class RandomStringUtilsTest {

    /** -----------------------------------------------------------------------
     *  Helper: a {@link Random} that returns a predefined sequence of ints.
     *  When the sequence is exhausted it repeats from the start.
     *  This makes the random selection deterministic for the tests that need it.
     *  -------------------------------------------------------------------- */
    private static final class FixedRandom extends Random {
        private static final long serialVersionUID = 1L;
        private final int[] values;
        private int idx = 0;

        FixedRandom(int... values) {
            this.values = values.clone();
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            int v = values[idx % values.length] % bound;
            idx++;
            return v;
        }
    }

    /** -----------------------------------------------------------------------
     *  1. public static String random(int count)
     *  -------------------------------------------------------------------- */
    @Test
    public void testRandom_basicLength() {
        String s = RandomStringUtils.random(10);
        assertNotNull(s);
        assertEquals(10, s.length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandom_negativeCountThrows() {
        RandomStringUtils.random(-1);
    }

    @Test
    public void testRandom_zeroCountReturnsEmpty() {
        assertEquals("", RandomStringUtils.random(0));
    }

    /** -----------------------------------------------------------------------
     *  2. public static String randomAscii(int count)
     *  -------------------------------------------------------------------- */
    @Test
    public void testRandomAscii_rangeAndLength() {
        String s = RandomStringUtils.randomAscii(20);
        assertEquals(20, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("ASCII char out of range: " + (int) c,
                       c >= 32 && c <= 126);
        }
    }

    /** -----------------------------------------------------------------------
     *  3. public static String randomAlphabetic(int count)
     *  -------------------------------------------------------------------- */
    @Test
    public void testRandomAlphabetic_containsOnlyLetters() {
        String s = RandomStringUtils.randomAlphabetic(15);
        assertEquals(15, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Non‑letter found: " + c, Character.isLetter(c));
        }
    }

    /** -----------------------------------------------------------------------
     *  4. public static String randomAlphanumeric(int count)
     *  -------------------------------------------------------------------- */
    @Test
    public void testRandomAlphanumeric_containsLettersOrDigits() {
        String s = RandomStringUtils.randomAlphanumeric(25);
        assertEquals(25, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Character is neither letter nor digit: " + c,
                       Character.isLetter(c) || Character.isDigit(c));
        }
    }

    /** -----------------------------------------------------------------------
     *  5. public static String randomNumeric(int count)
     *  -------------------------------------------------------------------- */
    @Test
    public void testRandomNumeric_containsOnlyDigits() {
        String s = RandomStringUtils.randomNumeric(12);
        assertEquals(12, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Non‑digit found: " + c, Character.isDigit(c));
        }
    }

    /** -----------------------------------------------------------------------
     *  6. public static String random(int count, boolean letters, boolean numbers)
     *  -------------------------------------------------------------------- */
    @Test
    public void testRandom_lettersTrueNumbersFalse() {
        String s = RandomStringUtils.random(8, true, false);
        assertEquals(8, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Expected only letters", Character.isLetter(c));
        }
    }

    @Test
    public void testRandom_lettersFalseNumbersTrue() {
        String s = RandomStringUtils.random(8, false, true);
        assertEquals(8, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Expected only digits", Character.isDigit(c));
        }
    }

    @Test
    public void testRandom_lettersTrueNumbersTrue() {
        String s = RandomStringUtils.random(8, true, true);
        assertEquals(8, s.length());
        // each char must be either a letter or a digit
        for (char c : s.toCharArray()) {
            assertTrue("Expected letter or digit",
                       Character.isLetter(c) || Character.isDigit(c));
        }
    }

    @Test
    public void testRandom_lettersFalseNumbersFalse() {
        // When both flags are false, any character may be returned.
        // We just verify length and that no exception is thrown.
        String s = RandomStringUtils.random(8, false, false);
        assertEquals(8, s.length());
    }

    /** -----------------------------------------------------------------------
     *  7. public static String random(int count, int start, int end,
     *                                 boolean letters, boolean numbers)
     *  -------------------------------------------------------------------- */
    @Test
    public void testRandom_startEndRange() {
        // Choose a narrow range: characters 'a'..'c' (97‑99)
        String s = RandomStringUtils.random(5, 97, 100, false, false);
        assertEquals(5, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Char not in expected range", c >= 'a' && c <= 'c');
        }
    }

    /** -----------------------------------------------------------------------
     *  8. public static String random(int count, int start, int end,
     *                                 boolean letters, boolean numbers, char... chars)
     *  -------------------------------------------------------------------- */
    @Test
    public void testRandom_withCustomCharArray() {
        char[] custom = {'X', 'Y', 'Z'};
        String s = RandomStringUtils.random(6, 0, custom.length, false, false, custom);
        assertEquals(6, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Unexpected char from custom array", c == 'X' || c == 'Y' || c == 'Z');
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandom_customCharArrayEmptyThrows() {
        RandomStringUtils.random(5, 0, 0, false, false, new char[0]);
    }

    /** -----------------------------------------------------------------------
     *  9. public static String random(int count, int start, int end,
     *                                 boolean letters, boolean numbers,
     *                                 char[] chars, Random random)
     *  -------------------------------------------------------------------- */
    @Test
    public void testRandom_withFixedRandomDeterministic() {
        // chars array contains 'a', 'b', 'c'
        char[] chars = {'a', 'b', 'c'};
        // FixedRandom will always return 1 -> selects 'b'
        FixedRandom rng = new FixedRandom(1);
        String s = RandomStringUtils.random(4, 0, chars.length, false, false, chars, rng);
        assertEquals("bbbb", s);
    }

    /** ------------------- Surrogate handling tests --------------------------- */
    @Test
    public void testRandom_highSurrogateFollowedByLow() {
        // Build a char set that forces selection of a high surrogate (0xD800)
        char[] set = {(char) 0xD800, 'A'};
        // FixedRandom returns index 0 first (high surrogate), then 0 again for low
        FixedRandom rng = new FixedRandom(0, 0);
        // request length 2, we expect a proper surrogate pair "high+low"
        String s = RandomStringUtils.random(2, 0, set.length, false, false, set, rng);
        assertEquals(2, s.length());
        char high = s.charAt(0);
        char low  = s.charAt(1);
        assertTrue("First char should be high surrogate", high >= 0xD800 && high <= 0xDBFF);
        assertTrue("Second char should be low surrogate", low >= 0xDC00 && low <= 0xDFFF);
        // Verify that the pair forms a valid Unicode code point
        int codePoint = Character.toCodePoint(high, low);
        assertTrue("Generated code point should be within surrogate range",
                   codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }

    @Test
    public void testRandom_lowSurrogatePrecededByHigh() {
        // Force selection of a low surrogate (0xDC00) first
        char[] set = {(char) 0xDC00, 'B'};
        // FixedRandom returns index 0 (low surrogate), then 0 again for high
        FixedRandom rng = new FixedRandom(0, 0);
        // request length 2, we expect a proper surrogate pair "high+low"
        String s = RandomStringUtils.random(2, 0, set.length, false, false, set, rng);
        assertEquals(2, s.length());
        char first = s.charAt(0);
        char second = s.charAt(1);
        // According to the implementation, low surrogate triggers insertion of a high surrogate before it
        assertTrue("First char should be high surrogate", first >= 0xD800 && first <= 0xDBFF);
        assertTrue("Second char should be low surrogate", second >= 0xDC00 && second <= 0xDFFF);
        int cp = Character.toCodePoint(first, second);
        assertTrue(cp >= 0x10000 && cp <= 0x10FFFF);
    }

    @Test
    public void testRandom_privateHighSurrogateIsSkipped() {
        // Private high surrogate range: 0xDB80‑0xDBFF
        char[] set = {(char) 0xDB80, 'C'};
        // FixedRandom always picks index 0 (the private high surrogate)
        FixedRandom rng = new FixedRandom(0);
        // Request length 5 – the private surrogate should be skipped, so we will end up with only 'C's
        String s = RandomStringUtils.random(5, 0, set.length, false, false, set, rng);
        assertEquals(5, s.length());
        for (char c : s.toCharArray()) {
            assertEquals('C', c);
        }
    }

    /** -----------------------------------------------------------------------
     * 10. public static String random(int count, String chars)
     * -------------------------------------------------------------------- */
    @Test
    public void testRandom_stringSource_nullUsesAllChars() {
        // null chars should behave like random(count)
        String s = RandomStringUtils.random(7, (String) null);
        assertEquals(7, s.length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandom_stringSource_emptyThrows() {
        RandomStringUtils.random(5, "");
    }

    /** -----------------------------------------------------------------------
     * 11. public static String random(int count, char... chars)
     * -------------------------------------------------------------------- */
    @Test
    public void testRandom_charArraySource_nullUsesAllChars() {
        String s = RandomStringUtils.random(4, (char[]) null);
        assertEquals(4, s.length());
    }

    @Test
    public void testRandom_charArraySource_nonNull() {
        char[] source = {'x', 'y', 'z'};
        String s = RandomStringUtils.random(9, source);
        assertEquals(9, s.length());
        for (char c : s.toCharArray()) {
            assertTrue(c == 'x' || c == 'y' || c == 'z');
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandom_charArraySource_negativeCountThrows() {
        RandomStringUtils.random(-3, new char[]{'a', 'b'});
    }

    /** -----------------------------------------------------------------------
     * Additional sanity checks for the public constructor (even though it does nothing)
     * -------------------------------------------------------------------- */
    @Test
    public void testConstructorIsPublic() {
        RandomStringUtils utils = new RandomStringUtils();
        assertNotNull(utils);
    }
}