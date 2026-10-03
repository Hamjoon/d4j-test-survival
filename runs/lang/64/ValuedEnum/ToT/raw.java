###Test START##
```java
/****************************************************************************************
 * JUnit 4 test suite for {@link org.apache.commons.lang.enums.ValuedEnum}
 *
 * The tests are written from the perspective of three “experts” who each contributed
 * one test case per public/protected method.  The final suite combines their ideas
 * into a comprehensive set of use‑case, edge‑case and error‑scenario tests.
 *
 * Author:  Test generation by ChatGPT (based on expert discussion)
 * Version: 1.0
 ****************************************************************************************/

package org.apache.commons.lang.enums;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;

import org.junit.Test;

/**
 * Concrete subclass used only for testing {@link ValuedEnum}.
 * Mirrors the example from the Javadoc but keeps the API minimal.
 */
final class TestEnum extends ValuedEnum {

    // -----------------------------------------------------------------------
    //  Enum instances (public static final as required by ValuedEnum contract)
    // -----------------------------------------------------------------------
    public static final int A_VALUE = 1;
    public static final int B_VALUE = 2;
    public static final int C_VALUE = 3;

    public static final TestEnum A = new TestEnum("A", A_VALUE);
    public static final TestEnum B = new TestEnum("B", B_VALUE);
    public static final TestEnum C = new TestEnum("C", C_VALUE);

    // -----------------------------------------------------------------------
    //  Private constructor – only the static fields are the enum values
    // -----------------------------------------------------------------------
    private TestEnum(String name, int value) {
        super(name, value);
    }

    // -----------------------------------------------------------------------
    //  Helper methods that delegate to the protected static methods in ValuedEnum.
    //  These are the public façade that a normal client would use.
    // -----------------------------------------------------------------------
    public static TestEnum getEnum(String name) {
        return (TestEnum) Enum.getEnum(TestEnum.class, name);
    }

    public static TestEnum getEnum(int value) {
        return (TestEnum) ValuedEnum.getEnum(TestEnum.class, value);
    }

    public static List getEnumList() {
        return Enum.getEnumList(TestEnum.class);
    }

    public static Iterator iterator() {
        return Enum.iterator(TestEnum.class);
    }

    // -----------------------------------------------------------------------
    //  Expose the protected static method for the null‑class error test.
    // -----------------------------------------------------------------------
    static Enum getEnumViaProtected(Class enumClass, int value) {
        return ValuedEnum.getEnum(enumClass, value);
    }
}

/**
 * Test suite for {@link ValuedEnum}.
 */
public class ValuedEnumTest {

    /* -----------------------------------------------------------------------
     *  Test getValue()
     *  (Expert 1 contribution)
     * ----------------------------------------------------------------------- */
    @Test
    public void testGetValue() {
        assertEquals("A should have value 1", 1, TestEnum.A.getValue());
        assertEquals("B should have value 2", 2, TestEnum.B.getValue());
        assertEquals("C should have value 3", 3, TestEnum.C.getValue());
    }

    /* -----------------------------------------------------------------------
     *  Test protected static getEnum(Class, int) – normal case
     *  (Expert 2 contribution)
     * ----------------------------------------------------------------------- */
    @Test
    public void testGetEnumByValueFound() {
        // existing values
        assertSame("Lookup by value 1 should return A", TestEnum.A, TestEnum.getEnum(1));
        assertSame("Lookup by value 2 should return B", TestEnum.B, TestEnum.getEnum(2));
        assertSame("Lookup by value 3 should return C", TestEnum.C, TestEnum.getEnum(3));
    }

    /* -----------------------------------------------------------------------
     *  Test protected static getEnum(Class, int) – value not present
     *  (Expert 2 contribution)
     * ----------------------------------------------------------------------- */
    @Test
    public void testGetEnumByValueNotFound() {
        assertNull("Lookup for non‑existent value should return null", TestEnum.getEnum(99));
    }

    /* -----------------------------------------------------------------------
     *  Test protected static getEnum(Class, int) – null class argument
     *  (Expert 3 contribution)
     * ----------------------------------------------------------------------- */
    @Test(expected = IllegalArgumentException.class)
    public void testGetEnumNullClassThrows() {
        // The wrapper simply forwards to the protected method.
        TestEnum.getEnumViaProtected(null, 1);
    }

    /* -----------------------------------------------------------------------
     *  Test compareTo(Object) – basic ordering
     *  (Expert 1 contribution)
     * ----------------------------------------------------------------------- */
    @Test
    public void testCompareToOrdering() {
        // B (2) > A (1)
        assertTrue("B should be greater than A", TestEnum.B.compareTo(TestEnum.A) > 0);
        // A (1) < C (3)
        assertTrue("A should be less than C", TestEnum.A.compareTo(TestEnum.C) < 0);
        // C (3) == C (3)
        assertEquals("C should be equal to itself", 0, TestEnum.C.compareTo(TestEnum.C));
    }

    /* -----------------------------------------------------------------------
     *  Test compareTo(Object) – null argument
     *  (Expert 2 contribution)
     * ----------------------------------------------------------------------- */
    @Test(expected = NullPointerException.class)
    public void testCompareToNullThrows() {
        TestEnum.A.compareTo(null);
    }

    /* -----------------------------------------------------------------------
     *  Test compareTo(Object) – incompatible type
     *  (Expert 3 contribution)
     * ----------------------------------------------------------------------- */
    @Test(expected = ClassCastException.class)
    public void testCompareToIncompatibleTypeThrows() {
        // Use a plain Enum from the superclass that is NOT a ValuedEnum
        Enum plainEnum = new Enum("plain") {};
        TestEnum.A.compareTo(plainEnum);
    }

    /* -----------------------------------------------------------------------
     *  Test toString() – format and caching behaviour
     *  (Expert 1 contribution)
     * ----------------------------------------------------------------------- */
    @Test
    public void testToStringFormat() {
        // Expected format: ShortClassName[Name=Value]
        String expected = "TestEnum[A=1]";
        assertEquals("toString() should match expected format", expected, TestEnum.A.toString());

        // Call again to ensure the cached string is returned (no change expected)
        assertSame("Second call should return the same cached String instance",
                TestEnum.A.toString(),
                TestEnum.A.toString());
    }

    /* -----------------------------------------------------------------------
     *  Test Enum utilities (getEnumList, iterator) supplied by the superclass.
     *  These are not part of ValuedEnum directly but are commonly used together.
     * ----------------------------------------------------------------------- */
    @Test
    public void testEnumListAndIterator() {
        List list = TestEnum.getEnumList();
        assertEquals("There should be exactly three enum instances", 3, list.size());
        assertTrue("List should contain A", list.contains(TestEnum.A));
        assertTrue("List should contain B", list.contains(TestEnum.B));
        assertTrue("List should contain C", list.contains(TestEnum.C));

        // iterator should traverse the same elements
        Iterator it = TestEnum.iterator();
        int count = 0;
        while (it.hasNext()) {
            Object obj = it.next();
            assertTrue("Iterator element must be a TestEnum", obj instanceof TestEnum);
            count++;
        }
        assertEquals("Iterator should have visited three elements", 3, count);
    }
}
```
###Test END##