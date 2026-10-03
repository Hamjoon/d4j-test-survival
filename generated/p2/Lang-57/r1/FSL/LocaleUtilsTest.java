package org.apache.commons.lang;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.Test;

/**
 * Comprehensive JUnit 4 test suite for {@link LocaleUtils}.
 */
public class LocaleUtilsTest {

    // -----------------------------------------------------------------------
    // toLocale tests
    // -----------------------------------------------------------------------
    @Test
    public void testToLocale_NullInput() {
        assertNull(LocaleUtils.toLocale(null));
    }

    @Test
    public void testToLocale_ValidLanguageOnly() {
        Locale locale = LocaleUtils.toLocale("en");
        assertEquals("en", locale.getLanguage());
        assertEquals("", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testToLocale_ValidLanguageAndCountry() {
        Locale locale = LocaleUtils.toLocale("en_US");
        assertEquals("en", locale.getLanguage());
        assertEquals("US", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testToLocale_ValidLanguageCountryAndVariant() {
        Locale locale = LocaleUtils.toLocale("en_US_POSIX");
        assertEquals("en", locale.getLanguage());
        assertEquals("US", locale.getCountry());
        assertEquals("POSIX", locale.getVariant());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidTooShort() {
        LocaleUtils.toLocale("e");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidUpperCaseLanguage() {
        LocaleUtils.toLocale("EN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidMissingUnderscore() {
        LocaleUtils.toLocale("enUS");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidCountryLowerCase() {
        LocaleUtils.toLocale("en_us");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidCountryTooShort() {
        LocaleUtils.toLocale("en_U");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidCountryNonAlpha() {
        LocaleUtils.toLocale("en_1A");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidTrailingUnderscore() {
        LocaleUtils.toLocale("en_US_");
    }

    // -----------------------------------------------------------------------
    // localeLookupList tests
    // -----------------------------------------------------------------------
    @Test
    public void testLocaleLookupList_SingleArgument_NoVariant() {
        Locale locale = new Locale("fr", "CA");
        List<Locale> list = LocaleUtils.localeLookupList(locale);
        assertEquals(3, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("fr", "CA", ""), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
    }

    @Test
    public void testLocaleLookupList_SingleArgument_WithVariant() {
        Locale locale = new Locale("fr", "CA", "xxx");
        List<Locale> list = LocaleUtils.localeLookupList(locale);
        assertEquals(3, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
    }

    @Test
    public void testLocaleLookupList_TwoArguments_DefaultNotDuplicate() {
        Locale locale = new Locale("fr", "CA", "xxx");
        Locale defaultLocale = Locale.ENGLISH; // en
        List<Locale> list = LocaleUtils.localeLookupList(locale, defaultLocale);
        assertEquals(4, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
        assertEquals(defaultLocale, list.get(3));
    }

    @Test
    public void testLocaleLookupList_TwoArguments_DefaultIsAlreadyInList() {
        Locale locale = new Locale("en", "US");
        Locale defaultLocale = new Locale("en", "US");
        List<Locale> list = LocaleUtils.localeLookupList(locale, defaultLocale);
        // defaultLocale should not be added twice
        assertEquals(2, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("en", ""), list.get(1));
    }

    @Test
    public void testLocaleLookupList_NullLocale() {
        List<Locale> list = LocaleUtils.localeLookupList(null);
        assertTrue(list.isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLocaleLookupList_Unmodifiable() {
        Locale locale = new Locale("en", "GB");
        List<Locale> list = LocaleUtils.localeLookupList(locale);
        list.add(Locale.CANADA); // should throw
    }

    // -----------------------------------------------------------------------
    // availableLocaleList tests
    // -----------------------------------------------------------------------
    @Test
    public void testAvailableLocaleList_ContentsMatchJDK() {
        List<Locale> fromUtils = LocaleUtils.availableLocaleList();
        Locale[] fromJDK = Locale.getAvailableLocales();
        assertEquals("Size mismatch", fromJDK.length, fromUtils.size());
        for (Locale loc : fromJDK) {
            assertTrue("Missing locale: " + loc, fromUtils.contains(loc));
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAvailableLocaleList_Unmodifiable() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        list.add(Locale.ROOT);
    }

    // -----------------------------------------------------------------------
    // availableLocaleSet tests
    // -----------------------------------------------------------------------
    @Test
    public void testAvailableLocaleSet_LazyInitializationAndContents() {
        // First call should create the set
        Set<Locale> set1 = LocaleUtils.availableLocaleSet();
        assertNotNull(set1);
        // Second call should return the same (cached) instance
        Set<Locale> set2 = LocaleUtils.availableLocaleSet();
        assertSame(set1, set2);

        // Verify that every locale from the JDK is present
        Locale[] fromJDK = Locale.getAvailableLocales();
        for (Locale loc : fromJDK) {
            assertTrue("Set missing locale: " + loc, set1.contains(loc));
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAvailableLocaleSet_Unmodifiable() {
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        set.add(new Locale("xx", "YY"));
    }

    // -----------------------------------------------------------------------
    // isAvailableLocale tests
    // -----------------------------------------------------------------------
    @Test
    public void testIsAvailableLocale_KnownAndUnknown() {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US)); // known
        Locale unknown = new Locale("xx", "YY");
        assertFalse(LocaleUtils.isAvailableLocale(unknown)); // likely unknown
    }

    // -----------------------------------------------------------------------
    // languagesByCountry tests
    // -----------------------------------------------------------------------
    @Test
    public void testLanguagesByCountry_NullParameter() {
        List<Locale> list = LocaleUtils.languagesByCountry(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLanguagesByCountry_ValidCountry_NoVariants() {
        // US is a widely supported country; there are many locales with country US
        List<Locale> list = LocaleUtils.languagesByCountry("US");
        assertNotNull(list);
        assertFalse(list.isEmpty());

        for (Locale loc : list) {
            assertEquals("US", loc.getCountry());
            assertEquals("Variant should be stripped", "", loc.getVariant());
        }
    }

    @Test
    public void testLanguagesByCountry_CachingBehavior() {
        List<Locale> first = LocaleUtils.languagesByCountry("FR");
        List<Locale> second = LocaleUtils.languagesByCountry("FR");
        assertSame("Result should be cached and identical", first, second);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLanguagesByCountry_Unmodifiable() {
        List<Locale> list = LocaleUtils.languagesByCountry("US");
        list.add(Locale.CANADA);
    }

    // -----------------------------------------------------------------------
    // countriesByLanguage tests
    // -----------------------------------------------------------------------
    @Test
    public void testCountriesByLanguage_NullParameter() {
        List<Locale> list = LocaleUtils.countriesByLanguage(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testCountriesByLanguage_ValidLanguage_NoVariants() {
        // English is a common language with many country locales
        List<Locale> list = LocaleUtils.countriesByLanguage("en");
        assertNotNull(list);
        assertFalse(list.isEmpty());

        for (Locale loc : list) {
            assertEquals("Language should be en", "en", loc.getLanguage());
            assertTrue("Country must be present", loc.getCountry().length() > 0);
            assertEquals("Variant must be empty", "", loc.getVariant());
        }
    }

    @Test
    public void testCountriesByLanguage_CachingBehavior() {
        List<Locale> first = LocaleUtils.countriesByLanguage("fr");
        List<Locale> second = LocaleUtils.countriesByLanguage("fr");
        assertSame("Result should be cached and identical", first, second);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCountriesByLanguage_Unmodifiable() {
        List<Locale> list = LocaleUtils.countriesByLanguage("en");
        list.remove(0);
    }

    // -----------------------------------------------------------------------
    // Additional sanity checks for internal collections
    // -----------------------------------------------------------------------
    @Test
    public void testInternalCollectionsAreUnmodifiable() {
        // Verify that the list returned by localeLookupList is unmodifiable
        List<Locale> lookup = LocaleUtils.localeLookupList(Locale.CANADA);
        try {
            lookup.clear();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}
