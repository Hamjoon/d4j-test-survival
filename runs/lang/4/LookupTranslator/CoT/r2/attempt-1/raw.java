**1. Public methods of `LookupTranslator`**

| Modifier / Return | Method signature |
|-------------------|------------------|
| `public LookupTranslator(CharSequence[]... lookup)` | Constructor – creates a translator from a two‑dimensional array where each inner array holds a *key* and a *value*. |
| `public int translate(CharSequence input, int index, Writer out) throws IOException` | Translates a portion of the given `input` starting at `index`. If a matching key is found in the internal lookup map, the corresponding value is written to `out` and the number of consumed characters is returned; otherwise `0` is returned. |

---

**2. Basic JUnit 4 test cases (functionality)**  

| Test case | What it verifies |
|-----------|-------------------|
| `testSimpleTranslation` | A single‑character mapping works and the returned length is correct. |
| `testNoMatchReturnsZero` | When the input character(s) are not present in the map, the method returns `0` and writes nothing. |
| `testGreedyLongestMatch` | When several keys share a prefix, the longest key is chosen (greedy behaviour). |
| `testConstructorWithNullLookup` | Passing `null` to the constructor does not throw and results in a translator that never matches. |
| `testConstructorWithEmptyLookup` | An empty lookup array behaves like the `null` case. |
| `testDuplicateKeyOverrides` | If the same key appears more than once, the last definition wins. |

---

**3. Edge‑case / exception scenarios**

| Scenario | Expected outcome |
|----------|-------------------|
| `input == null` | `NullPointerException` (the method dereferences `input`). |
| `index < 0` | `StringIndexOutOfBoundsException` (triggered by `subSequence`). |
| `index > input.length()` | `StringIndexOutOfBoundsException`. |
| `index == input.length()` | Returns `0` (nothing to translate) – **no** exception. |
| `Writer` throws `IOException` | The exception is propagated unchanged. |
| Lookup map contains a `null` value (should not happen with the provided constructor) – not testable via public API. |

---

**4. Complete JUnit 4 test file**

```java
###Test START##
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

/**
 * Unit tests for {@link LookupTranslator}.
 * <p>
 * The tests cover normal functionality, edge‑cases and exception handling.
 * </p>
 */
public class LookupTranslatorTest {

    /* --------------------------------------------------------------------- */
    /*  Basic functionality tests                                           */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSimpleTranslation() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"a", "b"}
        });
        StringWriter out = new StringWriter();
        int consumed = lt.translate("a", 0, out);
        assertEquals("Should consume one character", 1, consumed);
        assertEquals("b", out.toString());
    }

    @Test
    public void testNoMatchReturnsZero() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"a", "b"}
        });
        StringWriter out = new StringWriter();
        int consumed = lt.translate("c", 0, out);
        assertEquals("No match -> consume 0 characters", 0, consumed);
        assertEquals("Writer must stay empty", "", out.toString());
    }

    @Test
    public void testGreedyLongestMatch() throws IOException {
        // "ab" is longer than "a" – the translator must pick "ab"
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"a", "A"},
            {"ab", "AB"}
        });
        StringWriter out = new StringWriter();
        int consumed = lt.translate("ab", 0, out);
        assertEquals("Longest match should be chosen", 2, consumed);
        assertEquals("AB", out.toString());
    }

    @Test
    public void testConstructorWithNullLookup() throws IOException {
        LookupTranslator lt = new LookupTranslator((CharSequence[][]) null);
        StringWriter out = new StringWriter();
        int consumed = lt.translate("anything", 0, out);
        assertEquals("With null lookup no translation occurs", 0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testConstructorWithEmptyLookup() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[0][]);
        StringWriter out = new StringWriter();
        int consumed = lt.translate("anything", 0, out);
        assertEquals("With empty lookup no translation occurs", 0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testDuplicateKeyOverrides() throws IOException {
        // The second entry for "x" should win.
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"x", "first"},
            {"x", "second"}
        });
        StringWriter out = new StringWriter();
        int consumed = lt.translate("x", 0, out);
        assertEquals(1, consumed);
        assertEquals("second", out.toString());
    }

    /* --------------------------------------------------------------------- */
    /*  Edge‑case / exception handling tests                                */
    /* --------------------------------------------------------------------- */

    @Test(expected = NullPointerException.class)
    public void testTranslateWithNullInput() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"a", "b"}
        });
        lt.translate(null, 0, new StringWriter());
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testTranslateWithNegativeIndex() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"a", "b"}
        });
        lt.translate("abc", -1, new StringWriter());
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testTranslateWithIndexBeyondLength() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"a", "b"}
        });
        lt.translate("abc", 4, new StringWriter()); // length is 3
    }

    @Test
    public void testTranslateWithIndexEqualToLength() throws IOException {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"a", "b"}
        });
        StringWriter out = new StringWriter();
        int consumed = lt.translate("abc", 3, out); // index == length
        assertEquals("When index == length, nothing is consumed", 0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testWriterThrowsIOExceptionIsPropagated() {
        LookupTranslator lt = new LookupTranslator(new CharSequence[][]{
            {"a", "b"}
        });
        Writer brokenWriter = new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("forced");
            }
            @Override public void flush() throws IOException {}
            @Override public void close() throws IOException {}
        };
        try {
            lt.translate("a", 0, brokenWriter);
            fail("Expected IOException to be thrown");
        } catch (IOException e) {
            assertEquals("forced", e.getMessage());
        }
    }
}
###Test END##
```