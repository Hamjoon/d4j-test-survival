###Test START##
```java
package org.apache.commons.lang;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * JUnit‑4 test cases for {@link LocaleUtils}.
 *
 * The tests cover:
 * <ul>
 *   <li>All public methods and their basic functionality</li>
 *   <li>Edge‑case handling (nulls, invalid strings, empty results)</li>
 *   <li>Exception scenarios (IllegalArgumentException from {@code toLocale})</li>
 *   <li>Behaviour of the internal caches (languagesByCountry / countriesByLanguage)</li>
 *   <li>Immutability of the collections returned by the API</li>
 * </ul>
 */
public class LocaleUtilsTest {

    // -----------------------------------------------------------------------
    // 1.  toLocale(String)
    // -----------------------------------------------------------------------

    @Test
    public void testToLocale_TwoLetterLanguage() {
        Locale locale = LocaleUtils.toLocale("en");
        assertEquals("en", locale.getLanguage());
        assertEquals("", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testToLocale_LanguageAndCountry() {
        Locale locale = LocaleUtils.toLocale("en_GB");
        assertEquals("en", locale.getLanguage());
        assertEquals("GB", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testToLocale_WithVariant() {
        Locale locale = LocaleUtils.toLocale("en_GB_xxx");
        assertEquals("en", locale.getLanguage());
        assertEquals("GB", locale.getCountry());
        // Variant case is preserved (JDK‑4+ does not upper‑case it)
        assertEquals("xxx", locale.getVariant());
    }

    @Test
    public void testToLocale_NullInput() {
        assertNull(LocaleUtils.toLocale(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_EmptyString() {
        LocaleUtils.toLocale("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_OneChar() {
        LocaleUtils.toLocale("e");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_UpperCaseLanguage() {
        LocaleUtils.toLocale("EN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_LowerCaseCountry() {
        LocaleUtils.toLocale("en_gb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_MissingUnderscore() {
        LocaleUtils.toLocale("enGB");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidCountryChars() {
        LocaleUtils.toLocale("en_1B");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_TrailingUnderscore() {
        LocaleUtils.toLocale("en_GB_");
    }

    // -----------------------------------------------------------------------
    // 2.  localeLookupList(Locale)  &  localeLookupList(Locale, Locale)
    // -----------------------------------------------------------------------

    @Test
    public void testLocaleLookupList_Simple() {
        Locale locale = new Locale("fr", "CA", "xxx");
        List<Locale> list = LocaleUtils.localeLookupList(locale);
        assertEquals(3, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
    }

    @Test
    public void testLocaleLookupList_WithDefaultDifferent() {
        Locale locale = new Locale("fr", "CA", "xxx");
        Locale defaultLocale = new Locale("en", "US");
        List<Locale> list = LocaleUtils.localeLookupList(locale, defaultLocale);
        assertEquals(4, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
        assertEquals(defaultLocale, list.get(3));
    }

    @Test
    public void testLocaleLookupList_WithDefaultSameAsLocale() {
        Locale locale = new Locale("de", "DE");
        List<Locale> list = LocaleUtils.localeLookupList(locale, locale);
        // default should not be added a second time
        assertEquals(2, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("de", ""), list.get(1));
    }

    @Test
    public void testLocaleLookupList_NullLocale() {
        List<Locale> list = LocaleUtils.localeLookupList(null);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLocaleLookupList_NullDefault() {
        Locale locale = new Locale("es", "ES");
        List<Locale> list = LocaleUtils.localeLookupList(locale, null);
        // null default is added because it is not already present
        assertEquals(3, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("es", ""), list.get(1));
        assertNull(list.get(2));
    }

    // -----------------------------------------------------------------------
    // 3.  availableLocaleList()  &  availableLocaleSet()
    // -----------------------------------------------------------------------

    @Test
    public void testAvailableLocaleList_NotModifiable() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        try {
            list.add(Locale.CANADA);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testAvailableLocaleSet_NotModifiable() {
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        try {
            set.add(Locale.CANADA);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testAvailableLocaleSet_MatchesList() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        assertEquals(new HashSet<>(list), set);
    }

    // -----------------------------------------------------------------------
    // 4.  isAvailableLocale(Locale)
    // -----------------------------------------------------------------------

    @Test
    public void testIsAvailableLocale_KnownLocale() {
        // Locale.US is guaranteed to be present on any JDK
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
    }

    @Test
    public void testIsAvailableLocale_UnknownLocale() {
        Locale bogus = new Locale("xx", "YY");
        // Most JDKs do not provide such a locale
        assertFalse(LocaleUtils.isAvailableLocale(bogus));
    }

    // -----------------------------------------------------------------------
    // 5.  languagesByCountry(String)
    // -----------------------------------------------------------------------

    @Test
    public void testLanguagesByCountry_KnownCountry() {
        List<Locale> langs = LocaleUtils.languagesByCountry("US");
        assertNotNull(langs);
        assertFalse(langs.isEmpty());

        // All returned locales must have country "US" and no variant
        for (Locale l : langs) {
            assertEquals("US", l.getCountry());
            assertEquals("", l.getVariant());
        }
    }

    @Test
    public void testLanguagesByCountry_NullReturnsEmpty() {
        List<Locale> langs = LocaleUtils.languagesByCountry(null);
        assertNotNull(langs);
        assertTrue(langs.isEmpty());
    }

    @Test
    public void testLanguagesByCountry_UnknownCountryReturnsEmpty() {
        List<Locale> langs = LocaleUtils.languagesByCountry("ZZ");
        assertNotNull(langs);
        assertTrue(langs.isEmpty());
    }

    @Test
    public void testLanguagesByCountry_CacheBehaviour() {
        List<Locale> firstCall = LocaleUtils.languagesByCountry("US");
        List<Locale> secondCall = LocaleUtils.languagesByCountry("US");
        // The method caches the result; the same unmodifiable list instance should be returned
        assertSame(firstCall, secondCall);
    }

    // -----------------------------------------------------------------------
    // 6.  countriesByLanguage(String)
    // -----------------------------------------------------------------------

    @Test
    public void testCountriesByLanguage_KnownLanguage() {
        List<Locale> countries = LocaleUtils.countriesByLanguage("en");
        assertNotNull(countries);
        assertFalse(countries.isEmpty());

        // All returned locales must have language "en", a non‑empty country, and no variant
        for (Locale l : countries) {
            assertEquals("en", l.getLanguage());
            assertFalse(l.getCountry().isEmpty());
            assertEquals("", l.getVariant());
        }
    }

    @Test
    public void testCountriesByLanguage_NullReturnsEmpty() {
        List<Locale> countries = LocaleUtils.countriesByLanguage(null);
        assertNotNull(countries);
        assertTrue(countries.isEmpty());
    }

    @Test
    public void testCountriesByLanguage_UnknownLanguageReturnsEmpty() {
        List<Locale> countries = LocaleUtils.countriesByLanguage("zz");
        assertNotNull(countries);
        assertTrue(countries.isEmpty());
    }

    @Test
    public void testCountriesByLanguage_CacheBehaviour() {
        List<Locale> first = LocaleUtils.countriesByLanguage("en");
        List<Locale> second = LocaleUtils.countriesByLanguage("en");
        assertSame(first, second);
    }

    // -----------------------------------------------------------------------
    // 7.  Constructor (instantiation) – simple sanity check
    // -----------------------------------------------------------------------

    @Test
    public void testConstructorIsPublic() {
        // The class has a public no‑arg constructor; instantiate it to ensure it works.
        LocaleUtils utils = new LocaleUtils();
        assertNotNull(utils);
    }
}
```
###Test END##