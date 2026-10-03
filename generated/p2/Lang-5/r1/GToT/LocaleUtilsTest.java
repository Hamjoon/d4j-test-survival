package org.apache.commons.lang3;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * JUnit‑4 test suite for {@link LocaleUtils}.
 *
 * The suite covers:
 * <ul>
 *   <li>normal behaviour of each public method</li>
 *   <li>edge‑case handling (nulls, empty strings, unknown codes)</li>
 *   <li>exceptional paths (invalid locale strings, attempts to modify unmodifiable collections)</li>
 * </ul>
 *
 * The static caches inside {@link LocaleUtils} are cleared before each test
 * to guarantee test independence.
 */
public class LocaleUtilsTest {

    /** Clears the internal caches used by {@code languagesByCountry} and {@code countriesByLanguage}. */
    @Before
    public void clearCaches() throws Exception {
        // cLanguagesByCountry
        Field fLang = LocaleUtils.class.getDeclaredField("cLanguagesByCountry");
        fLang.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentMap<String, List<Locale>> mapLang =
                (java.util.concurrent.ConcurrentMap<String, List<Locale>>) fLang.get(null);
        mapLang.clear();

        // cCountriesByLanguage
        Field fCountry = LocaleUtils.class.getDeclaredField("cCountriesByLanguage");
        fCountry.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentMap<String, List<Locale>> mapCountry =
                (java.util.concurrent.ConcurrentMap<String, List<Locale>>) fCountry.get(null);
        mapCountry.clear();
    }

    // -----------------------------------------------------------------------
    // toLocale(String)
    // -----------------------------------------------------------------------

    @Test
    public void testToLocale_nullInput() {
        assertNull(LocaleUtils.toLocale(null));
    }

    @Test
    public void testToLocale_languageOnly() {
        Locale locale = LocaleUtils.toLocale("en");
        assertEquals("en", locale.getLanguage());
        assertEquals("", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testToLocale_languageAndCountry() {
        Locale locale = LocaleUtils.toLocale("en_GB");
        assertEquals("en", locale.getLanguage());
        assertEquals("GB", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testToLocale_languageCountryVariant() {
        Locale locale = LocaleUtils.toLocale("en_GB_xxx");
        assertEquals("en", locale.getLanguage());
        assertEquals("GB", locale.getCountry());
        assertEquals("xxx", locale.getVariant());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_tooShort() {
        LocaleUtils.toLocale("e"); // length < 2
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_invalidLanguageCase() {
        LocaleUtils.toLocale("EN"); // language must be lower case
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_missingUnderscore() {
        LocaleUtils.toLocale("engb"); // missing '_'
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_invalidCountryCase() {
        LocaleUtils.toLocale("en_gb"); // country must be upper case
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_invalidVariantSeparator() {
        LocaleUtils.toLocale("en_GBx"); // missing '_' before variant
    }

    // -----------------------------------------------------------------------
    // localeLookupList(Locale)  – overload that uses the same locale as default
    // -----------------------------------------------------------------------

    @Test
    public void testLocaleLookupList_singleArgument_null() {
        List<Locale> list = LocaleUtils.localeLookupList(null);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLocaleLookupList_singleArgument_simple() {
        Locale frCA = new Locale("fr", "CA");
        List<Locale> list = LocaleUtils.localeLookupList(frCA);
        assertEquals(3, list.size());
        assertEquals(frCA, list.get(0));
        assertEquals(new Locale("fr", ""), list.get(1));
        assertEquals(frCA, list.get(2)); // default is the same as the original locale
    }

    @Test
    public void testLocaleLookupList_singleArgument_withVariant() {
        Locale locale = new Locale("fr", "CA", "xxx");
        List<Locale> list = LocaleUtils.localeLookupList(locale);
        assertEquals(4, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
        assertEquals(locale, list.get(3)); // default = original locale, not duplicated
    }

    // -----------------------------------------------------------------------
    // localeLookupList(Locale, Locale)
    // -----------------------------------------------------------------------

    @Test
    public void testLocaleLookupList_twoArgs_nullLocale() {
        Locale defaultLocale = Locale.US;
        List<Locale> list = LocaleUtils.localeLookupList(null, defaultLocale);
        // when locale is null the method returns an empty list (default not added)
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLocaleLookupList_twoArgs_defaultIsAdded() {
        Locale locale = new Locale("de", "DE");
        Locale defaultLocale = Locale.US;
        List<Locale> list = LocaleUtils.localeLookupList(locale, defaultLocale);
        assertEquals(4, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("de", ""), list.get(1));
        assertEquals(defaultLocale, list.get(3));
    }

    @Test
    public void testLocaleLookupList_twoArgs_defaultEqualsLocale() {
        Locale locale = Locale.CANADA_FRENCH; // ("fr","CA")
        List<Locale> list = LocaleUtils.localeLookupList(locale, locale);
        // default should not be duplicated
        assertEquals(3, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("fr", ""), list.get(1));
        assertEquals(locale, list.get(2)); // default (same as locale) added as last element
    }

    // -----------------------------------------------------------------------
    // availableLocaleList() & availableLocaleSet()
    // -----------------------------------------------------------------------

    @Test
    public void testAvailableLocaleList_isUnmodifiable() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        try {
            list.add(Locale.ROOT);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }

    @Test
    public void testAvailableLocaleSet_isUnmodifiable() {
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        try {
            set.add(Locale.ROOT);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }

    @Test
    public void testAvailableLocaleList_andSet_consistency() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        assertEquals(list.size(), set.size());
        assertTrue(set.containsAll(list));
    }

    // -----------------------------------------------------------------------
    // isAvailableLocale(Locale)
    // -----------------------------------------------------------------------

    @Test
    public void testIsAvailableLocale_knownLocale() {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
    }

    @Test
    public void testIsAvailableLocale_unknownLocale() {
        Locale unknown = new Locale("xx", "YY");
        assertFalse(LocaleUtils.isAvailableLocale(unknown));
    }

    // -----------------------------------------------------------------------
    // languagesByCountry(String)
    // -----------------------------------------------------------------------

    @Test
    public void testLanguagesByCountry_null() {
        assertTrue(LocaleUtils.languagesByCountry(null).isEmpty());
    }

    @Test
    public void testLanguagesByCountry_knownCountry() {
        // Use a country that is guaranteed to exist on any JRE – e.g. "US"
        List<Locale> langs = LocaleUtils.languagesByCountry("US");
        assertFalse(langs.isEmpty());
        for (Locale l : langs) {
            assertEquals("US", l.getCountry());
            assertTrue(l.getVariant().isEmpty());
        }
        // Ensure the list is unmodifiable
        try {
            langs.add(Locale.US);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }

    @Test
    public void testLanguagesByCountry_unknownCountry() {
        List<Locale> langs = LocaleUtils.languagesByCountry("ZZ");
        assertTrue(langs.isEmpty());
    }

    // -----------------------------------------------------------------------
    // countriesByLanguage(String)
    // -----------------------------------------------------------------------

    @Test
    public void testCountriesByLanguage_null() {
        assertTrue(LocaleUtils.countriesByLanguage(null).isEmpty());
    }

    @Test
    public void testCountriesByLanguage_knownLanguage() {
        // "en" is present on every JRE
        List<Locale> countries = LocaleUtils.countriesByLanguage("en");
        assertFalse(countries.isEmpty());
        for (Locale l : countries) {
            assertEquals("en", l.getLanguage());
            assertTrue(l.getCountry().length() > 0);
            assertTrue(l.getVariant().isEmpty());
        }
        // Verify immutability
        try {
            countries.remove(0);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }

    @Test
    public void testCountriesByLanguage_unknownLanguage() {
        List<Locale> countries = LocaleUtils.countriesByLanguage("zz");
        assertTrue(countries.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Additional sanity checks for cache reuse (languagesByCountry & countriesByLanguage)
    // -----------------------------------------------------------------------

    @Test
    public void testLanguagesByCountry_cacheReuse() {
        List<Locale> first = LocaleUtils.languagesByCountry("US");
        List<Locale> second = LocaleUtils.languagesByCountry("US");
        assertSame("Cache should return the same unmodifiable list instance", first, second);
    }

    @Test
    public void testCountriesByLanguage_cacheReuse() {
        List<Locale> first = LocaleUtils.countriesByLanguage("en");
        List<Locale> second = LocaleUtils.countriesByLanguage("en");
        assertSame("Cache should return the same unmodifiable list instance", first, second);
    }
}
