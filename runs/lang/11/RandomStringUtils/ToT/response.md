###Test START##
```java
/*****************************************************************************************
 * JUnit‑4 test suite for {@link org.apache.commons.lang3.RandomStringUtils}.
 *
 * The tests cover:
 *   • Normal usage of every public overload.
 *   • Edge cases (zero length, surrogate handling, charset limits).
 *   • Error scenarios (negative length, empty source arrays/strings).
 *
 * The suite purposefully does **not** rely on the exact random output (the class uses a
 * static Random instance).  Instead it validates contracts such as length, character
 * class constraints and surrogate pairing.
 *
 * Author:  Test‑generation assistant
 * Version: 1.0
 *****************************************************************************************/

package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

/**
 * Comprehensive tests for {@link RandomStringUtils}.
 */
public class RandomStringUtilsTest {

    /* --------------------------------------------------------------------- */
    /* Helper utilities                                                    */
    /* --------------------------------------------------------------------- */

    /** Checks that every character of {@code s} is a letter. */
    private static void assertAllLetters(String s) {
        for (char c : s.toCharArray()) {
            assertTrue("Expected a letter but got: " + Integer.toHexString(c),
                    Character.isLetter(c));
        }
    }

    /** Checks that every character of {@code s} is a digit. */
    private static void assertAllDigits(String s) {
        for (char c : s.toCharArray()) {
            assertTrue("Expected a digit but got: " + Integer.toHexString(c),
                    Character.isDigit(c));
        }
    }

    /** Checks that every character of {@code s} is either a letter or a digit. */
    private static void assertAllAlphaNumeric(String s) {
        for (char c : s.toCharArray()) {
            assertTrue("Expected a letter or digit but got: " + Integer.toHexString(c),
                    Character.isLetterOrDigit(c));
        }
    }

    /** Checks that every character of {@code s} is within the ASCII printable range. */
    private static void assertAllAsciiPrintable(String s) {
        for (char c : s.toCharArray()) {
            assertTrue("Expected ASCII printable (32‑126) but got: " + (int) c,
                    c >= 32 && c <= 126);
        }
    }

    /** Checks that surrogate pairs are well‑formed (no isolated high/low surrogate). */
    private static void assertWellFormedSurrogates(String s) {
        char[] data = s.toCharArray();
        for (int i = 0; i < data.length; i++) {
            char ch = data[i];
            if (Character.isHighSurrogate(ch)) {
                assertTrue("High surrogate at end of string without low surrogate",
                        i + 1 < data.length);
                assertTrue("High surrogate not followed by low surrogate",
                        Character.isLowSurrogate(data[i + 1]));
                i++; // skip low surrogate
            } else {
                assertFalse("Isolated low surrogate found", Character.isLowSurrogate(ch));
            }
        }
    }

    /* --------------------------------------------------------------------- */
    /* Tests for the simple overloads                                        */
    /* --------------------------------------------------------------------- */

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
        String s = RandomStringUtils.randomAscii(1000);
        assertEquals(1000, s.length());
        assertAllAsciiPrintable(s);
    }

    @Test
    public void testRandomAlphabeticOnlyLetters() {
        String s = RandomStringUtils.randomAlphabetic(500);
        assertEquals(500, s.length());
        assertAllLetters(s);
    }

    @Test
    public void testRandomAlphanumericLettersOrDigits() {
        String s = RandomStringUtils.randomAlphanumeric(400);
        assertEquals(400, s.length());
        assertAllAlphaNumeric(s);
    }

    @Test
    public void testRandomNumericOnlyDigits() {
        String s = RandomStringUtils.randomNumeric(250);
        assertEquals(250, s.length());
        assertAllDigits(s);
    }

    /* --------------------------------------------------------------------- */
    /* Tests for the generic overloads                                        */
    /* --------------------------------------------------------------------- */

    @Test
    public void testRandomWithLettersAndNumbersBothFalse() {
        // start/end = 0 triggers default range (0 .. Integer.MAX_VALUE)
        // The result can be any char; we only verify length.
        String s = RandomStringUtils.random(30, false, false);
        assertEquals(30, s.length());
    }

    @Test
    public void testRandomWithCustomStartEnd() {
        // Choose a narrow range: characters 'A' to 'D' (65‑68)
        int start = 'A';
        int end = 'E'; // exclusive
        String s = RandomStringUtils.random(50, start, end, false, false);
        assertEquals(50, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Char out of expected range", c >= start && c < end);
        }
    }

    @Test
    public void testRandomWithCharArraySource() {
        char[] source = {'x', 'y', 'z'};
        String s = RandomStringUtils.random(20, source);
        assertEquals(20, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Unexpected char from source array", c == 'x' || c == 'y' || c == 'z');
        }
    }

    @Test
    public void testRandomWithStringSource() {
        String source = "ABC123";
        String s = RandomStringUtils.random(15, source);
        assertEquals(15, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Unexpected char from source string", source.indexOf(c) >= 0);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomCharArrayEmpty() {
        RandomStringUtils.random(5, new char[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomStringEmpty() {
        RandomStringUtils.random(5, "");
    }

    /* --------------------------------------------------------------------- */
    /* Tests for surrogate handling                                          */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSurrogatePairsAreWellFormed() {
        // Generate a relatively long string to increase chance of surrogate selection
        String s = RandomStringUtils.random(500, true, true);
        assertEquals(500, s.length());
        assertWellFormedSurrogates(s);
    }

    @Test
    public void testSurrogatePairDoesNotBreakLength() {
        // The method always returns a string whose length equals the requested count,
        // even when surrogate pairs are inserted (they count as 2 characters).
        for (int i = 1; i <= 20; i++) {
            String s = RandomStringUtils.random(i, true, true);
            assertEquals(i, s.length());
        }
    }

    /* --------------------------------------------------------------------- */
    /* Tests for overloads that accept explicit Random (indirectly)           */
    /* --------------------------------------------------------------------- */

    @Test
    public void testDeterministicRandomViaCustomOverload() {
        // Use the overload that accepts a Random instance to obtain a deterministic output
        Random seeded = new Random(12345L);
        String s1 = RandomStringUtils.random(10, 0, 0, false, false, null, seeded);
        Random seededAgain = new Random(12345L);
        String s2 = RandomStringUtils.random(10, 0, 0, false, false, null, seededAgain);
        assertEquals("Deterministic runs with same seed must produce identical strings", s1, s2);
        assertEquals(10, s1.length());
    }

    /* --------------------------------------------------------------------- */
    /* Tests for the overload that mixes all parameters                       */
    /* --------------------------------------------------------------------- */

    @Test
    public void testRandomFullSignatureLettersOnly() {
        String s = RandomStringUtils.random(30, 65, 122, true, false);
        assertEquals(30, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Expected a letter in the range 65‑122", Character.isLetter(c));
        }
    }

    @Test
    public void testRandomFullSignatureNumbersOnly() {
        String s = RandomStringUtils.random(30, 48, 58, false, true);
        assertEquals(30, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Expected a digit in the range 48‑57", Character.isDigit(c));
        }
    }

    @Test
    public void testRandomFullSignatureLettersAndNumbers() {
        String s = RandomStringUtils.random(40, 48, 123, true, true);
        assertEquals(40, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Expected letter or digit", Character.isLetterOrDigit(c));
        }
    }

    @Test
    public void testRandomFullSignatureWithCustomCharArray() {
        char[] pool = {'α', 'β', 'γ', '1', '2', '3'};
        String s = RandomStringUtils.random(25, 0, pool.length, false, false, pool, new Random());
        assertEquals(25, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Character not from custom pool", new String(pool).indexOf(c) >= 0);
        }
    }

    /* --------------------------------------------------------------------- */
    /* Additional defensive tests                                            */
    /* --------------------------------------------------------------------- */

    @Test
    public void testRandomWithBothLettersAndNumbersFalseUsesAllChars() {
        // When both flags are false and start/end are default (0,0), the method uses
        // the full Unicode range.  We simply confirm that the returned string
        // contains at least one character outside the ASCII printable range.
        String s = RandomStringUtils.random(100, false, false);
        assertEquals(100, s.length());

        boolean hasNonAscii = false;
        for (char c : s.toCharArray()) {
            if (c < 32 || c > 126) {
                hasNonAscii = true;
                break;
            }
        }
        assertTrue("Expected at least one non‑ASCII printable character", hasNonAscii);
    }

    @Test
    public void testRandomWithStartEqualsEndThrowsArrayIndexOutOfBounds() {
        // When start == end, gap == 0, which leads to division by zero in nextInt().
        // The Javadoc says an ArrayIndexOutOfBoundsException is thrown if the
        // computed range does not contain any characters.
        try {
            RandomStringUtils.random(5, 10, 10, false, false);
            fail("Expected ArrayIndexOutOfBoundsException because start == end");
        } catch (ArrayIndexOutOfBoundsException e) {
            // expected
        }
    }
}
```
###Test END##