package org.apache.commons.lang3;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.io.*;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Comprehensive JUnit4 test suite for {@link SerializationUtils}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Typical usage of clone, serialize and deserialize methods</li>
 *   <li>Edge cases such as {@code null} arguments and {@code null} payloads</li>
 *   <li>Error scenarios that should result in {@link IllegalArgumentException}
 *       or {@link SerializationException}</li>
 *   <li>Behaviour of the internal {@code ClassLoaderAwareObjectInputStream}</li>
 * </ul>
 * </p>
 */
public class SerializationUtilsTest {

    /* --------------------------------------------------------------------- */
    /* Helper classes used in the tests                                      */
    /* --------------------------------------------------------------------- */

    /** Simple serializable bean used for deep‑clone verification. */
    static class Simple implements Serializable {
        private static final long serialVersionUID = 1L;
        int value;

        Simple(int value) { this.value = value; }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Simple) && ((Simple) o).value == this.value;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(value);
        }
    }

    /** Bean that contains another serializable object – used to test deep cloning. */
    static class Nested implements Serializable {
        private static final long serialVersionUID = 1L;
        Simple inner;

        Nested(Simple inner) { this.inner = inner; }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Nested) && ((Nested) o).inner.equals(this.inner);
        }

        @Override
        public int hashCode() {
            return inner.hashCode();
        }
    }

    /** Class that is NOT serializable – used to provoke a serialization failure. */
    static class NotSerializable {
        // intentionally empty
    }

    /** Serializable class that contains a non‑serializable field – triggers NotSerializableException. */
    static class Bad implements Serializable {
        private static final long serialVersionUID = 1L;
        NotSerializable ns = new NotSerializable();
    }

    /** Custom ClassLoader that refuses to load a specific class name. */
    static class RejectingClassLoader extends ClassLoader {
        private final String rejectedName;

        RejectingClassLoader(String rejectedName, ClassLoader parent) {
            super(parent);
            this.rejectedName = rejectedName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(rejectedName)) {
                throw new ClassNotFoundException("Rejecting class: " + name);
            }
            return super.loadClass(name, resolve);
        }
    }

    /* --------------------------------------------------------------------- */
    /* 1. clone() – typical usage, null handling and failure scenarios       */
    /* --------------------------------------------------------------------- */

    @Test
    public void testCloneSimpleObject() {
        Simple original = new Simple(42);
        Simple copy = SerializationUtils.clone(original);
        assertNotSame("Clone must be a different instance", original, copy);
        assertEquals("Clone must be equal to the original", original, copy);
    }

    @Test
    public void testCloneNestedObjectDeepCopy() {
        Simple inner = new Simple(7);
        Nested original = new Nested(inner);
        Nested copy = SerializationUtils.clone(original);
        assertNotSame("Root object must be a different instance", original, copy);
        assertNotSame("Nested inner object must also be a different instance",
                original.inner, copy.inner);
        assertEquals("Clone must be equal to the original", original, copy);
    }

    @Test
    public void testCloneNullReturnsNull() {
        assertNull("Cloning null should return null", SerializationUtils.clone(null));
    }

    @Test(expected = SerializationException.class)
    public void testCloneWithUnserializableFieldThrowsException() {
        Bad bad = new Bad();
        SerializationUtils.clone(bad); // should raise SerializationException caused by NotSerializableException
    }

    /* --------------------------------------------------------------------- */
    /* 2. serialize(Object, OutputStream) – normal, null payload, and error  */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeObjectToOutputStream() throws IOException {
        Simple obj = new Simple(123);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(obj, baos);
        byte[] data = baos.toByteArray();
        assertTrue("Serialized data should not be empty", data.length > 0);

        // Verify that deserialization yields the same object
        Object deserialized = SerializationUtils.deserialize(new ByteArrayInputStream(data));
        assertEquals("Deserialized object must equal original", obj, deserialized);
    }

    @Test
    public void testSerializeNullObject() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        byte[] data = baos.toByteArray();
        assertTrue("Data for a null object must be non‑empty (null reference token)", data.length > 0);

        Object result = SerializationUtils.deserialize(new ByteArrayInputStream(data));
        assertNull("Deserialization of a null payload must return null", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSerializeWithNullOutputStreamThrowsException() {
        SerializationUtils.serialize(new Simple(1), null);
    }

    @Test(expected = SerializationException.class)
    public void testSerializeUnserializableObjectThrowsException() {
        Bad bad = new Bad();
        // The method should wrap NotSerializableException into SerializationException
        SerializationUtils.serialize(bad, new ByteArrayOutputStream());
    }

    /* --------------------------------------------------------------------- */
    /* 3. serialize(Object) – byte[] overload                                   */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeToByteArray() {
        Simple obj = new Simple(99);
        byte[] bytes = SerializationUtils.serialize(obj);
        assertNotNull("Byte array must not be null", bytes);
        assertTrue("Byte array must contain data", bytes.length > 0);

        Object deserialized = SerializationUtils.deserialize(bytes);
        assertEquals("Deserialized object must equal original", obj, deserialized);
    }

    @Test
    public void testSerializeNullToByteArray() {
        byte[] bytes = SerializationUtils.serialize(null);
        assertNotNull("Byte array for null payload must not be null", bytes);
        assertTrue("Byte array must not be empty", bytes.length > 0);

        Object result = SerializationUtils.deserialize(bytes);
        assertNull("Deserialization of null payload must return null", result);
    }

    /* --------------------------------------------------------------------- */
    /* 4. deserialize(InputStream) – normal, null argument and corrupted data */
    /* --------------------------------------------------------------------- */

    @Test
    public void testDeserializeFromInputStream() {
        Simple original = new Simple(55);
        byte[] data = SerializationUtils.serialize(original);
        InputStream in = new ByteArrayInputStream(data);
        Object obj = SerializationUtils.deserialize(in);
        assertEquals("Deserialized object must match original", original, obj);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeFromNullInputStreamThrowsException() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test(expected = SerializationException.class)
    public void testDeserializeCorruptedDataThrowsException() {
        byte[] corrupted = new byte[] {0x01, 0x02, 0x03, 0x04};
        SerializationUtils.deserialize(corrupted);
    }

    /* --------------------------------------------------------------------- */
    /* 5. deserialize(byte[]) – normal, null argument                           */
    /* --------------------------------------------------------------------- */

    @Test
    public void testDeserializeFromByteArray() {
        Simple original = new Simple(77);
        byte[] data = SerializationUtils.serialize(original);
        Object obj = SerializationUtils.deserialize(data);
        assertEquals("Deserialized object must match original", original, obj);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeFromNullByteArrayThrowsException() {
        SerializationUtils.deserialize((byte[]) null);
    }

    /* --------------------------------------------------------------------- */
    /* 6. ClassLoaderAwareObjectInputStream – resolveClass fallback mechanism */
    /* --------------------------------------------------------------------- */

    @Test
    public void testClassLoaderAwareObjectInputStreamFallbackToThreadContext() throws Exception {
        // Serialize a Simple instance using the default mechanism
        Simple original = new Simple(13);
        byte[] payload = SerializationUtils.serialize(original);

        // Create a ClassLoader that *rejects* loading Simple
        ClassLoader rejecting = new RejectingClassLoader(Simple.class.getName(),
                Thread.currentThread().getContextClassLoader());

        // Use the custom ClassLoaderAwareObjectInputStream with the rejecting loader
        try (ByteArrayInputStream bais = new ByteArrayInputStream(payload);
             SerializationUtils.ClassLoaderAwareObjectInputStream clIn =
                     new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, rejecting)) {

            Object deserialized = clIn.readObject();
            assertEquals("Fallback to thread context ClassLoader must succeed", original, deserialized);
        }
    }

    @Test(expected = SerializationException.class)
    public void testClassLoaderAwareObjectInputStreamWhenBothLoadersFail() throws Exception {
        // Serialize a Simple instance
        Simple original = new Simple(31);
        byte[] payload = SerializationUtils.serialize(original);

        // Create a ClassLoader that rejects Simple *and* set thread context loader that also rejects it
        ClassLoader rejecting = new RejectingClassLoader(Simple.class.getName(),
                null); // parent null -> cannot load anything
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContext = currentThread.getContextClassLoader();
        try {
            // Temporarily replace the thread context ClassLoader with another rejecting loader
            currentThread.setContextClassLoader(rejecting);

            // Attempt deserialization – should fail and be wrapped in SerializationException
            try (ByteArrayInputStream bais = new ByteArrayInputStream(payload);
                 SerializationUtils.ClassLoaderAwareObjectInputStream clIn =
                         new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, rejecting)) {
                clIn.readObject(); // expected to throw
            }
        } finally {
            // Restore original context loader to avoid side‑effects on other tests
            currentThread.setContextClassLoader(originalContext);
        }
    }

    /* --------------------------------------------------------------------- */
    /* 7. Verify that resources are closed even when exceptions occur         */
    /* --------------------------------------------------------------------- */

    private static class CloseTrackingOutputStream extends OutputStream {
        boolean closed = false;
        @Override public void write(int b) { /* no‑op */ }
        @Override public void close() { closed = true; }
    }

    private static class CloseTrackingInputStream extends InputStream {
        boolean closed = false;
        @Override public int read() { return -1; } // end‑of‑stream
        @Override public void close() { closed = true; }
    }

    @Test
    public void testSerializeClosesOutputStreamEvenOnException() {
        CloseTrackingOutputStream ctOut = new CloseTrackingOutputStream();
        // Force an exception by serializing a non‑serializable object
        try {
            SerializationUtils.serialize(new Bad(), ctOut);
            fail("Expected SerializationException");
        } catch (SerializationException ignored) {
            // expected
        }
        assertTrue("OutputStream must be closed after exception", ctOut.closed);
    }

    @Test
    public void testDeserializeClosesInputStreamEvenOnException() {
        // Corrupt data to cause an exception during readObject()
        CloseTrackingInputStream ctIn = new CloseTrackingInputStream();
        try {
            SerializationUtils.deserialize(ctIn);
            fail("Expected SerializationException");
        } catch (SerializationException ignored) {
            // expected
        }
        assertTrue("InputStream must be closed after exception", ctIn.closed);
    }
}
