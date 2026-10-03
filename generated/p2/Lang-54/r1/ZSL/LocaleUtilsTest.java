/*
 * JUnit 4 test cases for {@link org.apache.commons.lang.LocaleUtils}.
 *
 * These tests cover all public static methods, checking normal operation,
 * edge cases, exception handling and the immutability / caching behaviour
 * of the utility class.
 */
package org.apache.commons.lang;

import org.junit.Test;
import org.junit.BeforeClass;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.*;

public class LocaleUtilsTest {

    // -----------------------------------------------------------------------
    // toLocale tests
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
    public void testToLocale_withVariant() {
        Locale locale = LocaleUtils.toLocale("fr_CA_xxx");
        assertEquals("fr", locale.getLanguage());
        assertEquals("CA", locale.getCountry());
        assertEquals("xxx", locale.getVariant());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_invalidLength_oneChar() {
        LocaleUtils.toLocale("e");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_invalidLength_threeChars() {
        LocaleUtils.toLocale("eng");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_invalidLanguageUpperCase() {
        LocaleUtils.toLocale("En");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_invalidCountryLowerCase() {
        LocaleUtils.toLocale("en_gb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_missingUnderscore() {
        LocaleUtils.toLocale("engb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_missingSecondUnderscoreForVariant() {
        LocaleUtils.toLocale("en_GBx");
    }

    // -----------------------------------------------------------------------
    // localeLookupList tests (both overloads)
    // -----------------------------------------------------------------------
    @Test
    public void testLocaleLookupList_nullLocale() {
        List list = LocaleUtils.localeLookupList(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLocaleLookupList_simpleLocale() {
        Locale locale = new Locale("de", "DE");
        List list = LocaleUtils.localeLookupList(locale);
        assertEquals(3, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("de", ""), list.get(1));
        // default locale is added as the third element (same as passed locale)
        assertEquals(Locale.getDefault(), list.get(2));
    }

    @Test
    public void testLocaleLookupList_withVariantAndDefault() {
        Locale specific = new Locale("fr", "CA", "xxx");
        Locale defaultLoc = Locale.CANADA_FRENCH;
        List list = LocaleUtils.localeLookupList(specific, defaultLoc);
        assertEquals(4, list.size());
        assertEquals(specific, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1)); // without variant
        assertEquals(new Locale("fr", ""), list.get(2));   // language only
        assertEquals(defaultLoc, list.get(3));            // default supplied
    }

    @Test
    public void testLocaleLookupList_defaultAlreadyInList() {
        Locale locale = new Locale("it", "IT");
        List list = LocaleUtils.localeLookupList(locale, locale);
        // default (=locale) should not be added twice
        assertEquals(3, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("it", ""), list.get(1));
        assertEquals(locale, list.get(2));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLocaleLookupList_isUnmodifiable() {
        Locale locale = new Locale("es", "ES");
        List list = LocaleUtils.localeLookupList(locale);
        list.add(Locale.JAPAN); // should throw
    }

    // -----------------------------------------------------------------------
    // availableLocaleList / Set tests
    // -----------------------------------------------------------------------
    @Test
    public void testAvailableLocaleList_isUnmodifiableAndContainsKnownLocale() {
        List list = LocaleUtils.availableLocaleList();
        assertNotNull(list);
        assertFalse(list.isEmpty());
        assertTrue(list.contains(Locale.US));

        try {
            list.add(Locale.CHINA);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }

    @Test
    public void testAvailableLocaleSet_isSingletonAndUnmodifiable() {
        Set set1 = LocaleUtils.availableLocaleSet();
        Set set2 = LocaleUtils.availableLocaleSet();

        // The method should cache the set and return the same instance
        assertSame(set1, set2);
        assertTrue(set1.contains(Locale.US));

        try {
            set1.add(Locale.CANADA);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }

    @Test
    public void testIsAvailableLocale() {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
        assertTrue(LocaleUtils.isAvailableLocale(Locale.ROOT)); // always present
        // Build a locale that is very unlikely to exist
        Locale bogus = new Locale("zz", "ZZ");
        // It may exist on some exotic JDKs, so we test the method's contract
        // by checking that the result matches the list content
        boolean inList = LocaleUtils.availableLocaleList().contains(bogus);
        assertEquals(inList, LocaleUtils.isAvailableLocale(bogus));
    }

    // -----------------------------------------------------------------------
    // languagesByCountry tests
    // -----------------------------------------------------------------------
    @Test
    public void testLanguagesByCountry_nullInput() {
        List list = LocaleUtils.languagesByCountry(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLanguagesByCountry_knownCountry() {
        // Use "US" – most JDKs have a handful of locales for this country.
        List usLangs = LocaleUtils.languagesByCountry("US");
        assertNotNull(usLangs);
        assertFalse(usLangs.isEmpty());

        // All returned locales must have country "US" and no variant.
        for (Object obj : usLangs) {
            Locale loc = (Locale) obj;
            assertEquals("US", loc.getCountry());
            assertEquals("", loc.getVariant());
        }

        // Cache test – second call should return the same (unmodifiable) list instance
        List usLangs2 = LocaleUtils.languagesByCountry("US");
        assertSame(usLangs, usLangs2);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLanguagesByCountry_isUnmodifiable() {
        List list = LocaleUtils.languagesByCountry("GB");
        list.add(Locale.GERMANY);
    }

    @Test
    public void testLanguagesByCountry_excludesVariantLocales() {
        // Find a locale with a variant for a known country (e.g., "en_US_POSIX")
        Locale variant = null;
        for (Locale l : Locale.getAvailableLocales()) {
            if ("US".equals(l.getCountry()) && l.getVariant().length() > 0) {
                variant = l;
                break;
            }
        }
        if (variant != null) {
            List usLangs = LocaleUtils.languagesByCountry("US");
            assertFalse("Variant locale should be excluded", usLangs.contains(variant));
        }
    }

    // -----------------------------------------------------------------------
    // countriesByLanguage tests
    // -----------------------------------------------------------------------
    @Test
    public void testCountriesByLanguage_nullInput() {
        List list = LocaleUtils.countriesByLanguage(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testCountriesByLanguage_knownLanguage() {
        // Use "en" – should have many locales with non‑empty country.
        List enCountries = LocaleUtils.countriesByLanguage("en");
        assertNotNull(enCountries);
        assertFalse(enCountries.isEmpty());

        for (Object obj : enCountries) {
            Locale loc = (Locale) obj;
            assertEquals("en", loc.getLanguage());
            assertFalse(loc.getCountry().isEmpty());
            assertEquals("", loc.getVariant());
        }

        // Cache test – second call returns same instance.
        List enCountries2 = LocaleUtils.countriesByLanguage("en");
        assertSame(enCountries, enCountries2);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCountriesByLanguage_isUnmodifiable() {
        List list = LocaleUtils.countriesByLanguage("fr");
        list.add(Locale.JAPAN);
    }

    @Test
    public void testCountriesByLanguage_excludesVariantLocales() {
        // Find a variant locale for language "en"
        Locale variant = null;
        for (Locale l : Locale.getAvailableLocales()) {
            if ("en".equals(l.getLanguage()) && l.getVariant().length() > 0) {
                variant = l;
                break;
            }
        }
        if (variant != null) {
            List enCountries = LocaleUtils.countriesByLanguage("en");
            assertFalse("Variant locale should be excluded", enCountries.contains(variant));
        }
    }

    // -----------------------------------------------------------------------
    // Helper to avoid test ordering issues (JUnit does not guarantee order)
    // -----------------------------------------------------------------------
    @BeforeClass
    public static void setUpClass() {
        // Ensure the default locale is not null; it is used in some lookup list tests.
        assertNotNull(Locale.getDefault());
    }
}
