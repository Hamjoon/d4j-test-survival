###Test START##
```java
/**
 * JUnit 4 test suite for {@link org.apache.commons.lang.LocaleUtils}.
 *
 * The tests cover:
 * <ul>
 *   <li>Conversion from String to {@link Locale} (valid, null and invalid inputs).</li>
 *   <li>Locale lookup list generation (both overloads, handling of variants, default locale and immutability).</li>
 *   <li>Available locale list / set (content, immutability and caching).</li>
 *   <li>Availability check.</li>
 *   <li>Languages by country and countries by language (including null handling, variant filtering, caching and immutability).</li>
 * </ul>
 *
 * The test class deliberately does not rely on the internal static caches of
 * {@code LocaleUtils} except to verify that they behave as documented (e.g. the
 * same object is returned on subsequent calls).  Reflection is used only to
 * read the private static fields for that purpose.
 */
package org.apache.commons.lang;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class LocaleUtilsTest {

    private static final Locale EN = new Locale("en", "");
    private static final Locale EN_GB = new Locale("en", "GB");
    private static final Locale EN_GB_VARIANT = new Locale("en", "GB", "xxx");
    private static final Locale FR_CA = new Locale("fr", "CA");
    private static final Locale FR_CA_VARIANT = new Locale("fr", "CA", "xxx");
    private static final Locale US = Locale.US; // en_US

    @Before
    public void resetCaches() throws Exception {
        // Clear the lazy‑initialized set so that each test sees a fresh state.
        Field setField = LocaleUtils.class.getDeclaredField("cAvailableLocaleSet");
        setField.setAccessible(true);
        setField.set(null, null);
    }

    // -------------------------------------------------------------------------
    // toLocale()
    // -------------------------------------------------------------------------

    @Test
    public void testToLocale_nullInput() {
        assertNull(LocaleUtils.toLocale(null));
    }

    @Test
    public void testToLocale_twoLetterLanguage() {
        Locale result = LocaleUtils.toLocale("en");
        assertEquals("en", result.getLanguage());
        assertEquals("", result.getCountry());
        assertEquals("", result.getVariant());
    }

    @Test
    public void testToLocale_languageAndCountry() {
        Locale result = LocaleUtils.toLocale("en_GB");
        assertEquals("en", result.getLanguage());
        assertEquals("GB", result.getCountry());
        assertEquals("", result.getVariant());
    }

    @Test
    public void testToLocale_withVariant() {
        Locale result = LocaleUtils.toLocale("en_GB_xxx");
        assertEquals("en", result.getLanguage());
        assertEquals("GB", result.getCountry());
        assertEquals("xxx", result.getVariant());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_invalidLengthTooShort() {
        LocaleUtils.toLocale("e");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_invalidLengthTooLongNoUnderscore() {
        LocaleUtils.toLocale("enGB");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_invalidLanguageUpperCase() {
        LocaleUtils.toLocale("EN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_invalidCountryLowerCase() {
        LocaleUtils.toLocale("en_gb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_missingUnderscoreBetweenLangAndCountry() {
        LocaleUtils.toLocale("engb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_missingUnderscoreBeforeVariant() {
        LocaleUtils.toLocale("enGB_xxx");
    }

    // -------------------------------------------------------------------------
    // localeLookupList(Locale)
    // -------------------------------------------------------------------------

    @Test
    public void testLocaleLookupList_simple() {
        List<Locale> list = LocaleUtils.localeLookupList(EN);
        assertEquals(1, list.size());
        assertEquals(EN, list.get(0));
        assertUnmodifiable(list);
    }

    @Test
    public void testLocaleLookupList_withCountryOnly() {
        List<Locale> list = LocaleUtils.localeLookupList(FR_CA);
        // Expected order: fr_CA, fr
        assertEquals(2, list.size());
        assertEquals(FR_CA, list.get(0));
        assertEquals(new Locale("fr", ""), list.get(1));
    }

    @Test
    public void testLocaleLookupList_withVariant() {
        List<Locale> list = LocaleUtils.localeLookupList(FR_CA_VARIANT);
        // Expected order: fr_CA_xxx, fr_CA, fr
        assertEquals(3, list.size());
        assertEquals(FR_CA_VARIANT, list.get(0));
        assertEquals(FR_CA, list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
    }

    @Test
    public void testLocaleLookupList_nullLocale() {
        List<Locale> list = LocaleUtils.localeLookupList(null);
        assertTrue(list.isEmpty());
    }

    // -------------------------------------------------------------------------
    // localeLookupList(Locale, Locale)
    // -------------------------------------------------------------------------

    @Test
    public void testLocaleLookupListWithDefault_notInList() {
        Locale defaultLocale = Locale.US;
        List<Locale> list = LocaleUtils.localeLookupList(FR_CA, defaultLocale);
        // Expected: fr_CA, fr, en_US
        assertEquals(3, list.size());
        assertEquals(FR_CA, list.get(0));
        assertEquals(new Locale("fr", ""), list.get(1));
        assertEquals(defaultLocale, list.get(2));
    }

    @Test
    public void testLocaleLookupListWithDefault_alreadyPresent() {
        // defaultLocale equals the language‑only locale that will be added
        Locale defaultLocale = new Locale("fr", "");
        List<Locale> list = LocaleUtils.localeLookupList(FR_CA, defaultLocale);
        // Expected: fr_CA, fr (default already present, not duplicated)
        assertEquals(2, list.size());
        assertEquals(FR_CA, list.get(0));
        assertEquals(defaultLocale, list.get(1));
    }

    @Test
    public void testLocaleLookupListWithDefault_nullDefault() {
        List<Locale> list = LocaleUtils.localeLookupList(FR_CA, null);
        // Expected: fr_CA, fr (null default not added)
        assertEquals(2, list.size());
        assertEquals(FR_CA, list.get(0));
        assertEquals(new Locale("fr", ""), list.get(1));
    }

    @Test
    public void testLocaleLookupListWithDefault_nullLocale() {
        List<Locale> list = LocaleUtils.localeLookupList(null, Locale.US);
        assertTrue(list.isEmpty()); // null locale => empty list, default ignored
    }

    @Test
    public void testLocaleLookupListWithDefault_immutability() {
        List<Locale> list = LocaleUtils.localeLookupList(EN_GB, Locale.US);
        assertUnmodifiable(list);
    }

    // -------------------------------------------------------------------------
    // availableLocaleList()
    // -------------------------------------------------------------------------

    @Test
    public void testAvailableLocaleList_content() {
        List<Locale> list = LocaleUtils.availableLocaleList();
        assertNotNull(list);
        assertTrue(list.contains(Locale.US));
        assertTrue(list.contains(Locale.FRANCE));
        assertUnmodifiable(list);
    }

    // -------------------------------------------------------------------------
    // availableLocaleSet()
    // -------------------------------------------------------------------------

    @Test
    public void testAvailableLocaleSet_contentAndCaching() throws Exception {
        Set<Locale> set1 = LocaleUtils.availableLocaleSet();
        Set<Locale> set2 = LocaleUtils.availableLocaleSet();

        // Both calls must return the same (cached) instance
        assertSame(set1, set2);

        // Content must match the list version
        assertEquals(LocaleUtils.availableLocaleList().size(), set1.size());
        assertTrue(set1.contains(Locale.US));

        assertUnmodifiable(set1);
    }

    // -------------------------------------------------------------------------
    // isAvailableLocale()
    // -------------------------------------------------------------------------

    @Test
    public void testIsAvailableLocale_known() {
        assertTrue(LocaleUtils.isAvailableLocale(Locale.US));
    }

    @Test
    public void testIsAvailableLocale_unknown() {
        Locale fake = new Locale("xx", "YY");
        assertFalse(LocaleUtils.isAvailableLocale(fake));
    }

    // -------------------------------------------------------------------------
    // languagesByCountry()
    // -------------------------------------------------------------------------

    @Test
    public void testLanguagesByCountry_knownCountry() {
        List<Locale> langs = LocaleUtils.languagesByCountry("US");
        assertNotNull(langs);
        assertFalse(langs.isEmpty());

        // Every locale must have country US and no variant
        for (Locale l : langs) {
            assertEquals("US", l.getCountry());
            assertEquals(0, l.getVariant().length());
        }

        // The list should contain at least en_US
        assertTrue(langs.contains(Locale.US));

        // Verify caching – second call returns the same instance
        List<Locale> langs2 = LocaleUtils.languagesByCountry("US");
        assertSame(langs, langs2);

        assertUnmodifiable(langs);
    }

    @Test
    public void testLanguagesByCountry_nullCode() {
        List<Locale> langs = LocaleUtils.languagesByCountry(null);
        assertSame(Collections.EMPTY_LIST, langs);
    }

    @Test
    public void testLanguagesByCountry_noMatches() {
        // Assuming "ZZ" is not a real ISO country code in the default JRE
        List<Locale> langs = LocaleUtils.languagesByCountry("ZZ");
        assertNotNull(langs);
        assertTrue(langs.isEmpty());
    }

    // -------------------------------------------------------------------------
    // countriesByLanguage()
    // -------------------------------------------------------------------------

    @Test
    public void testCountriesByLanguage_knownLanguage() {
        List<Locale> countries = LocaleUtils.countriesByLanguage("en");
        assertNotNull(countries);
        assertFalse(countries.isEmpty());

        // Every locale must have language en, a non‑empty country and no variant
        for (Locale l : countries) {
            assertEquals("en", l.getLanguage());
            assertFalse(l.getCountry().isEmpty());
            assertEquals(0, l.getVariant().length());
        }

        // The list should contain at least en_US
        assertTrue(countries.contains(Locale.US));

        // Verify caching – second call returns the same instance
        List<Locale> countries2 = LocaleUtils.countriesByLanguage("en");
        assertSame(countries, countries2);

        assertUnmodifiable(countries);
    }

    @Test
    public void testCountriesByLanguage_nullCode() {
        List<Locale> list = LocaleUtils.countriesByLanguage(null);
        assertSame(Collections.EMPTY_LIST, list);
    }

    @Test
    public void testCountriesByLanguage_noMatches() {
        // Assuming "zz" is not a real ISO language code in the default JRE
        List<Locale> list = LocaleUtils.countriesByLanguage("zz");
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helper – assert that a collection is unmodifiable
    // -------------------------------------------------------------------------

    private static void assertUnmodifiable(List<?> list) {
        try {
            list.add(new Object());
            fail("Expected UnsupportedOperationException for unmodifiable list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void assertUnmodifiable(Set<?> set) {
        try {
            set.add(new Object());
            fail("Expected UnsupportedOperationException for unmodifiable set");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}
```
###Test END##