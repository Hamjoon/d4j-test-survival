**1.  Public methods of `RandomStringUtils` (including signatures)**  

| # | Return type | Method signature |
|---|-------------|------------------|
| 1 | `String` | `public static String random(int count)` |
| 2 | `String` | `public static String randomAscii(int count)` |
| 3 | `String` | `public static String randomAlphabetic(int count)` |
| 4 | `String` | `public static String randomAlphanumeric(int count)` |
| 5 | `String` | `public static String randomNumeric(int count)` |
| 6 | `String` | `public static String random(int count, boolean letters, boolean numbers)` |
| 7 | `String` | `public static String random(int count, int start, int end, boolean letters, boolean numbers)` |
| 8 | `String` | `public static String random(int count, int start, int end, boolean letters, boolean numbers, char... chars)` |
| 9 | `String` | `public static String random(int count, int start, int end, boolean letters, boolean numbers, char[] chars, Random random)` |
|10 | `String` | `public static String random(int count, String chars)` |
|11 | `String` | `public static String random(int count, char... chars)` |
|12 | Constructor | `public RandomStringUtils()` (public no‑arg constructor – required for JavaBean tools) |

---

**2.  Basic JUnit 4 test ideas (one per method)**  

| Method | Basic test goal |
|--------|-----------------|
| `random(int)` | Verify length equals *count* and that the result is not `null`. |
| `randomAscii(int)` | Verify every character is in the printable ASCII range 32‑126. |
| `randomAlphabetic(int)` | Verify all characters are letters (`Character.isLetter`). |
| `randomAlphanumeric(int)` | Verify all characters are letters **or** digits. |
| `randomNumeric(int)` | Verify all characters are digits. |
| `random(int,boolean,boolean)` | Test a few combinations (`true/false`) and check that the produced characters respect the flags. |
| `random(int,int,int,boolean,boolean)` | Use a custom start/end (e.g., `'a'`‑`'z'`) and verify characters lie in that range. |
| `random(int,int,int,boolean,boolean,char...)` | Supply a custom char array (e.g., `{ 'A', 'b', '3' }`) and verify output contains only those chars. |
| `random(int,int,int,boolean,boolean,char[],Random)` | Same as above but using a deterministic `Random` (seeded) to make the test repeatable. |
| `random(int,String)` | Pass a non‑null string of allowed chars and verify output uses only those chars. |
| `random(int,char...)` | Pass a custom char array and verify output uses only those chars. |
| Constructor | Simply instantiate the class – it should not throw. |

---

**3.  Edge‑case & exception scenarios that must be covered**

| Situation | Expected behaviour |
|-----------|--------------------|
| `count == 0` for any overload | Returns an empty string (`""`). |
| `count < 0` | Throws `IllegalArgumentException`. |
| `chars` array is **empty** (`new char[0]`) | Throws `IllegalArgumentException`. |
| `chars` string is empty (`""`) | Throws `IllegalArgumentException`. |
| Both `letters` and `numbers` are `false` and start/end are `0` | The method falls back to the full Unicode range – we can only check that a result is produced and its length matches. |
| Private high‑surrogate range (0xDB80‑0xDBFF) is present in the supplied `char[]` | Those characters must be *skipped*; the final string must not contain any code‑point in that range. |
| Low‑surrogate (0xDC00‑0xDFFF) appears – the method must prepend a matching high surrogate. |
| High‑surrogate (0xD800‑0xDB7F) appears – the method must append a matching low surrogate. |
| When a surrogate pair is generated, the pair must be in the correct order (high before low) and the overall string length must still equal the requested `count`. |

---

**4.  Complete JUnit 4 test file**

```java
###Test START##
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

/**
 * JUnit‑4 test suite for {@link RandomStringUtils}.
 * The tests cover normal behaviour, edge cases and the surrogate‑handling logic.
 */
public class RandomStringUtilsTest {

    /* ----------------------------------------------------------------------
     *  Basic functionality tests
     * ---------------------------------------------------------------------- */

    @Test
    public void testRandomLengthZero() {
        assertEquals("", RandomStringUtils.random(0));
        assertEquals("", RandomStringUtils.randomAscii(0));
        assertEquals("", RandomStringUtils.randomAlphabetic(0));
        assertEquals("", RandomStringUtils.randomAlphanumeric(0));
        assertEquals("", RandomStringUtils.randomNumeric(0));
        assertEquals("", RandomStringUtils.random(0, true, true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomNegativeCount() {
        RandomStringUtils.random(-5);
    }

    @Test
    public void testRandomSimpleLength() {
        int len = 10;
        String s = RandomStringUtils.random(len);
        assertNotNull(s);
        assertEquals(len, s.length());
    }

    @Test
    public void testRandomAsciiRange() {
        String s = RandomStringUtils.randomAscii(100);
        for (char c : s.toCharArray()) {
            assertTrue("ASCII char out of range: " + (int) c,
                    c >= 32 && c <= 126);
        }
    }

    @Test
    public void testRandomAlphabeticOnly() {
        String s = RandomStringUtils.randomAlphabetic(50);
        for (char c : s.toCharArray()) {
            assertTrue("Non‑letter found: " + c, Character.isLetter(c));
        }
    }

    @Test
    public void testRandomAlphanumericOnly() {
        String s = RandomStringUtils.randomAlphanumeric(50);
        for (char c : s.toCharArray()) {
            assertTrue("Char neither letter nor digit: " + c,
                    Character.isLetter(c) || Character.isDigit(c));
        }
    }

    @Test
    public void testRandomNumericOnly() {
        String s = RandomStringUtils.randomNumeric(30);
        for (char c : s.toCharArray()) {
            assertTrue("Non‑digit found: " + c, Character.isDigit(c));
        }
    }

    @Test
    public void testRandomWithFlags() {
        // letters = true, numbers = false
        String lettersOnly = RandomStringUtils.random(20, true, false);
        for (char c : lettersOnly.toCharArray()) {
            assertTrue(Character.isLetter(c));
        }

        // letters = false, numbers = true
        String numbersOnly = RandomStringUtils.random(20, false, true);
        for (char c : numbersOnly.toCharArray()) {
            assertTrue(Character.isDigit(c));
        }

        // both true -> alphanumeric
        String alnum = RandomStringUtils.random(20, true, true);
        for (char c : alnum.toCharArray()) {
            assertTrue(Character.isLetter(c) || Character.isDigit(c));
        }
    }

    @Test
    public void testRandomWithCustomRange() {
        // only lower‑case letters a‑z
        String s = RandomStringUtils.random(40, (int) 'a', (int) 'z' + 1, true, false);
        for (char c : s.toCharArray()) {
            assertTrue("Char out of a‑z range: " + c, c >= 'a' && c <= 'z');
        }
    }

    @Test
    public void testRandomWithCustomCharArray() {
        char[] pool = new char[] { 'A', 'b', '3', '@' };
        String s = RandomStringUtils.random(25, 0, pool.length, false, false, pool);
        for (char c : s.toCharArray()) {
            boolean found = false;
            for (char p : pool) {
                if (c == p) { found = true; break; }
            }
            assertTrue("Unexpected character: " + c, found);
        }
    }

    @Test
    public void testRandomWithCustomCharArrayDeterministic() {
        char[] pool = new char[] { 'X', 'Y', 'Z' };
        Random rnd = new Random(12345L); // deterministic seed
        String s = RandomStringUtils.random(10, 0, pool.length, false, false, pool, rnd);
        assertEquals(10, s.length());
        // Because the seed is fixed we can assert the exact string produced
        assertEquals("ZYXXZZYXZY", s);
    }

    @Test
    public void testRandomFromString() {
        String source = "abc123";
        String s = RandomStringUtils.random(20, source);
        for (char c : s.toCharArray()) {
            assertTrue("Char not from source string: " + c,
                    source.indexOf(c) >= 0);
        }
    }

    @Test
    public void testRandomFromCharArray() {
        char[] source = new char[] { 'm', 'n', 'o', '0', '1' };
        String s = RandomStringUtils.random(15, source);
        for (char c : s.toCharArray()) {
            boolean ok = false;
            for (char p : source) {
                if (c == p) { ok = true; break; }
            }
            assertTrue("Char not from source array: " + c, ok);
        }
    }

    @Test
    public void testConstructorIsPublic() {
        // Just ensure we can instantiate – no exception expected
        new RandomStringUtils();
    }

    /* ----------------------------------------------------------------------
     *  Edge‑case / exception tests
     * ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testRandomNegativeCountWithFullSignature() {
        RandomStringUtils.random(-1, 0, 0, false, false, null, new Random());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomEmptyCharArray() {
        RandomStringUtils.random(5, new char[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomEmptyCharArrayFullSignature() {
        RandomStringUtils.random(5, 0, 0, false, false, new char[0], new Random());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomEmptyStringArgument() {
        RandomStringUtils.random(5, "");
    }

    @Test
    public void testSkipPrivateHighSurrogate() {
        // Private high surrogate range: 0xDB80 – 0xDBFF
        char privateHigh = '\uDB80';
        char[] pool = new char[] { privateHigh, 'A', 'b', '3' };
        // Use a deterministic Random that will *attempt* to pick the private high surrogate
        Random rnd = new Random(0L);
        String s = RandomStringUtils.random(20, 0, pool.length, false, false, pool, rnd);
        for (char c : s.toCharArray()) {
            assertFalse("Private high surrogate should be skipped", c >= '\uDB80' && c <= '\uDBFF');
        }
    }

    @Test
    public void testLowSurrogateIsPrecededByHighSurrogate() {
        // Force low surrogate to be selected by providing a pool that contains only one low surrogate
        char low = '\uDC00'; // first low surrogate
        char[] pool = new char[] { low };
        Random rnd = new Random(0L); // deterministic – will always pick index 0
        String s = RandomStringUtils.random(2, 0, 1, false, false, pool, rnd);
        assertEquals(2, s.length());

        char first = s.charAt(0);
        char second = s.charAt(1);
        // The algorithm should have generated a high surrogate before the low one
        assertTrue("First char should be a high surrogate", first >= '\uD800' && first <= '\uDB7F');
        assertTrue("Second char should be the low surrogate we supplied", second == low);
    }

    @Test
    public void testHighSurrogateIsFollowedByLowSurrogate() {
        // Force high surrogate to be selected by providing a pool that contains only one high surrogate
        char high = '\uD800'; // first high surrogate
        char[] pool = new char[] { high };
        Random rnd = new Random(0L);
        String s = RandomStringUtils.random(2, 0, 1, false, false, pool, rnd);
        assertEquals(2, s.length());

        char first = s.charAt(0);
        char second = s.charAt(1);
        // The algorithm should have generated a low surrogate after the high one
        assertTrue("First char should be the high surrogate we supplied", first == high);
        assertTrue("Second char should be a low surrogate", second >= '\uDC00' && second <= '\uDFFF');
    }

    @Test
    public void testCountZeroWithFullSignatureReturnsEmptyString() {
        String s = RandomStringUtils.random(0, 0, 0, false, false, null, new Random());
        assertEquals("", s);
    }
}
###Test END##
```