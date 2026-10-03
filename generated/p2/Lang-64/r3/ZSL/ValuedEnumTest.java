/**
 * JUnit 4 test suite for {@link org.apache.commons.lang.enums.ValuedEnum}.
 *
 * The tests cover:
 * <ul>
 *   <li>Construction and {@code getValue()}</li>
 *   <li>Static {@code getEnum(Class,int)} behaviour (including error handling)</li>
 *   <li>Comparable contract via {@code compareTo(Object)}</li>
 *   <li>String representation via {@code toString()}</li>
 * </ul>
 *
 * The Apache Commons Lang {@code Enum} class (the superclass of {@code ValuedEnum})
 * is abstract, therefore a concrete test subclass is created inside this test file.
 *
 * @author  OpenAI ChatGPT
 */
package org.apache.commons.lang.enums;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

/**
 * Concrete subclass of {@link ValuedEnum} used only for testing.
 */
public final class ValuedEnumTest {

    /** Simple enum with three distinct values. */
    public static final class TestValuedEnum extends ValuedEnum {
        private static final long serialVersionUID = 1L;

        public static final TestValuedEnum ONE   = new TestValuedEnum("ONE",   1);
        public static final TestValuedEnum TWO   = new TestValuedEnum("TWO",   2);
        public static final TestValuedEnum THREE = new TestValuedEnum("THREE", 3);

        private TestValuedEnum(String name, int value) {
            super(name, value);
        }

        /** Public wrapper for the protected static {@code getEnum(Class,int)}. */
        public static TestValuedEnum getEnum(int value) {
            return (TestValuedEnum) ValuedEnum.getEnum(TestValuedEnum.class, value);
        }

        /** Public wrapper that deliberately passes {@code null} to trigger the
         *  IllegalArgumentException in {@code ValuedEnum#getEnum(Class,int)}. */
        public static TestValuedEnum getEnumWithNullClass(int value) {
            return (TestValuedEnum) ValuedEnum.getEnum(null, value);
        }

        /** Convenience methods mirroring the example in the Javadoc. */
        public static List getEnumList() {
            return Enum.getEnumList(TestValuedEnum.class);
        }
    }

    /**
     * A dummy subclass of {@link Enum} that is **not** a {@code ValuedEnum}.
     * Used to verify that {@code compareTo} throws {@code ClassCastException}
     * when given an incompatible type.
     */
    public static final class SimpleEnum extends Enum {
        private static final long serialVersionUID = 1L;
        public static final SimpleEnum A = new SimpleEnum("A");

        private SimpleEnum(String name) {
            super(name);
        }
    }

    /* --------------------------------------------------------------------- */
    /*  Tests for instance construction and getValue()                        */
    /* --------------------------------------------------------------------- */

    @Test
    public void testGetValue() {
        assertEquals(1, TestValuedEnum.ONE.getValue());
        assertEquals(2, TestValuedEnum.TWO.getValue());
        assertEquals(3, TestValuedEnum.THREE.getValue());
    }

    /* --------------------------------------------------------------------- */
    /*  Tests for the protected static getEnum(Class,int) method               */
    /* --------------------------------------------------------------------- */

    @Test
    public void testStaticGetEnumFound() {
        assertSame(TestValuedEnum.ONE, TestValuedEnum.getEnum(1));
        assertSame(TestValuedEnum.TWO, TestValuedEnum.getEnum(2));
        assertSame(TestValuedEnum.THREE, TestValuedEnum.getEnum(3));
    }

    @Test
    public void testStaticGetEnumNotFound() {
        assertNull("No enum should be returned for an unknown value", TestValuedEnum.getEnum(999));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStaticGetEnumNullClass() {
        // This call should throw IllegalArgumentException because the enumClass argument is null.
        TestValuedEnum.getEnumWithNullClass(1);
    }

    /* --------------------------------------------------------------------- */
    /*  Tests for compareTo()                                                 */
    /* --------------------------------------------------------------------- */

    @Test
    public void testCompareToLessThan() {
        assertTrue(TestValuedEnum.ONE.compareTo(TestValuedEnum.TWO) < 0);
    }

    @Test
    public void testCompareToGreaterThan() {
        assertTrue(TestValuedEnum.THREE.compareTo(TestValuedEnum.TWO) > 0);
    }

    @Test
    public void testCompareToEqual() {
        assertEquals(0, TestValuedEnum.TWO.compareTo(TestValuedEnum.TWO));
    }

    @Test(expected = NullPointerException.class)
    public void testCompareToNull() {
        TestValuedEnum.ONE.compareTo(null);
    }

    @Test(expected = ClassCastException.class)
    public void testCompareToIncompatibleType() {
        // Passing an object that is not a ValuedEnum should result in ClassCastException.
        TestValuedEnum.ONE.compareTo(SimpleEnum.A);
    }

    /* --------------------------------------------------------------------- */
    /*  Tests for toString()                                                 */
    /* --------------------------------------------------------------------- */

    @Test
    public void testToStringFormat() {
        String s = TestValuedEnum.ONE.toString();
        // Expected format: "TestValuedEnum[ONE=1]"
        assertTrue("String representation should contain the short class name",
                s.startsWith("TestValuedEnum["));
        assertTrue("String representation should contain name and value",
                s.contains("ONE=1"));
        assertTrue("String should end with a closing bracket", s.endsWith("]"));
    }

    @Test
    public void testToStringCaching() {
        // The first call creates the cached string.
        String first = TestValuedEnum.TWO.toString();
        // Subsequent calls should return the exact same object (cached value).
        String second = TestValuedEnum.TWO.toString();
        assertSame("toString should cache its result", first, second);
    }

    /* --------------------------------------------------------------------- */
    /*  Additional sanity checks                                            */
    /* --------------------------------------------------------------------- */

    @Test
    public void testEnumListContainsAllInstances() {
        List list = TestValuedEnum.getEnumList();
        // The list should contain exactly the three defined instances.
        assertEquals("Enum list size should be 3", 3, list.size());
        assertTrue(list.contains(TestValuedEnum.ONE));
        assertTrue(list.contains(TestValuedEnum.TWO));
        assertTrue(list.contains(TestValuedEnum.THREE));
    }
}
