/**
 * JUnit‑4 test suite for {@link org.apache.commons.lang3.SerializationUtils}.
 *
 * The tests cover:
 * <ul>
 *   <li>Normal usage of {@code clone()}, {@code serialize()} and {@code deserialize()}</li>
 *   <li>Edge cases such as {@code null} arguments</li>
 *   <li>Error scenarios that should result in {@link IllegalArgumentException}
 *       or {@link org.apache.commons.lang3.SerializationException}</li>
 *   <li>Behaviour of the inner {@code ClassLoaderAwareObjectInputStream}</li>
 * </ul>
 *
 * The suite is deliberately written as a *single* test class – this mirrors the
 * “three experts” scenario where each expert contributes one test per public
 * method and the final file aggregates all of them.
 */
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Objects;

import org.junit.Test;

/**
 * Comprehensive test cases for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /* ---------------------------------------------------------------------- */
    /* Helper classes used by several test cases                              */
    /* ---------------------------------------------------------------------- */

    /** Simple POJO that is {@link Serializable} and implements proper {@code equals} / {@code hashCode}. */
    private static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final int    value;

        SimpleBean(String name, int value) {
            this.name  = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) { return true; }
            if (!(obj instanceof SimpleBean)) { return false; }
            SimpleBean other = (SimpleBean) obj;
            return Objects.equals(name, other.name) && value == other.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }

    /**
     * A {@code Serializable} class that deliberately contains a non‑serializable field.
     * Attempting to serialize an instance will throw {@code NotSerializableException},
     * which should be wrapped by {@link SerializationException}.
     */
    private static class BadSerializable implements Serializable {
        private static final long serialVersionUID = 1L;
        // This field is NOT serializable and is NOT marked transient.
        private final Object nonSerializable = new Object();
    }

    /**
     * Custom {@link ClassLoader} that *cannot* load a specific class (by name) and delegates
     * to its parent for everything else. Used to test the fallback logic in
     * {@code ClassLoaderAwareObjectInputStream#resolveClass}.
     */
    private static class BlockingClassLoader extends ClassLoader {
        private final String blockedClassName;

        BlockingClassLoader(String blockedClassName, ClassLoader parent) {
            super(parent);
            this.blockedClassName = blockedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(blockedClassName)) {
                // Simulate a loader that cannot find the class.
                throw new ClassNotFoundException("Blocked by test");
            }
            return super.loadClass(name, resolve);
        }
    }

    /* ---------------------------------------------------------------------- */
    /* Tests for {@code clone()}                                               */
    /* ---------------------------------------------------------------------- */

    /** {@code clone(null)} must return {@code null}. */
    @Test
    public void testClone_NullInput_ReturnsNull() {
        assertNull(SerializationUtils.clone(null));
    }

    /** A normal deep‑clone operation using a simple serializable bean. */
    @Test
    public void testClone_SimpleBean_DeepClone() {
        SimpleBean original = new SimpleBean("test", 42);
        SimpleBean cloned = SerializationUtils.clone(original);

        assertNotSame("Clone must be a different instance", original, cloned);
        assertEquals("Clone must be equal to the original", original, cloned);
    }

    /** Cloning an object that cannot be serialized must raise {@link SerializationException}. */
    @Test(expected = SerializationException.class)
    public void testClone_BadSerializable_ThrowsSerializationException() {
        BadSerializable bad = new BadSerializable();
        // The following line should trigger a NotSerializableException wrapped in SerializationException.
        SerializationUtils.clone(bad);
    }

    /* ---------------------------------------------------------------------- */
    /* Tests for {@code serialize(Serializable, OutputStream)}                  */
    /* ---------------------------------------------------------------------- */

    /** Passing a {@code null} {@link OutputStream} must raise {@link IllegalArgumentException}. */
    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_NullOutputStream_ThrowsIllegalArgumentException() {
        SerializationUtils.serialize(new SimpleBean("x", 1), (OutputStream) null);
    }

    /** Serializing a {@code null} object must succeed and produce a valid byte array that
     *  deserializes back to {@code null}. */
    @Test
    public void testSerialize_NullObject_RoundTrip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        byte[] data = baos.toByteArray();

        // Deserialize the data – it should be null.
        Object result = SerializationUtils.deserialize(data);
        assertNull("Deserialized result must be null", result);
    }

    /** Normal serialization to a {@link ByteArrayOutputStream} and subsequent
     *  deserialization must reproduce the original object. */
    @Test
    public void testSerialize_AndDeserialize_RoundTrip() throws Exception {
        SimpleBean bean = new SimpleBean("roundtrip", 99);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        SerializationUtils.serialize(bean, baos);
        byte[] bytes = baos.toByteArray();

        // Directly use the low‑level deserialize(byte[]) method.
        Object deserialized = SerializationUtils.deserialize(bytes);
        assertTrue(deserialized instanceof SimpleBean);
        assertEquals(bean, deserialized);
    }

    /* ---------------------------------------------------------------------- */
    /* Tests for {@code serialize(Serializable)} – convenience overload         */
    /* ---------------------------------------------------------------------- */

    /** Serializing a simple bean using the convenience method must return a non‑empty byte array. */
    @Test
    public void testSerializeToByteArray_SimpleBean() {
        SimpleBean bean = new SimpleBean("byteArray", 123);
        byte[] data = SerializationUtils.serialize(bean);
        assertNotNull("Returned byte array must not be null", data);
        assertTrue("Returned byte array must contain data", data.length > 0);
    }

    /* ---------------------------------------------------------------------- */
    /* Tests for {@code deserialize(InputStream)}                              */
    /* ---------------------------------------------------------------------- */

    /** Deserializing from a {@code null} {@link InputStream} must raise {@link IllegalArgumentException}. */
    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStream_ThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((InputStream) null);
    }

    /** Deserialization of a previously serialized object via streams must yield the original instance. */
    @Test
    public void testDeserialize_Stream_RoundTrip() throws Exception {
        SimpleBean original = new SimpleBean("stream", 7);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Use the high‑level serialize method that closes the stream for us.
        SerializationUtils.serialize(original, baos);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        Object result = SerializationUtils.deserialize(bais);
        assertTrue(result instanceof SimpleBean);
        assertEquals(original, result);
    }

    /** Deserialization of corrupt data must raise {@link SerializationException}. */
    @Test(expected = SerializationException.class)
    public void testDeserialize_CorruptData_ThrowsSerializationException() throws Exception {
        byte[] corrupt = new byte[] {0, 1, 2, 3, 4, 5}; // not a valid serialized stream
        SerializationUtils.deserialize(corrupt);
    }

    /* ---------------------------------------------------------------------- */
    /* Tests for {@code deserialize(byte[])} – convenience overload              */
    /* ---------------------------------------------------------------------- */

    /** Passing a {@code null} byte array must raise {@link IllegalArgumentException}. */
    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_ByteArray_Null_ThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((byte[]) null);
    }

    /** Normal deserialization from a byte array must reproduce the original object. */
    @Test
    public void testDeserialize_ByteArray_RoundTrip() {
        SimpleBean bean = new SimpleBean("array", 55);
        byte[] data = SerializationUtils.serialize(bean);
        Object recovered = SerializationUtils.deserialize(data);
        assertEquals(bean, recovered);
    }

    /* ---------------------------------------------------------------------- */
    /* Tests for the inner class {@code ClassLoaderAwareObjectInputStream}      */
    /* ---------------------------------------------------------------------- */

    /**
     * Verify that {@code ClassLoaderAwareObjectInputStream} first tries the supplied
     * {@link ClassLoader} and, if that fails, falls back to the thread‑context
     * {@link ClassLoader}. The test uses a {@link BlockingClassLoader} that refuses
     * to load {@code SimpleBean}. The fallback should succeed because the test’s
     * own classloader can load the class.
     */
    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToContextClassLoader() throws Exception {
        // Serialize a SimpleBean using the normal utility method.
        SimpleBean bean = new SimpleBean("fallback", 88);
        byte[] serialized = SerializationUtils.serialize(bean);

        // Prepare a ClassLoader that *cannot* load SimpleBean.
        BlockingClassLoader blockingLoader = new BlockingClassLoader(SimpleBean.class.getName(),
                getClass().getClassLoader());

        // Use the custom ClassLoaderAwareObjectInputStream directly.
        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        SerializationUtils.ClassLoaderAwareObjectInputStream clois =
                new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, blockingLoader);

        Object read = clois.readObject();
        clois.close();

        assertTrue("Object read should be a SimpleBean", read instanceof SimpleBean);
        assertEquals("Deserialized bean must equal the original", bean, read);
    }

    /**
     * Verify that when both the supplied {@link ClassLoader} and the thread‑context
     * {@link ClassLoader} cannot resolve the class, a {@link ClassNotFoundException}
     * propagates and is wrapped as a {@link SerializationException} by {@code clone()}.
     */
    @Test(expected = SerializationException.class)
    public void testClassLoaderAwareObjectInputStream_NoClassFound_ThrowsSerializationException() throws Exception {
        // Serialize a SimpleBean.
        SimpleBean bean = new SimpleBean("no‑class", 0);
        byte[] data = SerializationUtils.serialize(bean);

        // Create a ClassLoader that blocks every class (including the context loader).
        ClassLoader deadEnd = new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                throw new ClassNotFoundException("Everything is blocked");
            }
        };

        // Use the dead‑end loader in the clone() implementation.
        // The clone method creates a new ClassLoaderAwareObjectInputStream with
        // object.getClass().getClassLoader(). We cannot change that directly,
        // so we invoke the low‑level path ourselves.
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        SerializationUtils.ClassLoaderAwareObjectInputStream clois =
                new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, deadEnd);
        // The following readObject should fail and cause a ClassNotFoundException.
        clois.readObject();
    }

    /* ---------------------------------------------------------------------- */
    /* Additional sanity checks                                                */
    /* ---------------------------------------------------------------------- */

    /** Verify that the public no‑arg constructor does not interfere with utility usage. */
    @Test
    public void testPublicConstructor_DoesNotThrow() {
        // The class is intended to be used statically, but it must be instantiable.
        new SerializationUtils();
    }

}
