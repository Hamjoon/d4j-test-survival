###Test START##
package org.apache.commons.lang.enums;

import org.junit.Test;
import static org.junit.Assert.*;

public class ValuedEnumTest {

    /** ------------------------------------------------------------
     *  Basic functionality tests
     *  ------------------------------------------------------------ */
    @Test
    public void testGetValue() {
        assertEquals(1, TestValuedEnum.ONE.getValue());
        assertEquals(2, TestValuedEnum.TWO.getValue());
        assertEquals(-5, TestValuedEnum.NEG.getValue());
    }

    @Test
    public void testCompareToLessThan() {
        assertTrue(TestValuedEnum.ONE.compareTo(TestValuedEnum.TWO) < 0);
    }

    @Test
    public void testCompareToGreaterThan() {
        assertTrue(TestValuedEnum.TWO.compareTo(TestValuedEnum.ONE) > 0);
    }

    @Test
    public void testCompareToEqualValuesDifferentInstances() {
        // ONE and SAME have the same numeric value (1)
        assertEquals(0, TestValuedEnum.ONE.compareTo(TestValuedEnum.SAME));
    }

    @Test
    public void testToStringFormat() {
        String expected = "TestValuedEnum[ONE=1]";
        assertEquals(expected, TestValuedEnum.ONE.toString());

        // second call must return the same cached string
        assertSame(expected, TestValuedEnum.ONE.toString());
    }

    /** ------------------------------------------------------------
     *  Tests for the protected static getEnum method (via wrapper)
     *  ------------------------------------------------------------ */
    @Test
    public void testGetEnumByValueFound() {
        assertSame(TestValuedEnum.ONE, TestValuedEnum.getEnumByValue(1));
        assertSame(TestValuedEnum.TWO, TestValuedEnum.getEnumByValue(2));
        assertSame(TestValuedEnum.NEG, TestValuedEnum.getEnumByValue(-5));
    }

    @Test
    public void testGetEnumByValueNotFound() {
        assertNull(TestValuedEnum.getEnumByValue(999));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEnumWithNullClass() throws Exception {
        // Use reflection to call the protected method directly with a null class
        java.lang.reflect.Method m = ValuedEnum.class.getDeclaredMethod(
                "getEnum", Class.class, int.class);
        m.setAccessible(true);
        m.invoke(null, null, 1);
    }

    /** ------------------------------------------------------------
     *  Exception handling for compareTo
     *  ------------------------------------------------------------ */
    @Test(expected = NullPointerException.class)
    public void testCompareToNull() {
        TestValuedEnum.ONE.compareTo(null);
    }

    @Test(expected = ClassCastException.class)
    public void testCompareToWrongType() {
        // Compare with an object that is not a ValuedEnum
        TestValuedEnum.ONE.compareTo("not an enum");
    }
}
###Test END##