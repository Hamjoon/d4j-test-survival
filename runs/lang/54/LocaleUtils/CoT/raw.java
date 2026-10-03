**1. Public methods extracted from `LocaleUtils`**

| # | Method signature |
|---|-------------------|
| 1 | `public static Locale toLocale(String str)` |
| 2 | `public static List localeLookupList(Locale locale)` |
| 3 | `public static List localeLookupList(Locale locale, Locale defaultLocale)` |
| 4 | `public static List availableLocaleList()` |
| 5 | `public static Set availableLocaleSet()` |
| 6 | `public static boolean isAvailableLocale(Locale locale)` |
| 7 | `public static List languagesByCountry(String countryCode)` |
| 8 | `public static List countriesByLanguage(String languageCode)` |

---

**2. Basic JUnit‑4 test cases (functionality checks)**  

Below are simple tests that verify the *happy‑path* behaviour of each method.

```java
@Test
public void testToLocaleSimple() {
    Locale en = LocaleUtils.toLocale("en");
    assertEquals("en", en.getLanguage());
    assertEquals("", en.getCountry());
    assertEquals("", en.getVariant());
}

@Test
public void testToLocaleWithCountry() {
    Locale enGB = LocaleUtils.toLocale("en_GB");
    assertEquals("en", enGB.getLanguage());
    assertEquals("GB", enGB.getCountry());
    assertEquals("", enGB.getVariant());
}

@Test
public void testToLocaleWithVariant() {
    Locale loc = LocaleUtils.toLocale("en_GB_xxx");
    assertEquals("en", loc.getLanguage());
    assertEquals("GB", loc.getCountry());
    assertEquals("xxx", loc.getVariant());
}
```

```java
@Test
public void testLocaleLookupListSingleArgument() {
    Locale frCA = new Locale("fr", "CA", "xxx");
    List list = LocaleUtils.localeLookupList(frCA);
    assertEquals(3, list.size());
    assertEquals(frCA, list.get(0));
    assertEquals(new Locale("fr", "CA"), list.get(1));
    assertEquals(new Locale("fr", ""), list.get(2));
}
```

```java
@Test
public void testLocaleLookupListTwoArguments() {
    Locale frCA = new Locale("fr", "CA", "xxx");
    Locale defaultLoc = Locale.ENGLISH;
    List list = LocaleUtils.localeLookupList(frCA, defaultLoc);
    assertEquals(4, list.size());
    assertEquals(frCA, list.get(0));
    assertEquals(new Locale("fr", "CA"), list.get(1));
    assertEquals(new Locale("fr", ""), list.get(2));
    assertEquals(defaultLoc, list.get(3));
}
```

```java
@Test
public void testAvailableLocaleListIsUnmodifiable() {
    List list = LocaleUtils.availableLocaleList();
    try {
        list.add(Locale.ROOT);
        fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
        // expected
    }
}
```

```java
@Test
public void testAvailableLocaleSetIsUnmodifiable() {
    Set set = LocaleUtils.availableLocaleSet();
    try {
        set.add(Locale.ROOT);
        fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
        // expected
    }
}
```

```java
@Test
public void testIsAvailableLocale() {
    assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
    assertFalse(LocaleUtils.isAvailableLocale(new Locale("xx", "YY")));
}
```

```java
@Test
public void testLanguagesByCountry() {
    List langs = LocaleUtils.languagesByCountry("US");
    assertNotNull(langs);
    // at least English should be present
    assertTrue(langs.contains(Locale.US));
}
```

```java
@Test
public void testCountriesByLanguage() {
    List countries = LocaleUtils.countriesByLanguage("en");
    assertNotNull(countries);
    // at least US and GB should be present
    assertTrue(countries.contains(Locale.US));
    assertTrue(countries.contains(Locale.UK));
}
```

---

**3. Edge‑case & exception scenarios that must be tested**

| Method | Edge cases / Exceptions |
|--------|--------------------------|
| `toLocale` | * `null` input → returns `null`<br>* Empty string → `IllegalArgumentException`<br>* Lengths other than 2,5,≥7 → `IllegalArgumentException`<br>* Invalid language case (uppercase) → `IllegalArgumentException`<br>* Invalid country case (lowercase) → `IllegalArgumentException`<br>* Missing underscores at required positions → `IllegalArgumentException` |
| `localeLookupList(Locale)` | * `null` argument → returns empty list (size 0) |
| `localeLookupList(Locale,Locale)` | * `locale == null` → empty list (size 0) irrespective of default<br>* `defaultLocale` already present in the generated list → should **not** be added twice |
| `availableLocaleList` / `availableLocaleSet` | * Verify that the returned collections are *truly* unmodifiable (attempt to `remove` as well) |
| `isAvailableLocale` | * Passing `null` → should return `false` (the implementation checks `contains(null)` which returns `false`) |
| `languagesByCountry` | * `null` argument → returns `Collections.EMPTY_LIST` (size 0)<br>* Unknown country code → returns empty list<br>* Call twice with same argument → same cached list instance (reference equality) |
| `countriesByLanguage` | * `null` argument → empty list<br>* Unknown language code → empty list<br>* Call twice → same cached list instance |

---

**4. Detailed JUnit‑4 test cases for the identified edge cases**

```java
// -------------- toLocale edge cases -----------------
@Test
public void testToLocaleNull() {
    assertNull(LocaleUtils.toLocale(null));
}

@Test(expected = IllegalArgumentException.class)
public void testToLocaleEmptyString() {
    LocaleUtils.toLocale("");
}

@Test(expected = IllegalArgumentException.class)
public void testToLocaleInvalidLength() {
    LocaleUtils.toLocale("e");          // length 1
    LocaleUtils.toLocale("engb");       // length 4
    LocaleUtils.toLocale("en_G");       // length 4 (invalid)
}

@Test(expected = IllegalArgumentException.class)
public void testToLocaleInvalidLanguageCase() {
    LocaleUtils.toLocale("EN");
}

@Test(expected = IllegalArgumentException.class)
public void testToLocaleInvalidCountryCase() {
    LocaleUtils.toLocale("en_gb");
}

@Test(expected = IllegalArgumentException.class)
public void testToLocaleMissingUnderscore() {
    LocaleUtils.toLocale("enGB");
}

@Test(expected = IllegalArgumentException.class)
public void testToLocaleMissingSecondUnderscoreForVariant() {
    LocaleUtils.toLocale("en_GBxxx");   // should be en_GB_xxx
}
```

```java
// -------------- localeLookupList(Locale) edge case -----------------
@Test
public void testLocaleLookupListWithNull() {
    List list = LocaleUtils.localeLookupList((Locale) null);
    assertNotNull(list);
    assertTrue(list.isEmpty());
}
```

```java
// -------------- localeLookupList(Locale,Locale) edge cases -----------------
@Test
public void testLocaleLookupListTwoArgsNullLocale() {
    List list = LocaleUtils.localeLookupList(null, Locale.CANADA);
    assertNotNull(list);
    assertTrue(list.isEmpty());   // default is ignored when locale is null
}

@Test
public void testLocaleLookupListDefaultAlreadyInList() {
    Locale loc = new Locale("fr", "FR");
    List list = LocaleUtils.localeLookupList(loc, loc);
    // default locale should NOT be added a second time
    assertEquals(2, list.size()); // fr_FR and fr (no duplicate)
    assertEquals(loc, list.get(0));
    assertEquals(new Locale("fr", ""), list.get(1));
}
```

```java
// -------------- availableLocaleList / Set immutability -----------------
@Test(expected = UnsupportedOperationException.class)
public void testAvailableLocaleListRemove() {
    LocaleUtils.availableLocaleList().remove(0);
}

@Test(expected = UnsupportedOperationException.class)
public void testAvailableLocaleSetRemove() {
    LocaleUtils.availableLocaleSet().remove(Locale.US);
}
```

```java
// -------------- isAvailableLocale edge case -----------------
@Test
public void testIsAvailableLocaleNull() {
    assertFalse(LocaleUtils.isAvailableLocale(null));
}
```

```java
// -------------- languagesByCountry edge cases -----------------
@Test
public void testLanguagesByCountryNull() {
    List list = LocaleUtils.languagesByCountry(null);
    assertNotNull(list);
    assertTrue(list.isEmpty());
    // should be the canonical EMPTY_LIST instance
    assertSame(Collections.EMPTY_LIST, list);
}

@Test
public void testLanguagesByCountryUnknownCountry() {
    List list = LocaleUtils.languagesByCountry("ZZ"); // unlikely to exist
    assertNotNull(list);
    assertTrue(list.isEmpty());
}

@Test
public void testLanguagesByCountryCaching() {
    List first = LocaleUtils.languagesByCountry("US");
    List second = LocaleUtils.languagesByCountry("US");
    assertSame(first, second); // same cached reference
}
```

```java
// -------------- countriesByLanguage edge cases -----------------
@Test
public void testCountriesByLanguageNull() {
    List list = LocaleUtils.countriesByLanguage(null);
    assertNotNull(list);
    assertTrue(list.isEmpty());
    assertSame(Collections.EMPTY_LIST, list);
}

@Test
public void testCountriesByLanguageUnknownLanguage() {
    List list = LocaleUtils.countriesByLanguage("zz");
    assertNotNull(list);
    assertTrue(list.isEmpty());
}

@Test
public void testCountriesByLanguageCaching() {
    List first = LocaleUtils.countriesByLanguage("en");
    List second = LocaleUtils.countriesByLanguage("en");
    assertSame(first, second);
}
```

---

**5. Complete JUnit‑4 test file (`LocaleUtilsTest.java`)**

```java
###Test START##
package org.apache.commons.lang;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * JUnit4 test suite for {@link LocaleUtils}.
 *
 * The tests cover normal functionality, edge‑cases and exception handling.
 */
public class LocaleUtilsTest {

    // -----------------------------------------------------------------
    // 1. toLocale – normal cases
    // -----------------------------------------------------------------
    @Test
    public void testToLocaleSimple() {
        Locale en = LocaleUtils.toLocale("en");
        assertEquals("en", en.getLanguage());
        assertEquals("", en.getCountry());
        assertEquals("", en.getVariant());
    }

    @Test
    public void testToLocaleWithCountry() {
        Locale enGB = LocaleUtils.toLocale("en_GB");
        assertEquals("en", enGB.getLanguage());
        assertEquals("GB", enGB.getCountry());
        assertEquals("", enGB.getVariant());
    }

    @Test
    public void testToLocaleWithVariant() {
        Locale loc = LocaleUtils.toLocale("en_GB_xxx");
        assertEquals("en", loc.getLanguage());
        assertEquals("GB", loc.getCountry());
        assertEquals("xxx", loc.getVariant());
    }

    // -----------------------------------------------------------------
    // 2. toLocale – edge / exception cases
    // -----------------------------------------------------------------
    @Test
    public void testToLocaleNull() {
        assertNull(LocaleUtils.toLocale(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleEmptyString() {
        LocaleUtils.toLocale("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleInvalidLength() {
        LocaleUtils.toLocale("e");          // length 1
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleInvalidLengthFour() {
        LocaleUtils.toLocale("engb");       // length 4
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleInvalidLengthFour2() {
        LocaleUtils.toLocale("en_G");       // length 4, missing country part
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleInvalidLanguageCase() {
        LocaleUtils.toLocale("EN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleInvalidCountryCase() {
        LocaleUtils.toLocale("en_gb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleMissingUnderscore() {
        LocaleUtils.toLocale("enGB");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleMissingSecondUnderscoreForVariant() {
        LocaleUtils.toLocale("en_GBxxx");   // should be en_GB_xxx
    }

    // -----------------------------------------------------------------
    // 3. localeLookupList(Locale) – normal & edge cases
    // -----------------------------------------------------------------
    @Test
    public void testLocaleLookupListSingleArgument() {
        Locale frCA = new Locale("fr", "CA", "xxx");
        List list = LocaleUtils.localeLookupList(frCA);
        assertEquals(3, list.size());
        assertEquals(frCA, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
    }

    @Test
    public void testLocaleLookupListWithNull() {
        List list = LocaleUtils.localeLookupList((Locale) null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    // -----------------------------------------------------------------
    // 4. localeLookupList(Locale,Locale) – normal & edge cases
    // -----------------------------------------------------------------
    @Test
    public void testLocaleLookupListTwoArguments() {
        Locale frCA = new Locale("fr", "CA", "xxx");
        Locale defaultLoc = Locale.ENGLISH;
        List list = LocaleUtils.localeLookupList(frCA, defaultLoc);
        assertEquals(4, list.size());
        assertEquals(frCA, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
        assertEquals(defaultLoc, list.get(3));
    }

    @Test
    public void testLocaleLookupListTwoArgsNullLocale() {
        List list = LocaleUtils.localeLookupList(null, Locale.CANADA);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLocaleLookupListDefaultAlreadyInList() {
        Locale loc = new Locale("fr", "FR");
        List list = LocaleUtils.localeLookupList(loc, loc);
        // default locale should NOT be duplicated
        assertEquals(2, list.size());
        assertEquals(loc, list.get(0));
        assertEquals(new Locale("fr", ""), list.get(1));
    }

    // -----------------------------------------------------------------
    // 5. availableLocaleList / Set – immutability
    // -----------------------------------------------------------------
    @Test
    public void testAvailableLocaleListIsUnmodifiable() {
        List list = LocaleUtils.availableLocaleList();
        try {
            list.add(Locale.ROOT);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAvailableLocaleListRemove() {
        LocaleUtils.availableLocaleList().remove(0);
    }

    @Test
    public void testAvailableLocaleSetIsUnmodifiable() {
        Set set = LocaleUtils.availableLocaleSet();
        try {
            set.add(Locale.ROOT);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAvailableLocaleSetRemove() {
        LocaleUtils.availableLocaleSet().remove(Locale.US);
    }

    // -----------------------------------------------------------------
    // 6. isAvailableLocale – normal & edge
    // -----------------------------------------------------------------
    @Test
    public void testIsAvailableLocale() {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
        assertFalse(LocaleUtils.isAvailableLocale(new Locale("xx", "YY")));
    }

    @Test
    public void testIsAvailableLocaleNull() {
        assertFalse(LocaleUtils.isAvailableLocale(null));
    }

    // -----------------------------------------------------------------
    // 7. languagesByCountry – normal & edge & caching
    // -----------------------------------------------------------------
    @Test
    public void testLanguagesByCountryNull() {
        List list = LocaleUtils.languagesByCountry(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
        assertSame(Collections.EMPTY_LIST, list);
    }

    @Test
    public void testLanguagesByCountryUnknownCountry() {
        List list = LocaleUtils.languagesByCountry("ZZ");
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLanguagesByCountryCaching() {
        List first = LocaleUtils.languagesByCountry("US");
        List second = LocaleUtils.languagesByCountry("US");
        assertSame(first, second);
    }

    @Test
    public void testLanguagesByCountryContainsExpectedLocales() {
        List usLangs = LocaleUtils.languagesByCountry("US");
        assertTrue(usLangs.contains(Locale.US)); // en_US is a standard locale
    }

    // -----------------------------------------------------------------
    // 8. countriesByLanguage – normal & edge & caching
    // -----------------------------------------------------------------
    @Test
    public void testCountriesByLanguageNull() {
        List list = LocaleUtils.countriesByLanguage(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
        assertSame(Collections.EMPTY_LIST, list);
    }

    @Test
    public void testCountriesByLanguageUnknownLanguage() {
        List list = LocaleUtils.countriesByLanguage("zz");
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testCountriesByLanguageCaching()