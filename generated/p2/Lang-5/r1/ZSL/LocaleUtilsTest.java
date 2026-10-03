/****************************************************************************************
 * LocaleUtilsTest.java
 *
 * Comprehensive JUnit 4 test suite for {@link org.apache.commons.lang3.LocaleUtils}.
 *
 * The tests cover:
 *   • {@code toLocale(String)} – valid inputs, null handling and all invalid‑format cases.
 *   • {@code localeLookupList(Locale)} and {@code localeLookupList(Locale,Locale)} –
 *     list ordering, handling of variants, default locale inclusion and null locale.
 *   • {@code availableLocaleList()} / {@code availableLocaleSet()} – content matches JDK,
 *     immutability, and set/list consistency.
 *   • {@code isAvailableLocale(Locale)} – true for a known locale, false for a fake one.
 *   • {@code languagesByCountry(String)} – correct filtering, null handling and caching.
 *   • {@code countriesByLanguage(String)} – correct filtering, null handling and caching.
 *
 * Run with JUnit 4 (e.g. via Maven Surefire or an IDE).
 *****************************************************************************************/

package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.Test;

/**
 * Test class for {@link LocaleUtils}.
 */
public class LocaleUtilsTest {

    // -------------------------------------------------------------------------
    // toLocale tests
    // -------------------------------------------------------------------------

    @Test
    public void testToLocale_NullInput() {
        assertNull(LocaleUtils.toLocale(null));
    }

    @Test
    public void testToLocale_TwoLetterLanguageOnly() {
        Locale loc = LocaleUtils.toLocale("en");
        assertEquals("en", loc.getLanguage());
        assertEquals("", loc.getCountry());
        assertEquals("", loc.getVariant());
    }

    @Test
    public void testToLocale_LanguageAndCountry() {
        Locale loc = LocaleUtils.toLocale("en_GB");
        assertEquals("en", loc.getLanguage());
        assertEquals("GB", loc.getCountry());
        assertEquals("", loc.getVariant());
    }

    @Test
    public void testToLocale_WithVariant() {
        Locale loc = LocaleUtils.toLocale("en_GB_xxx");
        assertEquals("en", loc.getLanguage());
        assertEquals("GB", loc.getCountry());
        assertEquals("xxx", loc.getVariant());
    }

    @Test
    public void testToLocale_InvalidFormats() {
        // helper array of invalid strings
        String[] invalid = {
                "",          // too short
                "e",         // too short
                "EN",        // upper case language
                "en_gb",     // lower case country
                "engb",      // missing separator
                "en_G",      // country too short
                "en_GBx",    // missing underscore before variant
                "en_1B",     // non‑letter in country
                "en_GB_",    // missing variant after underscore
                "en__GB",    // double underscore after language
                "en_GB_x"    // variant OK but length <7 (needs at least 7 inc underscores)
        };
        for (String s : invalid) {
            try {
                LocaleUtils.toLocale(s);
                fail("Expected IllegalArgumentException for: " + s);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    // -------------------------------------------------------------------------
    // localeLookupList tests
    // -------------------------------------------------------------------------

    @Test
    public void testLocaleLookupList_SimpleLocale() {
        Locale locale = new Locale("fr", "CA");
        List<Locale> list = LocaleUtils.localeLookupList(locale);
        // Expected order: fr_CA , fr
        assertEquals(2, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("fr", ""), list.get(1));
    }

    @Test
    public void testLocaleLookupList_WithVariantAndDefault() {
        Locale locale = new Locale("fr", "CA", "xx");
        Locale defaultLocale = Locale.ENGLISH; // en
        List<Locale> list = LocaleUtils.localeLookupList(locale, defaultLocale);
        // Expected: fr_CA_xx , fr_CA , fr , en
        assertEquals(4, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
        assertEquals(defaultLocale, list.get(3));
    }

    @Test
    public void testLocaleLookupList_DefaultAlreadyInList() {
        Locale locale = new Locale("en", "US");
        // defaultLocale same as locale → should not be duplicated
        List<Locale> list = LocaleUtils.localeLookupList(locale, locale);
        // en_US , en
        assertEquals(2, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("en", ""), list.get(1));
    }

    @Test
    public void testLocaleLookupList_NullLocale() {
        List<Locale> list = LocaleUtils.localeLookupList(null);
        assertTrue(list.isEmpty());

        List<Locale> list2 = LocaleUtils.localeLookupList(null, Locale.JAPANESE);
        // According to implementation, when locale == null the list stays empty,
        // defaultLocale is not added because the if‑block is skipped.
        assertTrue(list2.isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLocaleLookupList_Unmodifiable() {
        Locale locale = Locale.FRENCH;
        List<Locale> list = LocaleUtils.localeLookupList(locale);
        list.add(Locale.CHINESE); // should throw
    }

    // -------------------------------------------------------------------------
    // available locale list / set tests
    // -------------------------------------------------------------------------

    @Test
    public void testAvailableLocaleList_MatchesJDK() {
        List<Locale> fromUtils = LocaleUtils.availableLocaleList();
        Locale[] fromJDK = Locale.getAvailableLocales();

        // Content must be the same (order is guaranteed by the implementation)
        assertArrayEquals(fromJDK, fromUtils.toArray(new Locale[0]));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAvailableLocaleList_Unmodifiable() {
        LocaleUtils.availableLocaleList().add(Locale.ROOT);
    }

    @Test
    public void testAvailableLocaleSet_MatchesJDK() {
        Set<Locale> fromUtils = LocaleUtils.availableLocaleSet();
        Set<Locale> fromJDK = new HashSet<Locale>();
        fromJDK.addAll(List.of(Locale.getAvailableLocales()));
        assertEquals(fromJDK, fromUtils);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAvailableLocaleSet_Unmodifiable() {
        LocaleUtils.availableLocaleSet().add(Locale.ROOT);
    }

    @Test
    public void testAvailableLocaleListAndSet_Consistent() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        Set<Locale> set = LocaleUtils.availableLocaleSet();

        // All elements of the list must be present in the set
        assertTrue(set.containsAll(list));

        // The set should not contain any extra elements (size equality)
        assertEquals(list.size(), set.size());
    }

    // -------------------------------------------------------------------------
    // isAvailableLocale tests
    // -------------------------------------------------------------------------

    @Test
    public void testIsAvailableLocale_KnownLocale() {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
    }

    @Test
    public void testIsAvailableLocale_UnknownLocale() {
        // Construct a locale that is highly unlikely to be available.
        Locale fake = new Locale("xx", "YY", "ZZ");
        assertFalse(LocaleUtils.isAvailableLocale(fake));
    }

    // -------------------------------------------------------------------------
    // languagesByCountry tests
    // -------------------------------------------------------------------------

    @Test
    public void testLanguagesByCountry_NullParameter() {
        assertTrue(LocaleUtils.languagesByCountry(null).isEmpty());
    }

    @Test
    public void testLanguagesByCountry_FilteringAndCaching() {
        String country = "US";

        // First call – triggers population
        List<Locale> first = LocaleUtils.languagesByCountry(country);
        assertNotNull(first);
        assertFalse(first.isEmpty());

        // Every locale must have country US and empty variant
        for (Locale l : first) {
            assertEquals("Country code mismatch", country, l.getCountry());
            assertTrue("Variant should be empty", l.getVariant().isEmpty());
        }

        // Second call – should return the same (cached) unmodifiable list
        List<Locale> second = LocaleUtils.languagesByCountry(country);
        assertSame("Expected cached list instance", first, second);
    }

    // -------------------------------------------------------------------------
    // countriesByLanguage tests
    // -------------------------------------------------------------------------

    @Test
    public void testCountriesByLanguage_NullParameter() {
        assertTrue(LocaleUtils.countriesByLanguage(null).isEmpty());
    }

    @Test
    public void testCountriesByLanguage_FilteringAndCaching() {
        String language = "en";

        // First call – triggers population
        List<Locale> first = LocaleUtils.countriesByLanguage(language);
        assertNotNull(first);
        assertFalse(first.isEmpty());

        // Every locale must have language en, non‑empty country, and empty variant
        for (Locale l : first) {
            assertEquals("Language code mismatch", language, l.getLanguage());
            assertFalse("Country must not be empty", l.getCountry().isEmpty());
            assertTrue("Variant should be empty", l.getVariant().isEmpty());
        }

        // Second call – should return the same (cached) list
        List<Locale> second = LocaleUtils.countriesByLanguage(language);
        assertSame("Expected cached list instance", first, second);
    }

    // -------------------------------------------------------------------------
    // Additional edge‑case checks for variant handling in lookup list
    // -------------------------------------------------------------------------

    @Test
    public void testLocaleLookupList_VariantOnlyAddsLanguageAndDefault() {
        // Locale with language and variant but empty country
        Locale locale = new Locale("de", "", "POSIX");
        Locale defaultLocale = Locale.ITALIAN;
        List<Locale> list = LocaleUtils.localeLookupList(locale, defaultLocale);

        // Expected: de__POSIX (original), de (language only), default
        assertEquals(3, list.size());
        assertEquals(locale, list.get(0));
        // Since country is empty, there is no language‑country step.
        assertEquals(new Locale("de", ""), list.get(1));
        assertEquals(defaultLocale, list.get(2));
    }
}
