package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.Test;

/**
 * Comprehensive JUnit4 test suite for {@link LocaleUtils}.
 */
public class LocaleUtilsTest {

    // -----------------------------------------------------------------------
    // toLocale(String)
    // -----------------------------------------------------------------------

    @Test
    public void testToLocale_NullInput() {
        assertNull(LocaleUtils.toLocale(null));
    }

    @Test
    public void testToLocale_LanguageOnly() {
        Locale l = LocaleUtils.toLocale("en");
        assertEquals("en", l.getLanguage());
        assertEquals("", l.getCountry());
        assertEquals("", l.getVariant());
    }

    @Test
    public void testToLocale_LanguageCountry() {
        Locale l = LocaleUtils.toLocale("en_GB");
        assertEquals("en", l.getLanguage());
        assertEquals("GB", l.getCountry());
        assertEquals("", l.getVariant());
    }

    @Test
    public void testToLocale_LanguageCountryVariant() {
        Locale l = LocaleUtils.toLocale("en_GB_xyz");
        assertEquals("en", l.getLanguage());
        assertEquals("GB", l.getCountry());
        assertEquals("xyz", l.getVariant());
    }

    @Test
    public void testToLocale_LanguageEmptyCountryVariant() {
        // format en__VAR (double underscore) -> country empty, variant supplied
        Locale l = LocaleUtils.toLocale("en__myVar");
        assertEquals("en", l.getLanguage());
        assertEquals("", l.getCountry());
        assertEquals("myVar", l.getVariant());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_TooShort() {
        LocaleUtils.toLocale("e");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_LanguageNotLowerCase() {
        LocaleUtils.toLocale("EN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_NoUnderscoreBetweenLanguageCountry() {
        LocaleUtils.toLocale("engb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_CountryNotUpperCase() {
        LocaleUtils.toLocale("en_gb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_MissingVariantAfterUnderscore() {
        // length 6 = "en_GB_" (missing variant)
        LocaleUtils.toLocale("en_GB_");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_EmptyString() {
        LocaleUtils.toLocale("");
    }

    // -----------------------------------------------------------------------
    // localeLookupList(Locale)
    // -----------------------------------------------------------------------

    @Test
    public void testLocaleLookupList_Simple() {
        Locale start = new Locale("de");
        List<Locale> list = LocaleUtils.localeLookupList(start);
        assertEquals(2, list.size()); // de + default (null default => not added)
        assertEquals(start, list.get(0));
        assertEquals(new Locale("de", ""), list.get(1)); // language only after removing country (none)
    }

    @Test
    public void testLocaleLookupList_WithCountryAndVariant() {
        Locale start = new Locale("fr", "CA", "xxx");
        List<Locale> list = LocaleUtils.localeLookupList(start);
        assertEquals(3, list.size());
        assertEquals(start, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
    }

    @Test
    public void testLocaleLookupList_WithDefaultLocale() {
        Locale start = new Locale("en", "US");
        Locale defaultL = Locale.FRANCE; // fr_FR
        List<Locale> list = LocaleUtils.localeLookupList(start, defaultL);
        // Expected order: en_US, en, fr_FR
        assertEquals(3, list.size());
        assertEquals(start, list.get(0));
        assertEquals(new Locale("en", ""), list.get(1));
        assertEquals(defaultL, list.get(2));
    }

    @Test
    public void testLocaleLookupList_DefaultSameAsExisting() {
        Locale start = new Locale("en");
        // default locale is the same as the language‑only locale that will be added
        List<Locale> list = LocaleUtils.localeLookupList(start, start);
        // Should not contain duplicate entry
        assertEquals(2, list.size());
        assertEquals(start, list.get(0));
        assertEquals(new Locale("en", ""), list.get(1));
    }

    @Test
    public void testLocaleLookupList_NullLocale() {
        List<Locale> list = LocaleUtils.localeLookupList(null);
        assertTrue(list.isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLocaleLookupList_Unmodifiable() {
        Locale start = new Locale("en", "GB");
        List<Locale> list = LocaleUtils.localeLookupList(start);
        list.add(Locale.CANADA);
    }

    // -----------------------------------------------------------------------
    // localeLookupList(Locale, Locale)
    // -----------------------------------------------------------------------

    @Test
    public void testLocaleLookupList_TwoArg_NullDefault() {
        Locale start = new Locale("es", "MX");
        List<Locale> list = LocaleUtils.localeLookupList(start, null);
        // Should contain start, language‑only (es)
        assertEquals(2, list.size());
        assertEquals(start, list.get(0));
        assertEquals(new Locale("es", ""), list.get(1));
    }

    // -----------------------------------------------------------------------
    // availableLocaleList() & availableLocaleSet()
    // -----------------------------------------------------------------------

    @Test
    public void testAvailableLocaleList_NotEmpty() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        assertNotNull(list);
        assertFalse(list.isEmpty());
        // compare with JDK's list
        List<Locale> jdk = java.util.Arrays.asList(Locale.getAvailableLocales());
        assertTrue(jdk.containsAll(list));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAvailableLocaleList_Unmodifiable() {
        LocaleUtils.availableLocaleList().add(Locale.ROOT);
    }

    @Test
    public void testAvailableLocaleSet_NotEmpty() {
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        assertNotNull(set);
        assertFalse(set.isEmpty());
        // ensure every element of the set is among JDK's locales
        for (Locale l : set) {
            assertTrue(java.util.Arrays.asList(Locale.getAvailableLocales()).contains(l));
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAvailableLocaleSet_Unmodifiable() {
        LocaleUtils.availableLocaleSet().add(new Locale("xx", "YY"));
    }

    // -----------------------------------------------------------------------
    // isAvailableLocale(Locale)
    // -----------------------------------------------------------------------

    @Test
    public void testIsAvailableLocale_KnownLocale() {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
    }

    @Test
    public void testIsAvailableLocale_UnknownLocale() {
        Locale fake = new Locale("zz", "ZZ");
        assertFalse(LocaleUtils.isAvailableLocale(fake));
    }

    // -----------------------------------------------------------------------
    // languagesByCountry(String)
    // -----------------------------------------------------------------------

    @Test
    public void testLanguagesByCountry_Null() {
        assertTrue(LocaleUtils.languagesByCountry(null).isEmpty());
    }

    @Test
    public void testLanguagesByCountry_KnownCountry() {
        List<Locale> list = LocaleUtils.languagesByCountry("US");
        assertNotNull(list);
        assertFalse(list.isEmpty());
        // each locale must have country US and no variant
        for (Locale l : list) {
            assertEquals("US", l.getCountry());
            assertEquals("", l.getVariant());
        }
        // caching: second call returns the same list instance
        List<Locale> list2 = LocaleUtils.languagesByCountry("US");
        assertSame(list, list2);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLanguagesByCountry_Unmodifiable() {
        LocaleUtils.languagesByCountry("US").add(Locale.ROOT);
    }

    // -----------------------------------------------------------------------
    // countriesByLanguage(String)
    // -----------------------------------------------------------------------

    @Test
    public void testCountriesByLanguage_Null() {
        assertTrue(LocaleUtils.countriesByLanguage(null).isEmpty());
    }

    @Test
    public void testCountriesByLanguage_KnownLanguage() {
        List<Locale> list = LocaleUtils.countriesByLanguage("en");
        assertNotNull(list);
        assertFalse(list.isEmpty());
        for (Locale l : list) {
            assertEquals("en", l.getLanguage());
            assertFalse(l.getCountry().isEmpty());
            assertEquals("", l.getVariant());
        }
        // caching verification
        List<Locale> list2 = LocaleUtils.countriesByLanguage("en");
        assertSame(list, list2);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCountriesByLanguage_Unmodifiable() {
        LocaleUtils.countriesByLanguage("en").add(Locale.ROOT);
    }
}
