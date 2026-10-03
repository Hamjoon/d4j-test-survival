###Test START##
```java
/********************************************************************
 * JUnit 4 test suite for {@link org.apache.commons.lang3.SerializationUtils}
 * 
 * The tests cover:
 *   • clone()
 *   • serialize(Object, OutputStream) – normal flow & null stream
 *   • serialize(Object) – round‑trip via deserialize(byte[])
 *   • deserialize(InputStream) – normal flow & null stream
 *   • deserialize(byte[]) – normal flow & null argument
 *   • ClassLoaderAwareObjectInputStream – class‑loader fallback logic
 *
 * The test class resides in the same package to gain access to the
 * package‑private {@code ClassLoaderAwareObjectInputStream}.
 ********************************************************************/
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /** Simple serializable bean used for clone/serialization tests. */
    private static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private Map<String, Integer> data = new HashMap<>();

        SimpleBean(String name) {
            this.name = name;
        }

        void put(String key, Integer value) {
            data.put(key, value);
        }

        String getName() {
            return name;
        }

        Map<String, Integer> getData() {
            return data;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SimpleBean)) {
                return false;
            }
            SimpleBean other = (SimpleBean) obj;
            return name.equals(other.name) && data.equals(other.data);
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ data.hashCode();
        }
    }

    /** -----------------------------------------------------------------
     *  clone()
     *  ----------------------------------------------------------------- */
    @Test
    public void testClone_NullInput() {
        assertNull("clone(null) must return null", SerializationUtils.clone(null));
    }

    @Test
    public void testClone_DeepCopy() {
        SimpleBean original = new SimpleBean("test");
        original.put("a", 1);
        original.put("b", 2);

        SimpleBean cloned = SerializationUtils.clone(original);

        // Objects should be equal but not the same reference
        assertEquals("Cloned object must be equal to original", original, cloned);
        assertNotSame("Cloned object must be a different instance", original, cloned);

        // Mutating the cloned map must not affect the original (deep copy)
        cloned.getData().put("c", 3);
        assertFalse("Original map must not contain the new entry", original.getData().containsKey("c"));
    }

    /** -----------------------------------------------------------------
     *  serialize(Serializable, OutputStream)
     *  ----------------------------------------------------------------- */
    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_WithNullOutputStream() {
        SerializationUtils.serialize("foo", null);
    }

    @Test
    public void testSerialize_AndDeserialize_RoundTrip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize("hello world", baos);
        byte[] data = baos.toByteArray();

        // Ensure the data is not empty
        assertTrue("Serialized byte array should not be empty", data.length > 0);

        // Deserialize using the API that accepts a byte[]
        Object deserialized = SerializationUtils.deserialize(data);
        assertEquals("Deserialized object must equal the original", "hello world", deserialized);
    }

    /** -----------------------------------------------------------------
     *  serialize(Serializable) – byte[] overload
     *  ----------------------------------------------------------------- */
    @Test
    public void testSerializeToByteArray_NullObject() {
        // Serializing a null reference is allowed – it should produce a valid stream
        byte[] data = SerializationUtils.serialize(null);
        assertNotNull("Resulting byte array must not be null", data);
        // Deserializing it must give back null
        Object obj = SerializationUtils.deserialize(data);
        assertNull("Deserialized value must be null", obj);
    }

    /** -----------------------------------------------------------------
     *  deserialize(InputStream)
     *  ----------------------------------------------------------------- */
    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_WithNullInputStream() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void testDeserialize_WithValidStream() throws IOException {
        // Prepare a stream that contains a serialized Integer
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(Integer.valueOf(42), baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

        Object result = SerializationUtils.deserialize(bais);
        assertTrue("Result must be an Integer", result instanceof Integer);
        assertEquals("Deserialized Integer value must match original", Integer.valueOf(42), result);
    }

    /** -----------------------------------------------------------------
     *  deserialize(byte[])
     *  ----------------------------------------------------------------- */
    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeByteArray_NullArgument() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserializeByteArray_RoundTrip() {
        String original = "round‑trip test";
        byte[] bytes = SerializationUtils.serialize(original);
        Object recovered = SerializationUtils.deserialize(bytes);
        assertEquals("Recovered object must equal original", original, recovered);
    }

    /** -----------------------------------------------------------------
     *  ClassLoaderAwareObjectInputStream – fallback logic
     *  ----------------------------------------------------------------- */
    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToThreadContext() throws Exception {
        // Serialize an instance of SimpleBean using the default class loader
        SimpleBean bean = new SimpleBean("fallback");
        bean.put("x", 10);
        byte[] serialized = SerializationUtils.serialize(bean);

        // Create a ClassLoader that *cannot* load SimpleBean (throws CNF)
        ClassLoader sabotagingLoader = new ClassLoader(this.getClass().getClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                // Force failure for SimpleBean, delegate everything else
                if (name.equals(SimpleBean.class.getName())) {
                    throw new ClassNotFoundException("Simulated missing class");
                }
                return super.loadClass(name);
            }
        };

        // Use the custom ClassLoaderAwareObjectInputStream; it should fall back to the
        // thread‑context loader (which can load SimpleBean) and succeed.
        ObjectInputStream in = new SerializationUtils.ClassLoaderAwareObjectInputStream(
                new ByteArrayInputStream(serialized), sabotagingLoader);
        Object obj = in.readObject();
        in.close();

        assertTrue("Deserialized object must be instance of SimpleBean", obj instanceof SimpleBean);
        SimpleBean recovered = (SimpleBean) obj;
        assertEquals("Bean name must survive round‑trip", bean.getName(), recovered.getName());
        assertEquals("Bean map must survive round‑trip", bean.getData(), recovered.getData());
    }

    @Test(expected = SerializationException.class)
    public void testClassLoaderAwareObjectInputStream_BothLoadersFail() throws Exception {
        // Serialize a simple String – we will use a ClassLoader that cannot load ANY class.
        byte[] data = SerializationUtils.serialize("unloadable");

        // ClassLoader that always throws ClassNotFoundException
        ClassLoader deadEndLoader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                throw new ClassNotFoundException("All classes unavailable");
            }
        };

        // The thread context class loader is also set to the same dead‑end loader
        // to guarantee that the fallback also fails.
        Thread current = Thread.currentThread();
        ClassLoader previous = current.getContextClassLoader();
        try {
            current.setContextClassLoader(deadEndLoader);
            // This should trigger ClassNotFoundException inside resolveClass,
            // which is wrapped into SerializationException by deserialize(...)
            SerializationUtils.deserialize(data);
            fail("Expected SerializationException due to missing class");
        } finally {
            // Restore original context loader
            current.setContextClassLoader(previous);
        }
    }

    /** -----------------------------------------------------------------
     *  Miscellaneous – ensure serialize(byte[]) does not swallow IOException
     *  (simulated via a custom OutputStream that throws on close).
     *  ----------------------------------------------------------------- */
    @Test(expected = SerializationException.class)
    public void testSerialize_OutputStreamThrowsOnWrite() {
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("simulated write failure");
            }
        };
        SerializationUtils.serialize("boom", broken);
    }

    @Test
    public void testClone_WithSerializableSubclass() {
        // Ensure that cloning works for a class that overrides writeObject/readObject
        class CustomSerializable implements Serializable {
            private static final long serialVersionUID = 1L;
            private transient String secret = "top-secret";

            private void writeObject(ObjectOutputStream out) throws IOException {
                out.defaultWriteObject();
                // write a transformed version of secret
                out.writeObject(secret.toUpperCase());
            }

            private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
                in.defaultReadObject();
                // restore secret from the transformed version
                secret = ((String) in.readObject()).toLowerCase();
            }

            String getSecret() {
                return secret;
            }
        }

        CustomSerializable original = new CustomSerializable();
        CustomSerializable cloned = SerializationUtils.clone(original);
        assertNotSame(original, cloned);
        assertEquals("Secret must survive custom serialization logic", "top-secret", cloned.getSecret());
    }
}
```
###Test END##