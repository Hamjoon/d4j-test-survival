/****************************************************************************************
 * JUnit 4 test suite for {@link org.apache.commons.lang.LocaleUtils}
 *
 * The tests cover:
 *  • {@code toLocale(String)} – valid inputs, null handling and a range of illegal formats
 *  • {@code localeLookupList(Locale)} and {@code localeLookupList(Locale, Locale)}
 *  • {@code availableLocaleList()} and {@code availableLocaleSet()}
 *  • {@code isAvailableLocale(Locale)}
 *  • {@code languagesByCountry(String)} – caching and null handling
 *  • {@code countriesByLanguage(String)} – caching and null handling
 *
 * The test class is deliberately exhaustive to serve as a reference implementation for
 * future maintainers.
 *
 * Author: OpenAI ChatGPT (generated for educational purposes)
 ****************************************************************************************/

package org.apache.commons.lang;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test class for {@link LocaleUtils}.
 */
public class LocaleUtilsTest {

    /** A handful of locales that are guaranteed to be present on every JRE. */
    private static final Locale EN = new Locale("en", "");
    private static final Locale EN_GB = new Locale("en", "GB");
    private static final Locale EN_GB_VARIANT = new Locale("en", "GB", "variant");
    private static final Locale FR_CA = new Locale("fr", "CA");
    private static final Locale FR_CA_VARIANT = new Locale("fr", "CA", "xxx");
    private static final Locale FR = new Locale("fr", "");

    /**
     * Ensure that the cache for available locales is populated before tests that depend
     * on {@code isAvailableLocale}.  The method {@code availableLocaleSet()} lazily creates
     * the set and also populates {@code cAvailableLocaleSet} which {@code isAvailableLocale}
     * relies on.
     */
    @BeforeClass
    public static void initAvailableLocaleSet() {
        // Force initialization
        LocaleUtils.availableLocaleSet();
    }

    // -------------------------------------------------------------------------
    // toLocale tests
    // -------------------------------------------------------------------------

    @Test
    public void testToLocale_null() {
        assertNull(LocaleUtils.toLocale(null));
    }

    @Test
    public void testToLocale_languageOnly() {
        Locale locale = LocaleUtils.toLocale("de");
        assertEquals("de", locale.getLanguage());
        assertEquals("", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testToLocale_languageCountry() {
        Locale locale = LocaleUtils.toLocale("es_ES");
        assertEquals("es", locale.getLanguage());
        assertEquals("ES", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testToLocale_languageCountryVariant() {
        Locale locale = LocaleUtils.toLocale("ja_JP_JIS");
        assertEquals("ja", locale.getLanguage());
        assertEquals("JP", locale.getCountry());
        assertEquals("JIS", locale.getVariant());
    }

    @Test
    public void testToLocale_variantPreservesCase() {
        // The spec says the JDK may or may not upper‑case the variant.
        // We only assert that the variant string we passed is returned unchanged.
        Locale locale = LocaleUtils.toLocale("en_GB_myVariant");
        assertEquals("myVariant", locale.getVariant());
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
    public void testToLocale_invalidLanguageCase() {
        LocaleUtils.toLocale("EN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_invalidCountryCase() {
        LocaleUtils.toLocale("en_us");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_missingUnderscore() {
        LocaleUtils.toLocale("enUS");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_extraUnderscoreNoVariant() {
        LocaleUtils.toLocale("en_US_");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_variantWithoutLeadingUnderscore() {
        LocaleUtils.toLocale("enUS_variant");
    }

    // -------------------------------------------------------------------------
    // localeLookupList tests
    // -------------------------------------------------------------------------

    @Test
    public void testLocaleLookupList_singleArgument_null() {
        List<Locale> list = LocaleUtils.localeLookupList(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLocaleLookupList_singleArgument_variant() {
        List<Locale> list = LocaleUtils.localeLookupList(FR_CA_VARIANT);
        assertEquals(3, list.size());
        assertEquals(FR_CA_VARIANT, list.get(0));
        assertEquals(FR_CA, list.get(1));
        assertEquals(FR, list.get(2));
        // verify unmodifiable
        try {
            list.add(Locale.US);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testLocaleLookupList_singleArgument_noVariant() {
        List<Locale> list = LocaleUtils.localeLookupList(EN_GB);
        assertEquals(2, list.size());
        assertEquals(EN_GB, list.get(0));
        assertEquals(EN, list.get(1));
    }

    @Test
    public void testLocaleLookupList_twoArgs_nullLocale() {
        List<Locale> list = LocaleUtils.localeLookupList(null, EN);
        assertNotNull(list);
        assertTrue(list.isEmpty()); // spec: null locale returns empty list, default ignored
    }

    @Test
    public void testLocaleLookupList_twoArgs_withDefaultNotInList() {
        List<Locale> list = LocaleUtils.localeLookupList(FR_CA, EN);
        // Expected order: FR_CA, FR, EN
        assertEquals(3, list.size());
        assertEquals(FR_CA, list.get(0));
        assertEquals(FR, list.get(1));
        assertEquals(EN, list.get(2));
    }

    @Test
    public void testLocaleLookupList_twoArgs_defaultAlreadyPresent() {
        List<Locale> list = LocaleUtils.localeLookupList(FR_CA, FR);
        // FR is already part of the chain, should not be duplicated.
        assertEquals(2, list.size());
        assertEquals(FR_CA, list.get(0));
        assertEquals(FR, list.get(1));
    }

    @Test
    public void testLocaleLookupList_twoArgs_variantAndDefault() {
        List<Locale> list = LocaleUtils.localeLookupList(FR_CA_VARIANT, EN);
        // Expected: FR_CA_VARIANT, FR_CA, FR, EN
        assertEquals(4, list.size());
        assertEquals(FR_CA_VARIANT, list.get(0));
        assertEquals(FR_CA, list.get(1));
        assertEquals(FR, list.get(2));
        assertEquals(EN, list.get(3));
    }

    // -------------------------------------------------------------------------
    // availableLocaleList / Set tests
    // -------------------------------------------------------------------------

    @Test
    public void testAvailableLocaleList_isUnmodifiable_andMatchesJDK() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        // Should be the same size as Locale.getAvailableLocales()
        Locale[] jdkArray = Locale.getAvailableLocales();
        assertEquals(jdkArray.length, list.size());

        // Check that the list contains a known locale
        assertTrue(list.contains(Locale.US));

        // Unmodifiable test
        try {
            list.add(Locale.ROOT);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testAvailableLocaleSet_isUnmodifiable_andConsistentWithList() {
        Set<Locale> set = LocaleUtils.availableLocaleSet();

        // Verify all list entries are present in the set
        List<Locale> list = LocaleUtils.availableLocaleList();
        for (Locale locale : list) {
            assertTrue("Set missing locale " + locale, set.contains(locale));
        }

        // Verify unmodifiable
        try {
            set.add(Locale.ROOT);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        // Verify lazy initialization does not create a new set on subsequent calls
        Set<Locale> set2 = LocaleUtils.availableLocaleSet();
        assertSame(set, set2);
    }

    // -------------------------------------------------------------------------
    // isAvailableLocale tests
    // -------------------------------------------------------------------------

    @Test
    public void testIsAvailableLocale_knownAndUnknown() {
        // Known locale (should be true)
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));

        // Unknown locale (unlikely to be present)
        Locale fake = new Locale("xx", "YY");
        // In practice, the fake locale is not among the JRE's available locales
        assertFalse(LocaleUtils.isAvailableLocale(fake));
    }

    // -------------------------------------------------------------------------
    // languagesByCountry tests
    // -------------------------------------------------------------------------

    @Test
    public void testLanguagesByCountry_nullReturnsEmptyList() {
        List<Locale> list = LocaleUtils.languagesByCountry(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
        // Ensure it's the canonical EMPTY_LIST instance (unmodifiable)
        try {
            list.add(Locale.US);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testLanguagesByCountry_validCountryCaching() {
        String country = "US";
        List<Locale> firstCall = LocaleUtils.languagesByCountry(country);
        assertNotNull(firstCall);
        // The list must contain at least the language-only locale for US (e.g., en_US)
        boolean containsEnglishUS = false;
        for (Locale l : firstCall) {
            if ("en".equals(l.getLanguage()) && "US".equals(l.getCountry()) && l.getVariant().isEmpty()) {
                containsEnglishUS = true;
                break;
            }
        }
        assertTrue("Expected at least one language for US", containsEnglishUS);

        // Call a second time and verify the same immutable instance is returned (cache hit)
        List<Locale> secondCall = LocaleUtils.languagesByCountry(country);
        assertSame("Cache should return the same list instance", firstCall, secondCall);
    }

    @Test
    public void testLanguagesByCountry_excludesVariants() {
        // Choose a country that definitely has variant locales, e.g., "CA" (French Canada)
        List<Locale> langs = LocaleUtils.languagesByCountry("CA");
        for (Locale l : langs) {
            assertEquals("Variant must be empty", "", l.getVariant());
        }
    }

    // -------------------------------------------------------------------------
    // countriesByLanguage tests
    // -------------------------------------------------------------------------

    @Test
    public void testCountriesByLanguage_nullReturnsEmptyList() {
        List<Locale> list = LocaleUtils.countriesByLanguage(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
        // Unmodifiable check
        try {
            list.add(Locale.US);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testCountriesByLanguage_validLanguageCaching() {
        String language = "en";
        List<Locale> first = LocaleUtils.countriesByLanguage(language);
        assertNotNull(first);
        // Should contain at least one country (e.g., en_US)
        boolean hasAtLeastOneCountry = false;
        for (Locale l : first) {
            if ("en".equals(l.getLanguage()) && !l.getCountry().isEmpty()) {
                hasAtLeastOneCountry = true;
                break;
            }
        }
        assertTrue("Expected at least one country for language 'en'", hasAtLeastOneCountry);

        List<Locale> second = LocaleUtils.countriesByLanguage(language);
        assertSame("Cache should return the same list instance", first, second);
    }

    @Test
    public void testCountriesByLanguage_excludesVariants() {
        // Language "fr" typically has variants such as fr_CA_xxx
        List<Locale> locales = LocaleUtils.countriesByLanguage("fr");
        for (Locale l : locales) {
            assertEquals("Variant must be empty", "", l.getVariant());
            assertFalse("Country must not be empty", l.getCountry().isEmpty());
        }
    }
}
