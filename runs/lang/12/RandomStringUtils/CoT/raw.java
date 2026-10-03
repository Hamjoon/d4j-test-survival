**1. Public methods in `RandomStringUtils`**

| # | Method signature |
|---|-------------------|
| 1 | `public static String random(int count)` |
| 2 | `public static String randomAscii(int count)` |
| 3 | `public static String randomAlphabetic(int count)` |
| 4 | `public static String randomAlphanumeric(int count)` |
| 5 | `public static String randomNumeric(int count)` |
| 6 | `public static String random(int count, boolean letters, boolean numbers)` |
| 7 | `public static String random(int count, int start, int end, boolean letters, boolean numbers)` |
| 8 | `public static String random(int count, int start, int end, boolean letters, boolean numbers, char... chars)` |
| 9 | `public static String random(int count, int start, int end, boolean letters, boolean numbers, char[] chars, Random random)` |
|10| `public static String random(int count, String chars)` |
|11| `public static String random(int count, char... chars)` |

---

**2. Basic JUnit‑4 test cases for each method**

*The tests below verify the *primary* contract of each overload (correct length, basic character‑class constraints, and handling of `null` arguments).*

---

**3. Edge‑case / exception scenarios that must be covered**

| Scenario | Reason |
|----------|--------|
| `count == 0` | Must return an empty string (no exception). |
| `count < 0` | All overloads should throw `IllegalArgumentException`. |
| `count > 0` with `letters`/`numbers` flags – ensure only allowed characters appear. |
| `randomAscii` – characters must be within printable ASCII (32‑126). |
| `randomAlphabetic` – every character must satisfy `Character.isLetter`. |
| `randomAlphanumeric` – every character must be a letter **or** a digit. |
| `randomNumeric` – every character must be a digit. |
| `random(int, String)` – passing `null` → behaves like `random(int)`. Passing empty string → `IllegalArgumentException`. |
| `random(int, char[])` – passing `null` → behaves like `random(int)`. Passing empty array → `IllegalArgumentException` (via `Random.nextInt(0)`). |
| `random(int, int, int, …)` when `start==0 && end==0` and both `letters` & `numbers` are `false` → the method should still produce a string (uses full Unicode range). |
| Surrogate handling – the method must not throw an exception when the generated range includes surrogate code points. A deterministic `Random` seed is used to make the test repeatable. |

---

**4. Complete JUnit‑4 test file (`RandomStringUtilsTest.java`)**

```java
###Test START##
package org.apache.commons.lang3;

import org.junit.Test;
import org.junit.Assert;

import java.util.Random;
import java.util.Set;
import java.util.HashSet;

/**
 * Comprehensive JUnit‑4 test suite for {@link RandomStringUtils}.
 */
public class RandomStringUtilsTest {

    /* --------------------------------------------------------------------- */
    /*  Basic functionality tests                                            */
    /* --------------------------------------------------------------------- */

    @Test
    public void testRandomZeroLength() {
        Assert.assertEquals("", RandomStringUtils.random(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomNegativeLengthThrows() {
        RandomStringUtils.random(-5);
    }

    @Test
    public void testRandomAsciiRange() {
        String s = RandomStringUtils.randomAscii(100);
        Assert.assertEquals(100, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("ASCII printable range", c >= 32 && c <= 126);
        }
    }

    @Test
    public void testRandomAlphabeticOnlyLetters() {
        String s = RandomStringUtils.randomAlphabetic(120);
        Assert.assertEquals(120, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Alphabetic character expected", Character.isLetter(c));
        }
    }

    @Test
    public void testRandomAlphanumericContainsLetterOrDigit() {
        String s = RandomStringUtils.randomAlphanumeric(80);
        Assert.assertEquals(80, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Alphanumeric character expected",
                    Character.isLetter(c) || Character.isDigit(c));
        }
    }

    @Test
    public void testRandomNumericOnlyDigits() {
        String s = RandomStringUtils.randomNumeric(55);
        Assert.assertEquals(55, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Numeric character expected", Character.isDigit(c));
        }
    }

    @Test
    public void testRandomLettersOnlyFlag() {
        String s = RandomStringUtils.random(40, true, false);
        Assert.assertEquals(40, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Only letters allowed", Character.isLetter(c));
        }
    }

    @Test
    public void testRandomNumbersOnlyFlag() {
        String s = RandomStringUtils.random(30, false, true);
        Assert.assertEquals(30, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Only digits allowed", Character.isDigit(c));
        }
    }

    @Test
    public void testRandomLettersAndNumbersFlag() {
        String s = RandomStringUtils.random(70, true, true);
        Assert.assertEquals(70, s.length());
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Letter or digit expected",
                    Character.isLetter(c) || Character.isDigit(c));
        }
    }

    @Test
    public void testRandomWithCustomCharString() {
        String source = "ABCXYZ0123";
        String s = RandomStringUtils.random(25, source);
        Assert.assertEquals(25, s.length());
        Set<Character> allowed = new HashSet<>();
        for (char c : source.toCharArray()) {
            allowed.add(c);
        }
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Character must belong to supplied set", allowed.contains(c));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomWithEmptyCharStringThrows() {
        RandomStringUtils.random(10, "");
    }

    @Test
    public void testRandomWithCustomCharArray() {
        char[] source = new char[] {'x', 'y', 'z', '1', '2'};
        String s = RandomStringUtils.random(15, source);
        Assert.assertEquals(15, s.length());
        Set<Character> allowed = new HashSet<>();
        for (char c : source) {
            allowed.add(c);
        }
        for (char c : s.toCharArray()) {
            Assert.assertTrue("Character must belong to supplied array", allowed.contains(c));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomWithEmptyCharArrayThrows() {
        RandomStringUtils.random(5, new char[0]);
    }

    @Test
    public void testRandomWithNullCharStringBehavesLikeRandom() {
        // When chars == null the method delegates to the generic random()
        String s = RandomStringUtils.random(12, (String) null);
        Assert.assertEquals(12, s.length());
        // No specific character restriction – just ensure we got a string
        Assert.assertNotNull(s);
    }

    @Test
    public void testRandomWithNullCharArrayBehavesLikeRandom() {
        String s = RandomStringUtils.random(9, (char[]) null);
        Assert.assertEquals(9, s.length());
        Assert.assertNotNull(s);
    }

    @Test
    public void testRandomStartEndDefaultsWhenBothZeroAndNoLettersNumbers() {
        // letters = false, numbers = false → full Unicode range should be used
        String s = RandomStringUtils.random(20, 0, 0, false, false);
        Assert.assertEquals(20, s.length());
        // Just verify that we got a result; the characters can be any Unicode value
        Assert.assertNotNull(s);
    }

    /* --------------------------------------------------------------------- */
    /*  Edge‑case tests – surrogate handling & deterministic randomness      */
    /* --------------------------------------------------------------------- */

    @Test
    public void testRandomWithSurrogateRangeDoesNotThrow() {
        // Force the method to work with surrogate code points.
        // Use a deterministic Random so the test is repeatable.
        Random deterministic = new Random(12345L);
        // Range covering high surrogate (0xD800‑0xDB7F) and low surrogate (0xDC00‑0xDFFF)
        int start = 0xD800;          // 55296
        int end   = 0xE000;          // 57344 (exclusive)
        String s = RandomStringUtils.random(50, start, end, true, true, null, deterministic);
        Assert.assertEquals(50, s.length());

        // Verify that we never get an isolated private high surrogate (0xDB80‑0xDBFF)
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            Assert.assertFalse("Private high surrogate must be skipped",
                    ch >= 0xDB80 && ch <= 0xDBFF);
        }
        // The string length is guaranteed, we are not asserting exact content because of randomness.
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomNegativeLengthWithAllParametersThrows() {
        RandomStringUtils.random(-3, 0, 0, false, false, null, new Random());
    }

    @Test
    public void testRandomZeroLengthWithAllParametersReturnsEmptyString() {
        String s = RandomStringUtils.random(0, 10, 20, true, true, null, new Random());
        Assert.assertEquals("", s);
    }

}
###Test END##
```