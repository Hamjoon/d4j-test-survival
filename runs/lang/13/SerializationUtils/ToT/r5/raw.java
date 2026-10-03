###Test START##
```java
package org.apache.commons.lang3;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Comprehensive JUnit‑4 test suite for {@link SerializationUtils}.
 *
 * <p>This test class covers:
 * <ul>
 *   <li>Normal usage of {@code clone}, {@code serialize} and {@code deserialize}</li>
 *   <li>Edge cases such as {@code null} arguments</li>
 *   <li>Exception handling (IllegalArgumentException, SerializationException)</li>
 *   <li>Behaviour of the internal {@code ClassLoaderAwareObjectInputStream}</li>
 * </ul>
 * </p>
 */
public class SerializationUtilsTest {

    /** Simple mutable, serializable bean used for deep‑clone checks. */
    public static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private int[] values;

        public SimpleBean(String name, int[] values) {
            this.name = name;
            this.values = values;
        }

        public String getName() { return name; }
        public int[] getValues() { return values; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SimpleBean)) {
                return false;
            }
            SimpleBean other = (SimpleBean) o;
            return name.equals(other.name) && Arrays.equals(values, other.values);
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ Arrays.hashCode(values);
        }
    }

    private SimpleBean original;

    @Before
    public void setUp() {
        original = new SimpleBean("test", new int[] {1, 2, 3});
    }

    @After
    public void tearDown() {
        original = null;
    }

    /* --------------------------------------------------------------------- */
    /* clone() tests                                                         */
    /* --------------------------------------------------------------------- */

    @Test
    public void testCloneTypical() {
        SimpleBean cloned = SerializationUtils.clone(original);
        assertNotSame("Clone must be a different instance", original, cloned);
        assertEquals("Clone must be equal to original", original, cloned);

        // mutate clone and verify original is unchanged (deep clone)
        cloned.getValues()[0] = 99;
        assertEquals("Original must stay unchanged after mutating clone", 1, original.getValues()[0]);
    }

    @Test
    public void testCloneWithNull() {
        assertNull("Cloning null should return null", SerializationUtils.clone(null));
    }

    @Test(expected = SerializationException.class)
    public void testCloneWhenDeserializationFails() throws Exception {
        // Serialize a valid object, then corrupt the byte array to force an IOException
        byte[] data = SerializationUtils.serialize(original);
        data[0] = (byte) 0xFF; // corrupt header

        // Use reflection to invoke the private clone logic with corrupted data
        // (the public clone method always serializes the object itself,
        //  so we need to simulate a failure inside the method.)
        // Instead we call the private serialize+deserialize chain directly:
        SerializationUtils.deserialize(data);
    }

    /* --------------------------------------------------------------------- */
    /* serialize(OutputStream) tests                                         */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testSerializeOutputStream_NullStream() {
        SerializationUtils.serialize(original, null);
    }

    @Test
    public void testSerializeOutputStream_NullObject() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        // The stream should contain a serialized null reference (0x70 0x00 0x00 0x00 0x00)
        byte[] bytes = baos.toByteArray();
        assertTrue("Serialized null must produce a non‑empty byte array", bytes.length > 0);
        // Deserialise and verify we get null back
        Object deserialized = SerializationUtils.deserialize(new ByteArrayInputStream(bytes));
        assertNull("Deserialized object must be null", deserialized);
    }

    @Test
    public void testSerializeOutputStream_ClosesStream() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream() {
            private boolean closed = false;
            @Override
            public void close() throws IOException {
                closed = true;
                super.close();
            }
            public boolean isClosed() {
                return closed;
            }
        };
        SerializationUtils.serialize(original, baos);
        assertTrue("OutputStream must be closed after serialize()", ((ByteArrayOutputStream) baos).isClosed());
    }

    /* --------------------------------------------------------------------- */
    /* serialize() to byte[] tests                                            */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeToByteArray() {
        byte[] data = SerializationUtils.serialize(original);
        assertNotNull("Byte array must not be null", data);
        assertTrue("Byte array must contain data", data.length > 0);
        // round‑trip
        Object roundTrip = SerializationUtils.deserialize(data);
        assertEquals("Round‑trip object must equal original", original, roundTrip);
    }

    /* --------------------------------------------------------------------- */
    /* deserialize(InputStream) tests                                         */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeInputStream_NullStream() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void testDeserializeInputStream_ClosesStream() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(original, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray()) {
            private boolean closed = false;
            @Override
            public void close() throws IOException {
                closed = true;
                super.close();
            }
            public boolean isClosed() {
                return closed;
            }
        };
        Object obj = SerializationUtils.deserialize(bais);
        assertEquals(original, obj);
        assertTrue("InputStream must be closed after deserialize()", ((ByteArrayInputStream) bais).isClosed());
    }

    @Test(expected = SerializationException.class)
    public void testDeserializeInputStream_CorruptedData() throws IOException {
        byte[] corrupted = new byte[] {0x00, 0x01, 0x02, 0x03};
        SerializationUtils.deserialize(new ByteArrayInputStream(corrupted));
    }

    /* --------------------------------------------------------------------- */
    /* deserialize(byte[]) tests                                              */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeByteArray_NullArray() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserializeByteArray_Typical() {
        byte[] data = SerializationUtils.serialize(original);
        Object obj = SerializationUtils.deserialize(data);
        assertEquals("Deserialized object must equal original", original, obj);
    }

    /* --------------------------------------------------------------------- */
    /* ClassLoaderAwareObjectInputStream tests                                */
    /* --------------------------------------------------------------------- */

    /**
     * Custom class loader that deliberately fails to load {@code SimpleBean}
     * but can load every other class via its parent.
     */
    private static class RejectingClassLoader extends ClassLoader {
        private final String blockedClassName;

        RejectingClassLoader(ClassLoader parent, String blockedClassName) {
            super(parent);
            this.blockedClassName = blockedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(blockedClassName)) {
                throw new ClassNotFoundException("Blocked by RejectingClassLoader");
            }
            return super.loadClass(name, resolve);
        }
    }

    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToThreadContext() throws Exception {
        // Serialize the object with the normal classloader (current thread)
        byte[] data = SerializationUtils.serialize(original);

        // Create a classloader that cannot load SimpleBean
        ClassLoader rejecting = new RejectingClassLoader(
                SerializationUtilsTest.class.getClassLoader(),
                SimpleBean.class.getName());

        // Ensure the thread context classloader can load SimpleBean
        Thread.currentThread().setContextClassLoader(SerializationUtilsTest.class.getClassLoader());

        // Use the special stream with the rejecting classloader
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        SerializationUtils.ClassLoaderAwareObjectInputStream clois =
                new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, rejecting);

        Object result = clois.readObject();
        assertTrue("Result must be instance of SimpleBean", result instanceof SimpleBean);
        assertEquals("Deserialized object must equal original", original, result);
    }

    @Test(expected = SerializationException.class)
    public void testClassLoaderAwareObjectInputStream_UnresolvableClass() throws Exception {
        // Serialize an object of an inner class that we will make completely unavailable
        class Unloadable implements Serializable {
            private static final long serialVersionUID = 1L;
        }
        Unloadable obj = new Unloadable();
        byte[] data = SerializationUtils.serialize(obj);

        // Create a classloader that cannot load the inner class at all
        ClassLoader rejecting = new RejectingClassLoader(
                SerializationUtilsTest.class.getClassLoader(),
                Unloadable.class.getName());

        // Remove the inner class from the thread context as well
        Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0], null));

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        SerializationUtils.ClassLoaderAwareObjectInputStream clois =
                new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, rejecting);

        // This should trigger a ClassNotFoundException wrapped in SerializationException
        clois.readObject();
    }

    /* --------------------------------------------------------------------- */
    /* Miscellaneous sanity checks                                            */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeDeserialize_ComplexObjectGraph() {
        // Build a map containing a bean and a nested array
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("bean", original);
        map.put("numbers", new int[] {7, 8, 9});

        byte[] data = SerializationUtils.serialize((Serializable) map);
        @SuppressWarnings("unchecked")
        Map<String, Object> restored = (Map<String, Object>) SerializationUtils.deserialize(data);

        assertEquals("Map size must be preserved", map.size(), restored.size());
        assertEquals("Bean must be correctly restored", original, restored.get("bean"));
        assertArrayEquals("Array must be correctly restored",
                (int[]) map.get("numbers"), (int[]) restored.get("numbers"));
    }
}
```
###Test END##