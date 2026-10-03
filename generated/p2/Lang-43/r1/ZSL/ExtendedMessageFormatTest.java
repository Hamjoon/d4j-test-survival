package org.apache.commons.lang.text;

import static org.junit.Assert.*;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;

/**
 * JUnit4 test suite for {@link ExtendedMessageFormat}.
 */
public class ExtendedMessageFormatTest {

    /**
     * Simple {@link FormatFactory} that always returns a {@link Format}
     * which prefixes the formatted value with "CUSTOM:".
     */
    private static class CustomFormatFactory implements FormatFactory {
        @Override
        public Format getFormat(String name, String args, Locale locale) {
            return new Format() {
                @Override
                public StringBuffer format(Object obj, StringBuffer toAppendTo,
                        FieldPosition pos) {
                    return toAppendTo.append("CUSTOM:" + obj);
                }

                @Override
                public Object parseObject(String source, ParsePosition pos) {
                    // not needed for these tests
                    return null;
                }
            };
        }
    }

    /**
     * Verify that mutator methods inherited from {@link MessageFormat}
     * are disabled and throw {@link UnsupportedOperationException}.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatThrows() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("test {0}");
        emf.setFormat(0, new Format() {
            @Override
            public StringBuffer format(Object obj, StringBuffer toAppendTo,
                    FieldPosition pos) {
                return toAppendTo;
            }

            @Override
            public Object parseObject(String source, ParsePosition pos) {
                return null;
            }
        });
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatByArgumentIndexThrows() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("test {0}");
        emf.setFormatByArgumentIndex(0, null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatsThrows() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("test {0}");
        emf.setFormats(new Format[0]);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatsByArgumentIndexThrows() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("test {0}");
        emf.setFormatsByArgumentIndex(new Format[0]);
    }

    /**
     * Test that a custom format supplied via a registry is correctly
     * applied during formatting and that {@link #toPattern()} returns the
     * original pattern (including the custom format description).
     */
    @Test
    public void testCustomFormatApplied() {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("custom", new CustomFormatFactory());

        String pattern = "Value: {0,custom}";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern, Locale.US, registry);

        // Verify that formatting uses the custom format.
        String result = emf.format(new Object[] { "test" });
        assertEquals("Value: CUSTOM:test", result);

        // Verify that toPattern reproduces the original pattern including the custom description.
        assertEquals(pattern, emf.toPattern());
    }

    /**
     * Verify that when no registry is supplied the class behaves like a normal
     * {@link MessageFormat}.
     */
    @Test
    public void testWithoutCustomRegistry() {
        String pattern = "Hello {0}";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern);
        String result = emf.format(new Object[] { "World" });
        assertEquals("Hello World", result);
        assertEquals(pattern, emf.toPattern());
    }

    /**
     * Passing an invalid argument index (non‑numeric) should cause an
     * {@link IllegalArgumentException}.
     */
    @Test
    public void testInvalidArgumentIndex() {
        try {
            new ExtendedMessageFormat("{a}");
            fail("Expected IllegalArgumentException for non‑numeric argument index");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Unterminated format element (missing closing brace) should raise an
     * {@link IllegalArgumentException}.
     */
    @Test
    public void testUnterminatedFormatElement() {
        try {
            new ExtendedMessageFormat("{0,custom");
            fail("Expected IllegalArgumentException for unterminated format element");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test handling of quoted literals and escaped quotes inside a pattern.
     */
    @Test
    public void testQuotedLiteralsAndEscapedQuotes() {
        // Quoted braces should appear literally.
        ExtendedMessageFormat emf1 = new ExtendedMessageFormat("Quote: '{0}'");
        String result1 = emf1.format(new Object[] { "ignored" });
        assertEquals("Quote: {0}", result1);

        // escaped single quote results in a single quote character.
        ExtendedMessageFormat emf2 = new ExtendedMessageFormat("It''s a test");
        String result2 = emf2.format(new Object[0]);
        assertEquals("It's a test", result2);
    }

    /**
     * Directly test the private {@code containsElements} method via reflection.
     */
    @Test
    public void testContainsElementsViaReflection() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("containsElements", java.util.Collection.class);
        m.setAccessible(true);

        // Empty collection → false
        assertFalse((Boolean) m.invoke(emf, Collections.emptyList()));

        // Collection with only nulls → false
        assertFalse((Boolean) m.invoke(emf, java.util.Arrays.asList(null, null)));

        // Collection with a non‑null element → true
        assertTrue((Boolean) m.invoke(emf, java.util.Arrays.asList(null, "x", null)));
    }

    /**
     * Verify that {@code parseFormatDescription} throws an exception for an
     * unterminated format description. Accessed via reflection.
     */
    @Test
    public void testParseFormatDescriptionUnterminatedViaReflection() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("parseFormatDescription", String.class, ParsePosition.class);
        m.setAccessible(true);

        ParsePosition pos = new ParsePosition(0);
        // Position points after the opening brace and argument index: pattern "{0,custom"
        // Simulate the state that the private method would see.
        String pattern = "{0,custom";

        // Move position to after the comma (index 3)
        pos.setIndex(3);
        try {
            m.invoke(emf, pattern, pos);
            fail("Expected IllegalArgumentException for unterminated format description");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            assertTrue(ite.getCause() instanceof IllegalArgumentException);
        }
    }

    /**
     * Test that {@code readArgumentIndex} correctly parses an argument index
     * and throws when the index is malformed. Accessed via reflection.
     */
    @Test
    public void testReadArgumentIndexViaReflection() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("readArgumentIndex", String.class, ParsePosition.class);
        m.setAccessible(true);

        // Valid index
        ParsePosition posValid = new ParsePosition(0);
        int idx = (Integer) m.invoke(emf, "{12}", posValid);
        assertEquals(12, idx);

        // Invalid (non‑digit) index should cause IllegalArgumentException
        ParsePosition posInvalid = new ParsePosition(0);
        try {
            m.invoke(emf, "{a}", posInvalid);
            fail("Expected IllegalArgumentException for non‑numeric index");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            assertTrue(ite.getCause() instanceof IllegalArgumentException);
        }
    }

    /**
     * Ensure that {@code insertFormats} correctly re‑inserts custom format
     * descriptions into the pattern when {@code toPattern()} is called.
     */
    @Test
    public void testInsertFormatsReconstruction() {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("custom", new CustomFormatFactory());

        String pattern = "Start {0,custom} middle {1} end";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern, Locale.US, registry);
        // The format should be applied correctly.
        String formatted = emf.format(new Object[] { "A", "B" });
        assertEquals("Start CUSTOM:A middle B end", formatted);

        // The pattern reconstructed by toPattern must match the original.
        assertEquals(pattern, emf.toPattern());
    }
}
