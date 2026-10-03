###Test START##
```java
/****************************************************************************************
 * JUnit 4 test suite for {@link org.apache.commons.lang3.LocaleUtils}
 *
 * The tests cover:
 *   • Normal, edge and error cases for {@code toLocale(String)}
 *   • {@code localeLookupList(...)} variants and immutability
 *   • Availability helpers and their immutability
 *   • Language‑by‑country and country‑by‑language look‑ups, including caching
 *
 * The suite is deliberately exhaustive while remaining readable.  All assertions
 * use JUnit‑4 static imports for brevity.
 ****************************************************************************************/

package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Comprehensive tests for {@link LocaleUtils}.
 */
public class LocaleUtilsTest {

    // -------------------------------------------------------------------------
    //  Helper data that is stable across the JDK versions we run the tests on.
    // -------------------------------------------------------------------------

    /** A set containing *all* locales returned by the JDK – used for cross‑checks. */
    private static Set<Locale> jdkLocaleSet;

    @BeforeClass
    public static void setUpClass() {
        jdkLocaleSet = new HashSet<Locale>();
        Collections.addAll(jdkLocaleSet, Locale.getAvailableLocales());
    }

    // -------------------------------------------------------------------------
    //  Tests for LocaleUtils.toLocale(String)
    // -------------------------------------------------------------------------

    @Test
    public void testToLocale_NullInput() {
        assertNull(LocaleUtils.toLocale(null));
    }

    @Test
    public void testToLocale_LanguageOnly() {
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
    public void testToLocale_LanguageCountryAndVariant() {
        Locale loc = LocaleUtils.toLocale("en_GB_xxx");
        assertEquals("en", loc.getLanguage());
        assertEquals("GB", loc.getCountry());
        assertEquals("xxx", loc.getVariant());
    }

    @Test
    public void testToLocale_LanguageWithEmptyCountryButVariant() {
        // According to the implementation, "en__POSIX" yields language=en, country="", variant=POSIX
        Locale loc = LocaleUtils.toLocale("en__POSIX");
        assertEquals("en", loc.getLanguage());
        assertEquals("", loc.getCountry());
        assertEquals("POSIX", loc.getVariant());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_TooShort() {
        LocaleUtils.toLocale("e"); // length < 2
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_LanguageUpperCase() {
        LocaleUtils.toLocale("EN"); // language must be lower‑case
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_MissingUnderscore() {
        LocaleUtils.toLocale("enGB"); // separator must be '_'
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_CountryNotUpperCase() {
        LocaleUtils.toLocale("en_gb"); // country must be upper‑case
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_IncompleteCountryCode() {
        LocaleUtils.toLocale("en_G"); // country length must be 2
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_MissingVariantSeparator() {
        // 6 characters, but char at index 5 is not '_' (e.g., "en_GBxx")
        LocaleUtils.toLocale("en_GBxx");
    }

    // -------------------------------------------------------------------------
    //  Tests for localeLookupList(...)
    // -------------------------------------------------------------------------

    @Test
    public void testLocaleLookupList_NullLocale() {
        List<Locale> list = LocaleUtils.localeLookupList(null);
        assertTrue(list.isEmpty());
        // immutability check
        try {
            list.add(Locale.US);
            fail("List should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void testLocaleLookupList_SimpleLocale_NoDefault() {
        Locale fr = new Locale("fr");
        List<Locale> list = LocaleUtils.localeLookupList(fr);
        assertEquals(1, list.size());
        assertEquals(fr, list.get(0));
    }

    @Test
    public void testLocaleLookupList_WithCountryAndVariant_NoDefault() {
        Locale locale = new Locale("fr", "CA", "xxx");
        List<Locale> list = LocaleUtils.localeLookupList(locale);
        assertEquals(3, list.size());
        assertEquals(locale, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr"), list.get(2));
    }

    @Test
    public void testLocaleLookupList_WithDefault_NotDuplicated() {
        Locale locale = new Locale("fr", "CA", "xxx");
        Locale defaultLocale = new Locale("fr"); // already present as the third element
        List<Locale> list = LocaleUtils.localeLookupList(locale, defaultLocale);
        // default should not be added again
        assertEquals(3, list.size());
        assertEquals(defaultLocale, list.get(2));
    }

    @Test
    public void testLocaleLookupList_WithDifferentDefault() {
        Locale locale = new Locale("fr", "CA", "xxx");
        Locale defaultLocale = new Locale("en", "US");
        List<Locale> list = LocaleUtils.localeLookupList(locale, defaultLocale);
        assertEquals(4, list.size());
        assertEquals(defaultLocale, list.get(3));
    }

    @Test
    public void testLocaleLookupList_Unmodifiable() {
        Locale locale = new Locale("de", "DE");
        List<Locale> list = LocaleUtils.localeLookupList(locale);
        try {
            list.remove(0);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // ok
        }
    }

    // -------------------------------------------------------------------------
    //  Tests for availableLocaleList() & availableLocaleSet()
    // -------------------------------------------------------------------------

    @Test
    public void testAvailableLocaleList_ContentsMatchJDK() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        // The JDK method returns a snapshot; our wrapper must contain the same elements.
        assertEquals(jdkLocaleSet.size(), list.size());
        assertTrue(jdkLocaleSet.containsAll(list));
    }

    @Test
    public void testAvailableLocaleSet_ContentsMatchJDK() {
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        assertEquals(jdkLocaleSet, set);
    }

    @Test
    public void testAvailableLocaleList_Unmodifiable() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        try {
            list.add(Locale.CANADA);
            fail("List should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void testAvailableLocaleSet_Unmodifiable() {
        Set<Locale> set = LocaleUtils.availableLocaleSet();
        try {
            set.add(Locale.CANADA);
            fail("Set should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    // -------------------------------------------------------------------------
    //  Tests for isAvailableLocale(Locale)
    // -------------------------------------------------------------------------

    @Test
    public void testIsAvailableLocale_KnownLocale() {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
    }

    @Test
    public void testIsAvailableLocale_UnknownLocale() {
        // A made‑up locale that is not part of the JDK's available locales
        Locale bogus = new Locale("zz", "ZZ");
        assertFalse(LocaleUtils.isAvailableLocale(bogus));
    }

    // -------------------------------------------------------------------------
    //  Tests for languagesByCountry(String)
    // -------------------------------------------------------------------------

    @Test
    public void testLanguagesByCountry_NullArgument() {
        List<Locale> list = LocaleUtils.languagesByCountry(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLanguagesByCountry_ValidCountry() {
        // Use a country that is guaranteed to have at least one language (e.g., "US")
        List<Locale> usLangs = LocaleUtils.languagesByCountry("US");
        assertNotNull(usLangs);
        assertFalse(usLangs.isEmpty());

        for (Locale loc : usLangs) {
            assertEquals("US", loc.getCountry());
            assertEquals("", loc.getVariant());
        }
    }

    @Test
    public void testLanguagesByCountry_CachingBehaviour() {
        List<Locale> firstCall = LocaleUtils.languagesByCountry("FR");
        List<Locale> secondCall = LocaleUtils.languagesByCountry("FR");
        // The implementation caches the unmodifiable list; the same instance should be returned.
        assertSame(firstCall, secondCall);
    }

    // -------------------------------------------------------------------------
    //  Tests for countriesByLanguage(String)
    // -------------------------------------------------------------------------

    @Test
    public void testCountriesByLanguage_NullArgument() {
        List<Locale> list = LocaleUtils.countriesByLanguage(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testCountriesByLanguage_ValidLanguage() {
        // English ("en") is present in many countries.
        List<Locale> enCountries = LocaleUtils.countriesByLanguage("en");
        assertNotNull(enCountries);
        assertFalse(enCountries.isEmpty());

        for (Locale loc : enCountries) {
            assertEquals("en", loc.getLanguage());
            assertTrue(loc.getCountry().length() > 0);
            assertEquals("", loc.getVariant());
        }
    }

    @Test
    public void testCountriesByLanguage_CachingBehaviour() {
        List<Locale> first = LocaleUtils.countriesByLanguage("de");
        List<Locale> second = LocaleUtils.countriesByLanguage("de");
        assertSame(first, second);
    }

    // -------------------------------------------------------------------------
    //  Miscellaneous sanity checks
    // -------------------------------------------------------------------------

    @Test
    public void testLocaleUtilsConstructorIsPublic() throws Exception {
        // The class has a public no‑arg constructor; ensure it can be instantiated.
        LocaleUtils utils = new LocaleUtils();
        assertNotNull(utils);
    }
}
```
###Test END##