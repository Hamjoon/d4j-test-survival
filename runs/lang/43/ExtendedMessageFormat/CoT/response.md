###Test START##
```java
/**********************************************************************
 * JUnit 4 test cases for org.apache.commons.lang.text.ExtendedMessageFormat
 *
 * The tests cover:
 *  • Public API methods (toPattern, applyPattern, and the mutators that
 *    must throw UnsupportedOperationException)
 *  • Normal usage with and without a custom FormatFactory registry
 *  • Edge‑case handling for malformed patterns (invalid argument index,
 *    unterminated format elements, and quoted‑string handling)
 *
 * These tests are written for JUnit 4 and require only the standard
 * JDK and the Commons‑Lang library that contains ExtendedMessageFormat.
 **********************************************************************/
package org.apache.commons.lang.text;

import static org.junit.Assert.*;

import java.text.Format;
import java.text.ParsePosition;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * Test suite for {@link ExtendedMessageFormat}.
 */
public class ExtendedMessageFormatTest {

    /** Simple {@link FormatFactory} that creates an {@link UpperCaseFormat}. */
    private static class UpperCaseFormatFactory implements FormatFactory {
        @Override
        public Format getFormat(String name, String arguments, Locale locale) {
            // arguments are ignored – we only need a format that upper‑cases strings
            return new UpperCaseFormat();
        }
    }

    /** A {@link Format} implementation that upper‑cases a {@link String}. */
    private static class UpperCaseFormat extends Format {
        private static final long serialVersionUID = 1L;

        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo,
                                   java.text.FieldPosition pos) {
            if (obj == null) {
                return toAppendTo;
            }
            return toAppendTo.append(String.valueOf(obj).toUpperCase(Locale.ROOT));
        }

        @Override
        public Object parseObject(String source, ParsePosition pos) {
            // parsing is not required for the tests – just return the raw string
            int start = pos.getIndex();
            pos.setIndex(source.length());
            return source.substring(start);
        }
    }

    /** Registry used for the tests that require a custom format. */
    private Map<String, FormatFactory> registry;

    @Before
    public void setUp() {
        registry = new HashMap<String, FormatFactory>();
        registry.put("upper", new UpperCaseFormatFactory());
    }

    /** --------------------------------------------------------------------
     *  Basic behaviour of toPattern() when no custom formats are supplied.
     * -------------------------------------------------------------------- */
    @Test
    public void testToPatternWithoutCustomFormats() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        assertEquals("Hello {0}", emf.toPattern());

        // Verify that the format works like a normal MessageFormat
        assertEquals("Hello world", emf.format(new Object[] {"world"}));
    }

    /** --------------------------------------------------------------------
     *  applyPattern() with a custom registry – the custom format must be
     *  inserted back into the pattern returned by toPattern() and must affect
     *  the output of format().
     * -------------------------------------------------------------------- */
    @Test
    public void testApplyPatternWithCustomRegistry() {
        String pattern = "Value: {0,upper}";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern, Locale.US, registry);

        // The pattern returned by toPattern() must contain the custom format name.
        assertEquals(pattern, emf.toPattern());

        // Formatting must use the UpperCaseFormat supplied by the registry.
        String result = emf.format(new Object[] {"test"});
        assertEquals("Value: TEST", result);
    }

    /** --------------------------------------------------------------------
     *  Whitespace around the format name should be tolerated.
     * -------------------------------------------------------------------- */
    @Test
    public void testWhitespaceAroundFormatName() {
        String pattern = "Value: {0, upper }";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern, Locale.US, registry);
        assertEquals("Value: {0, upper }", emf.toPattern());

        String result = emf.format(new Object[] {"abc"});
        assertEquals("Value: ABC", result);
    }

    /** --------------------------------------------------------------------
     *  The mutator methods inherited from MessageFormat must throw
     *  UnsupportedOperationException as documented.
     * -------------------------------------------------------------------- */
    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatThrowsException() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        emf.setFormat(0, new UpperCaseFormat());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatByArgumentIndexThrowsException() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        emf.setFormatByArgumentIndex(0, new UpperCaseFormat());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatsThrowsException() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        emf.setFormats(new Format[] { new UpperCaseFormat() });
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatsByArgumentIndexThrowsException() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        emf.setFormatsByArgumentIndex(new Format[] { new UpperCaseFormat() });
    }

    /** --------------------------------------------------------------------
     *  Invalid argument index – e.g. non‑numeric characters – must cause
     *  IllegalArgumentException during pattern parsing.
     * -------------------------------------------------------------------- */
    @Test
    public void testInvalidArgumentIndexThrowsException() {
        try {
            new ExtendedMessageFormat("{abc}");
            fail("Expected IllegalArgumentException because argument index is not numeric");
        } catch (IllegalArgumentException e) {
            // expected – message contains the offending pattern fragment
            assertTrue(e.getMessage().contains("Invalid format argument index"));
        }
    }

    /** --------------------------------------------------------------------
     *  Unterminated format element (missing closing '}') must cause
     *  IllegalArgumentException.
     * -------------------------------------------------------------------- */
    @Test
    public void testUnterminatedFormatElementThrowsException() {
        try {
            new ExtendedMessageFormat("{0,upper");
            fail("Expected IllegalArgumentException because the format element is not closed");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unterminated format element"));
        }
    }

    /** --------------------------------------------------------------------
     *  Quoted‑string handling – escaped single quotes (two consecutive
     *  quotes) should be interpreted as a literal quote character.
     * -------------------------------------------------------------------- */
    @Test
    public void testEscapedQuotesInPattern() {
        // Pattern:  '\''{0}'\''  -> literal quote, argument, literal quote
        String pattern = "'''{0}'''";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern);
        assertEquals("'''{0}'''", emf.toPattern());

        String result = emf.format(new Object[] {"quoted"});
        // Expected output: 'quoted'
        assertEquals("'quoted'", result);
    }

    /** --------------------------------------------------------------------
     *  When the registry is null, ExtendedMessageFormat must behave exactly
     *  like java.text.MessageFormat.  This test uses a built‑in date format.
     * -------------------------------------------------------------------- */
    @Test
    public void testApplyPatternWithoutRegistryFallsBackToMessageFormat() {
        // Using the built‑in date format "date" to ensure the fallback path works.
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,date,short}", Locale.US, null);
        assertEquals("{0,date,short}", emf.toPattern());

        // The result depends on the default locale; we just verify that formatting
        // does not throw and returns a non‑null string.
        String formatted = emf.format(new Object[] { new java.util.Date(0L) });
        assertNotNull(formatted);
    }
}
```
###Test END##