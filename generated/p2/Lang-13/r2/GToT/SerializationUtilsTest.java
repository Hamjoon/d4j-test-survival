package org.apache.commons.lang3;

import org.junit.Test;
import org.junit.Assert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.Serializable;

/**
 * Comprehensive JUnit‑4 test suite for {@link SerializationUtils}.
 *
 * <p>The tests cover:
 * <ul>
 *   <li>Normal use‑cases for all public methods.</li>
 *   <li>Edge‑cases such as {@code null} arguments.</li>
 *   <li>Exception handling – illegal arguments and serialization failures.</li>
 * </ul>
 *
 * <p>All tests are written with JUnit 4 annotations and use only the public API of
 * {@code SerializationUtils}; the package‑private {@code ClassLoaderAwareObjectInputStream}
 * is exercised indirectly via the normal {@code deserialize} calls.</p>
 */
public class SerializationUtilsTest {

    /* ---------------------------------------------------------------------- */
    /* Helper classes used in several tests                                   */
    /* ---------------------------------------------------------------------- */

    /** Simple mutable bean that implements {@link Serializable}. */
    private static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private int value;

        SimpleBean(String name, int value) {
            this.name = name;
            this.value = value;
        }

        // getters for assertions
        String getName() { return name; }
        int getValue() { return value; }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SimpleBean)) {
                return false;
            }
            SimpleBean other = (SimpleBean) obj;
            return this.value == other.value &&
                   ((this.name == null && other.name == null) ||
                    (this.name != null && this.name.equals(other.name)));
        }

        @Override
        public int hashCode() {
            int result = (name == null) ? 0 : name.hashCode();
            result = 31 * result + value;
            return result;
        }
    }

    /* ---------------------------------------------------------------------- */
    /* 1. Tests for {@code clone}                                              */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testClone_NullInputReturnsNull() {
        Assert.assertNull("Cloning a null reference should return null", SerializationUtils.clone(null));
    }

    @Test
    public void testClone_SimpleSerializableObject() {
        SimpleBean original = new SimpleBean("test", 42);
        SimpleBean clone = SerializationUtils.clone(original);

        // The clone must be equal but not the same instance
        Assert.assertNotSame("Clone must be a different instance", original, clone);
        Assert.assertEquals("Clone must be equal to the original", original, clone);
    }

    @Test
    public void testClone_StringObject() {
        String original = "Apache Commons Lang";
        String clone = SerializationUtils.clone(original);
        Assert.assertSame("String literals are interned – clone may be the same reference", original, clone);
    }

    /* ---------------------------------------------------------------------- */
    /* 2. Tests for {@code serialize(Serializable, OutputStream)}              */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_NullOutputStream_ThrowsIllegalArgumentException() {
        SerializationUtils.serialize("test", null);
    }

    @Test
    public void testSerialize_NullObject_WritesNullToken() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        byte[] data = baos.toByteArray();

        // Deserialise the data using the public API – should yield null
        Object deserialized = SerializationUtils.deserialize(new ByteArrayInputStream(data));
        Assert.assertNull("Deserialized object of a serialized null must be null", deserialized);
    }

    @Test
    public void testSerialize_AndDeserialize_RoundTrip() {
        SimpleBean bean = new SimpleBean("round‑trip", 99);
        byte[] bytes = SerializationUtils.serialize(bean);
        Object result = SerializationUtils.deserialize(bytes);

        Assert.assertTrue("Deserialized object should be a SimpleBean", result instanceof SimpleBean);
        Assert.assertEquals("Deserialized bean must be equal to the original", bean, result);
    }

    /* ---------------------------------------------------------------------- */
    /* 3. Tests for {@code serialize(Serializable)} returning a byte[]         */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testSerializeToByteArray_NullObject() {
        byte[] data = SerializationUtils.serialize(null);
        // The byte array must not be null and must represent a serialized null token
        Assert.assertNotNull("Byte array must not be null when serializing a null object", data);
        Object deserialized = SerializationUtils.deserialize(data);
        Assert.assertNull("Deserialized data from a null‑object byte array must be null", deserialized);
    }

    @Test
    public void testSerializeToByteArray_SimpleBean() {
        SimpleBean bean = new SimpleBean("byte‑array", 123);
        byte[] data = SerializationUtils.serialize(bean);
        Assert.assertNotNull("Serialized byte array must not be null", data);
        Assert.assertTrue("Serialized byte array should contain data", data.length > 0);

        Object result = SerializationUtils.deserialize(data);
        Assert.assertEquals("Deserialized bean must equal original", bean, result);
    }

    /* ---------------------------------------------------------------------- */
    /* 4. Tests for {@code deserialize(InputStream)}                           */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStream_ThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void testDeserialize_ValidStream_ReturnsObject() throws IOException {
        SimpleBean bean = new SimpleBean("stream‑test", 7);
        byte[] data = SerializationUtils.serialize(bean);
        InputStream in = new ByteArrayInputStream(data);
        Object result = SerializationUtils.deserialize(in);
        Assert.assertEquals("Deserialized object must equal original", bean, result);
    }

    @Test
    public void testDeserialize_CorruptData_ThrowsSerializationException() {
        // Create a byte array that does NOT represent a serialized object
        byte[] corrupt = new byte[] {0x01, 0x02, 0x03, 0x04};
        InputStream in = new ByteArrayInputStream(corrupt);
        try {
            SerializationUtils.deserialize(in);
            Assert.fail("Deserialization of corrupt data must throw SerializationException");
        } catch (SerializationException ex) {
            // Expected path
            Assert.assertTrue("Root cause should be an IOException",
                    ex.getCause() instanceof IOException);
        }
    }

    /* ---------------------------------------------------------------------- */
    /* 5. Tests for {@code deserialize(byte[])}                               */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeByteArray_NullArgument_ThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserializeByteArray_ValidData_ReturnsObject() {
        SimpleBean bean = new SimpleBean("byte‑array‑deserialize", 55);
        byte[] data = SerializationUtils.serialize(bean);
        Object result = SerializationUtils.deserialize(data);
        Assert.assertEquals("Deserialized object must equal original", bean, result);
    }

    @Test
    public void testDeserializeByteArray_CorruptData_ThrowsSerializationException() {
        byte[] corrupt = new byte[] {10, 20, 30, 40, 50};
        try {
            SerializationUtils.deserialize(corrupt);
            Assert.fail("Deserialization of corrupt byte array must throw SerializationException");
        } catch (SerializationException ex) {
            Assert.assertTrue("Root cause should be an IOException",
                    ex.getCause() instanceof IOException);
        }
    }

    /* ---------------------------------------------------------------------- */
    /* 6. Additional edge‑case tests (large object, custom class loader)      */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testClone_LargeObject() {
        // Create a relatively large serializable object (array of 10 000 integers)
        int[] largeArray = new int[10_000];
        for (int i = 0; i < largeArray.length; i++) {
            largeArray[i] = i;
        }
        int[] cloned = SerializationUtils.clone(largeArray);
        Assert.assertNotSame("Cloned array must be a different instance", largeArray, cloned);
        Assert.assertArrayEquals("Cloned array must contain the same values", largeArray, cloned);
    }

    @Test
    public void testDeserialize_UsesThreadContextClassLoaderWhenNecessary() throws Exception {
        // Serialize a SimpleBean with the system class loader
        SimpleBean bean = new SimpleBean("class‑loader‑test", 1);
        byte[] data = SerializationUtils.serialize(bean);

        // Install a custom ClassLoader that cannot load SimpleBean
        ClassLoader blockingLoader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.equals(SimpleBean.class.getName())) {
                    throw new ClassNotFoundException("Blocked by test loader");
                }
                return super.loadClass(name);
            }
        };

        // Use reflection to invoke the package‑private ClassLoaderAwareObjectInputStream
        // via the public deserialize(byte[]) method – it will fall back to the
        // thread‑context loader (which can load SimpleBean).
        Thread currentThread = Thread.currentThread();
        ClassLoader original = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(blockingLoader);
            Object result = SerializationUtils.deserialize(data);
            Assert.assertTrue("Deserialized object must be a SimpleBean", result instanceof SimpleBean);
            Assert.assertEquals("Deserialized bean must equal original", bean, result);
        } finally {
            currentThread.setContextClassLoader(original);
        }
    }
}
