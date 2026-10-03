###Test START##
package org.apache.commons.lang.enums;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;

import org.junit.Test;

/**
 * JUnit 4 test cases for {@link ValuedEnum}.
 *
 * The tests cover:
 * <ul>
 *   <li>Construction and {@link ValuedEnum#getValue()}</li>
 *   <li>{@link ValuedEnum#compareTo(Object)} – normal ordering, equality,
 *       {@link NullPointerException} for {@code null} and {@link ClassCastException}
 *       for objects of the wrong type.</li>
 *   <li>{@link ValuedEnum#getEnum(Class,int)} – successful lookup, lookup miss,
 *       and {@link IllegalArgumentException} when the class argument is {@code null}.</li>
 *   <li>{@link ValuedEnum#toString()} – format and memoisation.</li>
 * </ul>
 *
 * A concrete subclass {@code SampleValuedEnum} is declared in this file
 * because {@code ValuedEnum} is abstract and depends on the internal
 * {@code Enum} implementation from Apache Commons Lang.
 */
public class ValuedEnumTest {

    /** Simple concrete subclass used for testing. */
    public static final class SampleValuedEnum extends ValuedEnum {
        public static final SampleValuedEnum ONE   = new SampleValuedEnum("One",   1);
        public static final SampleValuedEnum TWO   = new SampleValuedEnum("Two",   2);
        public static final SampleValuedEnum THREE = new SampleValuedEnum("Three", 3);

        private SampleValuedEnum(String name, int value) {
            super(name, value);
        }

        /** Helper to retrieve by value using the protected method in ValuedEnum. */
        public static SampleValuedEnum getEnum(int value) {
            return (SampleValuedEnum) ValuedEnum.getEnum(SampleValuedEnum.class, value);
        }

        /** Expose the Enum list for sanity checks. */
        @SuppressWarnings("unchecked")
        public static List<SampleValuedEnum> getEnumList() {
            return Enum.getEnumList(SampleValuedEnum.class);
        }

        /** Expose the iterator for sanity checks. */
        public static Iterator<SampleValuedEnum> iterator() {
            return Enum.iterator(SampleValuedEnum.class);
        }
    }

    // ----------------------------------------------------------------------
    // getValue()
    // ----------------------------------------------------------------------
    @Test
    public void testGetValue() {
        assertEquals(1, SampleValuedEnum.ONE.getValue());
        assertEquals(2, SampleValuedEnum.TWO.getValue());
        assertEquals(3, SampleValuedEnum.THREE.getValue());
    }

    // ----------------------------------------------------------------------
    // compareTo(Object)
    // ----------------------------------------------------------------------
    @Test
    public void testCompareTo_LessThan() {
        assertTrue(SampleValuedEnum.ONE.compareTo(SampleValuedEnum.TWO) < 0);
        assertTrue(SampleValuedEnum.TWO.compareTo(SampleValuedEnum.THREE) < 0);
    }

    @Test
    public void testCompareTo_GreaterThan() {
        assertTrue(SampleValuedEnum.THREE.compareTo(SampleValuedEnum.ONE) > 0);
        assertTrue(SampleValuedEnum.TWO.compareTo(SampleValuedEnum.ONE) > 0);
    }

    @Test
    public void testCompareTo_Equality() {
        // compare to itself – should be zero
        assertEquals(0, SampleValuedEnum.ONE.compareTo(SampleValuedEnum.ONE));
    }

    @Test(expected = NullPointerException.class)
    public void testCompareTo_NullArgument() {
        SampleValuedEnum.ONE.compareTo(null);
    }

    @Test(expected = ClassCastException.class)
    public void testCompareTo_WrongType() {
        // Pass an object that is not a ValuedEnum – Enum's compareTo expects ValuedEnum
        SampleValuedEnum.ONE.compareTo("not an enum");
    }

    // ----------------------------------------------------------------------
    // getEnum(Class, int)
    // ----------------------------------------------------------------------
    @Test
    public void testGetEnumByValue_Found() {
        assertSame(SampleValuedEnum.ONE,   SampleValuedEnum.getEnum(1));
        assertSame(SampleValuedEnum.TWO,   SampleValuedEnum.getEnum(2));
        assertSame(SampleValuedEnum.THREE, SampleValuedEnum.getEnum(3));
    }

    @Test
    public void testGetEnumByValue_NotFound() {
        // value 99 does not exist – should return null
        assertNull(SampleValuedEnum.getEnum(99));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEnumByValue_NullClass() {
        // Directly invoke the protected static method via reflection because it is not
        // accessible with a null class argument from this test class otherwise.
        try {
            java.lang.reflect.Method m = ValuedEnum.class.getDeclaredMethod("getEnum", Class.class, int.class);
            m.setAccessible(true);
            m.invoke(null, new Object[] { null, Integer.valueOf(1) });
        } catch (Exception e) {
            // unwrap the cause – the test expects IllegalArgumentException to be thrown
            if (e.getCause() instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e.getCause();
            }
            // re‑throw any unexpected exception as a RuntimeException so the test fails
            throw new RuntimeException(e);
        }
    }

    // ----------------------------------------------------------------------
    // toString()
    // ----------------------------------------------------------------------
    @Test
    public void testToString_Format() {
        String s = SampleValuedEnum.ONE.toString();
        // Expected format: ShortClassName[Name=Value]
        assertTrue("toString does not contain class name", s.startsWith("SampleValuedEnum["));
        assertTrue("toString does not contain name", s.contains("One="));
        assertTrue("toString does not contain the numeric value", s.endsWith("1]"));
    }

    @Test
    public void testToString_Memoisation() {
        // The first call creates the string, subsequent calls should return the same
        // object reference (iToString is cached inside ValuedEnum). This is an
        // implementation detail but useful to assert that the cache works.
        String first = SampleValuedEnum.TWO.toString();
        String second = SampleValuedEnum.TWO.toString();
        assertSame("toString should be memoised and return the same instance", first, second);
    }

    // ----------------------------------------------------------------------
    // Enum list and iterator helpers – sanity checks that the underlying
    // Enum infrastructure works with our concrete subclass.
    // ----------------------------------------------------------------------
    @Test
    public void testEnumListContainsAllInstances() {
        List<SampleValuedEnum> list = SampleValuedEnum.getEnumList();
        assertEquals("Enum list should contain exactly three entries", 3, list.size());
        assertTrue(list.contains(SampleValuedEnum.ONE));
        assertTrue(list.contains(SampleValuedEnum.TWO));
        assertTrue(list.contains(SampleValuedEnum.THREE));
    }

    @Test
    public void testIteratorOrderMatchesValueOrdering() {
        Iterator<SampleValuedEnum> it = SampleValuedEnum.iterator();
        assertTrue(it.hasNext());
        assertSame(SampleValuedEnum.ONE, it.next());
        assertTrue(it.hasNext());
        assertSame(SampleValuedEnum.TWO, it.next());
        assertTrue(it.hasNext());
        assertSame(SampleValuedEnum.THREE, it.next());
        assertFalse(it.hasNext());
    }
}
###Test END##