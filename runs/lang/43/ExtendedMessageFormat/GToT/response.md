###Test START##
```java
/**
 * JUnit 4 test suite for {@link org.apache.commons.lang.text.ExtendedMessageFormat}.
 *
 * The tests cover:
 * <ul>
 *   <li>Public API extraction and basic functionality</li>
 *   <li>Edge‑case handling (invalid patterns, illegal argument indexes, quoting)</li>
 *   <li>Custom {@code FormatFactory} integration</li>
 *   <li>Verification that mutator methods throw {@code UnsupportedOperationException}</li>
 * </ul>
 *
 * The test class is deliberately self‑contained – a minimal {@code FormatFactory}
 * and a couple of simple {@code Format} implementations are defined as inner classes
 * so that the suite can be compiled and executed without external dependencies.
 */
package org.apache.commons.lang.text;

import static org.junit.Assert.*;

import java.text.Format;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;

/**
 * Test cases for {@link ExtendedMessageFormat}.
 */
public class ExtendedMessageFormatTest {

    // ------------------------------------------------------------------------
    //  Helper classes used in the tests
    // ------------------------------------------------------------------------

    /**
     * Simple {@code FormatFactory} that knows two custom formats:
     * <ul>
     *   <li>"upper" – returns an {@link UpperCaseFormat}</li>
     *   <li>"repeat" – returns a {@link RepeatFormat} using the argument supplied after a comma</li>
     * </ul>
     */
    private static class DummyFactory implements FormatFactory {
        @Override
        public Format getFormat(String name, String arguments, Locale locale) {
            if ("upper".equals(name)) {
                return new UpperCaseFormat();
            }
            if ("repeat".equals(name)) {
                int times = 1;
                if (arguments != null) {
                    try {
                        times = Integer.parseInt(arguments.trim());
                    } catch (NumberFormatException e) {
                        // ignore – fall back to default 1
                    }
                }
                return new RepeatFormat(times);
            }
            return null;
        }
    }

    /** {@code Format} that converts a string to upper case. */
    private static class UpperCaseFormat extends Format {
        private static final long serialVersionUID = 1L;

        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo,
                FieldPosition pos) {
            return toAppendTo.append(String.valueOf(obj).toUpperCase(Locale.ROOT));
        }

        @Override
        public Object parseObject(String source, ParsePosition pos) {
            // parsing not needed for the tests
            return source;
        }
    }

    /** {@code Format} that repeats the string a given number of times. */
    private static class RepeatFormat extends Format {
        private static final long serialVersionUID = 1L;
        private final int repeat;

        RepeatFormat(int repeat) {
            this.repeat = repeat;
        }

        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo,
                FieldPosition pos) {
            String s = String.valueOf(obj);
            for (int i = 0; i < repeat; i++) {
                toAppendTo.append(s);
            }
            return toAppendTo;
        }

        @Override
        public Object parseObject(String source, ParsePosition pos) {
            // parsing not needed for the tests
            return source;
        }
    }

    // ------------------------------------------------------------------------
    //  1. Extraction of public API (constructors + public methods)
    // ------------------------------------------------------------------------
    // Constructors (all are public):
    //   ExtendedMessageFormat(String pattern)
    //   ExtendedMessageFormat(String pattern, Locale locale)
    //   ExtendedMessageFormat(String pattern, Map registry)
    //   ExtendedMessageFormat(String pattern, Locale locale, Map registry)
    //
    // Public methods declared in this class:
    //   String toPattern()
    //   void applyPattern(String pattern)
    //   void setFormat(int formatElementIndex, Format newFormat)      // throws UnsupportedOperationException
    //   void setFormatByArgumentIndex(int argumentIndex, Format newFormat) // throws UnsupportedOperationException
    //   void setFormats(Format[] newFormats)                        // throws UnsupportedOperationException
    //   void setFormatsByArgumentIndex(Format[] newFormats)        // throws UnsupportedOperationException

    // ------------------------------------------------------------------------
    //  2. Basic functional tests for each public method
    // ------------------------------------------------------------------------

    @Test
    public void testToPattern_SimplePattern() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        assertEquals("Hello {0}", emf.toPattern());
    }

    @Test
    public void testApplyPattern_WithoutRegistry() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Number: {0,number}");
        // Changing the pattern should be reflected in toPattern()
        emf.applyPattern("Number: {0,number,#.##}");
        assertEquals("Number: {0,number,#.##}", emf.toPattern());
    }

    @Test
    public void testCustomFormat_UpperCase() {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("upper", new DummyFactory());

        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,upper}", registry);
        assertEquals("{0,upper}", emf.toPattern());

        Object[] args = new Object[] { "test" };
        String result = emf.format(args);
        assertEquals("TEST", result);
    }

    @Test
    public void testCustomFormat_RepeatWithArgument() {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("repeat", new DummyFactory());

        // pattern uses a custom format "repeat" with argument "3"
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,repeat,3}", registry);
        assertEquals("{0,repeat,3}", emf.toPattern());

        String result = emf.format(new Object[] { "ab" });
        assertEquals("ababab", result);
    }

    @Test
    public void testSetFormat_UnsupportedOperation() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        try {
            emf.setFormat(0, new UpperCaseFormat());
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testSetFormatByArgumentIndex_UnsupportedOperation() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        try {
            emf.setFormatByArgumentIndex(0, new UpperCaseFormat());
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testSetFormats_UnsupportedOperation() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        try {
            emf.setFormats(new Format[] { new UpperCaseFormat() });
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testSetFormatsByArgumentIndex_UnsupportedOperation() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        try {
            emf.setFormatsByArgumentIndex(new Format[] { new UpperCaseFormat() });
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ------------------------------------------------------------------------
    //  3. Edge‑case and exception handling tests
    // ------------------------------------------------------------------------

    /** Pattern missing a closing brace should cause IllegalArgumentException. */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPattern_UnterminatedBrace() {
        new ExtendedMessageFormat("{0");
    }

    /** Pattern with a non‑numeric argument index should cause IllegalArgumentException. */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPattern_NonNumericArgumentIndex() {
        new ExtendedMessageFormat("{abc}");
    }

    /** Pattern where the format description is not closed with '}' should throw. */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPattern_UnterminatedFormatDescription() {
        new ExtendedMessageFormat("{0,upper");
    }

    /** Verify that escaped single quotes are handled correctly. */
    @Test
    public void testEscapedQuote_InPattern() {
        // In MessageFormat, two consecutive single quotes become one literal quote.
        ExtendedMessageFormat emf = new ExtendedMessageFormat("He''llo {0}");
        assertEquals("He''llo {0}", emf.toPattern());

        String result = emf.format(new Object[] { "world" });
        assertEquals("He'llo world", result);
    }

    /** Verify that a quoted literal brace is treated as a literal, not a format element. */
    @Test
    public void testQuotedLiteralBrace() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("'{0}'");
        assertEquals("'{0}'", emf.toPattern());

        String result = emf.format(new Object[] { "ignored" });
        assertEquals("{0}", result);
    }

    /** Empty pattern should be accepted and toPattern() should return empty string. */
    @Test
    public void testEmptyPattern() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("");
        assertEquals("", emf.toPattern());
        assertEquals("", emf.format(new Object[0]));
    }

    /** Null registry should behave like the base MessageFormat (no custom formats). */
    @Test
    public void testNullRegistryFallsBackToBaseBehavior() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,number,#.##}", null);
        assertEquals("{0,number,#.##}", emf.toPattern());

        String result = emf.format(new Object[] { 1234.567 });
        // default number format for Locale.US (default locale of the test runner)
        // we don't assert exact locale‑dependent representation; just ensure no exception.
        assertNotNull(result);
    }

    /** Verify that whitespace around format name and arguments is ignored. */
    @Test
    public void testWhitespaceAroundCustomFormat() {
        Map<String, FormatFactory> registry = Collections.singletonMap("upper", new DummyFactory());
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,  upper   }", registry);
        assertEquals("{0,  upper   }", emf.toPattern());

        String result = emf.format(new Object[] { "spAce" });
        assertEquals("SPACE", result);
    }

    /** Verify that a custom format returning null is ignored (fallback to default). */
    @Test
    public void testCustomFormatFactoryReturnsNull() {
        // Registry contains a factory that returns null for unknown names.
        Map<String, FormatFactory> registry = Collections.singletonMap("dummy", new DummyFactory());

        // "unknown" is not present in the registry – should be treated as a normal format name.
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,unknown}", registry);
        assertEquals("{0,unknown}", emf.toPattern());

        // Since "unknown" is not a standard MessageFormat format, MessageFormat will treat it as a literal.
        String result = emf.format(new Object[] { "value" });
        assertEquals("{0,unknown}", result);
    }

    // ------------------------------------------------------------------------
    //  4. Integration test – round‑trip pattern reconstruction
    // ------------------------------------------------------------------------

    @Test
    public void testRoundTripPatternWithMultipleCustomFormats() {
        Map<String, FormatFactory> registry = new HashMap<String, FormatFactory>();
        registry.put("upper", new DummyFactory());
        registry.put("repeat", new DummyFactory());

        String pattern = "Start {0,upper} middle {1,repeat,2} end";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern, registry);

        // toPattern should reproduce the original pattern (including whitespace)
        assertEquals(pattern, emf.toPattern());

        Object[] args = new Object[] { "a", "b" };
        String formatted = emf.format(args);
        // upper -> "A", repeat 2 -> "bb"
        assertEquals("Start A middle bb end", formatted);
    }
}
```
###Test END##