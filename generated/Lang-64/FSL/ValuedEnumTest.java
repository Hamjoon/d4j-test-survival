package org.apache.commons.lang.enums;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Test suite for {@link ValuedEnum}.
 *
 * The abstract class {@code ValuedEnum} cannot be instantiated directly,
 * therefore a simple concrete subclass {@code DummyEnum} is defined inside
 * this test file.  The subclass mirrors the pattern shown in the Javadoc
 * of {@code ValuedEnum} and provides a public static accessor for the
 * protected {@code getEnum(Class,int)} method so that it can be exercised
 * from the tests.
 */
public class ValuedEnumTest {

    /** Simple concrete implementation of {@link ValuedEnum} for testing. */
    public static final class DummyEnum extends ValuedEnum {
        private static final long serialVersionUID = 1L;

        public static final DummyEnum ONE   = new DummyEnum("ONE",   1);
        public static final DummyEnum TWO   = new DummyEnum("TWO",   2);
        public static final DummyEnum TEN   = new DummyEnum("TEN",  10);
        public static final DummyEnum NEG   = new DummyEnum("NEG", -5);

        private DummyEnum(String name, int value) {
            super(name, value);
        }

        /** Public wrapper around the protected {@code getEnum} method. */
        public static DummyEnum getEnum(int value) {
            return (DummyEnum) getEnum(DummyEnum.class, value);
        }

        /** Exposes the protected method with a {@code null} class argument
         * to verify that the correct exception is thrown. */
        public static Enum getEnumWithNullClass(int value) {
            // deliberately passing null to trigger IllegalArgumentException
            return getEnum(null, value);
        }
    }

    // ----------------------------------------------------------------------
    // Tests for the instance behaviour (getValue, compareTo, toString)
    // ----------------------------------------------------------------------

    @Test
    public void testGetValue() {
        assertEquals(1, DummyEnum.ONE.getValue());
        assertEquals(2, DummyEnum.TWO.getValue());
        assertEquals(10, DummyEnum.TEN.getValue());
        assertEquals(-5, DummyEnum.NEG.getValue());
    }

    @Test
    public void testCompareToLessThan() {
        // ONE < TWO
        assertTrue(DummyEnum.ONE.compareTo(DummyEnum.TWO) < 0);
        // NEG < ONE
        assertTrue(DummyEnum.NEG.compareTo(DummyEnum.ONE) < 0);
    }

    @Test
    public void testCompareToGreaterThan() {
        // TEN > TWO
        assertTrue(DummyEnum.TEN.compareTo(DummyEnum.TWO) > 0);
        // ONE > NEG
        assertTrue(DummyEnum.ONE.compareTo(DummyEnum.NEG) > 0);
    }

    @Test
    public void testCompareToEqual() {
        // a value compared to itself must be zero
        assertEquals(0, DummyEnum.TWO.compareTo(DummyEnum.TWO));
    }

    @Test(expected = ClassCastException.class)
    public void testCompareToWithDifferentTypeThrows() {
        // Comparing to an object that is not a ValuedEnum should throw CCE
        DummyEnum.ONE.compareTo("not an enum");
    }

    @Test(expected = NullPointerException.class)
    public void testCompareToWithNullThrows() {
        DummyEnum.ONE.compareTo(null);
    }

    @Test
    public void testToStringFormat() {
        // Expected format: ShortClassName[name=value]
        // ShortClassName for DummyEnum is "DummyEnum"
        assertEquals("DummyEnum[ONE=1]", DummyEnum.ONE.toString());
        assertEquals("DummyEnum[TWO=2]", DummyEnum.TWO.toString());
        assertEquals("DummyEnum[TEN=10]", DummyEnum.TEN.toString());
        assertEquals("DummyEnum[NEG=-5]", DummyEnum.NEG.toString());
    }

    // ----------------------------------------------------------------------
    // Tests for the static helper method getEnum(Class,int)
    // ----------------------------------------------------------------------

    @Test
    public void testGetEnumByValueExists() {
        assertSame(DummyEnum.ONE, DummyEnum.getEnum(1));
        assertSame(DummyEnum.TWO, DummyEnum.getEnum(2));
        assertSame(DummyEnum.TEN, DummyEnum.getEnum(10));
        assertSame(DummyEnum.NEG, DummyEnum.getEnum(-5));
    }

    @Test
    public void testGetEnumByValueNotFoundReturnsNull() {
        assertNull(DummyEnum.getEnum(999)); // no enum with value 999
        assertNull(DummyEnum.getEnum(0));   // no enum with value 0
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEnumWithNullClassThrowsIllegalArgumentException() {
        // The wrapper method deliberately passes a null class argument.
        DummyEnum.getEnumWithNullClass(1);
    }
}