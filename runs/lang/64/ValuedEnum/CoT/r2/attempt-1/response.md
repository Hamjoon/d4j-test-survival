###Test START##
```java
package org.apache.commons.lang.enums;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;

/**
 * JUnit 4 test cases for {@link ValuedEnum}.
 *
 * The tests cover:
 * <ul>
 *   <li>Public API behavior (getValue, compareTo, toString)</li>
 *   <li>Protected static {@code getEnum(Class,int)} handling of normal,
 *       edge‑case and exceptional situations.</li>
 *   <li>Edge cases such as null arguments, unknown values and
 *       cross‑type comparisons.</li>
 * </ul>
 */
public class ValuedEnumTest {

    /** Simple concrete subclass used for testing. */
    public static final class DummyEnum extends ValuedEnum {
        private static final long serialVersionUID = 1L;

        public static final DummyEnum ONE   = new DummyEnum("ONE",   1);
        public static final DummyEnum TWO   = new DummyEnum("TWO",   2);
        public static final DummyEnum THREE = new DummyEnum("THREE", 3);

        private DummyEnum(String name, int value) {
            super(name, value);
        }

        /** Helper to expose the protected static getEnum method. */
        public static DummyEnum getEnum(int value) {
            return (DummyEnum) ValuedEnum.getEnum(DummyEnum.class, value);
        }

        /** Helper to expose the protected static getEnum method with name. */
        public static DummyEnum getEnum(String name) {
            return (DummyEnum) Enum.getEnum(DummyEnum.class, name);
        }

        /** Helper to expose iterator for this enum type. */
        public static Iterator iterator() {
            return Enum.iterator(DummyEnum.class);
        }

        /** Helper to expose list for this enum type. */
        public static List getEnumList() {
            return Enum.getEnumList(DummyEnum.class);
        }
    }

    /** Another enum type used to test ClassCastException in compareTo. */
    public static final class OtherEnum extends ValuedEnum {
        private static final long serialVersionUID = 1L;
        public static final OtherEnum A = new OtherEnum("A", 10);
        private OtherEnum(String name, int value) {
            super(name, value);
        }
    }

    @Before
    public void setUp() {
        // Force initialization of static fields
        DummyEnum.ONE.getValue();
        DummyEnum.TWO.getValue();
        DummyEnum.THREE.getValue();
        OtherEnum.A.getValue();
    }

    /* -----------------------------------------------------------------
     *  Public method signatures extracted from ValuedEnum
     * -----------------------------------------------------------------
     *  public final int getValue()
     *  public int compareTo(Object other)
     *  public String toString()
     * ----------------------------------------------------------------- */

    /** Test that the value returned by {@code getValue()} matches the
     *  constructor argument. */
    @Test
    public void testGetValue() {
        assertEquals(1, DummyEnum.ONE.getValue());
        assertEquals(2, DummyEnum.TWO.getValue());
        assertEquals(3, DummyEnum.THREE.getValue());
    }

    /** Verify natural ordering via {@code compareTo}. */
    @Test
    public void testCompareToOrdering() {
        // ONE < TWO < THREE
        assertTrue(DummyEnum.ONE.compareTo(DummyEnum.TWO) < 0);
        assertTrue(DummyEnum.THREE.compareTo(DummyEnum.TWO) > 0);
        assertEquals(0, DummyEnum.TWO.compareTo(DummyEnum.TWO));
    }

    /** {@code compareTo(null)} must throw {@link NullPointerException}. */
    @Test(expected = NullPointerException.class)
    public void testCompareToNull() {
        DummyEnum.ONE.compareTo(null);
    }

    /** {@code compareTo} with an object of a different enum type must throw
     *  {@link ClassCastException}. */
    @Test(expected = ClassCastException.class)
    public void testCompareToDifferentEnumType() {
        DummyEnum.ONE.compareTo(OtherEnum.A);
    }

    /** Verify the string representation format and caching behaviour. */
    @Test
    public void testToString() {
        String expected = "DummyEnum[ONE=1]";
        assertEquals(expected, DummyEnum.ONE.toString());

        // Call again to ensure cached value is returned (no change expected)
        assertSame(DummyEnum.ONE.toString(), DummyEnum.ONE.toString());
    }

    /* -----------------------------------------------------------------
     *  Protected static method signatures (exposed via subclass helpers)
     * -----------------------------------------------------------------
     *  protected static Enum getEnum(Class enumClass, int value)
     * ----------------------------------------------------------------- */

    /** Normal lookup – existing value returns the correct enum instance. */
    @Test
    public void testGetEnumByValueFound() {
        assertSame(DummyEnum.ONE, DummyEnum.getEnum(1));
        assertSame(DummyEnum.TWO, DummyEnum.getEnum(2));
        assertSame(DummyEnum.THREE, DummyEnum.getEnum(3));
    }

    /** Lookup with a value that does not correspond to any enum returns null. */
    @Test
    public void testGetEnumByValueNotFound() {
        assertNull(DummyEnum.getEnum(99));
    }

    /** Passing a null class argument must result in IllegalArgumentException. */
    @Test(expected = IllegalArgumentException.class)
    public void testGetEnumWithNullClass() {
        // Directly invoke the protected method via reflection because it is
        // not accessible through the public API.
        try {
            java.lang.reflect.Method m = ValuedEnum.class
                    .getDeclaredMethod("getEnum", Class.class, int.class);
            m.setAccessible(true);
            m.invoke(null, (Class<?>) null, 1);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // unwrap the actual exception thrown by the method
            throw (RuntimeException) e.getCause();
        } catch (Exception e) {
            fail("Unexpected reflection exception: " + e);
        }
    }

    /** Verify that the iterator returns all defined enum constants in declaration order. */
    @Test
    public void testIterator() {
        Iterator it = DummyEnum.iterator();
        assertTrue(it.hasNext());
        assertSame(DummyEnum.ONE, it.next());
        assertTrue(it.hasNext());
        assertSame(DummyEnum.TWO, it.next());
        assertTrue(it.hasNext());
        assertSame(DummyEnum.THREE, it.next());
        assertFalse(it.hasNext());
    }

    /** Verify that the list returned by {@code getEnumList} contains all enum instances. */
    @Test
    public void testEnumListContents() {
        List list = DummyEnum.getEnumList();
        assertEquals(3, list.size());
        assertTrue(list.contains(DummyEnum.ONE));
        assertTrue(list.contains(DummyEnum.TWO));
        assertTrue(list.contains(DummyEnum.THREE));
    }
}
```
###Test END##