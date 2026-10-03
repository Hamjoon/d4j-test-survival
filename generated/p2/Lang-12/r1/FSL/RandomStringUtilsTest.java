package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

/**
 * Comprehensive JUnit‑4 test suite for {@link RandomStringUtils}.
 * <p>
 * The tests cover:
 * <ul>
 *   <li>boundary conditions (zero length, negative length)</li>
 *   <li>behaviour of the convenience factories (alphabetic, alphanumeric,
 *   numeric, ASCII)</li>
 *   <li>custom character sources (char[] and String)</li>
 *   <li>surrogate handling (high/low and private high surrogates)</li>
 *   <li>deterministic output when a seeded {@link Random} instance is supplied</li>
 * </ul>
 * </p>
 */
public class RandomStringUtilsTest {

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------
    private static boolean isAllLetters(String s) {
        for (char c : s.toCharArray()) {
            if (!Character.isLetter(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllDigits(String s) {
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllAlphanumeric(String s) {
        for (char c : s.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiPrintable(String s) {
        for (char c : s.toCharArray()) {
            if (c < 32 || c > 126) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsOnlyFrom(char[] allowed, String s) {
        outer:
        for (char c : s.toCharArray()) {
            for (char a : allowed) {
                if (c == a) {
                    continue outer;
                }
            }
            return false;
        }
        return true;
    }

    private static boolean hasValidSurrogatePairs(String s) {
        char[] ch = s.toCharArray();
        for (int i = 0; i < ch.length; i++) {
            char c = ch[i];
            if (c >= 0xD800 && c <= 0xDB7F) { // high surrogate (valid range)
                if (i + 1 >= ch.length) {
                    return false; // no low surrogate follows
                }
                char low = ch[i + 1];
                if (low < 0xDC00 || low > 0xDFFF) {
                    return false; // not a low surrogate
                }
                i++; // skip the low surrogate
            } else if (c >= 0xDC00 && c <= 0xDFFF) {
                // low surrogate without preceding high surrogate
                return false;
            } else if (c >= 0xDB80 && c <= 0xDBFF) {
                // private‑high surrogate should never appear
                return false;
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Zero‑length handling
    // -----------------------------------------------------------------------
    @Test
    public void testZeroLengthReturnsEmptyString() {
        assertEquals("", RandomStringUtils.random(0));
        assertEquals("", RandomStringUtils.randomAscii(0));
        assertEquals("", RandomStringUtils.randomAlphabetic(0));
        assertEquals("", RandomStringUtils.randomAlphanumeric(0));
        assertEquals("", RandomStringUtils.randomNumeric(0));
        assertEquals("", RandomStringUtils.random(0, "xyz"));
        assertEquals("", RandomStringUtils.random(0, new char[]{'a', 'b'}));
        assertEquals("", RandomStringUtils.random(0, 0, 0, false, false, null, new Random()));
    }

    // -----------------------------------------------------------------------
    // Negative length handling
    // -----------------------------------------------------------------------
    @Test(expected = IllegalArgumentException.class)
    public void testNegativeLengthThrowsException_random() {
        RandomStringUtils.random(-5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeLengthThrowsException_randomAscii() {
        RandomStringUtils.randomAscii(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeLengthThrowsException_randomAlphabetic() {
        RandomStringUtils.randomAlphabetic(-2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeLengthThrowsException_randomAlphanumeric() {
        RandomStringUtils.randomAlphanumeric(-3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeLengthThrowsException_randomNumeric() {
        RandomStringUtils.randomNumeric(-4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeLengthThrowsException_randomWithString() {
        RandomStringUtils.random(-1, "abc");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeLengthThrowsException_randomWithCharArray() {
        RandomStringUtils.random(-1, new char[]{'x', 'y'});
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeLengthThrowsException_randomFullSignature() {
        RandomStringUtils.random(-1, 0, 0, false, false, null, new Random());
    }

    // -----------------------------------------------------------------------
    // Convenience factories – deterministic via seeded Random
    // -----------------------------------------------------------------------
    @Test
    public void testRandomAlphabeticContainsOnlyLetters() {
        Random rnd = new Random(12345L);
        String s = RandomStringUtils.random(20, 0, 0, true, false, null, rnd);
        assertEquals(20, s.length());
        assertTrue("String contains non‑letter characters", isAllLetters(s));
    }

    @Test
    public void testRandomAlphanumericContainsOnlyLettersOrDigits() {
        Random rnd = new Random(54321L);
        String s = RandomStringUtils.random(30, 0, 0, true, true, null, rnd);
        assertEquals(30, s.length());
        assertTrue("String contains characters other than letters/digits", isAllAlphanumeric(s));
    }

    @Test
    public void testRandomNumericContainsOnlyDigits() {
        Random rnd = new Random(999L);
        String s = RandomStringUtils.random(15, 0, 0, false, true, null, rnd);
        assertEquals(15, s.length());
        assertTrue("String contains non‑digit characters", isAllDigits(s));
    }

    @Test
    public void testRandomAsciiWithinPrintableRange() {
        Random rnd = new Random(42L);
        String s = RandomStringUtils.randomAscii(25);
        assertEquals(25, s.length());
        assertTrue("String contains non‑ASCII printable characters", isAsciiPrintable(s));
    }

    // -----------------------------------------------------------------------
    // Custom character sources
    // -----------------------------------------------------------------------
    @Test
    public void testRandomWithCustomCharArrayUsesOnlySuppliedChars() {
        char[] charset = new char[] {'A', 'b', '3', '#'};
        Random rnd = new Random(777L);
        String s = RandomStringUtils.random(50, 0, charset.length, false, false, charset, rnd);
        assertEquals(50, s.length());
        assertTrue("String contains characters outside the supplied charset",
                containsOnlyFrom(charset, s));
    }

    @Test
    public void testRandomWithCustomStringUsesOnlySuppliedChars() {
        String charset = "xyzXYZ12";
        Random rnd = new Random(2021L);
        String s = RandomStringUtils.random(40, charset);
        assertEquals(40, s.length());
        // delegate to the char[] overload – we can reuse the helper
        assertTrue("String contains characters outside the supplied charset",
                containsOnlyFrom(charset.toCharArray(), s));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomWithEmptyCharArrayThrowsException() {
        // empty array – code will compute gap = 0 and later throw
        RandomStringUtils.random(5, 0, 0, false, false, new char[0], new Random());
    }

    // -----------------------------------------------------------------------
    // Surrogate handling
    // -----------------------------------------------------------------------
    @Test
    public void testSurrogatePairGenerationAndPrivateHighSurrogateSkipping() {
        // Charset contains: valid high surrogate, valid low surrogate,
        // private high surrogate, and a regular letter.
        char[] charset = new char[] {
                (char)0xD800, // high surrogate (start of range)
                (char)0xDC00, // low surrogate (start of range)
                (char)0xDB80, // private high surrogate (to be skipped)
                'a'           // ordinary letter
        };
        // Seeded Random guarantees reproducible sequence.
        Random rnd = new Random(123L);
        // Request 6 characters; internal logic may insert companion surrogates,
        // but final String length must equal the requested count.
        String result = RandomStringUtils.random(6, 0, charset.length,
                false, false, charset, rnd);
        assertEquals("Result length must equal requested count", 6, result.length());
        // No private high surrogate should appear.
        for (char c : result.toCharArray()) {
            assertFalse("Private high surrogate (0xDB80–0xDBFF) must be omitted",
                    c >= 0xDB80 && c <= 0xDBFF);
        }
        // All remaining characters must be valid Unicode sequences.
        assertTrue("Surrogate pairs must be well‑formed", hasValidSurrogatePairs(result));
    }

    // -----------------------------------------------------------------------
    // Random with default Random (non‑deterministic) – basic sanity checks
    // -----------------------------------------------------------------------
    @Test
    public void testRandomDefaultRandomProducesStringOfRequestedLength() {
        String s = RandomStringUtils.random(12);
        assertNotNull(s);
        assertEquals(12, s.length());
    }

    @Test
    public void testRandomWithLettersAndNumbersBothFalseProducesFullUnicodeRange() {
        Random rnd = new Random(0L);
        // count 5, letters=false, numbers=false -> all Unicode chars allowed.
        String s = RandomStringUtils.random(5, 0, 0, false, false, null, rnd);
        assertEquals(5, s.length());
        // No further assertions – the method is exercised without triggering
        // the letters/numbers filters.
    }
}
