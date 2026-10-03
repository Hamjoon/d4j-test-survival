###Test START##
```java
/***************************************************************************************************
 * JUnit 4 test suite for {@link org.apache.commons.lang3.SerializationUtils}
 *
 * The tests are written from the perspective of three “experts”.  Each expert contributed one
 * test case for every public method of {@code SerializationUtils}.  After the discussion the
 * suite was merged into a single comprehensive file that covers:
 *
 *   • Typical use‑cases (successful clone/serialize/deserialize)
 *   • Edge‑cases (null arguments, empty streams, transient fields)
 *   • Error‑scenarios (illegal arguments, non‑serializable payloads, corrupted data,
 *     class‑loader fallback)
 *
 * The suite can be run with any JUnit‑4 runner (e.g. Maven Surefire, IDE JUnit runner).
 **************************************************************************************************/

package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;

/**
 * Comprehensive tests for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /* -------------------------------------------------------------------------
     * Helper classes used throughout the tests
     * ---------------------------------------------------------------------- */

    /** Simple serializable POJO with proper {@code equals}/{@code hashCode}. */
    private static class Simple implements Serializable {
        private static final long serialVersionUID = 1L;
        final int value;

        Simple(int value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof Simple) && ((Simple) obj).value == this.value;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(value);
        }
    }

    /** A class that deliberately contains a non‑serializable field. */
    @SuppressWarnings("serial")
    private static class WithNonSerializableField implements Serializable {
        // This field is *not* transient → serialization must fail.
        private final Object nonSerializable = new Object();
    }

    /* -------------------------------------------------------------------------
     * 1. clone(T) – typical usage, null handling and failure cases
     * ---------------------------------------------------------------------- */

    /** Expert A – clone should return {@code null} when the source is {@code null}. */
    @Test
    public void testClone_NullInput_ReturnsNull() {
        assertNull(SerializationUtils.clone(null));
    }

    /** Expert B – clone performs a deep copy; the returned object must be equal but not identical. */
    @Test
    public void testClone_ValidObject_DeepCopy() {
        Simple original = new Simple(42);
        Simple copy = SerializationUtils.clone(original);

        assertNotSame("Clone must be a different instance", original, copy);
        assertEquals("Clone must be equal to the original", original, copy);
    }

    /** Expert C – cloning an object that cannot be serialized must throw {@link SerializationException}. */
    @Test(expected = SerializationException.class)
    public void testClone_ObjectNotSerializable_ThrowsSerializationException() {
        // The generic bound forces compile‑time safety, therefore we have to bypass it via raw type.
        @SuppressWarnings("unchecked")
        Serializable bad = (Serializable) new WithNonSerializableField();
        SerializationUtils.clone(bad);
    }

    /* -------------------------------------------------------------------------
     * 2. serialize(Serializable, OutputStream) – argument validation and I/O errors
     * ---------------------------------------------------------------------- */

    /** Expert A – passing a {@code null} OutputStream must raise {@link IllegalArgumentException}. */
    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_NullOutputStream_ThrowsIllegalArgumentException() {
        SerializationUtils.serialize(new Simple(1), (OutputStream) null);
    }

    /** Expert B – serializing a null object should produce a valid byte array that deserialises to {@code null}. */
    @Test
    public void testSerialize_NullObject_RoundTripProducesNull() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        byte[] data = baos.toByteArray();

        Object result = SerializationUtils.deserialize(data);
        assertNull("Deserialized value must be null", result);
    }

    /** Expert C – attempting to serialize a non‑serializable payload must result in {@link SerializationException}. */
    @Test(expected = SerializationException.class)
    public void testSerialize_NonSerializableObject_ThrowsSerializationException() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // WithNonSerializableField cannot be serialized because it contains a non‑serializable field.
        SerializationUtils.serialize(new WithNonSerializableField(), baos);
    }

    /* -------------------------------------------------------------------------
     * 3. serialize(Serializable) – byte‑array convenience method
     * ---------------------------------------------------------------------- */

    /** Verify that the byte‑array overload delegates correctly and that the resulting bytes are non‑empty. */
    @Test
    public void testSerializeToByteArray_NonNullObject_ReturnsNonEmptyArray() {
        Simple original = new Simple(123);
        byte[] bytes = SerializationUtils.serialize(original);
        assertNotNull("Byte array must not be null", bytes);
        assertTrue("Byte array must contain data", bytes.length > 0);
    }

    /* -------------------------------------------------------------------------
     * 4. deserialize(InputStream) – typical usage, null handling and corrupted data
     * ---------------------------------------------------------------------- */

    /** Expert A – passing a {@code null} InputStream must raise {@link IllegalArgumentException}. */
    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStream_ThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((InputStream) null);
    }

    /** Expert B – a round‑trip using serialize‑to‑byte‑array and deserialize‑from‑stream must preserve the object. */
    @Test
    public void testDeserialize_ValidStream_RoundTripPreservesObject() {
        Simple original = new Simple(777);
        byte[] data = SerializationUtils.serialize(original);
        InputStream in = new ByteArrayInputStream(data);

        Object deserialized = SerializationUtils.deserialize(in);
        assertTrue("Deserialized object must be instance of Simple", deserialized instanceof Simple);
        assertEquals(original, deserialized);
    }

    /** Expert C – feeding corrupted data must result in a {@link SerializationException}. */
    @Test(expected = SerializationException.class)
    public void testDeserialize_CorruptData_ThrowsSerializationException() {
        // Create an obviously invalid stream (random bytes that do not represent a serialized object)
        byte[] corrupt = new byte[] { 0x00, 0x01, 0x02, 0x03 };
        InputStream in = new ByteArrayInputStream(corrupt);
        SerializationUtils.deserialize(in);
    }

    /* -------------------------------------------------------------------------
     * 5. deserialize(byte[]) – null handling and delegation to stream version
     * ---------------------------------------------------------------------- */

    /** Null byte array must trigger {@link IllegalArgumentException}. */
    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeByteArray_NullArray_ThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((byte[]) null);
    }

    /** Normal round‑trip using the byte‑array overload. */
    @Test
    public void testDeserializeByteArray_RoundTrip() {
        Simple original = new Simple(555);
        byte[] data = SerializationUtils.serialize(original);
        Object result = SerializationUtils.deserialize(data);
        assertEquals(original, result);
    }

    /* -------------------------------------------------------------------------
     * 6. ClassLoaderAwareObjectInputStream – fallback to thread context ClassLoader
     * ---------------------------------------------------------------------- */

    /**
     * The custom ClassLoader used below deliberately cannot load the {@code Simple} class.
     * The {@code ClassLoaderAwareObjectInputStream} should therefore fall back to the
     * thread‑context ClassLoader (which *can* load the class) and successfully deserialize.
     */
    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToThreadContextClassLoader()
            throws Exception {

        // 1. Serialize a Simple instance using the default mechanism.
        Simple original = new Simple(999);
        byte[] payload = SerializationUtils.serialize(original);

        // 2. Create a ClassLoader that throws ClassNotFoundException for every request.
        ClassLoader deadEndLoader = new URLClassLoader(new URL[0], null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                throw new ClassNotFoundException("Intentionally dead‑end loader");
            }
        };

        // 3. Use the dead‑end loader with the custom ObjectInputStream.
        InputStream bais = new ByteArrayInputStream(payload);
        try (SerializationUtils.ClassLoaderAwareObjectInputStream in =
                     new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, deadEndLoader)) {
            Object deserialized = in.readObject();
            assertTrue("Deserialized object must be a Simple instance", deserialized instanceof Simple);
            assertEquals("Deserialized object must be equal to the original", original, deserialized);
        }
    }

    /* -------------------------------------------------------------------------
     * 7. Additional sanity – ensure that the utility class cannot be instantiated
     *    (the public constructor is only for JavaBeans compliance)
     * ---------------------------------------------------------------------- */

    @Test
    public void testUtilityClassInstantiation() {
        // The constructor is public; we simply verify that it can be called without side‑effects.
        SerializationUtils utils = new SerializationUtils();
        assertNotNull("Utility class instance should not be null", utils);
    }
}
```
###Test END##