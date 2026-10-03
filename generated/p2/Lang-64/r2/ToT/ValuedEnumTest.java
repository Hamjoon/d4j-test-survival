package org.apache.commons.lang.enums;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * JUnit 4 test cases for {@link ValuedEnum}.
 *
 * The tests cover:
 * <ul>
 *   <li>normal and error handling of {@code getEnum(Class,int)}</li>
 *   <li>value retrieval via {@code getValue()}</li>
 *   <li>ordering/comparison via {@code compareTo(Object)}</li>
 *   <li>string representation via {@code toString()}</li>
 * </ul>
 *
 * A small concrete subclass {@code DummyEnum} is defined for the
 * purpose of the tests together with a second subclass {@code AnotherEnum}
 * to verify type‑safety of {@code compareTo}.
 */
public class ValuedEnumTest {

    /**
     * Simple concrete implementation of {@link ValuedEnum} used in the tests.
     */
    public static final class DummyEnum extends ValuedEnum {
        public static final DummyEnum A = new DummyEnum("A", 1);
        public static final DummyEnum B = new DummyEnum("B", 2);
        public static final DummyEnum C = new DummyEnum("C", 3);

        private DummyEnum(String name, int value) {
            super(name, value);
        }

        /** Helper to expose the protected static method for the test class. */
        public static Enum getEnumByValue(int value) {
            return ValuedEnum.getEnum(DummyEnum.class, value);
        }
    }

    /**
     * Second concrete implementation used to test {@code compareTo} type checking.
     */
    public static final class AnotherEnum extends ValuedEnum {
        public static final AnotherEnum X = new AnotherEnum("X", 10);

        private AnotherEnum(String name, int value) {
            super(name, value);
        }
    }

    // -----------------------------------------------------------------------
    //  Tests for the static getEnum(Class,int) method
    // -----------------------------------------------------------------------

    @Test
    public void testGetEnumValidValue() {
        assertSame("Should return the enum instance for a valid value",
                DummyEnum.A, DummyEnum.getEnumByValue(1));
        assertSame(DummyEnum.B, DummyEnum.getEnumByValue(2));
        assertSame(DummyEnum.C, DummyEnum.getEnumByValue(3));
    }

    @Test
    public void testGetEnumInvalidValueReturnsNull() {
        assertNull("Non‑existent value must return null", DummyEnum.getEnumByValue(99));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEnumNullClassThrowsException() {
        // Directly invoke the protected method via reflection because the
        // method is protected; the test class is in the same package, so we
        // can call it through a dummy subclass.
        ValuedEnum.getEnum(null, 1);
    }

    // -----------------------------------------------------------------------
    //  Tests for getValue()
    // -----------------------------------------------------------------------

    @Test
    public void testGetValue() {
        assertEquals("Enum A should have value 1", 1, DummyEnum.A.getValue());
        assertEquals("Enum B should have value 2", 2, DummyEnum.B.getValue());
        assertEquals("Enum C should have value 3", 3, DummyEnum.C.getValue());
    }

    // -----------------------------------------------------------------------
    //  Tests for compareTo(Object)
    // -----------------------------------------------------------------------

    @Test
    public void testCompareToLessThan() {
        assertTrue("A (1) should be less than B (2)",
                DummyEnum.A.compareTo(DummyEnum.B) < 0);
    }

    @Test
    public void testCompareToGreaterThan() {
        assertTrue("C (3) should be greater than B (2)",
                DummyEnum.C.compareTo(DummyEnum.B) > 0);
    }

    @Test
    public void testCompareToEqual() {
        assertEquals("An enum compared to itself must be zero",
                0, DummyEnum.B.compareTo(DummyEnum.B));
    }

    @Test(expected = ClassCastException.class)
    public void testCompareToDifferentEnumTypeThrowsException() {
        DummyEnum.A.compareTo(AnotherEnum.X);
    }

    @Test(expected = NullPointerException.class)
    public void testCompareToNullThrowsException() {
        DummyEnum.A.compareTo(null);
    }

    // -----------------------------------------------------------------------
    //  Tests for toString()
    // -----------------------------------------------------------------------

    @Test
    public void testToStringFormat() {
        String expectedA = "DummyEnum[A=1]";
        String expectedB = "DummyEnum[B=2]";
        String expectedC = "DummyEnum[C=3]";

        assertEquals("toString() for A does not match expected format",
                expectedA, DummyEnum.A.toString());
        assertEquals("toString() for B does not match expected format",
                expectedB, DummyEnum.B.toString());
        assertEquals("toString() for C does not match expected format",
                expectedC, DummyEnum.C.toString());
    }

    @Test
    public void testToStringCaching() {
        // The first call creates the cached string; subsequent calls must return the same value.
        String first = DummyEnum.A.toString();
        String second = DummyEnum.A.toString();
        assertSame("Subsequent toString() calls should return the cached string instance",
                first, second);
    }

    // -----------------------------------------------------------------------
    //  Additional sanity checks
    // -----------------------------------------------------------------------

    @Test
    public void testEnumClassIsCorrectInToString() {
        // Ensure that the class name used in toString() is the short name,
        // i.e., without package prefix.
        String toString = DummyEnum.B.toString();
        assertTrue("toString must contain the short class name",
                toString.startsWith("DummyEnum["));
    }
}
