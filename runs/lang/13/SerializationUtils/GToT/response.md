**Step 1 – Public API extraction**

| Visibility | Return type | Method (signature) |
|------------|-------------|--------------------|
| `public`   | `SerializationUtils()` | `public SerializationUtils()` |
| `public static` | `<T extends Serializable> T` | `public static <T extends Serializable> T clone(T object)` |
| `public static` | `void` | `public static void serialize(Serializable obj, OutputStream outputStream)` |
| `public static` | `byte[]` | `public static byte[] serialize(Serializable obj)` |
| `public static` | `Object` | `public static Object deserialize(InputStream inputStream)` |
| `public static` | `Object` | `public static Object deserialize(byte[] objectData)` |

*(The nested `ClassLoaderAwareObjectInputStream` is package‑private and therefore not part of the public API.)*  

---

**Step 2 – Basic JUnit 4 test ideas (one per method)**  

| Method | Basic test case (purpose) |
|--------|---------------------------|
| `clone` | `null` input returns `null`. |
| `clone` | Cloning a simple `Serializable` POJO returns an equal but *different* instance. |
| `serialize(OutputStream)` | Serialising a `null` object writes a `null` marker; deserialising yields `null`. |
| `serialize(OutputStream)` | Passing a `null` `OutputStream` throws `IllegalArgumentException`. |
| `serialize()` (byte‑array) | Serialising a POJO to a byte array and deserialising it back yields an equal object. |
| `deserialize(InputStream)` | Passing a `null` `InputStream` throws `IllegalArgumentException`. |
| `deserialize(InputStream)` | Deserialising corrupted data throws `SerializationException`. |
| `deserialize(byte[])` | Passing a `null` byte array throws `IllegalArgumentException`. |
| `deserialize(byte[])` | Deserialising a valid byte array returns the original object. |

---

**Step 3 – Edge‑case & exception scenarios**

* **`clone`** – object contains a non‑serializable field → `NotSerializableException` wrapped in `SerializationException`.  
* **`serialize`** – output stream that throws `IOException` on `close()` – not observable because the implementation swallows the close‑exception, but we can verify that the method still completes without propagating it.  
* **`deserialize(InputStream)`** – stream that throws `IOException` while reading → `SerializationException`.  
* **`deserialize(byte[])`** – empty array (`new byte[0]`) → `SerializationException` (no object header).  
* **`deserialize` with custom class loader** – not directly reachable, but we can provoke the fallback path by using a class loader that cannot load the class (e.g., `new URLClassLoader(new URL[0])`). The test verifies that deserialization still succeeds because the thread‑context loader is used.

---

**Step 4 – Full JUnit 4 test file**

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
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;

/**
 * Comprehensive JUnit‑4 test suite for {@link SerializationUtils}.
 *
 * Covers:
 *   – normal behaviour,
 *   – edge‑cases,
 *   – expected exceptions.
 */
public class SerializationUtilsTest {

    /** Simple POJO used for (de)serialisation tests. */
    private static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final int value;

        SimpleBean(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SimpleBean)) {
                return false;
            }
            SimpleBean other = (SimpleBean) obj;
            return name.equals(other.name) && value == other.value;
        }
    }

    /** Bean that contains a non‑serialisable field – used to provoke a failure. */
    private static class BadBean implements Serializable {
        private static final long serialVersionUID = 1L;
        // non‑serialisable field
        private final Thread nonSerializable = new Thread();
    }

    /* ---------------------------------------------------------------------- */
    /* clone() tests                                                          */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testClone_NullInput_ReturnsNull() {
        assertNull(SerializationUtils.clone(null));
    }

    @Test
    public void testClone_SimpleBean_DeepClone() {
        SimpleBean original = new SimpleBean("test", 42);
        SimpleBean copy = SerializationUtils.clone(original);
        assertNotSame("Clone must be a different instance", original, copy);
        assertEquals("Clone must be equal to the original", original, copy);
    }

    @Test(expected = SerializationException.class)
    public void testClone_BadBean_ThrowsSerializationException() {
        BadBean bad = new BadBean();
        // The call itself should throw because the inner Thread is not serialisable
        SerializationUtils.clone(bad);
    }

    /* ---------------------------------------------------------------------- */
    /* serialize(Serializable, OutputStream) tests                            */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testSerialize_NullObject_WritesNullMarker() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        // read back
        Object deserialized = SerializationUtils.deserialize(new ByteArrayInputStream(baos.toByteArray()));
        assertNull("Deserialized object must be null", deserialized);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_NullOutputStream_ThrowsIllegalArgumentException() {
        SerializationUtils.serialize("any", null);
    }

    @Test
    public void testSerialize_NullObject_WithFaultyClose_StreamClosesSilently() {
        // custom OutputStream that throws on close()
        OutputStream faulty = new OutputStream() {
            private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();

            @Override public void write(int b) throws IOException { delegate.write(b); }

            @Override public void close() throws IOException { throw new IOException("close failed"); }
        };
        // Should not propagate the IOException from close()
        SerializationUtils.serialize(null, faulty);
    }

    /* ---------------------------------------------------------------------- */
    /* serialize(Serializable) → byte[] tests                                 */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testSerializeToByteArray_RoundTrip() {
        SimpleBean bean = new SimpleBean("roundtrip", 99);
        byte[] data = SerializationUtils.serialize(bean);
        Object result = SerializationUtils.deserialize(data);
        assertEquals(bean, result);
    }

    /* ---------------------------------------------------------------------- */
    /* deserialize(InputStream) tests                                          */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStream_ThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test(expected = SerializationException.class)
    public void testDeserialize_CorruptedStream_ThrowsSerializationException() {
        byte[] garbage = new byte[] {0x01, 0x02, 0x03, 0x04};
        InputStream in = new ByteArrayInputStream(garbage);
        SerializationUtils.deserialize(in);
    }

    @Test
    public void testDeserialize_ValidStream_ReturnsObject() {
        SimpleBean bean = new SimpleBean("valid", 7);
        byte[] data = SerializationUtils.serialize(bean);
        InputStream in = new ByteArrayInputStream(data);
        Object result = SerializationUtils.deserialize(in);
        assertEquals(bean, result);
    }

    /* ---------------------------------------------------------------------- */
    /* deserialize(byte[]) tests                                               */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_ByteArray_Null_ThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test(expected = SerializationException.class)
    public void testDeserialize_ByteArray_Empty_ThrowsSerializationException() {
        SerializationUtils.deserialize(new byte[0]);
    }

    @Test
    public void testDeserialize_ByteArray_Valid_RoundTrip() {
        SimpleBean bean = new SimpleBean("bytearray", 123);
        byte[] data = SerializationUtils.serialize(bean);
        Object result = SerializationUtils.deserialize(data);
        assertEquals(bean, result);
    }

    /* ---------------------------------------------------------------------- */
    /* ClassLoaderAwareObjectInputStream fallback test                         */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToThreadContext() throws Exception {
        // Serialize a SimpleBean using the default class loader
        SimpleBean bean = new SimpleBean("fallback", 1);
        byte[] data = SerializationUtils.serialize(bean);

        // Use a ClassLoader that cannot load SimpleBean (empty URLClassLoader)
        ClassLoader emptyLoader = new URLClassLoader(new URL[0], null);
        // Feed the data via the private inner class using reflection
        Class<?> cls = SerializationUtils.ClassLoaderAwareObjectInputStream.class;
        java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor(InputStream.class, ClassLoader.class);
        ctor.setAccessible(true);
        Object stream = ctor.newInstance(new ByteArrayInputStream(data), emptyLoader);
        java.lang.reflect.Method readObject = cls.getMethod("readObject");
        Object deserialized = readObject.invoke(stream);
        assertEquals("Deserialization must succeed via thread‑context loader", bean, deserialized);
    }
}
###Test END##
```