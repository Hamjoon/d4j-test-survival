###Test START##
```java
package org.apache.commons.lang;

import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Comprehensive JUnit‑4 test suite for {@link LocaleUtils}.
 *
 * The tests are grouped into three logical phases that mirror the
 * “expert” workflow described in the task:
 *
 * 1. **Extraction & basic verification** – one test per public method that
 *    checks the normal, documented behaviour.
 * 2. **Edge‑case & exception analysis** – tests that provoke the
 *    documented error conditions (nulls, malformed strings, etc.).
 * 3. **Integration & caching checks** – tests that verify the
 *    interaction between methods (e.g. caching in the static maps) and
 *    that the returned collections are truly unmodifiable.
 *
 * All tests are written for JUnit 4 and use only the public API of
 * {@code LocaleUtils}; no reflection is required.
 */
public class LocaleUtilsTest {

    /* -----------------------------------------------------------------
     *  1.  Basic functionality tests
     * ----------------------------------------------------------------- */

    // ----- toLocale(String) -------------------------------------------------
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
    public void testToLocaleWithVariant() {
        Locale locale = LocaleUtils.toLocale("en_GB_xxx");
        assertEquals("en", locale.getLanguage());
        assertEquals("GB", locale.getCountry());
        assertEquals("xxx", locale.getVariant());
    }

    @Test
    public void testToLocaleNullInput() {
        assertNull(LocaleUtils.toLocale(null));
    }

    // ----- localeLookupList(Locale) -----------------------------------------
    @Test
    public void testLocaleLookupListSimple() {
        Locale frCaX = new Locale("fr", "CA", "xxx");
        List<Locale> list = LocaleUtils.localeLookupList(frCaX);
        assertEquals(3, list.size());
        assertEquals(frCaX, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
    }

    @Test
    public void testLocaleLookupListWithDefault() {
        Locale fr = new Locale("fr", "CA", "xxx");
        Locale defaultLocale = Locale.ENGLISH;
        List<Locale> list = LocaleUtils.localeLookupList(fr, defaultLocale);
        assertEquals(4, list.size());
        assertEquals(defaultLocale, list.get(3));
    }

    @Test
    public void testLocaleLookupListNullLocale() {
        List<Locale> list = LocaleUtils.localeLookupList(null);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLocaleLookupListDefaultAlreadyPresent() {
        Locale locale = new Locale("de", "DE");
        List<Locale> list = LocaleUtils.localeLookupList(locale, locale);
        // default locale should not be added twice
        assertEquals(2, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("de", ""), list.get(1));
    }

    // ----- availableLocaleList() --------------------------------------------
    @Test
    public void testAvailableLocaleListIsUnmodifiable() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        try {
            list.add(Locale.ROOT);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        // the returned list must be the same instance on repeated calls
        assertSame(list, LocaleUtils.availableLocaleList());
    }

    // ----- availableLocaleSet() ---------------------------------------------
    @Test
    public void testAvailableLocaleSetIsUnmodifiable() {
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        try {
            set.add(Locale.ROOT);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        // the returned set must be the same instance on repeated calls
        assertSame(set, LocaleUtils.availableLocaleSet());
    }

    // ----- isAvailableLocale(Locale) ----------------------------------------
    @Test
    public void testIsAvailableLocaleKnown() {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
    }

    @Test
    public void testIsAvailableLocaleUnknown() {
        Locale unknown = new Locale("xx", "YY");
        assertFalse(LocaleUtils.isAvailableLocale(unknown));
    }

    // ----- languagesByCountry(String) ----------------------------------------
    @Test
    public void testLanguagesByCountryKnown() {
        // "US" is guaranteed to be present on any JDK
        List<Locale> usLangs = LocaleUtils.languagesByCountry("US");
        assertFalse(usLangs.isEmpty());
        for (Locale l : usLangs) {
            assertEquals("US", l.getCountry());
            assertEquals("", l.getVariant());
        }
    }

    @Test
    public void testLanguagesByCountryNull() {
        List<Locale> result = LocaleUtils.languagesByCountry(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testLanguagesByCountryCaching() {
        List<Locale> first = LocaleUtils.languagesByCountry("GB");
        List<Locale> second = LocaleUtils.languagesByCountry("GB");
        // The method caches the result – the same object should be returned
        assertSame(first, second);
    }

    // ----- countriesByLanguage(String) ---------------------------------------
    @Test
    public void testCountriesByLanguageKnown() {
        // "en" is guaranteed to be present on any JDK
        List<Locale> enCountries = LocaleUtils.countriesByLanguage("en");
        assertFalse(enCountries.isEmpty());
        for (Locale l : enCountries) {
            assertEquals("en", l.getLanguage());
            assertTrue(l.getCountry().length() > 0);
            assertEquals("", l.getVariant());
        }
    }

    @Test
    public void testCountriesByLanguageNull() {
        List<Locale> result = LocaleUtils.countriesByLanguage(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testCountriesByLanguageCaching() {
        List<Locale> first = LocaleUtils.countriesByLanguage("fr");
        List<Locale> second = LocaleUtils.countriesByLanguage("fr");
        assertSame(first, second);
    }

    /* -----------------------------------------------------------------
     *  2.  Edge‑case & exception tests for toLocale(String)
     * ----------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleTooShort() {
        LocaleUtils.toLocale("e");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleInvalidLength() {
        LocaleUtils.toLocale("en_US_extra");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleUpperCaseLanguage() {
        LocaleUtils.toLocale("EN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleLowerCaseCountry() {
        LocaleUtils.toLocale("en_gb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleMissingUnderscore() {
        LocaleUtils.toLocale("enGB");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocaleBadVariantSeparator() {
        LocaleUtils.toLocale("en_GB-xxx");
    }

    @Test
    public void testToLocaleVariantPreservesCase() {
        // JDK 1.4+ does not change case of the variant; the test documents that.
        Locale locale = LocaleUtils.toLocale("en_GB_xXx");
        assertEquals("xXx", locale.getVariant());
    }

    /* -----------------------------------------------------------------
     *  3.  Integration / collection‑immutability tests
     * ----------------------------------------------------------------- */

    @Test
    public void testLocaleLookupListIsUnmodifiable() {
        Locale locale = new Locale("es", "MX");
        List<Locale> list = LocaleUtils.localeLookupList(locale);
        try {
            list.add(Locale.ROOT);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ok) {
            // pass
        }
    }

    @Test
    public void testAvailableLocaleListContainsAllSetElements() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        assertTrue(set.containsAll(list));
        assertTrue(list.containsAll(set));
    }

    @Test
    public void testAvailableLocaleSetSingletonBehavior() {
        Set<Locale> first = LocaleUtils.availableLocaleSet();
        Set<Locale> second = LocaleUtils.availableLocaleSet();
        assertSame(first, second);
    }

    // Clean‑up: reset the static caches after all tests to avoid cross‑test
    // contamination when the test suite is run repeatedly in the same JVM.
    @AfterClass
    public static void clearCaches() throws Exception {
        // Reflection is used only for test cleanup; production code does not
        // depend on it.
        java.lang.reflect.Field langField = LocaleUtils.class.getDeclaredField("cLanguagesByCountry");
        java.lang.reflect.Field countryField = LocaleUtils.class.getDeclaredField("cCountriesByLanguage");
        java.lang.reflect.Field setField = LocaleUtils.class.getDeclaredField("cAvailableLocaleSet");

        langField.setAccessible(true);
        countryField.setAccessible(true);
        setField.setAccessible(true);

        ((Map) langField.get(null)).clear();
        ((Map) countryField.get(null)).clear();
        setField.set(null, null);
    }
}
```
###Test END##