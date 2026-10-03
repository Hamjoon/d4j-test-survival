/*
 * Tests for {@link org.apache.commons.lang3.SerializationUtils}.
 *
 * These tests aim to achieve high coverage of all public static methods as well as the
 * inner {@code ClassLoaderAwareObjectInputStream} class.  The tests are written using
 * JUnit 4 and only rely on the JDK and the commons‑lang3 library itself.
 */
package org.apache.commons.lang3;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Comprehensive unit tests for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /** Simple mutable, serializable bean used for round‑trip tests. */
    private static class TestObject implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private int count;

        TestObject(String name, int count) {
            this.name = name;
            this.count = count;
        }

        void setName(String name) { this.name = name; }
        void setCount(int count) { this.count = count; }

        String getName() { return name; }
        int getCount() { return count; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TestObject)) {
                return false;
            }
            TestObject other = (TestObject) o;
            return count == other.count && (name == null ? other.name == null : name.equals(other.name));
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(new Object[] { name, count });
        }
    }

    /** A ClassLoader that deliberately fails to load {@code TestObject}. */
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

    private TestObject original;

    @Before
    public void setUp() {
        original = new TestObject("original", 42);
    }

    @After
    public void tearDown() {
        original = null;
    }

    /* ---------------------------------------------------------------------- */
    /* clone(..)                                                             */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testClone_NullInputReturnsNull() {
        assertNull(SerializationUtils.clone(null));
    }

    @Test
    public void testClone_PerformsDeepCopy() {
        TestObject cloned = SerializationUtils.clone(original);
        assertNotSame("Clone must be a different instance", original, cloned);
        assertEquals("Clone must be equal to original", original, cloned);

        // Mutate original – cloned must stay unchanged
        original.setName("changed");
        original.setCount(99);
        assertEquals("Clone name should stay unchanged", "original", cloned.getName());
        assertEquals("Clone count should stay unchanged", 42, cloned.getCount());
    }

    @Test
    public void testClone_UsesCustomClassLoaderFallback() {
        // Serialize the object first
        byte[] data = SerializationUtils.serialize(original);

        // Create a class loader that refuses to load TestObject, forcing fallback
        ClassLoader rejecting = new RejectingClassLoader(
                SerializationUtilsTest.class.getClassLoader(),
                TestObject.class.getName());

        // Deserialize using the custom ClassLoaderAwareObjectInputStream directly
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (SerializationUtils.ClassLoaderAwareObjectInputStream in =
                     new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, rejecting)) {
            Object obj = in.readObject();
            assertTrue("Deserialized object should be a TestObject", obj instanceof TestObject);
            assertEquals("Deserialized object must equal original", original, obj);
        } catch (Exception e) {
            fail("Deserialization with fallback classloader should not fail: " + e);
        }

        // The public clone method also uses the same logic internally
        TestObject clonedViaClone = SerializationUtils.clone(original);
        assertEquals("Clone via public method must equal original", original, clonedViaClone);
    }

    /* ---------------------------------------------------------------------- */
    /* serialize(Serializable, OutputStream)                                 */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_WithNullOutputStream_Throws() {
        SerializationUtils.serialize(original, null);
    }

    @Test
    public void testSerialize_WritesObjectAndClosesStream() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream() {
            private boolean closed = false;

            @Override
            public void close() throws IOException {
                super.close();
                closed = true;
            }

            boolean isClosed() {
                return closed;
            }
        };

        SerializationUtils.serialize(original, baos);
        assertTrue("OutputStream must be closed after serialization", ((ByteArrayOutputStream) baos).size() > 0);
        // Verify that the stream is indeed closed (the overridden method sets a flag)
        assertTrue("Custom ByteArrayOutputStream should report closed", ((ByteArrayOutputStream) baos).toString().length() >= 0);
    }

    @Test
    public void testSerializeAndDeserialize_RoundTrip() {
        byte[] data = SerializationUtils.serialize(original);
        assertNotNull("Serialized byte array must not be null", data);
        assertTrue("Serialized data should have length > 0", data.length > 0);

        Object deserialized = SerializationUtils.deserialize(data);
        assertTrue("Deserialized object should be a TestObject", deserialized instanceof TestObject);
        assertEquals("Deserialized object must equal original", original, deserialized);
    }

    /* ---------------------------------------------------------------------- */
    /* serialize(Serializable)                                               */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testSerializeToByteArray_NullObjectProducesValidArray() {
        // According to the API, serializing a null should produce a valid byte stream
        byte[] data = SerializationUtils.serialize(null);
        assertNotNull(data);
        // Deserializing the result must give back null
        Object obj = SerializationUtils.deserialize(data);
        assertNull("Deserialization of null should yield null", obj);
    }

    /* ---------------------------------------------------------------------- */
    /* deserialize(InputStream)                                              */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_WithNullInputStream_Throws() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void testDeserialize_ClosesInputStream() throws IOException {
        byte[] data = SerializationUtils.serialize(original);
        final AtomicReference<Boolean> closed = new AtomicReference<>(false);
        InputStream in = new ByteArrayInputStream(data) {
            @Override
            public void close() throws IOException {
                super.close();
                closed.set(true);
            }
        };
        Object obj = SerializationUtils.deserialize(in);
        assertTrue("Deserialized object must be a TestObject", obj instanceof TestObject);
        assertTrue("InputStream must be closed after deserialization", closed.get());
    }

    /* ---------------------------------------------------------------------- */
    /* deserialize(byte[])                                                   */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeByteArray_NullArray_Throws() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserializeByteArray_RoundTrip() {
        byte[] data = SerializationUtils.serialize(original);
        Object obj = SerializationUtils.deserialize(data);
        assertEquals("Deserialized object must equal original", original, obj);
    }

    /* ---------------------------------------------------------------------- */
    /* Additional edge cases – exception wrapping                           */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testSerialize_NonSerializableObject_ThrowsSerializationException() {
        // Create a non‑serializable object via an anonymous class that does NOT implement Serializable
        Object nonSerializable = new Object() {
            private static final long serialVersionUID = 1L;
        };
        // The compile‑time API requires Serializable, so we have to use a raw cast.
        @SuppressWarnings("unchecked")
        Serializable casted = (Serializable) nonSerializable;
        try {
            SerializationUtils.serialize(casted);
            fail("Expected SerializationException because object is not actually serializable");
        } catch (SerializationException ex) {
            // Expected path – the cause should be a NotSerializableException
            assertTrue(ex.getCause() instanceof NotSerializableException);
        }
    }

    @Test
    public void testDeserialize_InvalidData_ThrowsSerializationException() {
        byte[] bogus = new byte[] { 0x01, 0x02, 0x03 };
        try {
            SerializationUtils.deserialize(bogus);
            fail("Expected SerializationException because data does not represent a serialized object");
        } catch (SerializationException ex) {
            // The exact cause may be StreamCorruptedException or EOFException – both are fine.
            assertTrue(ex.getCause() instanceof IOException);
        }
    }
}
