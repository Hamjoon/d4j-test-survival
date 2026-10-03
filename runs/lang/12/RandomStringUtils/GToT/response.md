**Public API extracted from `RandomStringUtils`**

| # | Modifier | Return type | Method signature |
|---|----------|-------------|------------------|
| 1 | `public` | `RandomStringUtils` | `RandomStringUtils()` |
| 2 | `public static` | `String` | `random(int count)` |
| 3 | `public static` | `String` | `randomAscii(int count)` |
| 4 | `public static` | `String` | `randomAlphabetic(int count)` |
| 5 | `public static` | `String` | `randomAlphanumeric(int count)` |
| 6 | `public static` | `String` | `randomNumeric(int count)` |
| 7 | `public static` | `String` | `random(int count, boolean letters, boolean numbers)` |
| 8 | `public static` | `String` | `random(int count, int start, int end, boolean letters, boolean numbers)` |
| 9 | `public static` | `String` | `random(int count, int start, int end, boolean letters, boolean numbers, char... chars)` |
|10| `public static` | `String` | `random(int count, int start, int end, boolean letters, boolean numbers, char[] chars, Random random)` |
|11| `public static` | `String` | `random(int count, String chars)` |
|12| `public static` | `String` | `random(int count, char... chars)` |

---

## Identified Edge‑Cases & Exception Scenarios  

| Method | Edge case / Exception | Reason |
|--------|----------------------|--------|
| `random(int)` | `count == 0` → returns `""` | Guard clause in implementation |
| `random(int)` | `count < 0` → `IllegalArgumentException` | Guard clause |
| `randomAscii(int)` | Verify characters are in printable ASCII range (32‑126) | Method limits start/end to those bounds |
| `randomAlphabetic(int)` | Result contains only letters (`Character.isLetter`) | `letters = true, numbers = false` |
| `randomAlphanumeric(int)` | Result contains only letters **or** digits | `letters = true, numbers = true` |
| `randomNumeric(int)` | Result contains only digits (`Character.isDigit`) | `letters = false, numbers = true` |
| `random(int, boolean, boolean)` | Both flags false → any Unicode char (except private high surrogate) | Implementation falls back to full range |
| `random(int, int, int, …)` | `start == 0 && end == 0` → defaults to printable ASCII or full range | Guard clause inside method |
| `random(int, …, char[] chars, Random)` | `chars == null` → uses full Unicode range | Normal path |
| `random(int, …, char[] chars, Random)` | `chars` empty → `IllegalArgumentException` | Documented |
| `random(int, String chars)` | `chars == null` → falls back to full range | Normal path |
| `random(int, String chars)` | `chars.isEmpty()` → `IllegalArgumentException` | Documented |
| `random(int, char... chars)` | `chars == null` → falls back to full range | Normal path |
| `random(int, char... chars)` | `chars.length == 0` → `IllegalArgumentException` | Documented |
| All `random…` methods | Surrogate handling – private high surrogates (0xDB80‑0xDBFF) must never appear | Implementation skips them |
| All `random…` methods | When a surrogate pair is generated the final string length must still equal the requested `count` | Loop adjusts `count` internally |

---

## Complete JUnit 4 Test File  

```java
###Test START##
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.util.Random;
import java.util.Set;
import java.util.HashSet;

import org.junit.Test;

/**
 * Comprehensive JUnit‑4 test suite for {@link RandomStringUtils}.
 *
 * The tests cover:
 * <ul>
 *   <li>Normal use cases (correct length, character set constraints)</li>
 *   <li>Edge cases (zero length, negative length)</li>
 *   <li>Exception scenarios (empty char array / empty string)</li>
 *   <li>Surrogate handling – private high surrogates must never appear</li>
 *   <li>Deterministic generation using a seeded {@link Random}</li>
 * </ul>
 */
public class RandomStringUtilsTest {

    // -----------------------------------------------------------------------
    // Helper utilities
    // -----------------------------------------------------------------------

    /** Returns true if the supplied character is a private high surrogate (0xDB80‑0xDBFF). */
    private static boolean isPrivateHighSurrogate(char ch) {
        return ch >= 0xDB80 && ch <= 0xDBFF;
    }

    /** Checks that a string contains only characters that satisfy the supplied predicate. */
    private static void assertAllMatch(String s, java.util.function.Predicate<Character> predicate) {
        for (char c : s.toCharArray()) {
            assertTrue("Character '" + c + "' does not match predicate", predicate.test(c));
        }
    }

    /** Returns a deterministic Random instance for reproducible tests. */
    private static Random seededRandom() {
        return new Random(0xCAFEBABE);
    }

    // -----------------------------------------------------------------------
    // 1. Constructor – it should be callable (no behaviour to test)
    // -----------------------------------------------------------------------
    @Test
    public void testConstructorIsPublic() {
        new RandomStringUtils(); // simply ensure it compiles & runs
    }

    // -----------------------------------------------------------------------
    // 2. random(int)
    // -----------------------------------------------------------------------
    @Test
    public void testRandom_LengthAndAnyChar() {
        String s = RandomStringUtils.random(10);
        assertEquals(10, s.length());
        // No further restriction – just ensure no private high surrogate appears
        for (char c : s.toCharArray()) {
            assertFalse("Private high surrogate should not appear", isPrivateHighSurrogate(c));
        }
    }

    @Test
    public void testRandom_ZeroLength() {
        assertEquals("", RandomStringUtils.random(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandom_NegativeLength() {
        RandomStringUtils.random(-5);
    }

    // -----------------------------------------------------------------------
    // 3. randomAscii(int)
    // -----------------------------------------------------------------------
    @Test
    public void testRandomAscii_OnlyPrintableAscii() {
        String s = RandomStringUtils.randomAscii(50);
        assertEquals(50, s.length());
        assertAllMatch(s, c -> c >= 32 && c <= 126);
    }

    // -----------------------------------------------------------------------
    // 4. randomAlphabetic(int)
    // -----------------------------------------------------------------------
    @Test
    public void testRandomAlphabetic_OnlyLetters() {
        String s = RandomStringUtils.randomAlphabetic(30);
        assertEquals(30, s.length());
        assertAllMatch(s, Character::isLetter);
    }

    // -----------------------------------------------------------------------
    // 5. randomAlphanumeric(int)
    // -----------------------------------------------------------------------
    @Test
    public void testRandomAlphanumeric_LettersOrDigits() {
        String s = RandomStringUtils.randomAlphanumeric(40);
        assertEquals(40, s.length());
        assertAllMatch(s, c -> Character.isLetter(c) || Character.isDigit(c));
    }

    // -----------------------------------------------------------------------
    // 6. randomNumeric(int)
    // -----------------------------------------------------------------------
    @Test
    public void testRandomNumeric_OnlyDigits() {
        String s = RandomStringUtils.randomNumeric(25);
        assertEquals(25, s.length());
        assertAllMatch(s, Character::isDigit);
    }

    // -----------------------------------------------------------------------
    // 7. random(int, boolean, boolean)
    // -----------------------------------------------------------------------
    @Test
    public void testRandom_LettersOnlyViaFlags() {
        String s = RandomStringUtils.random(15, true, false);
        assertEquals(15, s.length());
        assertAllMatch(s, Character::isLetter);
    }

    @Test
    public void testRandom_NumbersOnlyViaFlags() {
        String s = RandomStringUtils.random(15, false, true);
        assertEquals(15, s.length());
        assertAllMatch(s, Character::isDigit);
    }

    @Test
    public void testRandom_LettersAndNumbersViaFlags() {
        String s = RandomStringUtils.random(15, true, true);
        assertEquals(15, s.length());
        assertAllMatch(s, c -> Character.isLetter(c) || Character.isDigit(c));
    }

    @Test
    public void testRandom_NoLettersNoNumbers_AnyUnicode() {
        String s = RandomStringUtils.random(12, false, false);
        assertEquals(12, s.length());
        // just verify that we never produced a private high surrogate
        for (char c : s.toCharArray()) {
            assertFalse(isPrivateHighSurrogate(c));
        }
    }

    // -----------------------------------------------------------------------
    // 8. random(int, int, int, boolean, boolean)
    // -----------------------------------------------------------------------
    @Test
    public void testRandom_StartEndDefaultsWhenBothZero() {
        // letters = true, numbers = false => printable ASCII range
        String s = RandomStringUtils.random(20, 0, 0, true, false);
        assertEquals(20, s.length());
        assertAllMatch(s, c -> c >= 32 && c <= 126 && Character.isLetter(c));
    }

    @Test
    public void testRandom_StartEndCustomRange() {
        // Choose a range that only contains uppercase letters (65‑90)
        String s = RandomStringUtils.random(10, 65, 91, true, false);
        assertEquals(10, s.length());
        assertAllMatch(s, c -> c >= 'A' && c <= 'Z');
    }

    // -----------------------------------------------------------------------
    // 9. random(int, int, int, boolean, boolean, char... chars)
    // -----------------------------------------------------------------------
    @Test
    public void testRandom_WithCustomCharArray() {
        char[] pool = {'a', 'b', 'c', '1', '2', '3'};
        String s = RandomStringUtils.random(12, 0, pool.length, false, false, pool);
        assertEquals(12, s.length());
        for (char c : s.toCharArray()) {
            boolean found = false;
            for (char p : pool) {
                if (c == p) {
                    found = true;
                    break;
                }
            }
            assertTrue("Character '" + c + "' not in custom pool", found);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandom_WithEmptyCharArray_Throws() {
        RandomStringUtils.random(5, 0, 0, false, false, new char[0]);
    }

    // -----------------------------------------------------------------------
    // 10. random(int, int, int, boolean, boolean, char[] chars, Random)
    // -----------------------------------------------------------------------
    @Test
    public void testRandom_DeterministicWithSeededRandom() {
        Random rnd = seededRandom();
        char[] pool = {'x', 'y', 'z'};
        String s1 = RandomStringUtils.random(8, 0, pool.length, false, false, pool, rnd);
        // Reset the Random with the same seed to get the same sequence
        Random rnd2 = seededRandom();
        String s2 = RandomStringUtils.random(8, 0, pool.length, false, false, pool, rnd2);
        assertEquals(s1, s2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandom_NegativeCount_Throws() {
        RandomStringUtils.random(-1, 0, 0, false, false, null, new Random());
    }

    // -----------------------------------------------------------------------
    // 11. random(int, String)
    // -----------------------------------------------------------------------
    @Test
    public void testRandom_WithStringPool() {
        String pool = "ABC123";
        String s = RandomStringUtils.random(9, pool);
        assertEquals(9, s.length());
        for (char c : s.toCharArray()) {
            assertTrue("Char not in pool", pool.indexOf(c) >= 0);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandom_WithEmptyString_Throws() {
        RandomStringUtils.random(5, "");
    }

    @Test
    public void testRandom_NullStringFallsBackToAllChars() {
        String s = RandomStringUtils.random(7, (String) null);
        assertEquals(7, s.length());
        // just verify that we didn't hit a private high surrogate
        for (char c : s.toCharArray()) {
            assertFalse(isPrivateHighSurrogate(c));
        }
    }

    // -----------------------------------------------------------------------
    // 12. random(int, char...)
    // -----------------------------------------------------------------------
    @Test
    public void testRandom_WithCharVarargs() {
        String s = RandomStringUtils.random(6, 'p', 'q', 'r', '1', '2');
        assertEquals(6, s.length());
        Set<Character> allowed = new HashSet<>();
        allowed.add('p'); allowed.add('q'); allowed.add('r'); allowed.add('1'); allowed.add('2');
        for (char c : s.toCharArray()) {
            assertTrue("Unexpected char: " + c, allowed.contains(c));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandom_WithEmptyCharVarargs_Throws() {
        RandomStringUtils.random(3, new char[0]);
    }

    @Test
    public void testRandom_NullCharArrayFallsBackToAllChars() {
        String s = RandomStringUtils.random(4, (char[]) null);
        assertEquals(4, s.length());
        // verify surrogate safety as before
        for (char c : s.toCharArray()) {
            assertFalse(isPrivateHighSurrogate(c));
        }
    }

    // -----------------------------------------------------------------------
    // Additional surrogate safety test (covers the internal loop logic)
    // -----------------------------------------------------------------------
    @Test
    public void testSurrogatePairs_DoNotBreakLength() {
        // Use a range that includes surrogate code points to force the internal
        // surrogate handling logic. 0xD800‑0xDFFF covers both high and low surrogates.
        String s = RandomStringUtils.random(20, 0xD800, 0xE000, false, false);
        assertEquals(20, s.length());

        // Ensure no private high surrogates (0xDB80‑0xDBFF) appear
        for (char c : s.toCharArray()) {
            assertFalse(isPrivateHighSurrogate(c));
        }
    }
}
###Test END##
```