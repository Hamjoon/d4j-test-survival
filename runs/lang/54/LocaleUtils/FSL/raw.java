###Test START##
```java
package org.apache.commons.lang;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * Comprehensive JUnit4 tests for {@link LocaleUtils}.
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
    public void testToLocale_LanguageOnly() {
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
        // Variant is taken as‑is (no upper‑casing enforced by LocaleUtils)
        assertEquals("xxx", locale.getVariant());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidLength_OneChar() {
        LocaleUtils.toLocale("e");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_InvalidLength_ThreeChars() {
        LocaleUtils.toLocale("eng");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_LowercaseCountry() {
        LocaleUtils.toLocale("en_gb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_UppercaseLanguage() {
        LocaleUtils.toLocale("EN_gb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_MissingUnderscore() {
        LocaleUtils.toLocale("engb");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToLocale_TooShortVariant() {
        LocaleUtils.toLocale("en_GB_");
    }

    // -----------------------------------------------------------------------
    // localeLookupList tests
    // -----------------------------------------------------------------------

    @Test
    public void testLocaleLookupList_Simple() {
        Locale start = new Locale("fr", "CA", "xxx");
        List list = LocaleUtils.localeLookupList(start);
        assertEquals(3, list.size());
        assertEquals(start, list.get(0));
        assertEquals(new Locale("fr", "CA"), list.get(1));
        assertEquals(new Locale("fr", ""), list.get(2));
    }

    @Test
    public void testLocaleLookupList_WithDefault_NotDuplicated() {
        Locale start = new Locale("fr", "CA", "xxx");
        Locale def = new Locale("fr", "CA"); // already present as second element
        List list = LocaleUtils.localeLookupList(start, def);
        // default should not be added a second time
        assertEquals(3, list.size());
        assertTrue(list.contains(def));
    }

    @Test
    public void testLocaleLookupList_WithDefault_AddedAtEnd() {
        Locale start = new Locale("fr", "CA", "xxx");
        Locale def = new Locale("en", "US");
        List list = LocaleUtils.localeLookupList(start, def);
        assertEquals(4, list.size());
        assertEquals(def, list.get(3));
    }

    @Test
    public void testLocaleLookupList_NullLocale() {
        List list = LocaleUtils.localeLookupList(null);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLocaleLookupList_NullLocaleWithDefault() {
        Locale def = new Locale("de", "DE");
        List list = LocaleUtils.localeLookupList(null, def);
        // According to implementation, null locale leads to empty list (default not added)
        assertTrue(list.isEmpty());
    }

    // -----------------------------------------------------------------------
    // availableLocaleList / availableLocaleSet tests
    // -----------------------------------------------------------------------

    @Test
    public void testAvailableLocaleList_NotModifiable() {
        List list = LocaleUtils.availableLocaleList();
        assertNotNull(list);
        try {
            list.add(Locale.CANADA);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        // The list should still contain the original locales
        assertTrue(list.contains(Locale.CANADA) || Locale.getAvailableLocales().length == 0);
    }

    @Test
    public void testAvailableLocaleSet_NotModifiable() {
        Set set = LocaleUtils.availableLocaleSet();
        assertNotNull(set);
        try {
            set.add(Locale.CANADA);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        // Ensure the set matches the underlying list
        assertEquals(LocaleUtils.availableLocaleList().size(), set.size());
        assertTrue(set.containsAll(LocaleUtils.availableLocaleList()));
    }

    @Test
    public void testAvailableLocaleSet_CachesResult() {
        Set first = LocaleUtils.availableLocaleSet();
        Set second = LocaleUtils.availableLocaleSet();
        assertSame(first, second);
    }

    // -----------------------------------------------------------------------
    // isAvailableLocale tests
    // -----------------------------------------------------------------------

    @Test
    public void testIsAvailableLocale_KnownLocale() {
        Locale known = Locale.US; // always present on a JDK
        assertTrue(LocaleUtils.isAvailableLocale(known));
    }

    @Test
    public void testIsAvailableLocale_UnknownLocale() {
        Locale unknown = new Locale("xx", "YY");
        // Not guaranteed to be present – assert false unless the JDK oddly provides it
        assertFalse(LocaleUtils.isAvailableLocale(unknown));
    }

    // -----------------------------------------------------------------------
    // languagesByCountry tests
    // -----------------------------------------------------------------------

    @Test
    public void testLanguagesByCountry_Null() {
        List list = LocaleUtils.languagesByCountry(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testLanguagesByCountry_ValidCountry() {
        // Use a country code that is guaranteed to exist on any JDK, e.g., "US"
        List langs = LocaleUtils.languagesByCountry("US");
        assertNotNull(langs);
        // At least English ("en") should be present
        boolean containsEnglish = false;
        for (Object o : langs) {
            Locale l = (Locale) o;
            if ("en".equals(l.getLanguage())) {
                containsEnglish = true;
                break;
            }
        }
        assertTrue("Expected to find English for US", containsEnglish);
        // Ensure no variant locales are present
        for (Object o : langs) {
            Locale l = (Locale) o;
            assertEquals("Variant should be empty", "", l.getVariant());
        }
    }

    @Test
    public void testLanguagesByCountry_Caching() {
        List first = LocaleUtils.languagesByCountry("GB");
        List second = LocaleUtils.languagesByCountry("GB");
        assertSame(first, second);
    }

    // -----------------------------------------------------------------------
    // countriesByLanguage tests
    // -----------------------------------------------------------------------

    @Test
    public void testCountriesByLanguage_Null() {
        List list = LocaleUtils.countriesByLanguage(null);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testCountriesByLanguage_ValidLanguage() {
        // Language "en" is present for many countries; pick a known one like US or GB
        List countries = LocaleUtils.countriesByLanguage("en");
        assertNotNull(countries);
        assertTrue(countries.size() > 0);

        boolean foundUS = false;
        for (Object o : countries) {
            Locale l = (Locale) o;
            // Must have a non‑empty country and no variant
            assertFalse("Country must not be empty", l.getCountry().isEmpty());
            assertEquals("Variant must be empty", "", l.getVariant());
            if ("US".equals(l.getCountry())) {
                foundUS = true;
            }
        }
        assertTrue("Expected to find US for language en", foundUS);
    }

    @Test
    public void testCountriesByLanguage_Caching() {
        List first = LocaleUtils.countriesByLanguage("fr");
        List second = LocaleUtils.countriesByLanguage("fr");
        assertSame(first, second);
    }

    // -----------------------------------------------------------------------
    // Helper to ensure static caches are cleared before each test (optional)
    // -----------------------------------------------------------------------

    @Before
    public void clearCaches() throws Exception {
        // Reflectively clear the private static caches to guarantee test isolation.
        // This is not required for correctness but makes the tests deterministic.
        java.lang.reflect.Field langField = LocaleUtils.class.getDeclaredField("cLanguagesByCountry");
        java.lang.reflect.Field countryField = LocaleUtils.class.getDeclaredField("cCountriesByLanguage");
        langField.setAccessible(true);
        countryField.setAccessible(true);
        ((java.util.Map) langField.get(null)).clear();
        ((java.util.Map) countryField.get(null)).clear();
        // Reset the locale set cache
        java.lang.reflect.Field setField = LocaleUtils.class.getDeclaredField("cAvailableLocaleSet");
        setField.setAccessible(true);
        setField.set(null, null);
    }
}
```
###Test END##