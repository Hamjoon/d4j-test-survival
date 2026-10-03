###Test START##
```java
package org.apache.commons.lang3;

import org.junit.Test;
import org.junit.Before;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * JUnit 4 test cases for {@link LocaleUtils}.
 */
public class LocaleUtilsTest {

    /* ----------------------------------------------------------------------
     *  Public method signatures extracted from LocaleUtils
     * ---------------------------------------------------------------------- */
    // public LocaleUtils()
    // public static Locale toLocale(String str)
    // public static List<Locale> localeLookupList(Locale locale)
    // public static List<Locale> localeLookupList(Locale locale, Locale defaultLocale)
    // public static List<Locale> availableLocaleList()
    // public static Set<Locale> availableLocaleSet()
    // public static boolean isAvailableLocale(Locale locale)
    // public static List<Locale> languagesByCountry(String countryCode)
    // public static List<Locale> countriesByLanguage(String languageCode)

    /* ----------------------------------------------------------------------
     *  Basic functionality tests
     * ---------------------------------------------------------------------- */

    @Test
    public void testConstructorIsPublic() throws Exception {
        LocaleUtils utils = new LocaleUtils(); // just ensure it can be instantiated
        assertNotNull(utils);
    }

    @Test
    public void testToLocaleSimpleLanguage() {
        Locale locale = LocaleUtils.toLocale("en");
        assertEquals("en", locale.getLanguage());
        assertEquals("", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testToLocaleLanguageAndCountry() {
        Locale locale = LocaleUtils.toLocale("en_GB");
        assertEquals("en", locale.getLanguage());
        assertEquals("GB", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testToLocaleFullWithVariant() {
        Locale locale = LocaleUtils.toLocale("en_GB_xxx");
        assertEquals("en", locale.getLanguage());
        assertEquals("GB", locale.getCountry());
        assertEquals("xxx", locale.getVariant());
    }

    @Test
    public void testToLocaleNullInput() {
        assertNull(LocaleUtils.toLocale(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleTooShort() {
        LocaleUtils.toLocale("a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleBadLanguageCase() {
        LocaleUtils.toLocale("EN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleMissingUnderscore() {
        LocaleUtils.toLocale("engb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleBadCountryCase() {
        LocaleUtils.toLocale("en_gb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleInvalidVariantFormat() {
        // length 6 is invalid because after language_country there must be '_' before variant
        LocaleUtils.toLocale("en_GBxx");
    }

    @Test
    public void testLocaleLookupListSingleArgument() {
        Locale start = new Locale("fr", "CA", "xxx");
        List<Locale> list = LocaleUtils.localeLookupList(start);
        assertEquals(3, list.size());
        assertEquals(start, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
    }

    @Test
    public void testLocaleLookupListWithDefault() {
        Locale start = new Locale("fr", "CA", "xxx");
        Locale defaultLocale = Locale.ENGLISH;
        List<Locale> list = LocaleUtils.localeLookupList(start, defaultLocale);
        assertEquals(4, list.size());
        assertEquals(start, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
        assertEquals(defaultLocale, list.get(3));
    }

    @Test
    public void testLocaleLookupListDefaultAlreadyPresent() {
        Locale start = new Locale("en", "US");
        // default locale is same as one of the generated entries (language only)
        List<Locale> list = LocaleUtils.localeLookupList(start, new Locale("en"));
        assertEquals(3, list.size());
        assertEquals(start, list.get(0));
        assertEquals(new Locale("en", ""), list.get(1));
        assertEquals(new Locale("en"), list.get(2)); // default added as language only
    }

    @Test
    public void testLocaleLookupListNullLocale() {
        List<Locale> list = LocaleUtils.localeLookupList(null);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testAvailableLocaleListIsUnmodifiable() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        try {
            list.add(Locale.CANADA);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        // verify that a known locale is present
        assertTrue(list.contains(Locale.US));
    }

    @Test
    public void testAvailableLocaleSetIsUnmodifiable() {
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        try {
            set.remove(Locale.US);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        assertTrue(set.contains(Locale.US));
    }

    @Test
    public void testIsAvailableLocale() {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
        // create a locale that is very unlikely to be available
        Locale unknown = new Locale("xx", "YY");
        assertFalse(LocaleUtils.isAvailableLocale(unknown));
    }

    @Test
    public void testLanguagesByCountryNull() {
        List<Locale> list = LocaleUtils.languagesByCountry(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLanguagesByCountryKnownCountry() {
        // Use a country that is guaranteed to exist in the JDK locales, e.g. "US"
        List<Locale> usLangs = LocaleUtils.languagesByCountry("US");
        assertNotNull(usLangs);
        // Every locale in the list must have country "US" and empty variant
        for (Locale loc : usLangs) {
            assertEquals("US", loc.getCountry());
            assertTrue(loc.getVariant().isEmpty());
        }
        // At least one language (English) should be present
        assertTrue(usLangs.contains(new Locale("en", "US")));
    }

    @Test
    public void testLanguagesByCountryCaching() {
        // First call populates cache
        List<Locale> first = LocaleUtils.languagesByCountry("FR");
        // Second call should retrieve same immutable list instance (cache hit)
        List<Locale> second = LocaleUtils.languagesByCountry("FR");
        assertSame(first, second);
    }

    @Test
    public void testCountriesByLanguageNull() {
        List<Locale> list = LocaleUtils.countriesByLanguage(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testCountriesByLanguageKnownLanguage() {
        // Use language "en" which exists for many countries
        List<Locale> enCountries = LocaleUtils.countriesByLanguage("en");
        assertNotNull(enCountries);
        // Every locale must have language "en", a non‑empty country and empty variant
        for (Locale loc : enCountries) {
            assertEquals("en", loc.getLanguage());
            assertFalse(loc.getCountry().isEmpty());
            assertTrue(loc.getVariant().isEmpty());
        }
        // At least one known country (US) should be present
        assertTrue(enCountries.contains(new Locale("en", "US")));
    }

    @Test
    public void testCountriesByLanguageCaching() {
        List<Locale> first = LocaleUtils.countriesByLanguage("de");
        List<Locale> second = LocaleUtils.countriesByLanguage("de");
        assertSame(first, second);
    }

    /* ----------------------------------------------------------------------
     *  Edge‑case tests for toLocale (invalid formats)
     * ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleEmptyString() {
        LocaleUtils.toLocale("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleOnlyUnderscore() {
        LocaleUtils.toLocale("_");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleLanguageTooLong() {
        // language part must be exactly 2 lower‑case letters
        LocaleUtils.toLocale("eng");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleCountryTooShort() {
        // language + '_' + country (must be 2 upper case letters)
        LocaleUtils.toLocale("en_G");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleCountryLowerCase() {
        LocaleUtils.toLocale("en_gb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleMissingVariantUnderscore() {
        // Variant present but missing '_' separator after country
        LocaleUtils.toLocale("en_GBxxx");
    }
}
```
###Test END##