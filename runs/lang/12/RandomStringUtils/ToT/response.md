###Test START##
```java
/*
 * Comprehensive JUnit‑4 test suite for org.apache.commons.lang3.RandomStringUtils
 *
 * The test suite is written as a collaborative effort of three imagined experts.
 * Each expert contributed one test method per public API method, covering:
 *   • Typical usage (correct length, character class constraints)
 *   • Edge cases (zero length, single‑character strings, surrogate handling)
 *   • Error scenarios (negative length, empty char sets, empty strings)
 *
 * The final file merges all contributions into a single, self‑contained test class.
 */
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.util.Random;
import java.util.Set;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;

/**
 * Test class for {@link RandomStringUtils}.
 *
 * @author  Expert‑1
 * @author  Expert‑2
 * @author  Expert‑3
 */
public class RandomStringUtilsTest {

    /** A deterministic Random instance used for tests that need reproducibility. */
    private Random deterministicRandom;

    @Before
    public void setUp() {
        // Fixed seed guarantees the same sequence on every run.
        deterministicRandom = new Random(0xCAFEBABE);
    }

    /*======================================================================
     * Helper utilities used by several tests
     *====================================================================*/

    /** Returns true if the supplied character is a private‑high surrogate (U+DB80..U+DBFF). */
    private boolean isPrivateHighSurrogate(char ch) {
        return ch >= '\uDB80' && ch <= '\uDBFF';
    }

    /** Returns true if the supplied character is a low surrogate (U+DC00..U+DFFF). */
    private boolean isLowSurrogate(char ch) {
        return ch >= '\uDC00' && ch <= '\uDFFF';
    }

    /** Returns true if the supplied character is a high surrogate (U+D800..U+DB7F). */
    private boolean isHighSurrogate(char ch) {
        return ch >= '\uD800' && ch <= '\uDB7F';
    }

    /** Checks that every character in the string satisfies the supplied predicate. */
    private void assertAllMatch(String s, java.util.function.Predicate<Character> predicate) {
        for (char c : s.toCharArray()) {
            assertTrue("Character '" + c + "' does not satisfy predicate.", predicate.test(c));
        }
    }

    /*======================================================================
     * 1. random(int)
     *====================================================================*/

    @Test
    public void testRandom_LengthAndFullRange() {
        // Typical case – length 10, full Unicode range.
        String result = RandomStringUtils.random(10);
        assertNotNull(result);
        assertEquals(10, result.length());

        // Verify that the result contains a mix of character types (very loose check).
        // Since the method can return any char, we only assert that it does not throw.
        // The presence of surrogate pairs is also acceptable.
    }

    @Test
    public void testRandom_ZeroLength() {
        assertEquals("", RandomStringUtils.random(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandom_NegativeLength() {
        RandomStringUtils.random(-5);
    }

    /*======================================================================
     * 2. randomAscii(int)
     *====================================================================*/

    @Test
    public void testRandomAscii_ValidRange() {
        int len = 15;
        String ascii = RandomStringUtils.randomAscii(len);
        assertEquals(len, ascii.length());
        // ASCII printable characters are 0x20 (space) .. 0x7E ('~')
        assertAllMatch(ascii, c -> c >= 32 && c <= 126);
    }

    @Test
    public void testRandomAscii_ZeroLength() {
        assertEquals("", RandomStringUtils.randomAscii(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomAscii_NegativeLength() {
        RandomStringUtils.randomAscii(-1);
    }

    /*======================================================================
     * 3. randomAlphabetic(int)
     *====================================================================*/

    @Test
    public void testRandomAlphabetic_OnlyLetters() {
        String s = RandomStringUtils.randomAlphabetic(20);
        assertEquals(20, s.length());
        assertAllMatch(s, Character::isLetter);
    }

    @Test
    public void testRandomAlphabetic_SurrogatePairsAreHandled() {
        // Use a deterministic random to increase chance of hitting surrogate logic.
        String s = RandomStringUtils.random(100, 0, 0, true, false, null, deterministicRandom);
        // Ensure the string length is as requested.
        assertEquals(100, s.length());

        // No private high surrogates should appear.
        for (char c : s.toCharArray()) {
            assertFalse("Private high surrogate found", isPrivateHighSurrogate(c));
        }

        // Verify that any low surrogate is preceded by a matching high surrogate.
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (isLowSurrogate(chars[i])) {
                assertTrue(i > 0);
                assertTrue(isHighSurrogate(chars[i - 1]));
            }
            if (isHighSurrogate(chars[i])) {
                assertTrue(i < chars.length - 1);
                assertTrue(isLowSurrogate(chars[i + 1]));
            }
        }
    }

    /*======================================================================
     * 4. randomAlphanumeric(int)
     *====================================================================*/

    @Test
    public void testRandomAlphanumeric_LettersOrDigits() {
        String s = RandomStringUtils.randomAlphanumeric(30);
        assertEquals(30, s.length());
        assertAllMatch(s, c -> Character.isLetterOrDigit(c));
    }

    @Test
    public void testRandomAlphanumeric_ZeroLength() {
        assertEquals("", RandomStringUtils.randomAlphanumeric(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomAlphanumeric_NegativeLength() {
        RandomStringUtils.randomAlphanumeric(-2);
    }

    /*======================================================================
     * 5. randomNumeric(int)
     *====================================================================*/

    @Test
    public void testRandomNumeric_OnlyDigits() {
        String s = RandomStringUtils.randomNumeric(12);
        assertEquals(12, s.length());
        assertAllMatch(s, Character::isDigit);
    }

    @Test
    public void testRandomNumeric_ZeroLength() {
        assertEquals("", RandomStringUtils.randomNumeric(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomNumeric_NegativeLength() {
        RandomStringUtils.randomNumeric(-3);
    }

    /*======================================================================
     * 6. random(int, boolean, boolean)
     *====================================================================*/

    @Test
    public void testRandom_LettersAndNumbers_TrueTrue() {
        String s = RandomStringUtils.random(25, true, true);
        assertEquals(25, s.length());
        assertAllMatch(s, c -> Character.isLetterOrDigit(c));
    }

    @Test
    public void testRandom_LettersOnly_TrueFalse() {
        String s = RandomStringUtils.random(18, true, false);
        assertEquals(18, s.length());
        assertAllMatch(s, Character::isLetter);
    }

    @Test
    public void testRandom_NumbersOnly_FalseTrue() {
        String s = RandomStringUtils.random(22, false, true);
        assertEquals(22, s.length());
        assertAllMatch(s, Character::isDigit);
    }

    @Test
    public void testRandom_NoRestriction_FalseFalse() {
        // Should accept any char from the default printable range.
        String s = RandomStringUtils.random(16, false, false);
        assertEquals(16, s.length());
        // No specific predicate – just ensure no exception and correct length.
    }

    /*======================================================================
     * 7. random(int, int, int, boolean, boolean)
     *====================================================================*/

    @Test
    public void testRandom_CustomRange_LettersOnly() {
        // Use ASCII range for uppercase letters only.
        int start = 'A';
        int end   = 'Z' + 1; // exclusive upper bound
        String s = RandomStringUtils.random(10, start, end, true, false);
        assertEquals(10, s.length());
        assertAllMatch(s, c -> c >= 'A' && c <= 'Z');
    }

    @Test
    public void testRandom_CustomRange_NumbersOnly() {
        int start = '0';
        int end   = '9' + 1;
        String s = RandomStringUtils.random(7, start, end, false, true);
        assertEquals(7, s.length());
        assertAllMatch(s, c -> c >= '0' && c <= '9');
    }

    @Test
    public void testRandom_CustomRange_InvalidBounds() {
        // When start == end the gap is zero – the method would try nextInt(0) and throw.
        // Expect an IllegalArgumentException from the underlying Random.nextInt.
        try {
            RandomStringUtils.random(5, 10, 10, true, true);
            fail("Expected IllegalArgumentException due to zero range.");
        } catch (IllegalArgumentException e) {
            // JDK Random throws IllegalArgumentException for nextInt(0)
            // The wrapper does not catch it, so it propagates.
        }
    }

    /*======================================================================
     * 8. random(int, int, int, boolean, boolean, char[])
     *====================================================================*/

    @Test
    public void testRandom_WithCharArray_AllCharsAllowed() {
        char[] pool = {'a', 'b', 'c', '1', '2', '3'};
        String s = RandomStringUtils.random(12, 0, pool.length, false, false, pool);
        assertEquals(12, s.length());
        // Every character must belong to the pool.
        for (char c : s.toCharArray()) {
            boolean found = false;
            for (char p : pool) {
                if (c == p) {
                    found = true;
                    break;
                }
            }
            assertTrue("Unexpected character '" + c + "'", found);
        }
    }

    @Test
    public void testRandom_WithCharArray_EmptyArray() {
        char[] empty = new char[0];
        try {
            RandomStringUtils.random(5, empty);
            fail("Expected IllegalArgumentException for empty char array.");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testRandom_WithCharArray_NullArray() {
        // Null array should fallback to the default character set.
        String s = RandomStringUtils.random(8, (char[]) null);
        assertEquals(8, s.length());
    }

    /*======================================================================
     * 9. random(int, String)
     *====================================================================*/

    @Test
    public void testRandom_WithString_Valid() {
        String source = "xyzXYZ123";
        String s = RandomStringUtils.random(9, source);
        assertEquals(9, s.length());
        // Every character must be from source.
        for (char c : s.toCharArray()) {
            assertTrue("Character not in source set", source.indexOf(c) >= 0);
        }
    }

    @Test
    public void testRandom_WithString_Null() {
        // Null string should behave like random(count) with full char set.
        String s = RandomStringUtils.random(5, (String) null);
        assertEquals(5, s.length());
    }

    @Test
    public void testRandom_WithString_EmptyString() {
        try {
            RandomStringUtils.random(4, "");
            fail("Expected IllegalArgumentException for empty source string.");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    /*======================================================================
     * 10. random(int, char...)
     *====================================================================*/

    @Test
    public void testRandom_WithCharVarargs_Valid() {
        char[] charset = {'x', 'y', 'z'};
        String s = RandomStringUtils.random(6, charset);
        assertEquals(6, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Unexpected char", c == 'x' || c == 'y' || c == 'z');
        }
    }

    @Test
    public void testRandom_WithCharVarargs_Null() {
        // Should delegate to the default full‑range random.
        String s = RandomStringUtils.random(4, (char[]) null);
        assertEquals(4, s.length());
    }

    /*======================================================================
     * 11. Surrogate handling – specific regression tests
     *====================================================================*/

    @Test
    public void testSurrogateHighLowPairsNeverSplit() {
        // Force the generator to pick surrogate code points by providing a custom pool.
        char[] pool = new char[4];
        // Private high surrogate (should be skipped)
        pool[0] = '\uDB80';
        // Valid high surrogate
        pool[1] = '\uD800';
        // Valid low surrogate
        pool[2] = '\uDC00';
        // A normal letter for filler
        pool[3] = 'A';

        // Use a deterministic Random that will iterate over the pool.
        Random r = new Random(1L);
        // Request a length that would require the generator to handle surrogate logic.
        String s = RandomStringUtils.random(5, 0, pool.length, false, false, pool, r);

        // Verify no private high surrogate appears.
        for (char c : s.toCharArray()) {
            assertFalse(isPrivateHighSurrogate(c));
        }

        // Verify every high surrogate is immediately followed by a low surrogate,
        // and every low surrogate is preceded by a high surrogate.
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (isHighSurrogate(chars[i])) {
                assertTrue("High surrogate at end of string", i + 1 < chars.length);
                assertTrue("High surrogate not followed by low surrogate", isLowSurrogate(chars[i + 1]));
                i++; // skip the low surrogate we just checked
            } else if (isLowSurrogate(chars[i])) {
                assertTrue("Low surrogate at start of string", i - 1 >= 0);
                assertTrue("Low surrogate not preceded by high surrogate", isHighSurrogate(chars[i - 1]));
            }
        }
    }

    @Test
    public void testRandom_CountZeroWithSurrogatePool() {
        // Even if the supplied pool contains surrogate characters, count 0 must return empty string.
        char[] pool = {'\uD800', '\uDC00', 'a'};
        String s = RandomStringUtils.random(0, 0, pool.length, false, false, pool, deterministicRandom);
        assertEquals("", s);
    }

    /*======================================================================
     * 12. Miscellaneous sanity checks
     *====================================================================*/

    @Test
    public void testRandom_StringLengthNeverExceedsRequested() {
        for (int len = 0; len <= 20; len++) {
            String s = RandomStringUtils.random(len);
            assertEquals(len, s.length());
        }
    }

    @Test
    public void testRandom_MultipleCallsProduceDifferentValues() {
        // Using the global RANDOM we cannot guarantee different strings,
        // but the probability of two consecutive 10‑char strings being equal is astronomically low.
        String s1 = RandomStringUtils.random(10);
        String s2 = RandomStringUtils.random(10);
        assertNotEquals("Two consecutive random strings happened to be equal – unlikely but possible.", s1, s2);
    }
}
```
###Test END##