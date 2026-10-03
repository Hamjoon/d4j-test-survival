###Test START##
```java
/**
 * JUnit 4 test suite for {@link org.apache.commons.lang.enums.ValuedEnum}.
 *
 * The tests cover:
 * <ul>
 *   <li>Basic construction and {@code getValue()}</li>
 *   <li>Ordering via {@code compareTo}</li>
 *   <li>Retrieval by integer value using the protected {@code getEnum(Class,int)}</li>
 *   <li>String representation and its caching behaviour</li>
 *   <li>Error handling – null arguments, wrong types and missing values</li>
 * </ul>
 *
 * A tiny concrete subclass {@code ColorEnum} is defined inside the test file
 * because {@code ValuedEnum} is abstract.  The subclass mirrors the usage
 * pattern shown in the javadoc of the original class.
 */
package org.apache.commons.lang.enums;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

/**
 * Concrete {@code ValuedEnum} implementation used only for testing.
 */
public final class ColorEnum extends ValuedEnum {

    public static final ColorEnum RED   = new ColorEnum("RED",   1);
    public static final ColorEnum GREEN = new ColorEnum("GREEN", 2);
    public static final ColorEnum BLUE  = new ColorEnum("BLUE",  3);

    private ColorEnum(String name, int value) {
        super(name, value);
    }

    /** Public façade for the protected static {@code getEnum(Class,int)}. */
    public static ColorEnum getEnum(int value) {
        return (ColorEnum) getEnum(ColorEnum.class, value);
    }

    /** Public façade for the protected static {@code getEnum(Class,String)} (inherited). */
    public static ColorEnum getEnum(String name) {
        return (ColorEnum) Enum.getEnum(ColorEnum.class, name);
    }

    public static List getEnumList() {
        return Enum.getEnumList(ColorEnum.class);
    }

    public static Iterator iterator() {
        return Enum.iterator(ColorEnum.class);
    }
}

/**
 * Another tiny {@code ValuedEnum} used to test {@code compareTo} type‑safety.
 */
final class AnotherEnum extends ValuedEnum {
    public static final AnotherEnum ONE = new AnotherEnum("ONE", 1);
    private AnotherEnum(String name, int value) {
        super(name, value);
    }
}

/**
 * Test class for {@link ValuedEnum}.
 */
public class ValuedEnumTest {

    /** -------------------------------------------------------------------- **/
    /**  BASIC ACCESSORS                                                       **/
    /** -------------------------------------------------------------------- **/

    @Test
    public void testGetValue() {
        assertEquals(1, ColorEnum.RED.getValue());
        assertEquals(2, ColorEnum.GREEN.getValue());
        assertEquals(3, ColorEnum.BLUE.getValue());
    }

    /** -------------------------------------------------------------------- **/
    /**  COMPARISON (compareTo)                                                **/
    /** -------------------------------------------------------------------- **/

    @Test
    public void testCompareTo_SameInstance() {
        assertEquals(0, ColorEnum.RED.compareTo(ColorEnum.RED));
    }

    @Test
    public void testCompareTo_LessThan() {
        assertTrue(ColorEnum.RED.compareTo(ColorEnum.GREEN) < 0);
        assertTrue(ColorEnum.GREEN.compareTo(ColorEnum.BLUE) < 0);
    }

    @Test
    public void testCompareTo_GreaterThan() {
        assertTrue(ColorEnum.BLUE.compareTo(ColorEnum.GREEN) > 0);
        assertTrue(ColorEnum.GREEN.compareTo(ColorEnum.RED) > 0);
    }

    @Test(expected = NullPointerException.class)
    public void testCompareTo_NullArgument() {
        ColorEnum.RED.compareTo(null);
    }

    @Test(expected = ClassCastException.class)
    public void testCompareTo_DifferentEnumType() {
        // This should throw because the other object is not a ValuedEnum of the same class.
        ColorEnum.RED.compareTo(AnotherEnum.ONE);
    }

    /** -------------------------------------------------------------------- **/
    /**  LOOKUP BY INT VALUE (protected static getEnum)                        **/
    /** -------------------------------------------------------------------- **/

    @Test
    public void testGetEnumByValue_Found() {
        assertSame(ColorEnum.RED,   ColorEnum.getEnum(1));
        assertSame(ColorEnum.GREEN, ColorEnum.getEnum(2));
        assertSame(ColorEnum.BLUE,  ColorEnum.getEnum(3));
    }

    @Test
    public void testGetEnumByValue_NotFound() {
        assertNull(ColorEnum.getEnum(99));
    }

    @Test
    public void testGetEnumByValue_NullClass_ThrowsIllegalArgumentException()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // Use reflection to call the protected static method with a null class argument.
        Method m = ValuedEnum.class.getDeclaredMethod("getEnum", Class.class, int.class);
        m.setAccessible(true);
        try {
            m.invoke(null, (Object) null, 1);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (InvocationTargetException ite) {
            // The target exception should be IllegalArgumentException.
            assertTrue(ite.getCause() instanceof IllegalArgumentException);
            assertEquals("The Enum Class must not be null", ite.getCause().getMessage());
        }
    }

    /** -------------------------------------------------------------------- **/
    /**  STRING REPRESENTATION (toString)                                      **/
    /** -------------------------------------------------------------------- **/

    @Test
    public void testToString_FormatAndCaching() {
        String expected = "ColorEnum[RED=1]";
        // First call builds the string.
        String first = ColorEnum.RED.toString();
        assertEquals(expected, first);

        // Second call should return the same cached instance.
        String second = ColorEnum.RED.toString();
        assertSame(first, second);
    }

    /** -------------------------------------------------------------------- **/
    /**  ITERATION AND LIST HELPERS (inherited from Enum)                      **/
    /** -------------------------------------------------------------------- **/

    @Test
    public void testEnumListContainsAllValues() {
        List list = ColorEnum.getEnumList();
        assertEquals(3, list.size());
        assertTrue(list.contains(ColorEnum.RED));
        assertTrue(list.contains(ColorEnum.GREEN));
        assertTrue(list.contains(ColorEnum.BLUE));
    }

    @Test
    public void testIterator_TraversesAllEnumsInDeclarationOrder() {
        Iterator it = ColorEnum.iterator();
        assertTrue(it.hasNext());
        assertEquals(ColorEnum.RED,   it.next());
        assertTrue(it.hasNext());
        assertEquals(ColorEnum.GREEN, it.next());
        assertTrue(it.hasNext());
        assertEquals(ColorEnum.BLUE,  it.next());
        assertFalse(it.hasNext());
    }
}
```
###Test END##