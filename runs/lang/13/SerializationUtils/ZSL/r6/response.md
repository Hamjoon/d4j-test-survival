###Test START##
```java
/****************************************************************************************
 * JUnit 4 test suite for {@link org.apache.commons.lang3.SerializationUtils}
 *
 * The tests cover:
 * <ul>
 *   <li>clone() – normal deep‑clone, null handling and exception wrapping</li>
 *   <li>serialize(Serializable, OutputStream) – normal, null stream, I/O error</li>
 *   <li>serialize(Serializable) – round‑trip via byte[]</li>
 *   <li>deserialize(InputStream) – normal, null stream</li>
 *   <li>deserialize(byte[]) – normal, null array</li>
 *   <li>ClassLoaderAwareObjectInputStream – class‑loader fallback logic</li>
 * </ul>
 *
 * The test class lives in the same package as {@code SerializationUtils} so that the
 * package‑private inner class {@code ClassLoaderAwareObjectInputStream} can be
 * instantiated directly.
 ****************************************************************************************/
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Objects;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Comprehensive tests for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /** Simple POJO used for normal (de)serialization tests. */
    private static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private String value;

        SimpleBean(String value) {
            this.value = value;
        }

        String getValue() {
            return value;
        }

        void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SimpleBean)) return false;
            SimpleBean other = (SimpleBean) obj;
            return Objects.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    /** Bean that contains a non‑serializable field – used to provoke a failure. */
    private static class BadBean implements Serializable {
        private static final long serialVersionUID = 1L;
        // Not serializable and NOT transient → serialization will fail
        private final Object nonSerializable = new Object();
        private String name;

        BadBean(String name) {
            this.name = name;
        }
    }

    /** OutputStream that throws IOException on any write – used to test exception wrapping. */
    private static class FailingOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            throw new IOException("forced failure");
        }
    }

    /** ClassLoader that never knows any class except java.lang.* – forces fallback. */
    private static class DummyClassLoader extends ClassLoader {
        DummyClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // Allow loading of core java classes, otherwise fail
            if (name.startsWith("java.")) {
                return super.loadClass(name, resolve);
            }
            throw new ClassNotFoundException("DummyClassLoader cannot load " + name);
        }
    }

    private ClassLoader originalContextClassLoader;

    @Before
    public void setUp() {
        // Preserve the original context class loader for later restoration
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @After
    public void tearDown() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    /* --------------------------------------------------------------------- */
    /* clone() tests */
    /* --------------------------------------------------------------------- */

    @Test
    public void testClone_NullInput_ReturnsNull() {
        assertNull(SerializationUtils.clone(null));
    }

    @Test
    public void testClone_DeepCopy() {
        SimpleBean original = new SimpleBean("original");
        SimpleBean clone = SerializationUtils.clone(original);

        assertNotSame("Clone must be a different instance", original, clone);
        assertEquals("Clone must be equal in state", original, clone);

        // Mutate original – clone must stay unchanged
        original.setValue("modified");
        assertEquals("original value changed", "modified", original.getValue());
        assertEquals("clone must retain original value", "original", clone.getValue());
    }

    @Test(expected = SerializationException.class)
    public void testClone_SerializationFailure_ThrowsSerializationException() {
        BadBean bad = new BadBean("oops");
        SerializationUtils.clone(bad); // should fail because of non‑serializable field
    }

    /* --------------------------------------------------------------------- */
    /* serialize(Serializable, OutputStream) tests */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_WithNullOutputStream_ThrowsIllegalArgumentException() {
        SerializationUtils.serialize("test", null);
    }

    @Test
    public void testSerialize_NullObject_RoundTrip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        byte[] data = baos.toByteArray();

        // Deserialise and verify we get null back
        Object deserialized = SerializationUtils.deserialize(data);
        assertNull("Deserialized object should be null", deserialized);
    }

    @Test
    public void testSerialize_WithFailingOutputStream_WrapsIOException() {
        try {
            SerializationUtils.serialize("trigger", new FailingOutputStream());
            fail("Expected SerializationException to be thrown");
        } catch (SerializationException ex) {
            assertTrue("Cause should be IOException", ex.getCause() instanceof IOException);
            assertEquals("forced failure", ex.getCause().getMessage());
        }
    }

    /* --------------------------------------------------------------------- */
    /* serialize(Serializable) tests */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerialize_ToByteArray_RoundTrip() {
        SimpleBean bean = new SimpleBean("byteArray");
        byte[] bytes = SerializationUtils.serialize(bean);
        assertNotNull("Resulting byte array must not be null", bytes);
        assertTrue("Byte array should contain data", bytes.length > 0);

        Object obj = SerializationUtils.deserialize(bytes);
        assertTrue("Deserialized object must be SimpleBean", obj instanceof SimpleBean);
        assertEquals(bean, obj);
    }

    @Test
    public void testSerialize_NullObject_ToByteArray_RoundTrip() {
        byte[] bytes = SerializationUtils.serialize(null);
        assertNotNull("Byte array must not be null even for null object", bytes);
        assertTrue("Byte array should contain data", bytes.length > 0);
        Object obj = SerializationUtils.deserialize(bytes);
        assertNull("Deserialized result must be null", obj);
    }

    /* --------------------------------------------------------------------- */
    /* deserialize(InputStream) tests */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStream_ThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((java.io.InputStream) null);
    }

    @Test
    public void testDeserialize_ValidStream_RoundTrip() throws Exception {
        SimpleBean bean = new SimpleBean("stream");
        byte[] data = SerializationUtils.serialize(bean);
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        Object result = SerializationUtils.deserialize(bais);
        assertEquals(bean, result);
    }

    /* --------------------------------------------------------------------- */
    /* deserialize(byte[]) tests */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_ByteArray_NullArray_ThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserialize_ByteArray_RoundTrip() {
        SimpleBean bean = new SimpleBean("byteArray2");
        byte[] data = SerializationUtils.serialize(bean);
        Object result = SerializationUtils.deserialize(data);
        assertEquals(bean, result);
    }

    /* --------------------------------------------------------------------- */
    /* ClassLoaderAwareObjectInputStream tests */
    /* --------------------------------------------------------------------- */

    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToThreadContextClassLoader() throws Exception {
        // Serialize a SimpleBean to a byte array using the normal mechanism
        SimpleBean bean = new SimpleBean("fallback");
        byte[] serialized = SerializationUtils.serialize(bean);

        // Install a DummyClassLoader that cannot load SimpleBean
        DummyClassLoader dummy = new DummyClassLoader(getClass().getClassLoader());
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader()); // ensure fallback works

        // Deserialize using the dummy loader – it should fall back to the thread context loader
        try (ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
             ClassLoaderAwareObjectInputStream in =
                     new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, dummy)) {

            Object deserialized = in.readObject();
            assertTrue("Deserialized object must be SimpleBean", deserialized instanceof SimpleBean);
            assertEquals("Object state must be preserved", bean, deserialized);
        }
    }

    @Test
    public void testClassLoaderAwareObjectInputStream_UsesProvidedClassLoaderWhenPossible() throws Exception {
        // Create a custom class loader that can load SimpleBean (delegates to parent)
        ClassLoader delegating = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                // delegate to parent for everything – this loader *can* find SimpleBean
                return super.loadClass(name, resolve);
            }
        };

        SimpleBean bean = new SimpleBean("direct");
        byte[] data = SerializationUtils.serialize(bean);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ClassLoaderAwareObjectInputStream in =
                     new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, delegating)) {

            Object deserialized = in.readObject();
            assertEquals(bean, deserialized);
        }
    }
}
```
###Test END##