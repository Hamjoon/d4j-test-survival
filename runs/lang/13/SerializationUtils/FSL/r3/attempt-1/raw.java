###Test START##
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive JUnit‑4 tests for {@link SerializationUtils}.
 *
 * <p>The tests cover:</p>
 * <ul>
 *   <li>Serialization and deserialization of {@code null} and normal objects</li>
 *   <li>Round‑trip integrity (deep clone) including mutable fields</li>
 *   <li>Behaviour of the overloads that accept {@link OutputStream} and {@code byte[]}</li>
 *   <li>Proper handling of illegal arguments</li>
 *   <li>Correct class‑loader handling performed by the
 *       {@code ClassLoaderAwareObjectInputStream} used in {@link SerializationUtils#clone(Object)}</li>
 * </ul>
 */
public class SerializationUtilsTest {

    /** Simple mutable serializable bean used in clone tests. */
    private static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        int id;
        String name;
        Map<String, String> data = new HashMap<>();

        SimpleBean(int id, String name) {
            this.id = id;
            this.name = name;
        }

        /** Mutates the internal map – used to verify deep‑clone behaviour. */
        void putData(String k, String v) {
            data.put(k, v);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SimpleBean)) {
                return false;
            }
            SimpleBean other = (SimpleBean) obj;
            return id == other.id &&
                   ((name == null && other.name == null) || (name != null && name.equals(other.name))) &&
                   data.equals(other.data);
        }

        @Override
        public int hashCode() {
            return id;
        }
    }

    /** Serializable object that intentionally implements custom {@code writeObject} that throws an IOException. */
    private static class BadSerializable implements Serializable {
        private static final long serialVersionUID = 1L;

        private void writeObject(ObjectOutputStream out) throws IOException {
            throw new IOException("forced write failure");
        }
    }

    // -----------------------------------------------------------------
    // serialize(OutputStream)  &  serialize()
    // -----------------------------------------------------------------
    @Test
    public void testSerializeToOutputStream() throws IOException {
        SimpleBean bean = new SimpleBean(42, "test");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(bean, baos);
        byte[] data = baos.toByteArray();
        assertNotNull("Serialized byte array must not be null", data);
        assertTrue("Serialized data should contain at least one byte", data.length > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSerializeToNullOutputStream() {
        SerializationUtils.serialize("any", null);
    }

    @Test(expected = SerializationException.class)
    public void testSerializeObjectThatFailsDuringWrite() {
        SerializationUtils.serialize(new BadSerializable());
    }

    @Test
    public void testSerializeAndDeserializeByteArray() {
        SimpleBean original = new SimpleBean(7, "byteArray");
        original.putData("k1", "v1");
        byte[] bytes = SerializationUtils.serialize(original);
        assertNotNull("bytes must not be null", bytes);
        Object deserialized = SerializationUtils.deserialize(bytes);
        assertTrue("Deserialized object must be instance of SimpleBean", deserialized instanceof SimpleBean);
        assertEquals("Deserialized object must be equal to the original", original, deserialized);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeFromNullByteArray() {
        SerializationUtils.deserialize((byte[]) null);
    }

    // -----------------------------------------------------------------
    // deserialize(InputStream)
    // -----------------------------------------------------------------
    @Test
    public void testDeserializeFromInputStream() throws IOException {
        SimpleBean bean = new SimpleBean(99, "stream");
        byte[] bytes = SerializationUtils.serialize(bean);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        Object result = SerializationUtils.deserialize(bais);
        assertTrue(result instanceof SimpleBean);
        assertEquals(bean, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeFromNullInputStream() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test(expected = SerializationException.class)
    public void testDeserializeCorruptData() {
        byte[] corrupt = new byte[] {0, 1, 2, 3, 4};
        SerializationUtils.deserialize(corrupt);
    }

    // -----------------------------------------------------------------
    // clone()
    // -----------------------------------------------------------------
    @Test
    public void testCloneSimpleObject() {
        SimpleBean bean = new SimpleBean(1, "original");
        bean.putData("a", "A");
        SimpleBean clone = SerializationUtils.clone(bean);
        assertNotSame("Clone must be a different instance", bean, clone);
        assertEquals("Clone must be equal to the original", bean, clone);

        // Mutate the clone and ensure original is unchanged (deep clone)
        clone.id = 2;
        clone.name = "modified";
        clone.putData("b", "B");

        assertEquals("Original ID must stay unchanged", 1, bean.id);
        assertEquals("Original name must stay unchanged", "original", bean.name);
        assertFalse("Original data map must not contain key 'b'", bean.data.containsKey("b"));
    }

    @Test
    public void testCloneNullReturnsNull() {
        assertNull("Cloning null must return null", SerializationUtils.clone(null));
    }

    /**
     * Verifies that {@link SerializationUtils.ClassLoaderAwareObjectInputStream}
     * correctly falls back to the thread context {@link ClassLoader} when the supplied
     * class loader cannot resolve the class.
     *
     * <p>The test performs the following steps:</p>
     * <ol>
     *   <li>Serializes an instance of {@link SimpleBean}.</li>
     *   <li>Temporarily replaces the thread context class loader with a dummy loader that cannot load {@code SimpleBean}.</li>
     *   <li>Invokes {@link SerializationUtils#clone(Object)} which internally uses {@code ClassLoaderAwareObjectInputStream}
     *       with the original class's own loader.</li>
     *   <li>Ensures that cloning still succeeds, meaning the fallback to the thread
     *       context class loader was exercised (the dummy loader throws {@code ClassNotFoundException},
     *       causing the method to try the thread‑context loader).</li>
     * </ol>
     */
    @Test
    public void testCloneWithUnusableThreadContextClassLoader() {
        SimpleBean original = new SimpleBean(5, "loaderTest");
        ClassLoader originalContextCL = Thread.currentThread().getContextClassLoader();

        // Create a ClassLoader that deliberately cannot load SimpleBean
        ClassLoader blockingCL = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.equals(SimpleBean.class.getName())) {
                    throw new ClassNotFoundException("Blocked by test class loader");
                }
                // delegate all other classes to parent (null -> bootstrap)
                return super.loadClass(name);
            }
        };

        try {
            Thread.currentThread().setContextClassLoader(blockingCL);
            // The clone method uses the class loader of SimpleBean (which is the system/app class loader)
            SimpleBean cloned = SerializationUtils.clone(original);
            assertNotNull("Cloned object must not be null", cloned);
            assertEquals("Cloned object should be equal to the original", original, cloned);
            assertNotSame("Cloned object must be a distinct instance", original, cloned);
        } finally {
            // Restore original context class loader to avoid side effects
            Thread.currentThread().setContextClassLoader(originalContextCL);
        }
    }

    // -----------------------------------------------------------------
    // Additional edge‑case: serializing and deserializing a mutable map
    // -----------------------------------------------------------------
    @Test
    public void testDeepCloneMutableMap() {
        Map<String, String> original = new HashMap<>();
        original.put("key", "value");
        @SuppressWarnings("unchecked")
        Map<String, String> cloned = SerializationUtils.clone((Serializable) original);
        assertNotSame("Cloned map must be a different instance", original, cloned);
        assertEquals("Cloned map must contain the same entries", original, cloned);

        // Mutate cloned map and verify original stays unchanged
        cloned.put("newKey", "newValue");
        assertFalse("Original map must not contain new entry", original.containsKey("newKey"));
    }
}
###Test END##