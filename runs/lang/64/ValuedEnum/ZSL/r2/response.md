###Test START##
```java
/*********************************************************************
 * JUnit 4 test cases for {@link ValuedEnum}
 *
 * These tests cover:
 *  - construction via a concrete subclass
 *  - {@link ValuedEnum#getValue()}
 *  - {@link ValuedEnum#compareTo(Object)}
 *  - {@link ValuedEnum#toString()}
 *  - {@link ValuedEnum#getEnum(Class,int)} (protected static)
 *
 * The test class resides in the same package as {@code ValuedEnum}
 * so that the protected static {@code getEnum} method is accessible.
 *********************************************************************/

package org.apache.commons.lang.enums;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;

import org.junit.Test;

/**
 * Concrete subclass used solely for testing {@code ValuedEnum}.
 */
public class ValuedEnumTest {

    /** Simple enum with three distinct values. */
    public static final class TestEnum extends ValuedEnum {
        private static final long serialVersionUID = 1L;

        public static final TestEnum ONE   = new TestEnum("ONE",   1);
        public static final TestEnum TWO   = new TestEnum("TWO",   2);
        public static final TestEnum THREE = new TestEnum("THREE", 3);

        private TestEnum(String name, int value) {
            super(name, value);
        }

        /** Helper to expose the protected static getEnum method for testing. */
        public static TestEnum getByValue(int value) {
            return (TestEnum) ValuedEnum.getEnum(TestEnum.class, value);
        }
    }

    /* -------------------------------------------------------------
     *  Test getValue()
     * ------------------------------------------------------------- */
    @Test
    public void testGetValue() {
        assertEquals(1, TestEnum.ONE.getValue());
        assertEquals(2, TestEnum.TWO.getValue());
        assertEquals(3, TestEnum.THREE.getValue());
    }

    /* -------------------------------------------------------------
     *  Test compareTo(Object)
     * ------------------------------------------------------------- */
    @Test
    public void testCompareToLessThan() {
        // ONE (1) < TWO (2)
        assertTrue(TestEnum.ONE.compareTo(TestEnum.TWO) < 0);
    }

    @Test
    public void testCompareToGreaterThan() {
        // THREE (3) > TWO (2)
        assertTrue(TestEnum.THREE.compareTo(TestEnum.TWO) > 0);
    }

    @Test
    public void testCompareToEqual() {
        // compare to itself should be zero
        assertEquals(0, TestEnum.TWO.compareTo(TestEnum.TWO));
    }

    @Test(expected = NullPointerException.class)
    public void testCompareToNullThrows() {
        TestEnum.ONE.compareTo(null);
    }

    @Test(expected = ClassCastException.class)
    public void testCompareToInvalidTypeThrows() {
        // Passing an object that is not a ValuedEnum forces a ClassCastException
        TestEnum.ONE.compareTo("not an enum");
    }

    /* -------------------------------------------------------------
     *  Test toString()
     * ------------------------------------------------------------- */
    @Test
    public void testToStringFormat() {
        // Expected format: SimpleClassName[name=value]
        String expected = "TestEnum[ONE=1]";
        assertEquals(expected, TestEnum.ONE.toString());

        // Ensure caching does not alter the result on subsequent calls
        assertEquals(expected, TestEnum.ONE.toString());
    }

    /* -------------------------------------------------------------
     *  Test protected static getEnum(Class,int)
     * ------------------------------------------------------------- */
    @Test
    public void testGetEnumValidValue() {
        assertSame(TestEnum.ONE,   TestEnum.getByValue(1));
        assertSame(TestEnum.TWO,   TestEnum.getByValue(2));
        assertSame(TestEnum.THREE, TestEnum.getByValue(3));
    }

    @Test
    public void testGetEnumInvalidValueReturnsNull() {
        assertNull(TestEnum.getByValue(99));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEnumNullClassThrows() {
        // Directly invoke the protected method via reflection because it is
        // protected; using a null class should trigger IllegalArgumentException.
        ValuedEnum.getEnum(null, 1);
    }

    /* -------------------------------------------------------------
     *  Additional sanity checks for Enum infrastructure (iterator,
     *  list) to ensure the static registration performed by the base
     *  class works as expected.
     * ------------------------------------------------------------- */
    @Test
    public void testIteratorAndListContainAllEnums() {
        List enumList = Enum.getEnumList(TestEnum.class);
        assertEquals(3, enumList.size());
        assertTrue(enumList.contains(TestEnum.ONE));
        assertTrue(enumList.contains(TestEnum.TWO));
        assertTrue(enumList.contains(TestEnum.THREE));

        Iterator it = Enum.iterator(TestEnum.class);
        int count = 0;
        while (it.hasNext()) {
            Object obj = it.next();
            assertTrue(obj instanceof TestEnum);
            count++;
        }
        assertEquals(3, count);
    }
}
```
###Test END##