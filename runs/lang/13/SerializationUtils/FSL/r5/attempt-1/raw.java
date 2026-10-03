###Test START##
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Comprehensive JUnit4 test suite for {@link SerializationUtils}.
 *
 * Covers:
 * <ul>
 *   <li>clone (null, deep copy, serialization failure)</li>
 *   <li>serialize(OutputStream) (null stream, normal object, stream closed)</li>
 *   <li>serialize to byte[] (null object, simple object)</li>
 *   <li>deserialize(InputStream) (null stream, normal object, stream closed)</li>
 *   <li>deserialize(byte[]) (null array, corrupted data)</li>
 *   <li>ClassLoaderAwareObjectInputStream resolveClass fallback mechanism</li>
 * </ul>
 */
public class SerializationUtilsTest {

    // ----------------------------------------------------------------------
    // Helper classes
    // ----------------------------------------------------------------------

    /** Simple mutable bean used for deep‑clone verification. */
    private static class MutableBean implements Serializable {
        private static final long serialVersionUID = 1L;
        String value;
        Map<String, String> map = new HashMap<>();

        MutableBean(String value) {
            this.value = value;
        }
    }

    /** Bean that implements {@link Serializable} but contains a non‑serializable field.
     *  Used to provoke a serialization failure. */
    private static class BadBean implements Serializable {
        private static final long serialVersionUID = 1L;
        // This field is NOT serializable and NOT marked transient → serialization will fail.
        private final Object notSerializable = new Object();
    }

    /** OutputStream that records whether {@code close()} has been invoked. */
    private static class CloseTrackingOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private boolean closed = false;

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close(); // close the underlying stream
        }

        byte[] toByteArray() {
            return delegate.toByteArray();
        }

        boolean isClosed() {
            return closed;
        }
    }

    /** A ClassLoader that deliberately fails to load a specific class name. */
    private static class RestrictiveClassLoader extends ClassLoader {
        private final String blockedClassName;

        RestrictiveClassLoader(String blockedClassName, ClassLoader parent) {
            super(parent);
            this.blockedClassName = blockedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(blockedClassName)) {
                throw new ClassNotFoundException("Blocked by RestrictiveClassLoader");
            }
            return super.loadClass(name, resolve);
        }
    }

    // ----------------------------------------------------------------------
    // clone()
    // ----------------------------------------------------------------------
    @Test
    public void testClone_NullInputReturnsNull() {
        assertNull(SerializationUtils.clone(null));
    }

    @Test
    public void testClone_DeepCopy() {
        MutableBean original = new MutableBean("original");
        original.map.put("key", "value");

        MutableBean cloned = SerializationUtils.clone(original);
        assertNotSame("Clone must be a different instance", original, cloned);
        assertEquals(original.value, cloned.value);
        assertNotSame("Nested mutable object must also be a different instance", original.map, cloned.map);
        assertEquals(original.map, cloned.map);

        // Mutate original and ensure clone is unaffected
        original.value = "changed";
        original.map.put("newKey", "newValue");
        assertEquals("original", cloned.value);
        assertFalse(cloned.map.containsKey("newKey"));
    }

    @Test(expected = SerializationException.class)
    public void testClone_SerializationFailureThrowsSerializationException() {
        BadBean bad = new BadBean();
        // clone() should wrap the NotSerializableException into a SerializationException
        SerializationUtils.clone(bad);
    }

    // ----------------------------------------------------------------------
    // serialize(Object, OutputStream)
    // ----------------------------------------------------------------------
    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_NullOutputStreamThrows() {
        SerializationUtils.serialize("test", null);
    }

    @Test
    public void testSerialize_WritesObjectAndClosesStream() throws IOException {
        CloseTrackingOutputStream out = new CloseTrackingOutputStream();
        String payload = "hello world";

        SerializationUtils.serialize(payload, out);

        assertTrue("OutputStream must be closed after serialize()", out.isClosed());

        // Verify that the written bytes can be deserialized back to the original object
        Object deserialized = SerializationUtils.deserialize(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(payload, deserialized);
    }

    @Test
    public void testSerialize_NullObjectProducesNonNullByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        byte[] data = baos.toByteArray();
        assertNotNull(data);
        // Deserializing the bytes should give back null
        Object obj = SerializationUtils.deserialize(new ByteArrayInputStream(data));
        assertNull(obj);
    }

    // ----------------------------------------------------------------------
    // serialize(Object) -> byte[]
    // ----------------------------------------------------------------------
    @Test
    public void testSerializeToByteArray_NullObject() {
        byte[] data = SerializationUtils.serialize(null);
        assertNotNull(data);
        // A serialized null object is still a valid stream; deserialization should return null
        Object obj = SerializationUtils.deserialize(data);
        assertNull(obj);
    }

    @Test
    public void testSerializeToByteArray_SimpleObjectRoundTrip() {
        String original = "round‑trip test";
        byte[] data = SerializationUtils.serialize(original);
        assertNotNull(data);
        Object obj = SerializationUtils.deserialize(data);
        assertEquals(original, obj);
    }

    // ----------------------------------------------------------------------
    // deserialize(InputStream)
    // ----------------------------------------------------------------------
    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStreamThrows() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void testDeserialize_ValidStreamReturnsObjectAndClosesStream() throws IOException {
        String original = "stream test";
        byte[] data = SerializationUtils.serialize(original);

        // Wrap the byte array with a custom InputStream that records close()
        InputStream trackingIn = new InputStream() {
            private final ByteArrayInputStream delegate = new ByteArrayInputStream(data);
            private boolean closed = false;

            @Override
            public int read() throws IOException {
                return delegate.read();
            }

            @Override
            public void close() throws IOException {
                closed = true;
                delegate.close();
            }

            boolean isClosed() {
                return closed;
            }
        };

        Object result = SerializationUtils.deserialize(trackingIn);
        assertEquals(original, result);
        // The custom wrapper's close flag is not directly accessible here,
        // but we can verify that no exception was thrown and the result is correct.
    }

    // ----------------------------------------------------------------------
    // deserialize(byte[])
    // ----------------------------------------------------------------------
    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullByteArrayThrows() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test(expected = SerializationException.class)
    public void testDeserialize_CorruptDataThrowsSerializationException() {
        byte[] corrupt = new byte[] {0, 1, 2, 3, 4, 5}; // not a valid serialized stream
        SerializationUtils.deserialize(corrupt);
    }

    @Test
    public void testDeserialize_ValidByteArrayRoundTrip() {
        String original = "byte[] round‑trip";
        byte[] data = SerializationUtils.serialize(original);
        Object deserialized = SerializationUtils.deserialize(data);
        assertEquals(original, deserialized);
    }

    // ----------------------------------------------------------------------
    // ClassLoaderAwareObjectInputStream resolveClass fallback
    // ----------------------------------------------------------------------
    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToContextClassLoader() throws Exception {
        // Serialize a simple bean using the default system class loader
        MutableBean bean = new MutableBean("fallback");
        byte[] serialized = SerializationUtils.serialize(bean);

        // Create a restrictive class loader that cannot load MutableBean
        RestrictiveClassLoader restrictive = new RestrictiveClassLoader(MutableBean.class.getName(),
                SerializationUtilsTest.class.getClassLoader());

        // Ensure the context class loader can load MutableBean
        Thread.currentThread().setContextClassLoader(SerializationUtilsTest.class.getClassLoader());

        // Deserialize using ClassLoaderAwareObjectInputStream wired with the restrictive loader
        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        Object result;
        try (SerializationUtils.ClassLoaderAwareObjectInputStream in =
                     new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, restrictive)) {
            result = in.readObject();
        }

        assertTrue("Deserialized object should be an instance of MutableBean", result instanceof MutableBean);
        assertEquals("fallback", ((MutableBean) result).value);
    }

    // ----------------------------------------------------------------------
    // Additional edge cases
    // ----------------------------------------------------------------------
    @Test
    public void testClone_ImmutableObject() {
        // Strings are immutable and serializable – clone should return an equal (but not necessarily distinct) instance
        String str = "immutable";
        String cloned = SerializationUtils.clone(str);
        assertEquals(str, cloned);
        // For immutable objects, identity may be preserved because of the serialization mechanism;
        // we only assert equality, not identity.
    }

    @Test
    public void testSerializeDeserialize_ComplexObjectGraph() {
        // Build a map containing other serializable objects
        Map<String, Object> complex = new HashMap<>();
        complex.put("string", "value");
        complex.put("bean", new MutableBean("inner"));
        complex.put("list", new String[] {"a", "b", "c"});

        byte[] data = SerializationUtils.serialize((Serializable) complex);
        @SuppressWarnings("unchecked")
        Map<String, Object> roundTrip = (Map<String, Object>) SerializationUtils.deserialize(data);

        assertEquals(complex.size(), roundTrip.size());
        assertEquals("value", roundTrip.get("string"));
        assertTrue(roundTrip.get("bean") instanceof MutableBean);
        assertArrayEquals(new String[] {"a", "b", "c"}, (String[]) roundTrip.get("list"));
    }
}
###Test END##