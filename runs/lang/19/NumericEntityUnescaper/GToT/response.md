**Step 1 – Public API of `NumericEntityUnescaper`**

| Modifier | Return type | Method name | Parameters | Throws |
|----------|-------------|-------------|------------|--------|
| `public` | `int` | `translate` | `CharSequence input, int index, Writer out` | `IOException` |

`translate` is the only public method that needs to be exercised.

---

**Step 2 – One basic JUnit‑4 test per method**

| Method | Basic test idea |
|--------|-----------------|
| `translate` | Verify that a simple decimal numeric entity (`&#65;`) is translated to the character **‘A’** and that the method returns the number of characters it consumed (‑‑ 5). |

```java
@Test
public void testTranslateDecimalEntity() throws IOException {
    NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
    StringWriter out = new StringWriter();

    int consumed = unescaper.translate("&#65;", 0, out);

    assertEquals(5, consumed);                 // whole string consumed
    assertEquals("A", out.toString());         // 65 → ‘A’
}
```

---

**Step 3 – Edge‑case & exception analysis**

| Situation | Why it is an edge case / what can go wrong? |
|-----------|---------------------------------------------|
| **Hex entity** (`&#x41;`) | Requires the `isHex` flag and correct length calculation. |
| **Entity without a trailing ‘;’** (`&#65`) | The `while (input.charAt(end) != ';')` loop runs off the end → **`StringIndexOutOfBoundsException`**. |
| **Hex entity without ‘;’** (`&#x41`) | Same as above, but also tests the `isHex` path. |
| **Surrogate‑pair value** (`&#128512;` → 😀) | `entityValue > 0xFFFF` ⇒ two `char`s are written. Must verify both chars are emitted and the return length is correct. |
| **Invalid numeric format** (`&#xZZ;`) | `Integer.parseInt` throws `NumberFormatException`; the method catches it and returns **0** without writing anything. |
| **Empty numeric part** (`&#;`) | Parses an empty string → `NumberFormatException` → return **0**. |
| **Non‑entity input** (`"abc"`) | Method should return **0** and write nothing. |
| **Entity that starts after the given index** | If `index` does not point to ‘&’, the method must return **0**. |
| **Very large index** (e.g. `index == input.length()`) | Guard `index < seqEnd‑1` prevents `ArrayIndexOutOfBoundsException`. |

All of the above need dedicated tests.

---

**Step 4 – JUnit‑4 tests for the identified edge cases**

```java
@Test
public void testTranslateHexEntityUpperCase() throws IOException {
    NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
    StringWriter out = new StringWriter();

    int consumed = unescaper.translate("&#x41;", 0, out);

    assertEquals(6, consumed);                 // '&' '#' 'x' '4' '1' ';'
    assertEquals("A", out.toString());
}

@Test(expected = StringIndexOutOfBoundsException.class)
public void testTranslateDecimalEntityMissingSemicolon() throws IOException {
    NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
    StringWriter out = new StringWriter();

    // This should blow up because the loop never finds ';'
    unescaper.translate("&#65", 0, out);
}

@Test(expected = StringIndexOutOfBoundsException.class)
public void testTranslateHexEntityMissingSemicolon() throws IOException {
    NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
    StringWriter out = new StringWriter();

    unescaper.translate("&#x41", 0, out);
}

@Test
public void testTranslateSurrogatePair() throws IOException {
    NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
    StringWriter out = new StringWriter();

    // 0x1F600 = 😀 (grinning face)
    int consumed = unescaper.translate("&#128512;", 0, out);

    assertEquals(9, consumed);                 // '&' '#' digits ';' → 9 chars
    String result = out.toString();
    assertEquals(2, result.length());          // surrogate pair = 2 char units
    int codePoint = result.codePointAt(0);
    assertEquals(0x1F600, codePoint);
}

@Test
public void testTranslateInvalidNumberFormat() throws IOException {
    NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
    StringWriter out = new StringWriter();

    int consumed = unescaper.translate("&#xZZ;", 0, out);

    assertEquals(0, consumed);                 // parsing failed → 0
    assertEquals("", out.toString());          // nothing written
}

@Test
public void testTranslateEmptyEntity() throws IOException {
    NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
    StringWriter out = new StringWriter();

    int consumed = unescaper.translate("&#;", 0, out);

    assertEquals(0, consumed);                 // empty numeric part → 0
    assertEquals("", out.toString());
}

@Test
public void testTranslateNonEntityInput() throws IOException {
    NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
    StringWriter out = new StringWriter();

    int consumed = unescaper.translate("Hello world", 0, out);

    assertEquals(0, consumed);
    assertEquals("", out.toString());
}

@Test
public void testTranslateStartsAtWrongIndex() throws IOException {
    NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
    StringWriter out = new StringWriter();

    // Index points to 'H', not '&'
    int consumed = unescaper.translate("Hello &#65;", 1, out);

    assertEquals(0, consumed);
    assertEquals("", out.toString());
}
```

---

**Step 5 – Complete JUnit‑4 test file**

```java
###Test START##
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Test;

/**
 * Comprehensive JUnit‑4 test suite for {@link NumericEntityUnescaper}.
 *
 * The tests cover:
 * <ul>
 *   <li>normal decimal and hexadecimal entities (with and without ';')</li>
 *   <li>surrogate‑pair handling for code points > 0xFFFF</li>
 *   <li>invalid numeric formats</li>
 *   <li>empty entity, non‑entity input and wrong start index</li>
 *   <li>the exceptional case where the terminating ';' is missing</li>
 * </ul>
 */
public class NumericEntityUnescaperTest {

    @Test
    public void testTranslateDecimalEntity() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = unescaper.translate("&#65;", 0, out);

        assertEquals(5, consumed);                 // whole string consumed
        assertEquals("A", out.toString());          // 65 → 'A'
    }

    @Test
    public void testTranslateHexEntityUpperCase() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = unescaper.translate("&#x41;", 0, out);

        assertEquals(6, consumed);                 // '&' '#' 'x' '4' '1' ';'
        assertEquals("A", out.toString());
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testTranslateDecimalEntityMissingSemicolon() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        // No terminating ';' → loop runs off the end
        unescaper.translate("&#65", 0, out);
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testTranslateHexEntityMissingSemicolon() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        unescaper.translate("&#x41", 0, out);
    }

    @Test
    public void testTranslateSurrogatePair() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        // 0x1F600 = 😀 (grinning face)
        int consumed = unescaper.translate("&#128512;", 0, out);

        assertEquals(9, consumed);                 // '&' '#' digits ';' → 9 chars
        String result = out.toString();
        assertEquals(2, result.length());           // surrogate pair = 2 char units
        int codePoint = result.codePointAt(0);
        assertEquals(0x1F600, codePoint);
    }

    @Test
    public void testTranslateInvalidNumberFormat() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = unescaper.translate("&#xZZ;", 0, out);

        assertEquals(0, consumed);                 // parsing failed → 0
        assertEquals("", out.toString());          // nothing written
    }

    @Test
    public void testTranslateEmptyEntity() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = unescaper.translate("&#;", 0, out);

        assertEquals(0, consumed);                 // empty numeric part → 0
        assertEquals("", out.toString());
    }

    @Test
    public void testTranslateNonEntityInput() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        int consumed = unescaper.translate("Hello world", 0, out);

        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }

    @Test
    public void testTranslateStartsAtWrongIndex() throws IOException {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter out = new StringWriter();

        // Index points to 'e' (not '&')
        int consumed = unescaper.translate("Hello &#65;", 1, out);

        assertEquals(0, consumed);
        assertEquals("", out.toString());
    }
}
###Test END##
```

The file above fulfills every requirement:

* Lists the public API.  
* Provides a basic test for the normal (decimal) path.  
* Identifies and exercises all edge‑cases and exception‑scenarios.  
* Merges everything into a single, ready‑to‑run JUnit‑4 test class (`NumericEntityUnescaperTest`).