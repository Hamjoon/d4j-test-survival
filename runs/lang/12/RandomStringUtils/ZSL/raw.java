###Test START##
package org.apache.commons.lang3;

import org.junit.Test;
import org.junit.Assert;

import java.util.Random;

/**
 * JUnit‑4 test suite for {@link RandomStringUtils}.
 *
 * The tests cover:
 * <ul>
 *   <li>basic contract (length, empty string, negative count)</li>
 *   <li>character‑set specific factories (ASCII, alphabetic, alphanumeric, numeric)</li>
 *   <li>generic {@code random(...)} overloads with boolean flags</li>
 *   <li>custom character sources (char[] and String)</li>
 *   <li>exceptional situations (null / empty arrays)</li>
 *   <li>surrogate‑pair handling using a deterministic {@link Random}</li>
 * </ul>
 */
public class RandomStringUtilsTest {

    /** Helper {@link Random} that returns a predefined sequence of ints. */
    private static class FixedRandom extends Random {
        private final int[] values;
        private int index = 0;

        FixedRandom(int... values) {
            this.values = values.clone();
        }

        @Override
        public int nextInt(int bound) {
            // Return the next predefined value modulo the bound (to stay inside the range)
            int v = values[index % values.length];
            index++;
            return Math.abs(v) % bound;
        }
    }

    // -------------------------------------------------------------------------
    // Basic length / argument validation
    // -------------------------------------------------------------------------

    @Test
    public void testRandomZeroLength() {
        Assert.assertEquals("", RandomStringUtils.random(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomNegativeLength() {
        RandomStringUtils.random(-5);
    }

    // -------------------------------------------------------------------------
    // ASCII, alphabetic, alphanumeric and numeric factories
    // -------------------------------------------------------------------------

    @Test
    public void testRandomAsciiRange() {
        String s = RandomStringUtils.randomAscii(1000);
        Assert.assertEquals(1000, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("ASCII char out of range: " + (int) c, c >= 32 && c <= 126);
        }
    }

    @Test
    public void testRandomAlphabeticOnlyLetters() {
        String s = RandomStringUtils.randomAlphabetic(500);
        Assert.assertEquals(500, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Non‑letter found: " + c, Character.isLetter(c));
        }
    }

    @Test
    public void testRandomAlphanumericContainsLettersOrDigits() {
        String s = RandomStringUtils.randomAlphanumeric(800);
        Assert.assertEquals(800, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Character is neither letter nor digit: " + c,
                    Character.isLetterOrDigit(c));
        }
    }

    @Test
    public void testRandomNumericOnlyDigits() {
        String s = RandomStringUtils.randomNumeric(400);
        Assert.assertEquals(400, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Non‑digit found: " + c, Character.isDigit(c));
        }
    }

    // -------------------------------------------------------------------------
    // Generic random(count, letters, numbers) overloads
    // -------------------------------------------------------------------------

    @Test
    public void testRandomLettersOnly() {
        String s = RandomStringUtils.random(200, true, false);
        Assert.assertEquals(200, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Expected only letters, got: " + c, Character.isLetter(c));
        }
    }

    @Test
    public void testRandomNumbersOnly() {
        String s = RandomStringUtils.random(200, false, true);
        Assert.assertEquals(200, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Expected only digits, got: " + c, Character.isDigit(c));
        }
    }

    @Test
    public void testRandomAnyCharacters() {
        // letters == false && numbers == false  => any printable character set is used
        String s = RandomStringUtils.random(150, false, false);
        Assert.assertEquals(150, s.length());
        // We cannot assert a concrete range, but we can be sure the string is not empty
        Assert.assertFalse("String should not be empty", s.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Custom character source (char[] and String)
    // -------------------------------------------------------------------------

    @Test
    public void testRandomWithCharArray() {
        char[] source = new char[] { 'x', 'y', 'z' };
        String s = RandomStringUtils.random(50, source);
        Assert.assertEquals(50, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Unexpected character: " + c, c == 'x' || c == 'y' || c == 'z');
        }
    }

    @Test
    public void testRandomWithStringSource() {
        String source = "ABC123";
        String s = RandomStringUtils.random(30, source);
        Assert.assertEquals(30, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Unexpected character: " + c, source.indexOf(c) >= 0);
        }
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testRandomWithEmptyCharArrayThrows() {
        RandomStringUtils.random(10, new char[0]);
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testRandomWithEmptyStringThrows() {
        RandomStringUtils.random(10, "");
    }

    // -------------------------------------------------------------------------
    // Surrogate‑pair handling – deterministic tests using FixedRandom
    // -------------------------------------------------------------------------

    @Test
    public void testLowSurrogateGetsPairedWithHighSurrogate() {
        // Force a low surrogate (range 0xDC00‑0xDFFF) → 56320‑57343
        int lowStart = 0xDC00;
        int lowEnd   = 0xDFFF + 1; // exclusive
        // FixedRandom returns 0 for every nextInt -> always selects the first char in the range
        FixedRandom rng = new FixedRandom(0, 0); // second 0 is for the high‑surrogate generation
        String result = RandomStringUtils.random(2, lowStart, lowEnd, false, false, null, rng);
        Assert.assertEquals(2, result.length());

        char first = result.charAt(0);
        char second = result.charAt(1);
        Assert.assertTrue("First char should be high surrogate", Character.isHighSurrogate(first));
        Assert.assertTrue("Second char should be low surrogate", Character.isLowSurrogate(second));
        // Verify that they form a valid surrogate pair
        int codePoint = Character.toCodePoint(first, second);
        Assert.assertTrue("Generated code point should be > 0xFFFF", codePoint > 0xFFFF);
    }

    @Test
    public void testHighSurrogateGetsPairedWithLowSurrogate() {
        // Force a high surrogate (range 0xD800‑0xDB7F) → 55296‑56191
        int highStart = 0xD800;
        int highEnd   = 0xDB7F + 1; // exclusive
        FixedRandom rng = new FixedRandom(0, 0); // first 0 → high surrogate, second 0 → low surrogate
        String result = RandomStringUtils.random(2, highStart, highEnd, false, false, null, rng);
        Assert.assertEquals(2, result.length());

        char first = result.charAt(0);
        char second = result.charAt(1);
        Assert.assertTrue("First char should be high surrogate", Character.isHighSurrogate(first));
        Assert.assertTrue("Second char should be low surrogate", Character.isLowSurrogate(second));
        int codePoint = Character.toCodePoint(first, second);
        Assert.assertTrue("Generated code point should be > 0xFFFF", codePoint > 0xFFFF);
    }

    // -------------------------------------------------------------------------
    // Miscellaneous sanity checks
    // -------------------------------------------------------------------------

    @Test
    public void testRandomWithNullCharArrayUsesDefaultSet() {
        String s = RandomStringUtils.random(20, (char[]) null);
        Assert.assertEquals(20, s.length());
        // The default printable range is 32‑'z' (122); ensure every char lies inside it
        for (char c : s.toCharArray()) {
            Assert.assertTrue("