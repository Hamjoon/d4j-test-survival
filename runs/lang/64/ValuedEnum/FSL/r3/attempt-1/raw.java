###Test START##
package org.apache.commons.lang.enums;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;

import org.junit.Test;

/**
 * Unit tests for {@link ValuedEnum}.
 */
public class ValuedEnumTest {

    /** Simple concrete ValuedEnum used only for testing. */
    public static final class TestEnum extends ValuedEnum {
        private static final long serialVersionUID = 1L;

        public static final TestEnum ONE   = new TestEnum("ONE",   1);
        public static final TestEnum TWO   = new TestEnum("TWO",   2);
        public static final TestEnum THREE = new TestEnum("THREE", 3);

        private TestEnum(String name, int value) {
            super(name, value);
        }

        /** expose protected getEnum(Class,int) for testing */
        public static TestEnum getEnum(int value) {
            return (TestEnum) getEnum(TestEnum.class, value);
        }

        /** expose protected getEnum(Class,int) for null‑class test via reflection */
        private static Enum getEnumNullClass(int value) {
            return getEnum(null, value);
        }

        public static List getEnumList() {
            return Enum.getEnumList(TestEnum.class);
        }

        public static Iterator iterator() {
            return Enum.iterator(TestEnum.class);
        }
    }

    @Test
    public void testGetValue() {
        assertEquals(1, TestEnum.ONE.getValue());
        assertEquals(2, TestEnum.TWO.getValue());
        assertEquals(3, TestEnum.THREE.getValue());
    }

    @Test
    public void testCompareTo_SameClass() {
        // less than
        assertTrue(TestEnum.ONE.compareTo(TestEnum.TWO) < 0);
        // greater than
        assertTrue(TestEnum.THREE.compareTo(TestEnum.TWO) > 0);
        // equal
        assertEquals(0, TestEnum.TWO.compareTo(TestEnum.TWO));
    }

    @Test
    public void testCompareTo_DifferentClass() {
        // Create a dummy enum of a different type
        class OtherEnum extends ValuedEnum {
            private static final long serialVersionUID = 1L;
            public static final OtherEnum X = new OtherEnum("X", 1);
            private OtherEnum(String name, int value) {
                super(name, value);
            }
        }

        try {
            TestEnum.ONE.compareTo(OtherEnum.X);
            fail("Expected ClassCastException when comparing different ValuedEnum types.");
        } catch (ClassCastException expected) {
            // expected
        }
    }

    @Test(expected = NullPointerException.class)
    public void testCompareTo_Null() {
        TestEnum.ONE.compareTo(null);
    }

    @Test
    public void testToString_CachesResult() {
        String s1 = TestEnum.ONE.toString();
        String s2 = TestEnum.ONE.toString(); // second call should return cached value
        assertEquals("TestEnum[ONE=1]", s1);
        assertSame("toString() should return the same cached instance", s1, s2);
    }

    @Test
    public void testGetEnumByValue_Existing() {
        assertSame(TestEnum.ONE,   TestEnum.getEnum(1));
        assertSame(TestEnum.TWO,   TestEnum.getEnum(2));
        assertSame(TestEnum.THREE, TestEnum.getEnum(3));
    }

    @Test
    public void testGetEnumByValue_NonExisting() {
        assertNull(TestEnum.getEnum(99));
    }

    @Test
    public void testGetEnumByValue_NullClassArgument() throws Exception {
        try {
            // Use reflection to invoke the private helper that passes null as the class argument
            java.lang.reflect.Method m = TestEnum.class.getDeclaredMethod("getEnumNullClass", int.class);
            m.setAccessible(true);
            m.invoke(null, 1);
            fail("Expected IllegalArgumentException when enumClass argument is null.");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals("The Enum Class must not be null", e.getCause().getMessage());
        }
    }

    @Test
    public void testEnumListAndIteratorOrder() {
        List list = TestEnum.getEnumList();
        assertEquals(3, list.size());
        assertEquals(TestEnum.ONE,   list.get(0));
        assertEquals(TestEnum.TWO,   list.get(1));
        assertEquals(TestEnum.THREE, list.get(2));

        Iterator it = TestEnum.iterator();
        assertTrue(it.hasNext());
        assertEquals(TestEnum.ONE,   it.next());
        assertEquals(TestEnum.TWO,   it.next());
        assertEquals(TestEnum.THREE, it.next());
        assertFalse(it.hasNext());
    }
}
###Test END##