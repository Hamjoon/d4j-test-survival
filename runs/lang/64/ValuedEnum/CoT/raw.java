**Step 1 – Public methods of `ValuedEnum`**

| Modifier / Return | Method signature | Description |
|-------------------|------------------|-------------|
| `public final int` | `getValue()` | Returns the integer value stored in the enum instance. |
| `public int` | `compareTo(Object other)` | Implements `Comparable`; compares two `ValuedEnum` objects by their integer values. |
| `public String` | `toString()` | Human‑readable description in the form `ShortClassName[name=value]`. |
| `protected static Enum` | `getEnum(Class enumClass, int value)` | Looks up an enum constant of the given class by its integer value (returns `null` if not found, throws `IllegalArgumentException` when `enumClass` is `null`). |

*(The class also inherits public methods from `Enum`, but the tests below focus on the members declared in `ValuedEnum`.)*  

---

**Step 2 – Simple concrete subclass for testing**

```java
package org.apache.commons.lang.enums;

public final class TestValuedEnum extends ValuedEnum {

    public static final TestValuedEnum ONE   = new TestValuedEnum("ONE",   1);
    public static final TestValuedEnum TWO   = new TestValuedEnum("TWO",   2);
    public static final TestValuedEnum NEG   = new TestValuedEnum("NEG",  -5);
    public static final TestValuedEnum SAME  = new TestValuedEnum("SAME",  1); // duplicate value on purpose

    private TestValuedEnum(String name, int value) {
        super(name, value);
    }

    /** wrapper to expose the protected static getEnum method */
    public static TestValuedEnum getEnumByValue(int value) {
        return (TestValuedEnum) getEnum(TestValuedEnum.class, value);
    }
}
```

The subclass lives in the same package so it can call the protected static `getEnum` method.

---

**Step 3 – Identify edge‑cases / exception scenarios**

| Method | Edge case / exception | Reason |
|--------|-----------------------|--------|
| `getEnum(Class, int)` | `enumClass == null` | Should throw `IllegalArgumentException`. |
| `getEnum(Class, int)` | value not present | Should return `null`. |
| `compareTo(Object)` | `other == null` | Should throw `NullPointerException` (the cast will cause it). |
| `compareTo(Object)` | `other` not a `ValuedEnum` (different class) | Should throw `ClassCastException`. |
| `compareTo(Object)` | same value, different instance | Should return `0`. |
| `toString()` | first call (lazy init) and subsequent calls | Result must be cached and identical. |
| `getValue()` | negative, zero, positive values | Simple functional verification. |

---

**Step 4 – JUnit 4 test cases**

```java
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
```

**Explanation of the tests**

* **Basic functionality** – verifies that `getValue`, `compareTo`, and `toString` behave as documented, including the lazy‑initialisation/caching of `toString`.
* **`getEnum` wrapper** – checks successful lookup, lookup failure (`null`), and the required `IllegalArgumentException` when the class argument is `null`. Because `getEnum` is `protected`, the test reaches it via reflection.
* **`compareTo` edge cases** – ensures a `NullPointerException` is thrown for a `null` argument and a `ClassCastException` for an object of an incompatible type.

All tests are written for JUnit 4 (`@Test` annotation, `expected` attribute) and compile against the supplied `ValuedEnum` source. The file is bounded by the required markers `###Test START##` and `###Test END##`.