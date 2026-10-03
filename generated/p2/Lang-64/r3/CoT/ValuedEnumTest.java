package org.apache.commons.lang.enums;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * JUnit 4 test suite for {@link ValuedEnum}.
 *
 * A tiny concrete subclass {@link SampleEnum} is defined inside the test
 * file so that we can instantiate enum values and invoke the protected
 * static {@code getEnum} method.
 */
public class ValuedEnumTest {

    /** Simple concrete implementation used only for testing. */
    public static final class SampleEnum extends ValuedEnum {
        private static final long serialVersionUID = 1L;

        public static final SampleEnum ONE = new SampleEnum("ONE", 1);
        public static final SampleEnum TWO = new SampleEnum("TWO", 2);

        private SampleEnum(String name, int value) {
            super(name, value);
        }

        /** Helper that forwards to the protected static method in ValuedEnum. */
        public static SampleEnum getEnum(int value) {
            return (SampleEnum) ValuedEnum.getEnum(SampleEnum.class, value);
        }
    }

    // -----------------------------------------------------------------
    // BASIC FUNCTIONALITY TESTS
    // -----------------------------------------------------------------

    @Test
    public void testGetValue() {
        assertEquals(1, SampleEnum.ONE.getValue());
        assertEquals(2, SampleEnum.TWO.getValue());
    }

    @Test
    public void testCompareToLess() {
        assertTrue("ONE should be less than TWO", SampleEnum.ONE.compareTo(SampleEnum.TWO) < 0);
    }

    @Test
    public void testCompareToGreater() {
        assertTrue("TWO should be greater than ONE", SampleEnum.TWO.compareTo(SampleEnum.ONE) > 0);
    }

    @Test
    public void testCompareToEqual() {
        assertEquals("Same instance must compare as equal", 0, SampleEnum.ONE.compareTo(SampleEnum.ONE));
    }

    @Test
    public void testToString() {
        String s = SampleEnum.ONE.toString();               // e.g. "SampleEnum[ONE=1]"
        assertTrue("String should contain class name and value",
                s.matches("SampleEnum\\[ONE=1\\]"));
        // second call must return the *same* cached object
        assertSame("toString should be cached", s, SampleEnum.ONE.toString());
    }

    // -----------------------------------------------------------------
    // EDGE / EXCEPTION CASES
    // -----------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void testGetEnumNullClass() {
        // The protected static method is visible because the test lives in the same package.
        ValuedEnum.getEnum(null, 1);
    }

    @Test
    public void testGetEnumValid() {
        assertSame("Lookup by existing value must return the same instance",
                SampleEnum.ONE, SampleEnum.getEnum(1));
        assertSame("Lookup by existing value must return the same instance",
                SampleEnum.TWO, SampleEnum.getEnum(2));
    }

    @Test
    public void testGetEnumNotFound() {
        assertNull("Lookup for a non‑existing value must return null", SampleEnum.getEnum(99));
    }

    @Test(expected = NullPointerException.class)
    public void testCompareToNull() {
        SampleEnum.ONE.compareTo(null);
    }

    @Test(expected = ClassCastException.class)
    public void testCompareToInvalidType() {
        SampleEnum.ONE.compareTo("not an enum");
    }
}
