/**
 * JUnit 4 test suite for {@link org.apache.commons.lang.LocaleUtils}.
 *
 * The tests cover:
 * <ul>
 *   <li>Conversion from String to Locale (valid, edge, and error cases)</li>
 *   <li>Locale lookup list generation (both overloads, duplicates handling)</li>
 *   <li>Available locale list and set (immutability and lazy initialisation)</li>
 *   <li>Availability checking</li>
 *   <li>Languages‑by‑country and countries‑by‑language lookup (caching, immutability)</li>
 * </ul>
 *
 * The tests are written using JUnit 4.12 and assume Java 8+.
 */
package org.apache.commons.lang;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * Comprehensive test class for {@link LocaleUtils}.
 */
public class LocaleUtilsTest {

    /** A few well‑known locales used throughout the tests. */
    private static final Locale EN = new Locale("en", "");
    private static final Locale EN_GB = new Locale("en", "GB");
    private static final Locale EN_GB_VARIANT = new Locale("en", "GB", "variant");
    private static final Locale FR_CA = new Locale("fr", "CA");
    private static final Locale FR_CA_VARIANT = new Locale("fr", "CA", "xxx");
    private static final Locale FR = new Locale("fr", "");

    /** Reset the internal caches before each test to avoid cross‑test contamination. */
    @Before
    public void setUp() {
        // Clear the static caches via reflection – this is safe for test purposes only.
        // The caches are package‑private static fields.
        try {
            java.lang.reflect.Field languagesField = LocaleUtils.class.getDeclaredField("cLanguagesByCountry");
            languagesField.setAccessible(true);
            ((java.util.Map) languagesField.get(null)).clear();

            java.lang.reflect.Field countriesField = LocaleUtils.class.getDeclaredField("cCountriesByLanguage");
            countriesField.setAccessible(true);
            ((java.util.Map) countriesField.get(null)).clear();

            java.lang.reflect.Field setField = LocaleUtils.class.getDeclaredField("cAvailableLocaleSet");
            setField.setAccessible(true);
            setField.set(null, null); // force lazy initialisation
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // -----------------------------------------------------------------------
    // toLocale(String)
    // -----------------------------------------------------------------------
    @Test
    public void testToLocale_NullInput() {
        assertNull(LocaleUtils.toLocale(null));
    }

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
        // JDK 1.4+ does not alter the case of the variant; we simply verify the exact string.
        assertEquals("xxx", locale.getVariant());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidLength_TooShort() {
        LocaleUtils.toLocale("e");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidLength_TooLongWithoutUnderscore() {
        LocaleUtils.toLocale("en_GB_foo_bar");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidLanguageUpperCase() {
        LocaleUtils.toLocale("EN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidCountryLowerCase() {
        LocaleUtils.toLocale("en_gb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_MissingUnderscoreSeparator() {
        LocaleUtils.toLocale("enGB");
    }

    // -----------------------------------------------------------------------
    // localeLookupList(Locale)  – single‑argument overload
    // -----------------------------------------------------------------------
    @Test
    public void testLocaleLookupList_SingleArgument_Null() {
        List<Locale> list = LocaleUtils.localeLookupList(null);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLocaleLookupList_SingleArgument_Simple() {
        List<Locale> list = LocaleUtils.localeLookupList(EN);
        assertEquals(1, list.size());
        assertEquals(EN, list.get(0));
    }

    @Test
    public void testLocaleLookupList_SingleArgument_WithCountry() {
        List<Locale> list = LocaleUtils.localeLookupList(EN_GB);
        assertEquals(2, list.size());
        assertEquals(EN_GB, list.get(0));
        assertEquals(EN, list.get(1));
    }

    @Test
    public void testLocaleLookupList_SingleArgument_WithVariant() {
        List<Locale> list = LocaleUtils.localeLookupList(EN_GB_VARIANT);
        assertEquals(3, list.size());
        assertEquals(EN_GB_VARIANT, list.get(0));
        assertEquals(EN_GB, list.get(1));
        assertEquals(EN, list.get(2));
    }

    // -----------------------------------------------------------------------
    // localeLookupList(Locale, Locale) – two‑argument overload
    // -----------------------------------------------------------------------
    @Test
    public void testLocaleLookupList_TwoArgs_NullLocale() {
        List<Locale> list = LocaleUtils.localeLookupList(null, EN);
        // According to the implementation the list will be empty (no default added)
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLocaleLookupList_TwoArgs_DefaultNotInList() {
        List<Locale> list = LocaleUtils.localeLookupList(FR_CA_VARIANT, EN);
        // Expected order: specific → language+country → language → default
        assertEquals(4, list.size());
        assertEquals(FR_CA_VARIANT, list.get(0));
        assertEquals(FR_CA, list.get(1));
        assertEquals(FR, list.get(2));
        assertEquals(EN, list.get(3));
    }

    @Test
    public void testLocaleLookupList_TwoArgs_DefaultAlreadyPresent() {
        // Default is already part of the cascade; it should not be duplicated.
        List<Locale> list = LocaleUtils.localeLookupList(EN_GB, EN);
        assertEquals(3, list.size());
        assertEquals(EN_GB, list.get(0));
        assertEquals(EN, list.get(1));
        // default EN already present, no second EN at the end
    }

    @Test
    public void testLocaleLookupList_TwoArgs_NoCountry_NoVariant() {
        List<Locale> list = LocaleUtils.localeLookupList(EN, FR);
        assertEquals(2, list.size());
        assertEquals(EN, list.get(0));
        assertEquals(FR, list.get(1));
    }

    @Test
    public void testLocaleLookupList_Unmodifiable() {
        List<Locale> list = LocaleUtils.localeLookupList(EN_GB);
        try {
            list.add(FR);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    // -----------------------------------------------------------------------
    // availableLocaleList()
    // -----------------------------------------------------------------------
    @Test
    public void testAvailableLocaleList_NotNullAndUnmodifiable() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        assertNotNull(list);
        assertFalse(list.isEmpty());

        // The returned list must be unmodifiable
        try {
            list.add(EN);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        // Verify that the list contains a known locale (e.g., EN)
        assertTrue(list.contains(EN));
    }

    // -----------------------------------------------------------------------
    // availableLocaleSet()
    // -----------------------------------------------------------------------
    @Test
    public void testAvailableLocaleSet_LazyInitialisation() {
        // First call should create the set
        Set<Locale> set1 = LocaleUtils.availableLocaleSet();
        assertNotNull(set1);
        assertFalse(set1.isEmpty());

        // Subsequent call must return the same (cached) instance
        Set<Locale> set2 = LocaleUtils.availableLocaleSet();
        assertSame(set1, set2);

        // Set must be unmodifiable
        try {
            set1.add(EN);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        // Verify containment of a known locale
        assertTrue(set1.contains(EN));
    }

    // -----------------------------------------------------------------------
    // isAvailableLocale(Locale)
    // -----------------------------------------------------------------------
    @Test
    public void testIsAvailableLocale_KnownLocale() {
        assertTrue(LocaleUtils.isAvailableLocale(EN));
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
    }

    @Test
    public void testIsAvailableLocale_UnknownLocale() {
        Locale unknown = new Locale("xx", "YY");
        // The set may or may not contain the unknown locale depending on the JRE.
        // We assert that the method returns the same result as a direct containment check.
        boolean expected = LocaleUtils.availableLocaleSet().contains(unknown);
        assertEquals(expected, LocaleUtils.isAvailableLocale(unknown));
    }

    // -----------------------------------------------------------------------
    // languagesByCountry(String)
    // -----------------------------------------------------------------------
    @Test
    public void testLanguagesByCountry_Null() {
        List<Locale> list = LocaleUtils.languagesByCountry(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLanguagesByCountry_ValidCountry() {
        // For a country that exists in the JRE (e.g., "CA" – Canada)
        List<Locale> list = LocaleUtils.languagesByCountry("CA");
        assertNotNull(list);
        assertFalse(list.isEmpty());

        // All locales must have country = "CA" and no variant
        for (Locale l : list) {
            assertEquals("CA", l.getCountry());
            assertEquals("", l.getVariant());
        }

        // The list must be unmodifiable
        try {
            list.add(EN);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void testLanguagesByCountry_Caching() {
        List<Locale> firstCall = LocaleUtils.languagesByCountry("FR");
        List<Locale> secondCall = LocaleUtils.languagesByCountry("FR");
        assertSame(firstCall, secondCall);
    }

    // -----------------------------------------------------------------------
    // countriesByLanguage(String)
    // -----------------------------------------------------------------------
    @Test
    public void testCountriesByLanguage_Null() {
        List<Locale> list = LocaleUtils.countriesByLanguage(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testCountriesByLanguage_ValidLanguage() {
        // For language "en"
        List<Locale> list = LocaleUtils.countriesByLanguage("en");
        assertNotNull(list);
        assertFalse(list.isEmpty());

        // All locales must have language = "en", a non‑empty country, and no variant
        for (Locale l : list) {
            assertEquals("en", l.getLanguage());
            assertFalse(l.getCountry().isEmpty());
            assertEquals("", l.getVariant());
        }

        // Verify immutability
        try {
            list.remove(0);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void testCountriesByLanguage_Caching() {
        List<Locale> firstCall = LocaleUtils.countriesByLanguage("de");
        List<Locale> secondCall = LocaleUtils.countriesByLanguage("de");
        assertSame(firstCall, secondCall);
    }

    // -----------------------------------------------------------------------
    // Additional sanity checks for internal cache consistency
    // -----------------------------------------------------------------------
    @Test
    public void testCacheConsistency_AvailableLocaleSetAfterLookup() {
        // Ensure that calling languagesByCountry or countriesByLanguage does not
        // corrupt the available locale set.
        Set<Locale> before = LocaleUtils.availableLocaleSet();
        LocaleUtils.languagesByCountry("US");
        LocaleUtils.countriesByLanguage("fr");
        Set<Locale> after = LocaleUtils.availableLocaleSet();
        assertSame(before, after);
    }
}
