package org.apache.commons.lang3;

import org.junit.Test;
import org.junit.Assert;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive JUnit‑4 test suite for {@link SerializationUtils}.
 *
 * <p>This test class covers:
 * <ul>
 *   <li>Typical usage of all public static methods.</li>
 *   <li>Edge‑case handling such as {@code null} arguments.</li>
 *   <li>Error scenarios that should result in {@link IllegalArgumentException}
 *       or {@link SerializationException}.</li>
 *   <li>Special behaviour of the inner {@code ClassLoaderAwareObjectInputStream}
 *       (fallback to the thread‑context class loader).</li>
 *   <li>Deep‑clone semantics (different instance, equal state).</li>
 * </ul>
 *
 * @author  Test‑author
 */
public class SerializationUtilsTest {

    /* --------------------------------------------------------------------- */
    /* Helper serializable types used throughout the test suite              */
    /* --------------------------------------------------------------------- */

    /** Simple POJO with proper {@code equals}/{@code hashCode} implementation. */
    private static class SimpleSerializable implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final int value;

        SimpleSerializable(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SimpleSerializable)) return false;
            SimpleSerializable that = (SimpleSerializable) o;
            return value == that.value && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + value;
        }
    }

    /** Serializable class that deliberately throws an {@link IOException} from {@code writeObject}. */
    private static class WriteExceptionSerializable implements Serializable {
        private static final long serialVersionUID = 1L;

        private void writeObject(ObjectOutputStream out) throws IOException {
            throw new IOException("forced write exception");
        }
    }

    /**
     * Custom {@link ClassLoader} that refuses to load a specific class name,
     * delegating all other requests to its parent.  Used to test the fallback
     * mechanism of {@code ClassLoaderAwareObjectInputStream}.
     */
    private static class BlockingClassLoader extends ClassLoader {
        private final String blockedClassName;

        BlockingClassLoader(ClassLoader parent, String blockedClassName) {
            super(parent);
            this.blockedClassName = blockedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(blockedClassName)) {
                throw new ClassNotFoundException("blocked by test loader");
            }
            return super.loadClass(name, resolve);
        }
    }

    /* --------------------------------------------------------------------- */
    /* clone(T) tests                                                       */
    /* --------------------------------------------------------------------- */

    @Test
    public void testClone_NullInput_ReturnsNull() {
        Assert.assertNull(SerializationUtils.clone(null));
    }

    @Test
    public void testClone_DeepCopy() {
        Map<String, Integer> original = new HashMap<>();
        original.put("one", 1);
        original.put("two", 2);

        @SuppressWarnings("unchecked")
        Map<String, Integer> copy = SerializationUtils.clone((Serializable) original);

        // Verify a deep copy (different instance, same content)
        Assert.assertNotSame(original, copy);
        Assert.assertEquals(original, copy);

        // Mutate the copy and ensure original stays unchanged
        copy.put("three", 3);
        Assert.assertFalse(original.containsKey("three"));
    }

    @Test
    public void testClone_WithClassLoaderFallback() {
        // Object to clone
        SimpleSerializable payload = new SimpleSerializable("fallback", 42);

        // Create a class loader that cannot load SimpleSerializable
        BlockingClassLoader blockingLoader =
                new BlockingClassLoader(this.getClass().getClassLoader(),
                                        SimpleSerializable.class.getName());

        // Set the thread‑context loader to the original one (so fallback works)
        ClassLoader originalContext = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        try {
            SimpleSerializable cloned = SerializationUtils.clone(payload);
            Assert.assertEquals(payload, cloned);
            Assert.assertNotSame(payload, cloned);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContext);
        }
    }

    /* --------------------------------------------------------------------- */
    /* serialize(Serializable, OutputStream) tests                          */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_NullOutputStream_Throws() {
        SerializationUtils.serialize("test", null);
    }

    @Test
    public void testSerialize_NullObject_WritesNullReference() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);

        // deserialize manually to verify a null reference was written
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            Object obj = ois.readObject();
            Assert.assertNull(obj);
        }
    }

    @Test
    public void testSerialize_NormalObject_WritesCorrectBytes() throws IOException, ClassNotFoundException {
        SimpleSerializable src = new SimpleSerializable("hello", 123);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(src, baos);

        // read back using standard ObjectInputStream to verify correctness
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Object deserialized = ois.readObject();
        Assert.assertEquals(src, deserialized);
    }

    @Test(expected = SerializationException.class)
    public void testSerialize_ObjectWithWriteException_ThrowsSerializationException() {
        WriteExceptionSerializable bad = new WriteExceptionSerializable();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(bad, baos);
    }

    /* --------------------------------------------------------------------- */
    /* serialize(Serializable) returning byte[] tests                        */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeToByteArray_NullObject() {
        // Null is a legal value – it should be serialized as a null reference.
        byte[] data = SerializationUtils.serialize(null);
        Assert.assertNotNull(data);
        // deserializing should give back null
        Object obj = SerializationUtils.deserialize(data);
        Assert.assertNull(obj);
    }

    @Test
    public void testSerializeToByteArray_RoundTrip() {
        SimpleSerializable src = new SimpleSerializable("roundtrip", 999);
        byte[] data = SerializationUtils.serialize(src);
        Object result = SerializationUtils.deserialize(data);
        Assert.assertEquals(src, result);
    }

    /* --------------------------------------------------------------------- */
    /* deserialize(InputStream) tests                                        */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStream_Throws() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void testDeserialize_ValidStream_ReturnsObject() throws IOException {
        SimpleSerializable src = new SimpleSerializable("stream", 7);
        byte[] data = SerializationUtils.serialize(src);
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        Object result = SerializationUtils.deserialize(bais);
        Assert.assertEquals(src, result);
    }

    @Test(expected = SerializationException.class)
    public void testDeserialize_CorruptData_ThrowsSerializationException() {
        // Random bytes that do not represent a serialized object
        byte[] garbage = new byte[] {0x01, 0x02, 0x03, 0x04};
        ByteArrayInputStream bais = new ByteArrayInputStream(garbage);
        SerializationUtils.deserialize(bais);
    }

    /* --------------------------------------------------------------------- */
    /* deserialize(byte[]) tests                                             */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_ByteArray_NullInput_Throws() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserialize_ByteArray_RoundTrip() {
        SimpleSerializable src = new SimpleSerializable("bytes", 55);
        byte[] data = SerializationUtils.serialize(src);
        Object result = SerializationUtils.deserialize(data);
        Assert.assertEquals(src, result);
    }

    @Test(expected = SerializationException.class)
    public void testDeserialize_ByteArray_CorruptData_ThrowsSerializationException() {
        byte[] garbage = new byte[] {0x0A, 0x0B, 0x0C};
        SerializationUtils.deserialize(garbage);
    }

    /* --------------------------------------------------------------------- */
    /* ClassLoaderAwareObjectInputStream internal behaviour tests           */
    /* --------------------------------------------------------------------- */

    @Test
    public void testClassLoaderAwareObjectInputStream_UsesProvidedClassLoader() throws Exception {
        // Serialize a SimpleSerializable instance
        SimpleSerializable src = new SimpleSerializable("clAware", 321);
        byte[] data = SerializationUtils.serialize(src);

        // Use a class loader that can load the class (the default one)
        ClassLoaderAwareObjectInputStream in = new SerializationUtils.ClassLoaderAwareObjectInputStream(
                new ByteArrayInputStream(data),
                this.getClass().getClassLoader()
        );
        Object deserialized = in.readObject();
        in.close();

        Assert.assertEquals(src, deserialized);
    }

    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToThreadContext() throws Exception {
        // Serialize an instance
        SimpleSerializable src = new SimpleSerializable("fallbackCL", 777);
        byte[] data = SerializationUtils.serialize(src);

        // Block the class loader from loading SimpleSerializable
        BlockingClassLoader blockingLoader = new BlockingClassLoader(
                this.getClass().getClassLoader(),
                SimpleSerializable.class.getName()
        );

        // Ensure thread‑context loader can load it
        ClassLoader originalCtx = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        try {
            SerializationUtils.ClassLoaderAwareObjectInputStream in =
                    new SerializationUtils.ClassLoaderAwareObjectInputStream(
                            new ByteArrayInputStream(data),
                            blockingLoader
                    );
            Object deserialized = in.readObject();
            in.close();

            Assert.assertEquals(src, deserialized);
        } finally {
            Thread.currentThread().setContextClassLoader(originalCtx);
        }
    }
}
