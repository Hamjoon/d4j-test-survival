package org.apache.commons.lang3;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive JUnit‑4 test suite for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    // ---------------------------------------------------------------------
    // Helper classes
    // ---------------------------------------------------------------------

    /** Simple mutable bean used for deep‑clone verification. */
    private static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        int id;
        List<String> values = new ArrayList<>();

        SimpleBean(int id, String... vals) {
            this.id = id;
            for (String v : vals) {
                values.add(v);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SimpleBean)) {
                return false;
            }
            SimpleBean other = (SimpleBean) obj;
            return this.id == other.id && this.values.equals(other.values);
        }

        @Override
        public int hashCode() {
            return id * 31 + values.hashCode();
        }
    }

    /**
     * Serializable class that contains a non‑serializable field.
     * Serialization of an instance must fail with a {@link NotSerializableException}.
     */
    private static class BadBean implements Serializable {
        private static final long serialVersionUID = 1L;
        // This field cannot be serialized.
        Object nonSerializable = new Object();
    }

    /** OutputStream that records whether {@code close()} was invoked. */
    private static class TrackingOutputStream extends ByteArrayOutputStream {
        boolean closed = false;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    /** InputStream that records whether {@code close()} was invoked. */
    private static class TrackingInputStream extends ByteArrayInputStream {
        boolean closed = false;

        TrackingInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    /** ClassLoader that deliberately fails to load a specific class. */
    private static class FailingClassLoader extends ClassLoader {
        private final String classNameToFail;

        FailingClassLoader(String classNameToFail) {
            super(SerializationUtilsTest.class.getClassLoader());
            this.classNameToFail = classNameToFail;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(classNameToFail)) {
                throw new ClassNotFoundException("Forced failure for " + name);
            }
            return super.loadClass(name, resolve);
        }
    }

    // ---------------------------------------------------------------------
    // clone(T)
    // ---------------------------------------------------------------------

    @Test
    public void testClone_NullInputReturnsNull() {
        assertNull(SerializationUtils.clone(null));
    }

    @Test
    public void testClone_DeepCopy() {
        SimpleBean original = new SimpleBean(42, "a", "b");
        SimpleBean copy = SerializationUtils.clone(original);

        // Verify equality but different instances
        assertEquals(original, copy);
        assertNotSame(original, copy);

        // Mutate original and ensure copy is unaffected (deep clone)
        original.values.add("c");
        assertFalse(original.values.equals(copy.values));
    }

    @Test(expected = SerializationException.class)
    public void testClone_SerializationFailureThrowsSerializationException() {
        BadBean bad = new BadBean();
        // The clone method will attempt to serialize BadBean and must fail.
        SerializationUtils.clone(bad);
    }

    // ---------------------------------------------------------------------
    // serialize(Serializable, OutputStream)
    // ---------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_NullOutputStreamThrowsIllegalArgumentException() {
        SerializationUtils.serialize("test", null);
    }

    @Test
    public void testSerialize_NullObjectProducesValidByteArray() {
        TrackingOutputStream out = new TrackingOutputStream();
        SerializationUtils.serialize((Serializable) null, out);
        assertTrue("OutputStream should be closed", out.closed);
        // The JDK writes a single TC_NULL byte (0x70) for a null object.
        byte[] data = out.toByteArray();
        assertNotNull(data);
        assertTrue(data.length > 0);
        // deserialize back to verify we get a null reference
        Object result = SerializationUtils.deserialize(new ByteArrayInputStream(data));
        assertNull(result);
    }

    @Test
    public void testSerializeAndDeserialize_RoundTrip() {
        SimpleBean bean = new SimpleBean(7, "x", "y", "z");
        TrackingOutputStream out = new TrackingOutputStream();
        SerializationUtils.serialize(bean, out);
        assertTrue(out.closed);

        byte[] data = out.toByteArray();
        assertNotNull(data);
        assertTrue(data.length > 0);

        TrackingInputStream in = new TrackingInputStream(data);
        Object deserialized = SerializationUtils.deserialize(in);
        assertTrue(in.closed);
        assertEquals(bean, deserialized);
    }

    @Test(expected = SerializationException.class)
    public void testSerialize_ObjectWithBadFieldThrowsSerializationException() {
        BadBean bad = new BadBean();
        TrackingOutputStream out = new TrackingOutputStream();
        // This should fail during writeObject()
        SerializationUtils.serialize(bad, out);
    }

    // ---------------------------------------------------------------------
    // serialize(Serializable) – byte[] variant
    // ---------------------------------------------------------------------

    @Test
    public void testSerializeToByteArray_NullObject() {
        byte[] data = SerializationUtils.serialize((Serializable) null);
        assertNotNull(data);
        // As above, a serialized null must contain at least the TC_NULL marker.
        assertTrue(data.length > 0);
        Object obj = SerializationUtils.deserialize(data);
        assertNull(obj);
    }

    @Test
    public void testSerializeToByteArray_RoundTrip() {
        SimpleBean bean = new SimpleBean(99, "alpha", "beta");
        byte[] data = SerializationUtils.serialize(bean);
        assertNotNull(data);
        assertTrue(data.length > 0);
        Object result = SerializationUtils.deserialize(data);
        assertEquals(bean, result);
    }

    // ---------------------------------------------------------------------
    // deserialize(InputStream)
    // ---------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStreamThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void testDeserialize_ClosesStream() {
        SimpleBean bean = new SimpleBean(1, "one");
        byte[] data = SerializationUtils.serialize(bean);
        TrackingInputStream in = new TrackingInputStream(data);
        Object result = SerializationUtils.deserialize(in);
        assertTrue(in.closed);
        assertEquals(bean, result);
    }

    @Test(expected = SerializationException.class)
    public void testDeserialize_MalformedDataThrowsSerializationException() {
        // Random bytes that do not represent a serialized object
        byte[] garbage = new byte[]{0x01, 0x02, 0x03, 0x04};
        ByteArrayInputStream in = new ByteArrayInputStream(garbage);
        SerializationUtils.deserialize(in);
    }

    // ---------------------------------------------------------------------
    // deserialize(byte[])
    // ---------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeByteArray_NullInputThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserializeByteArray_RoundTrip() {
        SimpleBean bean = new SimpleBean(5, "five");
        byte[] data = SerializationUtils.serialize(bean);
        Object result = SerializationUtils.deserialize(data);
        assertEquals(bean, result);
    }

    @Test(expected = SerializationException.class)
    public void testDeserializeByteArray_MalformedDataThrowsSerializationException() {
        byte[] garbage = new byte[]{0x0A, 0x0B, 0x0C};
        SerializationUtils.deserialize(garbage);
    }

    // ---------------------------------------------------------------------
    // ClassLoaderAwareObjectInputStream resolveClass fallback
    // ---------------------------------------------------------------------

    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToThreadContextClassLoader() throws Exception {
        // Serialize a SimpleBean instance using the default class loader.
        SimpleBean bean = new SimpleBean(123, "fallback");
        byte[] serialized = SerializationUtils.serialize(bean);

        // Create a ClassLoader that deliberately fails to load SimpleBean.
        ClassLoader failingLoader = new FailingClassLoader(SimpleBean.class.getName());

        // Use the custom ClassLoaderAwareObjectInputStream to deserialize.
        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        SerializationUtils.ClassLoaderAwareObjectInputStream in =
                new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, failingLoader);

        Object deserialized = in.readObject();
        in.close();

        assertEquals(bean, deserialized);
    }

    // ---------------------------------------------------------------------
    // Additional sanity checks
    // ---------------------------------------------------------------------

    @Test
    public void testSerializeDeserialize_NullObjectEquality() {
        // Serialize null and deserialize – must yield null.
        byte[] data = SerializationUtils.serialize((Serializable) null);
        Object result = SerializationUtils.deserialize(data);
        assertNull(result);
    }
}
