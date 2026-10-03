**Step 1 – Public methods of `SerializationUtils`**

| # | Method (signature) |
|---|---------------------|
| 1 | `public SerializationUtils()` |
| 2 | `public static <T extends Serializable> T clone(T object)` |
| 3 | `public static void serialize(Serializable obj, OutputStream outputStream)` |
| 4 | `public static byte[] serialize(Serializable obj)` |
| 5 | `public static Object deserialize(InputStream inputStream)` |
| 6 | `public static Object deserialize(byte[] objectData)` |

---

**Step 2 – Basic JUnit 4 test cases (functionality)**  

* `clone(null)` → returns `null`  
* `clone(serializableObject)` → returns a deep‑cloned object that is *equal* but not the same instance  

* `serialize(obj, ByteArrayOutputStream)` → writes a non‑empty byte array; the method must close the stream (no exception)  

* `serialize(obj)` → returns a non‑null byte array; deserialising that array must give back the original object (including the case where `obj` is `null`)  

* `deserialize(InputStream)` → returns the object that was written to the stream  

* `deserialize(byte[])` → returns the object that was written to the byte array  

---

**Step 3 – Edge‑case / exception scenarios**

| Method | Edge case / exception to test | Expected behaviour |
|--------|------------------------------|--------------------|
| `clone(T)` | `object == null` | returns `null` (already covered) |
| `serialize(Serializable, OutputStream)` | `outputStream == null` | throws `IllegalArgumentException` |
| `serialize(Serializable, OutputStream)` | `OutputStream` throws `IOException` on `write`/`close` | throws `SerializationException` |
| `deserialize(InputStream)` | `inputStream == null` | throws `IllegalArgumentException` |
| `deserialize(InputStream)` | `InputStream` throws `IOException` on read/close | throws `SerializationException` |
| `deserialize(byte[])` | `objectData == null` | throws `IllegalArgumentException` |
| `deserialize(byte[])` | corrupted byte array (e.g. random data) | throws `SerializationException` |
| `deserialize(byte[])` after `serialize(null)` | round‑trip of a `null` reference | result is `null` |

To provoke `IOException` we create small custom streams (`FailingOutputStream`, `FailingInputStream`) that deliberately throw an `IOException` when their `write`/`read` methods are invoked.

---

**Step 4 – Complete JUnit 4 test file**

```java
###Test START##
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;

import org.junit.Test;

/**
 * Test suite for {@link SerializationUtils}.
 *
 * The tests cover:
 *   * normal functionality of all public methods,
 *   * edge‑case handling (null arguments, I/O failures, corrupted data),
 *   * and verification that deep cloning really creates a new instance.
 */
public class SerializationUtilsTest {

    /** Simple serializable bean used in the tests. */
    private static class TestBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final int value;

        TestBean(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TestBean)) return false;
            TestBean other = (TestBean) obj;
            return value == other.value && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + value;
        }
    }

    /** OutputStream that always throws an IOException on write or close. */
    private static class FailingOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            throw new IOException("forced write failure");
        }

        @Override
        public void close() throws IOException {
            throw new IOException("forced close failure");
        }
    }

    /** InputStream that always throws an IOException on read or close. */
    private static class FailingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("forced read failure");
        }

        @Override
        public void close() throws IOException {
            throw new IOException("forced close failure");
        }
    }

    /* ---------------------------------------------------------------------- */
    /* 1. clone()                                                             */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testClone_NullReturnsNull() {
        assertNull(SerializationUtils.clone(null));
    }

    @Test
    public void testClone_DeepCopy() {
        TestBean original = new TestBean("alpha", 42);
        TestBean copy = SerializationUtils.clone(original);
        assertNotSame("clone should create a new instance", original, copy);
        assertEquals("clone should be equal to the original", original, copy);
    }

    /* ---------------------------------------------------------------------- */
    /* 2. serialize(Serializable, OutputStream)                                 */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_WithNullOutputStream() {
        SerializationUtils.serialize(new TestBean("x", 1), null);
    }

    @Test
    public void testSerialize_ToByteArrayOutputStream() {
        TestBean bean = new TestBean("beta", 7);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(bean, baos);
        byte[] data = baos.toByteArray();
        assertTrue("Serialized data must contain something", data.length > 0);
    }

    @Test
    public void testSerialize_OutputStreamThrowsIOException() {
        try {
            SerializationUtils.serialize(new TestBean("y", 9), new FailingOutputStream());
            fail("Expected SerializationException because the OutputStream fails");
        } catch (SerializationException ex) {
            // expected – wrapped IOException
            assertTrue(ex.getCause() instanceof IOException);
        }
    }

    /* ---------------------------------------------------------------------- */
    /* 3. serialize(Serializable) → byte[]                                      */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testSerializeToByteArray_NullObject() {
        byte[] data = SerializationUtils.serialize((Serializable) null);
        assertNotNull("Resulting byte array must not be null", data);
        // Deserialising the byte array should give back null
        Object obj = SerializationUtils.deserialize(data);
        assertNull("Deserialized object must be null", obj);
    }

    @Test
    public void testSerializeToByteArray_RoundTrip() {
        TestBean bean = new TestBean("gamma", 123);
        byte[] data = SerializationUtils.serialize(bean);
        assertTrue("Serialized byte array should not be empty", data.length > 0);
        Object deserialized = SerializationUtils.deserialize(data);
        assertTrue(deserialized instanceof TestBean);
        assertEquals(bean, deserialized);
    }

    /* ---------------------------------------------------------------------- */
    /* 4. deserialize(InputStream)                                              */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStream() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void testDeserialize_ValidStream() {
        TestBean bean = new TestBean("delta", 55);
        byte[] data = SerializationUtils.serialize(bean);
        InputStream is = new ByteArrayInputStream(data);
        Object result = SerializationUtils.deserialize(is);
        assertEquals(bean, result);
    }

    @Test
    public void testDeserialize_InputStreamThrowsIOException() {
        try {
            SerializationUtils.deserialize(new FailingInputStream());
            fail("Expected SerializationException because the InputStream fails");
        } catch (SerializationException ex) {
            assertTrue(ex.getCause() instanceof IOException);
        }
    }

    /* ---------------------------------------------------------------------- */
    /* 5. deserialize(byte[])                                                   */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeByteArray_NullArgument() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserializeByteArray_RoundTrip() {
        TestBean bean = new TestBean("epsilon", 99);
        byte[] data = SerializationUtils.serialize(bean);
        Object result = SerializationUtils.deserialize(data);
        assertEquals(bean, result);
    }

    @Test
    public void testDeserializeByteArray_CorruptedData() {
        byte[] corrupted = new byte[] {0, 1, 2, 3, 4, 5};
        try {
            SerializationUtils.deserialize(corrupted);
            fail("Expected SerializationException because the data is corrupted");
        } catch (SerializationException ex) {
            // expected – underlying IOException or StreamCorruptedException
        }
    }

    /* ---------------------------------------------------------------------- */
    /* 6. Additional sanity checks – ensure the class can be instantiated   */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testPublicConstructor() {
        // The constructor is public (for bean tools). Just instantiate it.
        SerializationUtils utils = new SerializationUtils();
        assertNotNull(utils);
    }
}
###Test END##
```