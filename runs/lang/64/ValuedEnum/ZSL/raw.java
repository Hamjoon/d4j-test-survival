###Test START##
```java
/*********************************************************************
 * ValuedEnumTest.java
 *
 * Comprehensive JUnit 4 test suite for {@link ValuedEnum}.
 *
 * The test creates a concrete subclass {@code SampleEnum} with several
 * static instances and verifies:
 *   • value retrieval (getValue)
 *   • lookup by value (getEnum)
 *   • handling of null arguments and missing values
 *   • natural ordering via compareTo (including null and illegal args)
 *   • string representation (toString) and its caching behaviour
 *
 * This file is self‑contained and does **not** depend on any external
 * test utilities beyond JUnit 4 and the Apache Commons Lang classes.
 *********************************************************************/

package org.apache.commons.lang.enums;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test class for {@link ValuedEnum}.
 */
public class ValuedEnumTest {

    /**
     * Simple concrete implementation of {@link ValuedEnum} used for testing.
     */
    public static final class SampleEnum extends ValuedEnum {
        private static final long serialVersionUID = 1L;

        // Define three enum instances with distinct values
        public static final SampleEnum ONE   = new SampleEnum("ONE",   1);
        public static final SampleEnum TWO   = new SampleEnum("TWO",   2);
        public static final SampleEnum THREE = new SampleEnum("THREE", 3);

        private SampleEnum(String name, int value) {
            super(name, value);
        }

        /** Helper to expose the protected static {@code getEnum} method. */
        public static SampleEnum getEnum(int value) {
            return (SampleEnum) ValuedEnum.getEnum(SampleEnum.class, value);
        }
    }

    /* -------------------------- getValue() -------------------------- */

    @Test
    public void testGetValue() {
        assertEquals("ONE should have value 1", 1, SampleEnum.ONE.getValue());
        assertEquals("TWO should have value 2", 2, SampleEnum.TWO.getValue());
        assertEquals("THREE should have value 3", 3, SampleEnum.THREE.getValue());
    }

    /* -------------------------- getEnum(Class,int) -------------------------- */

    @Test
    public void testGetEnumFound() {
        assertSame("Lookup value 1 should return ONE",   SampleEnum.ONE,   SampleEnum.getEnum(1));
        assertSame("Lookup value 2 should return TWO",   SampleEnum.TWO,   SampleEnum.getEnum(2));
        assertSame("Lookup value 3 should return THREE", SampleEnum.THREE, SampleEnum.getEnum(3));
    }

    @Test
    public void testGetEnumNotFound() {
        assertNull("Lookup of non‑existing value must return null", SampleEnum.getEnum(99));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEnumNullClass() {
        // Directly invoke the protected method via reflection to pass a null class.
        // This is the only way to hit the null‑check because the public wrapper
        // in SampleEnum always supplies a non‑null class.
        try {
            java.lang.reflect.Method m = ValuedEnum.class.getDeclaredMethod(
                    "getEnum", Class.class, int.class);
            m.setAccessible(true);
            m.invoke(null, null, 1);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap the IllegalArgumentException thrown by the method.
            throw (IllegalArgumentException) e.getCause();
        } catch (Exception e) {
            // Any other reflection problem should cause the test to fail.
            throw new RuntimeException(e);
        }
    }

    /* -------------------------- compareTo(Object) -------------------------- */

    @Test
    public void testCompareToSameInstance() {
        assertEquals("Comparing an enum to itself must be zero", 0,
                SampleEnum.ONE.compareTo(SampleEnum.ONE));
    }

    @Test
    public void testCompareToDifferentValues() {
        assertTrue("ONE < TWO", SampleEnum.ONE.compareTo(SampleEnum.TWO) < 0);
        assertTrue("THREE > TWO", SampleEnum.THREE.compareTo(SampleEnum.TWO) > 0);
        assertTrue("TWO > ONE", SampleEnum.TWO.compareTo(SampleEnum.ONE) > 0);
    }

    @Test(expected = NullPointerException.class)
    public void testCompareToNull() {
        SampleEnum.ONE.compareTo(null);
    }

    @Test(expected = ClassCastException.class)
    public void testCompareToInvalidType() {
        // Passing an object that is not a ValuedEnum causes a ClassCastException
        SampleEnum.ONE.compareTo("not an enum");
    }

    @Test
    public void testCompareToDifferentSubclassSameValue() {
        // Create another subclass with the same numeric value.
        class OtherEnum extends ValuedEnum {
            private static final long serialVersionUID = 1L;
            public static final OtherEnum OTHER = new OtherEnum("OTHER", 2);
            private OtherEnum(String name, int value) {
                super(name, value);
            }
        }
        // Compare SampleEnum.TWO (value 2) with OtherEnum.OTHER (also value 2)
        // The implementation only looks at the numeric value, so result must be 0.
        assertEquals("Different subclasses with same value should compare equal",
                0, SampleEnum.TWO.compareTo(OtherEnum.OTHER));
    }

    /* -------------------------- toString() -------------------------- */

    @Test
    public void testToStringFormat() {
        String s = SampleEnum.ONE.toString();

        // Expected format: ShortClassName[EnumName=Value]
        // Short class name for SampleEnum (no package) is "SampleEnum"
        String expected = "SampleEnum[ONE=1]";
        assertEquals("toString format must match expected", expected, s);
    }

    @Test
    public void testToStringCaching() {
        // First call builds the string; subsequent calls should return the same
        // content (the implementation caches the string in a private field).
        String first = SampleEnum.TWO.toString();
        String second = SampleEnum.TWO.toString();

        assertSame("Repeated toString calls should return the same cached instance",
                first, second);
    }

    /* -------------------------- Additional edge cases -------------------------- */

    @Test
    public void testEnumListIntegrityThrough getEnum() {
        // The underlying Enum.getEnumList should contain exactly the three instances.
        java.util.List list = Enum.getEnumList(SampleEnum.class);
        assertEquals("Enum list size must be 3", 3, list.size());
        assertTrue("List must contain ONE", list.contains(SampleEnum.ONE));
        assertTrue("List must contain TWO", list.contains(SampleEnum.TWO));
        assertTrue("List must contain THREE", list.contains(SampleEnum.THREE));
    }
}
```
###Test END##