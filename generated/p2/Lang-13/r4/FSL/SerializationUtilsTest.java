package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

/**
 * Comprehensive JUnit4 test suite for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /***--------------------  clone() tests  --------------------*/

    @Test
    public void testCloneNullReturnsNull() {
        assertNull(SerializationUtils.clone(null));
    }

    @Test
    public void testCloneDeepCopy() {
        Map<String, String> original = new HashMap<>();
        original.put("key", "value");

        @SuppressWarnings("unchecked")
        Map<String, String> cloned = SerializationUtils.clone((Serializable) original);
        assertNotSame("Clone must be a different instance", original, cloned);
        assertEquals("Clone must be equal to original", original, cloned);

        // modify original – clone must stay unchanged
        original.put("key", "newValue");
        assertEquals("Clone must not be affected by changes to the original", "value", cloned.get("key"));
    }

    @Test
    public void testCloneWithClassLoaderFallback() throws IOException, ClassNotFoundException {
        // Serialize a simple String (which is Serializable)
        final String original = "fallback-test";
        final byte[] data = SerializationUtils.serialize(original);

        // A ClassLoader that deliberately fails to load any class
        ClassLoader failingLoader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                throw new ClassNotFoundException("Forced failure for testing");
            }
        };

        // Preserve original thread context class loader
        final ClassLoader originalContext = Thread.currentThread().getContextClassLoader();
        try {
            // Set the thread context class loader to the normal loader (so fallback works)
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

            // Use the private ClassLoaderAwareObjectInputStream via the public clone() method
            // The clone() method creates a new ClassLoaderAwareObjectInputStream with the
            // object's class loader (which we replace with the failing one).
            // To inject the failing loader we need a wrapper object that carries it.
            // We'll create an anonymous subclass of Serializable that overrides getClassLoader()
            // via a custom class (not possible directly). Instead we call the protected
            // constructor of ClassLoaderAwareObjectInputStream directly.

            try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                 SerializationUtils.ClassLoaderAwareObjectInputStream in =
                         new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, failingLoader)) {
                Object read = in.readObject();
                assertEquals("Deserialized object must equal original", original, read);
            }
        } finally {
            // Restore original context class loader
            Thread.currentThread().setContextClassLoader(originalContext);
        }
    }

    /***--------------------  serialize(OutputStream) tests  --------------------*/

    @Test(expected = IllegalArgumentException.class)
    public void testSerializeWithNullOutputStreamThrows() {
        SerializationUtils.serialize("test", null);
    }

    @Test
    public void testSerializeAndDeserializeWithStreams() {
        String payload = "stream-test";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(payload, baos);
        byte[] bytes = baos.toByteArray();

        // deserialize using the corresponding method
        Object result = SerializationUtils.deserialize(new ByteArrayInputStream(bytes));
        assertEquals("Deserialized object must equal original payload", payload, result);
    }

    /***--------------------  serialize(Object) / deserialize(byte[]) tests  --------------------*/

    @Test
    public void testSerializeNullObject() {
        byte[] data = SerializationUtils.serialize((Serializable) null);
        // Deserializing the above data should give back null
        Object result = SerializationUtils.deserialize(data);
        assertNull("Deserializing a serialized null should return null", result);
    }

    @Test
    public void testSerializeAndDeserializeRoundTrip() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        map.put("two", 2);

        byte[] serialized = SerializationUtils.serialize(map);
        @SuppressWarnings("unchecked")
        HashMap<String, Integer> deserialized = (HashMap<String, Integer>) SerializationUtils.deserialize(serialized);
        assertEquals("Deserialized map must equal original", map, deserialized);
    }

    /***--------------------  deserialize(InputStream) tests  --------------------*/

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeNullInputStreamThrows() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void testDeserializeCorruptDataThrowsSerializationException() {
        // Random bytes that do not represent a serialized object
        byte[] corrupt = new byte[] {0x01, 0x02, 0x03, 0x04};
        try (InputStream in = new ByteArrayInputStream(corrupt)) {
            SerializationUtils.deserialize(in);
            fail("Expected SerializationException due to corrupt data");
        } catch (SerializationException ex) {
            // Expected path
            assertTrue("Cause should be IOException", ex.getCause() instanceof IOException);
        }
    }

    /***--------------------  deserialize(byte[]) tests  --------------------*/

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeByteArrayNullThrows() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserializeByteArrayCorruptThrowsSerializationException() {
        byte[] corrupt = new byte[] {10, 20, 30};
        try {
            SerializationUtils.deserialize(corrupt);
            fail("Expected SerializationException for corrupt byte array");
        } catch (SerializationException ex) {
            assertTrue("Cause should be IOException", ex.getCause() instanceof IOException);
        }
    }

    /***--------------------  helper for cleanup (if needed) --------------------*/

    @After
    public void resetThreadContextClassLoader() {
        // Ensure that the thread context class loader is restored after each test
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
    }
}
