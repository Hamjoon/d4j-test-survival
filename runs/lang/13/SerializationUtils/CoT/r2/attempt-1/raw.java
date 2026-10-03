**Public methods of `SerializationUtils`**

| Modifier & Return | Method signature |
|-------------------|------------------|
| `public SerializationUtils()` | `SerializationUtils()` |
| `public static <T extends Serializable> T` | `clone(T object)` |
| `public static void` | `serialize(Serializable obj, OutputStream outputStream)` |
| `public static byte[]` | `serialize(Serializable obj)` |
| `public static Object` | `deserialize(InputStream inputStream)` |
| `public static Object` | `deserialize(byte[] objectData)` |

---

## JUnit 4 test cases for `SerializationUtils`

The test class below contains  

* a **basic test** for each public method, verifying the normal behaviour,  
* **edge‑case / exception tests** covering null arguments, I/O failures, corrupted data, and deep‑clone correctness,  
* a simple instantiation test for the public constructor.

```java
###Test START##
package org.apache.commons.lang3;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.*;
import java.util.*;

/**
 * Test suite for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /** Simple serializable bean used in the tests. */
    static class Person implements Serializable {
        private static final long serialVersionUID = 1L;
        String name;
        int age;
        List<String> tags;

        Person(String name, int age) {
            this.name = name;
            this.age = age;
            this.tags = new ArrayList<>();
        }

        void addTag(String tag) { tags.add(tag); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Person)) return false;
            Person p = (Person) o;
            return age == p.age &&
                   Objects.equals(name, p.name) &&
                   Objects.equals(tags, p.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age, tags);
        }
    }

    /* -------------------------------------------------------
     *  1. clone(T)
     * ------------------------------------------------------- */

    /** Normal deep‑clone of a serializable object. */
    @Test
    public void testCloneSimpleObject() {
        Person original = new Person("Alice", 30);
        original.addTag("friend");
        Person cloned = SerializationUtils.clone(original);
        assertEquals("Cloned object must be equal to the original", original, cloned);

        // modify the original – the clone must stay unchanged (deep copy)
        original.name = "Bob";
        original.addTag("colleague");
        assertNotEquals("Clone must be a deep copy", original, cloned);
    }

    /** clone(null) should return null (no exception). */
    @Test
    public void testCloneNull() {
        assertNull("Cloning a null reference must return null", SerializationUtils.clone(null));
    }

    /* -------------------------------------------------------
     *  2. serialize(Serializable, OutputStream)
     * ------------------------------------------------------- */

    /** Serialize a normal object to a provided OutputStream. */
    @Test
    public void testSerializeToOutputStream() throws IOException {
        Person p = new Person("Charlie", 25);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(p, baos);
        byte[] data = baos.toByteArray();

        assertNotNull("Serialized byte array must not be null", data);
        assertTrue("Serialized byte array must contain data", data.length > 0);

        // round‑trip verification
        Person deserialized = (Person) SerializationUtils.deserialize(new ByteArrayInputStream(data));
        assertEquals("Deserialized object must equal the original", p, deserialized);
    }

    /** Passing a null OutputStream must raise IllegalArgumentException. */
    @Test(expected = IllegalArgumentException.class)
    public void testSerializeNullOutputStream() {
        SerializationUtils.serialize(new Person("D", 1), null);
    }

    /** Simulate an IOException while writing – should be wrapped in SerializationException. */
    @Test(expected = SerializationException.class)
    public void testSerializeIOException() {
        OutputStream badOut = new OutputStream() {
            @Override public void write(int b) throws IOException {
                throw new IOException("forced I/O error");
            }
        };
        SerializationUtils.serialize(new Person("E", 2), badOut);
    }

    /* -------------------------------------------------------
     *  3. serialize(Serializable) → byte[]
     * ------------------------------------------------------- */

    /** Serialize to a byte array and deserialize back. */
    @Test
    public void testSerializeAndDeserializeByteArray() {
        Person p = new Person("Fiona", 40);
        byte[] bytes = SerializationUtils.serialize(p);
        assertNotNull("Resulting byte array must not be null", bytes);
        Object obj = SerializationUtils.deserialize(bytes);
        assertTrue("Deserialized object must be a Person", obj instanceof Person);
        assertEquals("Deserialized Person must equal original", p, obj);
    }

    /** Serializing a null reference must produce a byte array that deserializes to null. */
    @Test
    public void testSerializeNullObject() {
        byte[] bytes = SerializationUtils.serialize((Serializable) null);
        Object obj = SerializationUtils.deserialize(bytes);
        assertNull("Deserialization of a null payload must yield null", obj);
    }

    /* -------------------------------------------------------
     *  4. deserialize(InputStream)
     * ------------------------------------------------------- */

    /** Passing a null InputStream must raise IllegalArgumentException. */
    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeNullInputStream() {
        SerializationUtils.deserialize((InputStream) null);
    }

    /** Corrupted data must cause a SerializationException. */
    @Test(expected = SerializationException.class)
    public void testDeserializeCorruptedData() {
        byte[] corrupted = new byte[] {0, 1, 2, 3, 4};
        SerializationUtils.deserialize(corrupted);
    }

    /* -------------------------------------------------------
     *  5. deserialize(byte[])
     * ------------------------------------------------------- */

    /** Passing a null byte[] must raise IllegalArgumentException. */
    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeByteArrayNull() {
        SerializationUtils.deserialize((byte[]) null);
    }

    /* -------------------------------------------------------
     *  6. Constructor (public but not used in normal code)
     * ------------------------------------------------------- */

    /** Simple instantiation test to ensure the public constructor exists. */
    @Test
    public void testConstructor() {
        SerializationUtils utils = new SerializationUtils();
        assertNotNull("Constructor should create a non‑null instance", utils);
    }

    /* -------------------------------------------------------
     *  7. Deep‑clone of a complex object graph (Map of Person)
     * ------------------------------------------------------- */

    /** Verify that clone works for a collection containing serializable objects. */
    @Test
    @SuppressWarnings("unchecked")
    public void testCloneDeepObjectGraph() {
        Map<String, Person> original = new HashMap<>();
        original.put("one", new Person("One", 1));
        original.put("two", new Person("Two", 2));

        Map<String, Person> cloned = SerializationUtils.clone((Serializable) original);
        assertEquals("Cloned map must be equal to the original", original, cloned);

        // mutate original – cloned map must stay unchanged
        original.get("one").name = "Changed";
        assertNotEquals("Cloned map must be a deep copy", original, cloned);
    }
}
###Test END##
```