package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

/**
 * Comprehensive JUnit‑4 test suite for {@link RandomStringUtils}.
 *
 * The tests cover:
 * <ul>
 *   <li>All public overloads</li>
 *   <li>Boundary conditions (zero / negative length, empty arrays)</li>
 *   <li>Correct handling of the {@code letters} and {@code numbers} flags</li>
 *   <li>Custom start / end ranges</li>
 *   <li>Custom character sets (including surrogate handling)</li>
 *   <li>Deterministic output when a seeded {@link Random} instance is supplied</li>
 * </ul>
 */
public class RandomStringUtilsTest {

    // -----------------------------------------------------------------------
    // Simple length / null handling tests
    // -----------------------------------------------------------------------

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
        RandomStringUtils.random(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomAlphabeticNegativeLength() {
        RandomStringUtils.randomAlphabetic(-5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomWithEmptyCharArray() {
        RandomStringUtils.random(5, new char[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomWithEmptyCharString() {
        RandomStringUtils.random(5, "");
    }

    // -----------------------------------------------------------------------
    // Basic overloads – length and character class validation
    // -----------------------------------------------------------------------

    @Test
    public void testRandomLength() {
        int len = 13;
        String s = RandomStringUtils.random(len);
        assertNotNull(s);
        assertEquals(len, s.length());
    }

    @Test
    public void testRandomAsciiCharacters() {
        int len = 50;
        String s = RandomStringUtils.randomAscii(len);
        assertEquals(len, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("ASCII char out of range: " + (int) c,
                    c >= 32 && c <= 126);
        }
    }

    @Test
    public void testRandomAlphabeticCharacters() {
        int len = 40;
        String s = RandomStringUtils.randomAlphabetic(len);
        assertEquals(len, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Non‑letter found: " + c, Character.isLetter(c));
        }
    }

    @Test
    public void testRandomAlphanumericCharacters() {
        int len = 30;
        String s = RandomStringUtils.randomAlphanumeric(len);
        assertEquals(len, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Non‑alphanumeric found: " + c,
                    Character.isLetter(c) || Character.isDigit(c));
        }
    }

    @Test
    public void testRandomNumericCharacters() {
        int len = 25;
        String s = RandomStringUtils.randomNumeric(len);
        assertEquals(len, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Non‑digit found: " + c, Character.isDigit(c));
        }
    }

    // -----------------------------------------------------------------------
    // Tests for the (count, letters, numbers) overload
    // -----------------------------------------------------------------------

    @Test
    public void testRandomLettersOnly() {
        String s = RandomStringUtils.random(20, true, false);
        assertEquals(20, s.length());
        for (char c : s.toCharArray()) {
            assertTrue(Character.isLetter(c));
        }
    }

    @Test
    public void testRandomNumbersOnly() {
        String s = RandomStringUtils.random(20, false, true);
        assertEquals(20, s.length());
        for (char c : s.toCharArray()) {
            assertTrue(Character.isDigit(c));
        }
    }

    @Test
    public void testRandomLettersAndNumbers() {
        String s = RandomStringUtils.random(20, true, true);
        assertEquals(20, s.length());
        for (char c : s.toCharArray()) {
            assertTrue(Character.isLetter(c) || Character.isDigit(c));
        }
    }

    @Test
    public void testRandomNeitherLettersNorNumbers() {
        // Any Unicode character (except the filtered private high surrogates) may appear.
        String s = RandomStringUtils.random(30, false, false);
        assertEquals(30, s.length());
        // Verify that no private high surrogate (0xDB80‑0xDBFF) is present.
        for (char c : s.toCharArray()) {
            assertFalse("Private high surrogate should be skipped",
                    c >= 0xDB80 && c <= 0xDBFF);
        }
    }

    // -----------------------------------------------------------------------
    // Tests for start / end range handling
    // -----------------------------------------------------------------------

    @Test
    public void testRandomWithCustomRangeLettersOnly() {
        // Range 'a' (97) to 'z'+1 (123)
        String s = RandomStringUtils.random(15, 'a', 'z' + 1, true, false);
        assertEquals(15, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Character out of expected range or not a letter: " + c,
                    c >= 'a' && c <= 'z' && Character.isLetter(c));
        }
    }

    @Test
    public void testRandomWithCustomRangeNumbersOnly() {
        // Range '0' (48) to '9'+1 (58)
        String s = RandomStringUtils.random(12, '0', '9' + 1, false, true);
        assertEquals(12, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Not a digit: " + c, Character.isDigit(c));
        }
    }

    @Test
    public void testRandomWithCustomRangeLettersAndNumbers() {
        // Range '0' to 'z'+1, but we restrict to letters or numbers.
        String s = RandomStringUtils.random(20, '0', 'z' + 1, true, true);
        assertEquals(20, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Unexpected character: " + c,
                    Character.isLetter(c) || Character.isDigit(c));
        }
    }

    // -----------------------------------------------------------------------
    // Tests for Char array / String overloads
    // -----------------------------------------------------------------------

    @Test
    public void testRandomFromCharArray() {
        char[] pool = {'a', 'b', '1', '2', '@', '#'};
        String s = RandomStringUtils.random(10, pool);
        assertEquals(10, s.length());
        for (char c : s.toCharArray()) {
            boolean found = false;
            for (char p : pool) {
                if (c == p) {
                    found = true;
                    break;
                }
            }
            assertTrue("Character not from supplied pool: " + c, found);
        }
    }

    @Test
    public void testRandomFromString() {
        String pool = "XYZxyz123";
        String s = RandomStringUtils.random(15, pool);
        assertEquals(15, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Character not from supplied pool: " + c,
                    pool.indexOf(c) >= 0);
        }
    }

    @Test
    public void testRandomFromStringNullUsesAllChars() {
        // Null string should behave like random(count) – we test length only.
        String s = RandomStringUtils.random(7, (String) null);
        assertEquals(7, s.length());
    }

    // -----------------------------------------------------------------------
    // Surrogate handling tests
    // -----------------------------------------------------------------------

    @Test
    public void testSurrogatePairGeneration() {
        // Pool contains a low surrogate (0xDC00) and a high surrogate (0xD800).
        char[] pool = {(char) 0xD800, (char) 0xDC00, 'A', '0'};
        // Use flags false/false so the algorithm will not filter them out.
        String s = RandomStringUtils.random(6, 0, pool.length, false, false, pool, new Random(12345L));
        assertEquals(6, s.length());

        // Verify that the string does not contain any unpaired surrogate.
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isLowSurrogate(ch)) {
                assertTrue("Low surrogate without preceding high surrogate at index " + i,
                        i > 0 && Character.isHighSurrogate(s.charAt(i - 1)));
            }
            if (Character.isHighSurrogate(ch)) {
                assertTrue("High surrogate without following low surrogate at index " + i,
                        i < s.length() - 1 && Character.isLowSurrogate(s.charAt(i + 1)));
            }
        }
    }

    @Test
    public void testPrivateHighSurrogateIsSkipped() {
        // Private high surrogate range: 0xDB80‑0xDBFF
        char privateHigh = (char) 0xDB80;
        char[] pool = {privateHigh, 'X', '9'};
        // Request many characters to increase the chance of selection.
        String s = RandomStringUtils.random(20, 0, pool.length, false, false, pool, new Random(9876L));
        assertEquals(20, s.length());

        // Ensure the private high surrogate never appears.
        for (char c : s.toCharArray()) {
            assertFalse("Private high surrogate should have been skipped", c >= 0xDB80 && c <= 0xDBFF);
        }
    }

    // -----------------------------------------------------------------------
    // Deterministic output with a seeded Random instance
    // -----------------------------------------------------------------------

    @Test
    public void testDeterministicWithSameSeed() {
        Random seeded = new Random(42L);
        String first = RandomStringUtils.random(12, 0, 0, false, false, null, seeded);
        // Reset seed to the same value to get the same sequence.
        seeded = new Random(42L);
        String second = RandomStringUtils.random(12, 0, 0, false, false, null, seeded);
        assertEquals("Two calls with the same seed must produce identical strings", first, second);
    }

    @Test
    public void testDifferentSeedsProduceDifferentStrings() {
        Random r1 = new Random(1L);
        Random r2 = new Random(2L);
        String s1 = RandomStringUtils.random(15, 0, 0, false, false, null, r1);
        String s2 = RandomStringUtils.random(15, 0, 0, false, false, null, r2);
        // Very unlikely to be equal; if they are, the test is inconclusive.
        assertFalse("Different seeds should normally produce different strings", s1.equals(s2));
    }

    // -----------------------------------------------------------------------
    // Additional edge cases
    // -----------------------------------------------------------------------

    @Test
    public void testRandomWithStartEqualsEndFallsBackToDefaults() {
        // start and end both zero – algorithm should use printable ASCII range.
        String s = RandomStringUtils.random(10, 0, 0, true, false);
        assertEquals(10, s.length());
        for (char c : s.toCharArray()) {
            assertTrue(Character.isLetter(c));
        }
    }

    @Test
    public void testRandomWhenBothLettersAndNumbersFalseAndCustomRange() {
        // With letters==false && numbers==false the method should allow any char in the range.
        String s = RandomStringUtils.random(8, 0, 128, false, false);
        assertEquals(8, s.length());
        // No further restriction – just ensure length matches.
    }
}
