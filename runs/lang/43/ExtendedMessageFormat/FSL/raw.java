###Test START##
```java
package org.apache.commons.lang.text;

import static org.junit.Assert.*;

import java.lang.reflect.*;
import java.text.Format;
import java.text.MessageFormat;
import java.util.*;

import org.junit.Test;

/**
 * Comprehensive JUnit 4 tests for {@link ExtendedMessageFormat}.
 */
public class ExtendedMessageFormatTest {

    /**
     * Simple {@link FormatFactory} used for testing custom formats.
     */
    private static class SimpleFactory implements FormatFactory {
        @Override
        public Format getFormat(String name, String arguments, Locale locale) {
            // arguments may be null – ignore for this simple factory
            return new Format() {
                private static final long serialVersionUID = 1L;

                @Override
                public StringBuffer format(Object obj, StringBuffer toAppendTo,
                                          java.text.FieldPosition pos) {
                    // Just prefix the argument with the factory name (and style if supplied)
                    String prefix = name;
                    if (arguments != null && !arguments.isEmpty()) {
                        prefix += "[" + arguments + "]";
                    }
                    return toAppendTo.append(prefix).append(":").append(obj);
                }

                @Override
                public Object parseObject(String source, java.text.ParsePosition pos) {
                    // Not needed for the tests
                    return null;
                }
            };
        }
    }

    // -----------------------------------------------------------------------
    // Helper to invoke private methods via reflection
    // -----------------------------------------------------------------------
    private static Object invokePrivate(Object target, String method,
                                         Class<?>[] paramTypes, Object... args)
            throws Exception {
        Method m = target.getClass().getDeclaredMethod(method, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    // -----------------------------------------------------------------------
    // Basic functionality without a registry
    // -----------------------------------------------------------------------
    @Test
    public void testSimplePatternFormatting() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Hello {0}");
        String result = emf.format(new Object[]{"World"});
        assertEquals("Hello World", result);
        assertEquals("Hello {0}", emf.toPattern());
    }

    @Test
    public void testSimplePatternWithLocale() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("Number: {0,number}",
                                                              Locale.US, null);
        String result = emf.format(new Object[]{12345.6});
        // MessageFormat uses locale‑specific number formatting
        assertEquals("Number: 12,345.6", result);
    }

    // -----------------------------------------------------------------------
    // Custom format handling via a registry
    // -----------------------------------------------------------------------
    @Test
    public void testCustomFormatApplied() {
        Map<String, FormatFactory> registry = new HashMap<>();
        registry.put("myfmt", new SimpleFactory());

        ExtendedMessageFormat emf = new ExtendedMessageFormat(
                "Value: {0, myfmt}", Locale.US, registry);

        // The custom format should prefix the argument with "myfmt:"
        String result = emf.format(new Object[]{"test"});
        assertEquals("Value: myfmt:test", result);

        // toPattern() must retain the custom format description
        assertEquals("Value: {0, myfmt}", emf.toPattern());
    }

    @Test
    public void testCustomFormatWithStyleArguments() {
        Map<String, FormatFactory> registry = new HashMap<>();
        registry.put("stylish", new SimpleFactory());

        // style argument will be passed to the factory as the second part of the description
        ExtendedMessageFormat emf = new ExtendedMessageFormat(
                "Styled: {0, stylish, upper}", Locale.US, registry);

        String result = emf.format(new Object[]{"hello"});
        assertEquals("Styled: stylish[upper]:hello", result);
        assertEquals("Styled: {0, stylish, upper}", emf.toPattern());
    }

    // -----------------------------------------------------------------------
    // Error handling in pattern parsing
    // -----------------------------------------------------------------------
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidArgumentIndexThrows() {
        // non‑numeric argument index
        new ExtendedMessageFormat("{a}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnterminatedFormatElementThrows() {
        // missing closing '}'
        new ExtendedMessageFormat("{0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnterminatedQuotedStringThrows() {
        // stray opening quote
        new ExtendedMessageFormat("'unclosed");
    }

    // -----------------------------------------------------------------------
    // Unsupported mutator methods
    // -----------------------------------------------------------------------
    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatUnsupported() {
        new ExtendedMessageFormat("{0}").setFormat(0, new MessageFormat("{0}"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatByArgumentIndexUnsupported() {
        new ExtendedMessageFormat("{0}").setFormatByArgumentIndex(0,
                new MessageFormat("{0}"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatsUnsupported() {
        new ExtendedMessageFormat("{0}").setFormats(new Format[]{new MessageFormat("{0}")});
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatsByArgumentIndexUnsupported() {
        new ExtendedMessageFormat("{0}").setFormatsByArgumentIndex(new Format[]{new MessageFormat("{0}")});
    }

    // -----------------------------------------------------------------------
    // Private method behavior exercised via reflection
    // -----------------------------------------------------------------------
    @Test
    public void testReadArgumentIndexValid() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        ParsePosition pos = new ParsePosition(0);
        // Simulate being positioned just after the opening '{'
        pos.setIndex(1);
        int index = (int) invokePrivate(emf, "readArgumentIndex",
                new Class[]{String.class, ParsePosition.class},
                "0,number}", pos);
        assertEquals(0, index);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadArgumentIndexInvalidDigitSequence() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        ParsePosition pos = new ParsePosition(0);
        pos.setIndex(1);
        // This will hit a non‑digit 'a' and should trigger an exception
        invokePrivate(emf, "readArgumentIndex",
                new Class[]{String.class, ParsePosition.class},
                "a}", pos);
    }

    @Test
    public void testParseFormatDescriptionSimple() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,number}");
        ParsePosition pos = new ParsePosition(0);
        // Position after the comma that starts the format description
        pos.setIndex(3);
        String desc = (String) invokePrivate(emf, "parseFormatDescription",
                new Class[]{String.class, ParsePosition.class},
                "{0,number}", pos);
        assertEquals("number", desc);
    }

    @Test
    public void testParseFormatDescriptionNestedBraces() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0,choice,0#zero|1#{0}}");
        ParsePosition pos = new ParsePosition(0);
        // Position after the first comma (start of the format description)
        pos.setIndex(3);
        String desc = (String) invokePrivate(emf, "parseFormatDescription",
                new Class[]{String.class, ParsePosition.class},
                "{0,choice,0#zero|1#{0}}", pos);
        assertEquals("choice,0#zero|1#{0}", desc);
    }

    @Test
    public void testInsertFormatsReconstruction() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        // Simulate a pattern that after core parsing became "Hello {0}"
        String basePattern = "Hello {0}";
        // custom description for the first format element
        ArrayList<String> custom = new ArrayList<>();
        custom.add("myfmt");
        String rebuilt = (String) invokePrivate(emf, "insertFormats",
                new Class[]{String.class, ArrayList.class},
                basePattern, custom);
        assertEquals("Hello {0, myfmt}", rebuilt);
    }

    @Test
    public void testSeekNonWsSkipsSpacesAndTabs() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        ParsePosition pos = new ParsePosition(0);
        String pattern = "  \t  123";
        // Position starts at 0; after calling seekNonWs it should point at '1'
        invokePrivate(emf, "seekNonWs",
                new Class[]{String.class, ParsePosition.class},
                pattern, pos);
        assertEquals(5, pos.getIndex()); // three spaces + tab + two spaces = 5
        assertEquals('1', pattern.charAt(pos.getIndex()));
    }

    @Test
    public void testNextAdvancesByOne() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        ParsePosition pos = new ParsePosition(2);
        invokePrivate(emf, "next",
                new Class[]{ParsePosition.class},
                pos);
        assertEquals(3, pos.getIndex());
    }

    @Test
    public void testAppendQuotedStringEscaping() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        ParsePosition pos = new ParsePosition(0);
        // pattern contains a quoted string with escaped quote:  'it''s'
        String pattern = "'it''s'";
        StringBuffer sb = new StringBuffer();
        invokePrivate(emf, "appendQuotedString",
                new Class[]{String.class, ParsePosition.class,
                        StringBuffer.class, boolean.class},
                pattern, pos, sb, true);
        // The result should be the unescaped content: it's
        assertEquals("it's", sb.toString());
        // Position should be after the closing quote
        assertEquals(pattern.length(), pos.getIndex());
    }

    @Test
    public void testGetQuotedStringJustConsumes() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        ParsePosition pos = new ParsePosition(0);
        String pattern = "'quoted'";
        // Should complete without exception and leave pos after the closing quote
        invokePrivate(emf, "getQuotedString",
                new Class[]{String.class, ParsePosition.class, boolean.class},
                pattern, pos, true);
        assertEquals(pattern.length(), pos.getIndex());
    }

    @Test
    public void testContainsElementsDetectsNonNull() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        ArrayList<Object> coll = new ArrayList<>();
        coll.add(null);
        coll.add("value");
        boolean result = (boolean) invokePrivate(emf, "containsElements",
                new Class[]{Collection.class}, coll);
        assertTrue(result);
    }

    @Test
    public void testContainsElementsAllNullOrEmpty() throws Exception {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        ArrayList<Object> coll = new ArrayList<>();
        coll.add(null);
        coll.add(null);
        boolean result = (boolean) invokePrivate(emf, "containsElements",
                new Class[]{Collection.class}, coll);
        assertFalse(result);
    }

    // -----------------------------------------------------------------------
    // Ensure toPattern reflects custom formats after applyPattern is called again
    // -----------------------------------------------------------------------
    @Test
    public void testReapplyPatternKeepsCustomFormats() {
        Map<String, FormatFactory> reg = new HashMap<>();
        reg.put("upper", new SimpleFactory());
        ExtendedMessageFormat emf = new ExtendedMessageFormat(
                "Start {0,upper} End", Locale.US, reg);
        // First formatting
        assertEquals("Start upper:hello End", emf.format(new Object[]{"hello"}));
        // Re‑apply a different pattern that still uses the same custom format
        emf.applyPattern("Again {0,upper}");
        assertEquals("Again upper:world", emf.format(new Object[]{"world"}));
        // toPattern must contain the custom description again
        assertEquals("Again {0, upper}", emf.toPattern());
    }
}
```
###Test END##