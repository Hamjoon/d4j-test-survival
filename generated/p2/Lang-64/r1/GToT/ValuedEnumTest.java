package org.apache.commons.lang.enums;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;

import org.junit.Test;

/**
 * JUnit‑4 test suite for {@link ValuedEnum}.
 *
 * The tests cover:
 *   • basic functionality of each public method,
 *   • edge‑cases and expected exceptions,
 *   • caching behaviour of {@code toString()}.
 */
public class ValuedEnumTest {

    /**
     * Minimal concrete subclass of {@link ValuedEnum} used only for testing.
     */
    public static final class TestEnum extends ValuedEnum {

        private static final long serialVersionUID = 1L;

        public static final TestEnum ONE = new TestEnum("ONE", 1);
        public static final TestEnum TWO = new TestEnum("TWO", 2);
        public static final TestEnum NEG = new TestEnum("NEG", -5);

        private TestEnum(String name, int value) {
            super(name, value);
        }

        /** Public wrapper for the protected static {@code getEnum} method. */
        public static TestEnum getByValue(int value) {
            return (TestEnum) getEnum(TestEnum.class, value);
        }

        /** Public wrapper for the protected static {@code getEnum} method that accepts a name. */
        public static TestEnum getByName(String name) {
            return (TestEnum) getEnum(TestEnum.class, name);
        }

        /** Helper to expose the internal enum list (inherited from Enum). */
        @SuppressWarnings("unchecked")
        public static List<TestEnum> getEnumList() {
            return (List<TestEnum>) Enum.getEnumList(TestEnum.class);
        }

        /** Helper to expose the iterator (inherited from Enum). */
        @SuppressWarnings("unchecked")
        public static Iterator<TestEnum> iterator() {
            return (Iterator<TestEnum>) Enum.iterator(TestEnum.class);
        }
    }

    /*---------------------------------------------------------------*
     *  1. Tests for getValue()
     *---------------------------------------------------------------*/
    @Test
    public void testGetValue() {
        assertEquals(1, TestEnum.ONE.getValue());
        assertEquals(2, TestEnum.TWO.getValue());
        assertEquals(-5, TestEnum.NEG.getValue());
    }

    /*---------------------------------------------------------------*
     *  2. Tests for compareTo(Object)
     *---------------------------------------------------------------*/
    @Test
    public void testCompareToLessThan() {
        assertTrue(TestEnum.ONE.compareTo(TestEnum.TWO) < 0);
    }

    @Test
    public void testCompareToGreaterThan() {
        assertTrue(TestEnum.TWO.compareTo(TestEnum.ONE) > 0);
    }

    @Test
    public void testCompareToEqual() {
        // compare to itself – should be zero
        assertEquals(0, TestEnum.ONE.compareTo(TestEnum.ONE));
    }

    @Test(expected = NullPointerException.class)
    public void testCompareToNull() {
        TestEnum.ONE.compareTo(null);
    }

    @Test(expected = ClassCastException.class)
    public void testCompareToIncompatibleType() {
        // Passing a String forces a ClassCastException inside compareTo
        TestEnum.ONE.compareTo("not an enum");
    }

    /*---------------------------------------------------------------*
     *  3. Tests for toString()
     *---------------------------------------------------------------*/
    @Test
    public void testToStringFormat() {
        String s = TestEnum.ONE.toString();
        // Expected format: ShortClassName[name=value]
        assertTrue(s.matches("TestEnum\\[ONE=1\\]"));
    }

    @Test
    public void testToStringCaching() {
        String first = TestEnum.TWO.toString();
        String second = TestEnum.TWO.toString();
        // The implementation caches the string in a field,
        // so the same reference should be returned.
        assertSame(first, second);
    }

    /*---------------------------------------------------------------*
     *  4. Tests for protected static getEnum(Class,int)
     *---------------------------------------------------------------*/
    @Test
    public void testGetEnumByValueFound() {
        assertSame(TestEnum.ONE, TestEnum.getByValue(1));
        assertSame(TestEnum.TWO, TestEnum.getByValue(2));
    }

    @Test
    public void testGetEnumByValueNotFound() {
        assertNull(TestEnum.getByValue(999));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEnumByValueNullClass() {
        // Directly invoke the protected method via reflection to pass a null class.
        // This mimics the internal call that would happen if a subclass passed null.
        ValuedEnum.getEnum(null, 1);
    }

    /*---------------------------------------------------------------*
     *  5. Tests for the inherited Enum utilities (list, iterator)
     *---------------------------------------------------------------*/
    @Test
    public void testEnumListContainsAllConstants() {
        List<TestEnum> list = TestEnum.getEnumList();
        // The list should contain exactly the three defined constants
        assertEquals(3, list.size());
        assertTrue(list.contains(TestEnum.ONE));
        assertTrue(list.contains(TestEnum.TWO));
        assertTrue(list.contains(TestEnum.NEG));
    }

    @Test
    public void testIteratorTraversesAllConstants() {
        Iterator<TestEnum> it = TestEnum.iterator();
        int count = 0;
        while (it.hasNext()) {
            it.next();
            count++;
        }
        assertEquals(3, count);
    }
}
