/**
 * JUnit 4 test suite for {@link org.apache.commons.lang.text.ExtendedMessageFormat}.
 *
 * The tests cover:
 * <ul>
 *   <li>Construction with and without a registry.</li>
 *   <li>Basic pattern handling (toPattern, applyPattern).</li>
 *   <li>Custom format factories.</li>
 *   <li>Parsing helpers (readArgumentIndex, parseFormatDescription, insertFormats,
 *       seekNonWs, next, appendQuotedString, getQuotedString, containsElements).</li>
 *   <li>Unsupported mutator methods.</li>
 *   <li>Error scenarios (unterminated elements, illegal argument indexes, etc.).</li>
 * </ul>
 *
 * The test class is deliberately exhaustive – each public method (and each
 * private helper that can be exercised indirectly) gets at least one positive
 * test and one negative test where appropriate.
 */
package org.apache.commons.lang.text;

import static org.junit.Assert.*;

import java.text.Format;
import java.text.MessageFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * Test cases for {@link ExtendedMessageFormat}.
 */
public class ExtendedMessageFormatTest {

    /** Simple {@link FormatFactory} that creates a format which upper‑cases strings. */
    private static class UpperCaseFormatFactory implements FormatFactory {
        @Override
        public Format getFormat(String name, String arguments, Locale locale) {
            // arguments are ignored – we only need a format that upper‑cases.
            return new Format() {
                @Override
                public StringBuffer format(Object obj, StringBuffer toAppendTo,
                        java.text.FieldPosition pos) {
                    return toAppendTo.append(String.valueOf(obj).toUpperCase(locale));
                }

                @Override
                public Object parseObject(String source, ParsePosition pos) {
                    // parsing is not needed for the tests – just return the raw string.
                    int start = pos.getIndex();
                    pos.setIndex(source.length());
                    return source.substring(start);
                }
            };
        }
    }

    private Map<String, FormatFactory> registry;

    @Before
    public void setUp() {
        registry = new HashMap<>();
        registry.put("upper", new UpperCaseFormatFactory());
    }

    /* ---------------------------------------------------------------------- */
    /*  Constructors & basic toPattern / applyPattern behavior                */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testDefaultConstructorUsesSuperPattern() {
        String pattern = "Hello {0}";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern);
        assertEquals(pattern, emf.toPattern());
        assertEquals("Hello World", emf.format(new Object[] { "World" }));
    }

    @Test
    public void testConstructorWithLocaleAndRegistry() {
        String pattern = "Value: {0,upper}";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern, Locale.US, registry);
        assertEquals(pattern, emf.toPattern());
        assertEquals("Value: TEST", emf.format(new Object[] { "test" }));
    }

    @Test
    public void testApplyPatternReplacesCustomFormats() {
        String pattern = "A:{0,upper} B:{1}";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern, Locale.US, registry);
        // toPattern must contain the original custom format description
        assertEquals(pattern, emf.toPattern());

        // The underlying MessageFormat (without custom factories) would have
        // left the format description out – ensure the custom format still works.
        assertEquals("A:FOO B:bar", emf.format(new Object[] { "foo", "bar" }));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testApplyPatternThrowsOnUnreadableElement() {
        // Missing closing brace after custom format description
        new ExtendedMessageFormat("Broken {0,upper", Locale.US, registry);
    }

    @Test
    public void testApplyPatternWithoutRegistryFallsBackToSuper() {
        String pattern = "Number: {0,number}";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern, Locale.US, null);
        // MessageFormat would format the number according to the locale.
        assertEquals("Number: 1,234.56", emf.format(new Object[] { 1234.56 }));
    }

    /* ---------------------------------------------------------------------- */
    /*  Unsupported mutator methods                                            */
    /* ---------------------------------------------------------------------- */

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatIsUnsupported() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        emf.setFormat(0, new MessageFormat("{0}"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatByArgumentIndexIsUnsupported() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        emf.setFormatByArgumentIndex(0, new MessageFormat("{0}"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatsIsUnsupported() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        emf.setFormats(new Format[] { new MessageFormat("{0}") });
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetFormatsByArgumentIndexIsUnsupported() {
        ExtendedMessageFormat emf = new ExtendedMessageFormat("{0}");
        emf.setFormatsByArgumentIndex(new Format[] { new MessageFormat("{0}") });
    }

    /* ---------------------------------------------------------------------- */
    /*  Private helper methods – exercised via reflection or indirect calls   */
    /* ---------------------------------------------------------------------- */

    /**
     * Helper to invoke the private {@code readArgumentIndex} method.
     */
    private int invokeReadArgumentIndex(String pattern, int startPos) throws Exception {
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("readArgumentIndex", String.class, ParsePosition.class);
        m.setAccessible(true);
        ParsePosition pos = new ParsePosition(startPos);
        return (Integer) m.invoke(new ExtendedMessageFormat("{0}"), pattern, pos);
    }

    /**
     * Helper to invoke the private {@code parseFormatDescription} method.
     */
    private String invokeParseFormatDescription(String pattern, int startPos) throws Exception {
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("parseFormatDescription", String.class, ParsePosition.class);
        m.setAccessible(true);
        ParsePosition pos = new ParsePosition(startPos);
        return (String) m.invoke(new ExtendedMessageFormat("{0}"), pattern, pos);
    }

    @Test
    public void testReadArgumentIndexSimple() throws Exception {
        assertEquals(0, invokeReadArgumentIndex("{0}", 1));
        assertEquals(12, invokeReadArgumentIndex("{  12 }", 1));
    }

    @Test
    public void testReadArgumentIndexWithWhitespaceAndComma() throws Exception {
        // Pattern: {  5 ,upper}
        assertEquals(5, invokeReadArgumentIndex("{ 5 ,upper}", 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadArgumentIndexInvalidNumber() throws Exception {
        // Non‑numeric argument index triggers an IllegalArgumentException.
        invokeReadArgumentIndex("{abc}", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadArgumentIndexUnterminated() throws Exception {
        // Missing closing brace – the parser runs off the end.
        invokeReadArgumentIndex("{123", 1);
    }

    @Test
    public void testParseFormatDescriptionSimple() throws Exception {
        // Pattern: {0,upper}
        String desc = invokeParseFormatDescription("{0,upper}", 3);
        assertEquals("upper", desc);
    }

    @Test
    public void testParseFormatDescriptionWithNestedBraces() throws Exception {
        // Pattern: {0,choice{0#zero|1#one{inner}}}
        String pattern = "{0,choice{0#zero|1#one{inner}}}";
        String desc = invokeParseFormatDescription(pattern, 3);
        assertEquals("choice{0#zero|1#one{inner}}", desc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseFormatDescriptionUnterminated() throws Exception {
        // No closing '}'
        invokeParseFormatDescription("{0,upper", 3);
    }

    @Test
    public void testInsertFormatsNoCustomPatterns() throws Exception {
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("insertFormats", String.class, ArrayList.class);
        m.setAccessible(true);
        String result = (String) m.invoke(new ExtendedMessageFormat("{0}"),
                "Hello {0}", new ArrayList<>());
        assertEquals("Hello {0}", result);
    }

    @Test
    public void testInsertFormatsWithCustomPatterns() throws Exception {
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("insertFormats", String.class, ArrayList.class);
        m.setAccessible(true);
        ArrayList<String> custom = new ArrayList<>();
        custom.add("upper");
        String result = (String) m.invoke(new ExtendedMessageFormat("{0,upper}"),
                "Test {0}", custom);
        assertEquals("Test {0,upper}", result);
    }

    @Test
    public void testSeekNonWsSkipsSpacesAndTabs() throws Exception {
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("seekNonWs", String.class, ParsePosition.class);
        m.setAccessible(true);
        ParsePosition pos = new ParsePosition(0);
        m.invoke(new ExtendedMessageFormat("{0}"), " \t  abc", pos);
        assertEquals(4, pos.getIndex()); // points at 'a'
    }

    @Test
    public void testNextAdvancesByOne() throws Exception {
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("next", ParsePosition.class);
        m.setAccessible(true);
        ParsePosition pos = new ParsePosition(5);
        m.invoke(new ExtendedMessageFormat("{0}"), pos);
        assertEquals(6, pos.getIndex());
    }

    @Test
    public void testAppendQuotedStringSimple() throws Exception {
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("appendQuotedString",
                        String.class, ParsePosition.class, StringBuffer.class, boolean.class);
        m.setAccessible(true);
        ParsePosition pos = new ParsePosition(0);
        StringBuffer sb = new StringBuffer();
        // pattern: 'abc'xyz   -> quoted part is abc, then we stop at closing quote.
        m.invoke(new ExtendedMessageFormat("{0}"), "'abc'xyz", pos, sb, true);
        assertEquals("abc", sb.toString());
        // Position should now be after the closing quote (index 5)
        assertEquals(5, pos.getIndex());
    }

    @Test
    public void testAppendQuotedStringEscapedQuote() throws Exception {
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("appendQuotedString",
                        String.class, ParsePosition.class, StringBuffer.class, boolean.class);
        m.setAccessible(true);
        ParsePosition pos = new ParsePosition(0);
        StringBuffer sb = new StringBuffer();
        // pattern: '' becomes a single quote when escapingOn==true
        m.invoke(new ExtendedMessageFormat("{0}"), "''", pos, sb, true);
        assertEquals("'", sb.toString());
        assertEquals(2, pos.getIndex());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAppendQuotedStringUnterminatedThrows() throws Exception {
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("appendQuotedString",
                        String.class, ParsePosition.class, StringBuffer.class, boolean.class);
        m.setAccessible(true);
        ParsePosition pos = new ParsePosition(0);
        // missing closing quote
        m.invoke(new ExtendedMessageFormat("{0}"), "'unclosed", pos, new StringBuffer(), true);
    }

    @Test
    public void testGetQuotedStringConsumesWithoutAppending() throws Exception {
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("getQuotedString",
                        String.class, ParsePosition.class, boolean.class);
        m.setAccessible(true);
        ParsePosition pos = new ParsePosition(0);
        // pattern: 'text'
        m.invoke(new ExtendedMessageFormat("{0}"), "'text'", pos, true);
        // After consuming, index should be after the closing quote (5)
        assertEquals(5, pos.getIndex());
    }

    @Test
    public void testContainsElementsVariousScenarios() throws Exception {
        java.lang.reflect.Method m = ExtendedMessageFormat.class
                .getDeclaredMethod("containsElements", java.util.Collection.class);
        m.setAccessible(true);
        // null collection
        assertFalse((Boolean) m.invoke(new ExtendedMessageFormat("{0}"), (Object) null));
        // empty collection
        assertFalse((Boolean) m.invoke(new ExtendedMessageFormat("{0}"), Collections.emptyList()));
        // collection with all nulls
        ArrayList<Object> allNull = new ArrayList<>();
        allNull.add(null);
        allNull.add(null);
        assertFalse((Boolean) m.invoke(new ExtendedMessageFormat("{0}"), allNull));
        // collection with a non‑null element
        ArrayList<Object> mixed = new ArrayList<>();
        mixed.add(null);
        mixed.add("something");
        assertTrue((Boolean) m.invoke(new ExtendedMessageFormat("{0}"), mixed));
    }

    /* ---------------------------------------------------------------------- */
    /*  Integration style tests – real usage of custom formats                */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testMultipleCustomFormatsInOnePattern() {
        String pattern = "{0,upper} - {1,upper}";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern, Locale.US, registry);
        assertEquals("HELLO - WORLD", emf.format(new Object[] { "hello", "world" }));
    }

    @Test
    public void testCustomFormatWithArguments() {
        // Register a factory that accepts an argument (prefix) and adds it.
        registry.put("prefixed", new FormatFactory() {
            @Override
            public Format getFormat(String name, String arguments, Locale locale) {
                final String prefix = arguments == null ? "" : arguments;
                return new Format() {
                    @Override
                    public StringBuffer format(Object obj, StringBuffer toAppendTo,
                            java.text.FieldPosition pos) {
                        return toAppendTo.append(prefix).append(String.valueOf(obj));
                    }

                    @Override
                    public Object parseObject(String source, ParsePosition pos) {
                        int start = pos.getIndex();
                        pos.setIndex(source.length());
                        return source.substring(start);
                    }
                };
            }
        });

        String pattern = "Value: {0,prefixed,>> }";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern, Locale.US, registry);
        assertEquals("Value: >> test", emf.format(new Object[] { "test" }));
        // Ensure toPattern returns the original pattern (including arguments)
        assertEquals(pattern, emf.toPattern());
    }

    @Test
    public void testQuotedLiteralsAndEscapedQuotesInPattern() {
        // Pattern contains a quoted literal and an escaped quote.
        String pattern = "He said ''{0}'' and then \"{1}\"";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern);
        String result = emf.format(new Object[] { "X", "Y" });
        // The doubled single quotes become a single quote literal.
        assertEquals("He said 'X' and then \"Y\"", result);
    }

    @Test
    public void testPatternWithNestedChoiceAndCustomFormat() {
        // Choose between two formats, the second uses a custom format.
        registry.put("upper", new UpperCaseFormatFactory());
        String pattern = "{0,choice,0#none|1#{1,upper}}";
        ExtendedMessageFormat emf = new ExtendedMessageFormat(pattern, Locale.US, registry);
        assertEquals("none", emf.format(new Object[] { 0, "ignored" }));
        assertEquals("HELLO", emf.format(new Object[] { 1, "hello" }));
    }
}
