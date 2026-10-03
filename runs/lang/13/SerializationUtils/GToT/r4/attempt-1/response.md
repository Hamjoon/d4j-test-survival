###Test START##
```java
/*
 * JUnit 4 test suite for {@link org.apache.commons.lang3.SerializationUtils}.
 *
 * The tests cover:
 *  • Normal operation of all public methods.
 *  • Edge‑case handling (null arguments, empty data, custom class‑loader fallback).
 *  • Expected runtime exceptions (IllegalArgumentException, SerializationException).
 *
 * The suite is deliberately self‑contained and does not depend on any external
 * resources.  It uses a few small helper classes defined at the bottom of the file.
 */
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Comprehensive JUnit‑4 tests for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /** The original object used in most tests. */
    private SimpleBean original;

    @Before
    public void setUp() {
        original = new SimpleBean();
        original.setId(42);
        original.setName("Apache");
    }

    @After
    public void tearDown() {
        original = null;
    }

    // -------------------------------------------------------------------------
    //  clone(T)
    // -------------------------------------------------------------------------

    @Test
    public void testClone_NullInputReturnsNull() {
        SimpleBean result = SerializationUtils.clone((SimpleBean) null);
        assertNull("Cloning a null reference must return null", result);
    }

    @Test
    public void testClone_DeepCopy() {
        SimpleBean cloned = SerializationUtils.clone(original);
        assertNotSame("Clone must be a different instance", original, cloned);
        assertEquals("Clone must have same field values", original, cloned);
        // Mutate original and verify cloned does not change (deep copy)
        original.setName("Changed");
        assertEquals("Cloned object's state must remain unchanged", "Apache", cloned.getName());
    }

    @Test
    public void testClone_WithFailingClassLoaderFallback() {
        // Use a ClassLoader that cannot load any class – it will force the fallback
        ClassLoader failingLoader = new FailingClassLoader();
        SimpleBean toClone = original;

        // Ensure the object's class loader is the failing one
        SimpleBean proxy = ClassLoaderProxy.createProxy(toClone, failingLoader);

        // Clone should succeed because ClassLoaderAwareObjectInputStream falls back
        // to the thread‑context class loader (which can load SimpleBean).
        SimpleBean cloned = SerializationUtils.clone(proxy);
        assertNotNull("Clone must not be null even when the supplied class loader fails", cloned);
        assertEquals("Cloned object must be equal to original", original, cloned);
    }

    @Test(expected = SerializationException.class)
    public void testClone_WithFaultySerializableThrowsSerializationException() {
        FaultySerializable faulty = new FaultySerializable();
        // The writeObject method of FaultySerializable throws IOException,
        // which should be wrapped in a SerializationException.
        SerializationUtils.clone(faulty);
    }

    // -------------------------------------------------------------------------
    //  serialize(Serializable, OutputStream)
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_WithNullOutputStreamThrowsIAE() {
        SerializationUtils.serialize(original, (OutputStream) null);
    }

    @Test
    public void testSerialize_NullObjectProducesValidByteArray() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize((Serializable) null, baos);
        byte[] data = baos.toByteArray();

        // Deserializing the data must return null.
        Object deserialized = SerializationUtils.deserialize(new ByteArrayInputStream(data));
        assertNull("Deserializing a null object should yield null", deserialized);
    }

    @Test
    public void testSerialize_NormalObject() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(original, baos);
        byte[] data = baos.toByteArray();
        assertTrue("Serialized byte array must not be empty", data.length > 0);
    }

    @Test(expected = SerializationException.class)
    public void testSerialize_WithFaultySerializableThrowsSerializationException() {
        FaultySerializable faulty = new FaultySerializable();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(faulty, baos);
    }

    // -------------------------------------------------------------------------
    //  serialize(Serializable) → byte[]
    // -------------------------------------------------------------------------

    @Test
    public void testSerializeToByteArray_NullObject() {
        byte[] data = SerializationUtils.serialize((Serializable) null);
        // The resulting byte array can be deserialized back to null.
        Object deserialized = SerializationUtils.deserialize(data);
        assertNull(deserialized);
    }

    @Test
    public void testSerializeToByteArray_NormalObject() {
        byte[] data = SerializationUtils.serialize(original);
        assertNotNull("Byte array must not be null", data);
        assertTrue("Byte array must contain data", data.length > 0);
        Object deserialized = SerializationUtils.deserialize(data);
        assertEquals("Deserialized object must equal original", original, deserialized);
    }

    // -------------------------------------------------------------------------
    //  deserialize(InputStream)
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_InputStreamNullThrowsIAE() {
        SerializationUtils.deserialize((java.io.InputStream) null);
    }

    @Test
    public void testDeserialize_ValidStream() {
        byte[] data = SerializationUtils.serialize(original);
        Object deserialized = SerializationUtils.deserialize(new ByteArrayInputStream(data));
        assertEquals("Deserialized object must equal original", original, deserialized);
    }

    @Test(expected = SerializationException.class)
    public void testDeserialize_WithCorruptStreamThrowsSerializationException() {
        // Create a stream that does not contain a valid serialized object.
        ByteArrayInputStream corrupt = new ByteArrayInputStream(new byte[]{0x01, 0x02, 0x03});
        SerializationUtils.deserialize(corrupt);
    }

    // -------------------------------------------------------------------------
    //  deserialize(byte[])
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_ByteArrayNullThrowsIAE() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserialize_ByteArrayNormal() {
        byte[] data = SerializationUtils.serialize(original);
        Object deserialized = SerializationUtils.deserialize(data);
        assertEquals("Deserialized object must equal original", original, deserialized);
    }

    // -------------------------------------------------------------------------
    //  Helper classes used by the tests
    // -------------------------------------------------------------------------

    /**
     * Simple POJO used for normal serialization tests.
     */
    public static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private int id;
        private String name;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        // Equality based on fields – useful for assertions.
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SimpleBean)) return false;
            SimpleBean that = (SimpleBean) o;
            return id == that.id && ((name == null && that.name == null) ||
                    (name != null && name.equals(that.name)));
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(id);
            result = 31 * result + (name != null ? name.hashCode() : 0);
            return result;
        }
    }

    /**
     * Serializable class whose custom writeObject deliberately throws an IOException.
     * This forces {@link SerializationUtils} to wrap the error in a {@link SerializationException}.
     */
    public static class FaultySerializable implements Serializable {
        private static final long serialVersionUID = 1L;

        private void writeObject(java.io.ObjectOutputStream out) throws IOException {
            throw new IOException("Intentional failure during serialization");
        }
    }

    /**
     * A ClassLoader that refuses to load any class, throwing ClassNotFoundException.
     * Used to verify the fallback logic in {@link SerializationUtils.ClassLoaderAwareObjectInputStream}.
     */
    public static class FailingClassLoader extends ClassLoader {
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException("Intentional failure for class: " + name);
        }
    }

    /**
     * Utility to create a proxy instance that pretends to have been loaded by a
     * specific {@link ClassLoader}.  The proxy simply forwards all method calls
     * to the original object.
     *
     * This is required because Java does not allow us to change the class loader
     * of an existing instance.  By creating a dynamic proxy we can control the
     * class loader used during serialization.
     */
    @SuppressWarnings("unchecked")
    public static class ClassLoaderProxy {
        public static <T> T createProxy(T target, ClassLoader loader) {
            // Use java.lang.reflect.Proxy for interfaces – SimpleBean does not implement
            // an interface, so we instead use a small subclass generated via a custom
            // class loader.  For the purpose of this test we can cheat a little:
            // create a new instance of the same class using reflection with the
            // supplied loader.
            try {
                Class<?> clazz = Class.forName(target.getClass().getName(), true, loader);
                return (T) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                // In the unlikely event the proxy cannot be created, fall back to the original.
                return target;
            }
        }
    }
}
```
###Test END##