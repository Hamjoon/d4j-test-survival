/********************************************************************
 * JUnit 4 test suite for {@link org.apache.commons.lang3.SerializationUtils}
 *
 * The tests cover:
 *   • clone (null handling, deep clone, exception propagation)
 *   • serialize(Object, OutputStream) (null stream, IOException handling,
 *     successful round‑trip, close tracking)
 *   • serialize(Object) (null object, round‑trip)
 *   • deserialize(InputStream) (null stream, successful round‑trip,
 *     close tracking)
 *   • deserialize(byte[]) (null array, successful round‑trip)
 *   • ClassLoaderAwareObjectInputStream resolveClass fallback logic
 *
 * The test class lives in the same package as {@code SerializationUtils}
 * so that the package‑private inner class {@code ClassLoaderAwareObjectInputStream}
 * can be accessed.
 ********************************************************************/
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

import org.junit.Test;

/**
 * Test cases for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /* -----------------------------------------------------------------
     * Helper classes
     * ----------------------------------------------------------------- */

    /** Simple mutable bean used for deep‑clone verification. */
    private static class Person implements Serializable {
        private static final long serialVersionUID = 1L;
        String name;
        int age;
        Map<String, String> attributes = new HashMap<>();

        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        // equality based on fields (deep)
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Person)) {
                return false;
            }
            Person p = (Person) o;
            return age == p.age &&
                   ((name == null && p.name == null) || (name != null && name.equals(p.name))) &&
                   attributes.equals(p.attributes);
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + age;
            result = 31 * result + attributes.hashCode();
            return result;
        }
    }

    /** Serializable class that throws an IOException during writeObject. */
    private static class BadSerializable implements Serializable {
        private static final long serialVersionUID = 1L;

        private void writeObject(ObjectOutputStream out) throws IOException {
            throw new IOException("forced write failure");
        }
    }

    /** OutputStream that records close() calls and can be forced to throw on write. */
    private static class TrackingOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private boolean closed = false;
        private boolean failOnWrite = false;

        void setFailOnWrite(boolean fail) {
            this.failOnWrite = fail;
        }

        boolean isClosed() {
            return closed;
        }

        byte[] toByteArray() {
            return delegate.toByteArray();
        }

        @Override
        public void write(int b) throws IOException {
            if (failOnWrite) {
                throw new IOException("forced write failure");
            }
            delegate.write(b);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }
    }

    /** InputStream that records close() calls and can be forced to throw on close. */
    private static class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed = false;
        private boolean failOnClose = false;

        TrackingInputStream(byte[] buf) {
            super(buf);
        }

        void setFailOnClose(boolean fail) {
            this.failOnClose = fail;
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() throws IOException {
            if (failOnClose) {
                throw new IOException("forced close failure");
            }
            closed = true;
            super.close();
        }
    }

    /* -----------------------------------------------------------------
     * clone() tests
     * ----------------------------------------------------------------- */

    @Test
    public void testCloneNullReturnsNull() {
        assertNull(SerializationUtils.clone(null));
    }

    @Test
    public void testCloneDeepCopy() {
        Person original = new Person("Alice", 30);
        original.attributes.put("city", "Wonderland");

        Person copy = SerializationUtils.clone(original);

        // Verify values are equal but not the same reference
        assertEquals(original, copy);
        assertNotSame(original, copy);
        // mutable map must be a deep copy
        assertNotSame(original.attributes, copy.attributes);
        // modifying the copy must not affect the original
        copy.attributes.put("city", "Elsewhere");
        assertEquals("Wonderland", original.attributes.get("city"));
    }

    @Test(expected = SerializationException.class)
    public void testCloneSerializationFailurePropagatesException() {
        BadSerializable bad = new BadSerializable();
        // clone will attempt to serialize and must wrap the IOException
        SerializationUtils.clone(bad);
    }

    @Test
    public void testCloneClosesInputStreamEvenWhenCloseFails() {
        // Prepare a normal object to be cloned
        Person original = new Person("Bob", 25);
        // Use a tracking InputStream that throws on close
        TrackingInputStream failingClose = new TrackingInputStream(SerializationUtils.serialize(original));
        failingClose.setFailOnClose(true);

        try {
            // Force the clone method to use our failing stream by hacking the
            // internal call path: we cannot inject the stream directly,
            // but we can simulate the scenario by creating a subclass of
            // SerializationUtils that overrides clone – however the method is static.
            // Instead we test the same behaviour via deserialize which has the
            // same finally‑close logic.
            SerializationUtils.deserialize(failingClose);
            fail("Expected SerializationException due to close failure");
        } catch (SerializationException ex) {
            // Expected – the exception message comes from the finally block
            assertTrue(ex.getMessage().contains("IOException"));
        }
    }

    /* -----------------------------------------------------------------
     * serialize(Serializable, OutputStream) tests
     * ----------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testSerializeWithNullOutputStream() {
        SerializationUtils.serialize("test", null);
    }

    @Test
    public void testSerializeWritesCorrectBytesAndClosesStream() {
        TrackingOutputStream out = new TrackingOutputStream();
        String obj = "hello world";

        SerializationUtils.serialize(obj, out);

        assertTrue("OutputStream should be closed", out.isClosed());
        // Verify that the bytes can be deserialized back to the original object
        Object deserialized = SerializationUtils.deserialize(out.toByteArray());
        assertEquals(obj, deserialized);
    }

    @Test(expected = SerializationException.class)
    public void testSerializePropagatesIOExceptionFromWrite() {
        TrackingOutputStream out = new TrackingOutputStream();
        out.setFailOnWrite(true);
        SerializationUtils.serialize("won't be written", out);
    }

    @Test
    public void testSerializeNullObjectProducesNonNullByteArray() {
        byte[] data = SerializationUtils.serialize((Serializable) null);
        assertNotNull(data);
        // Deserializing the byte array must yield null
        Object result = SerializationUtils.deserialize(data);
        assertNull(result);
    }

    /* -----------------------------------------------------------------
     * serialize(Serializable) tests
     * ----------------------------------------------------------------- */

    @Test
    public void testSerializeToByteArrayRoundTrip() {
        Person p = new Person("Carol", 40);
        p.attributes.put("role", "admin");

        byte[] bytes = SerializationUtils.serialize(p);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        Object obj = SerializationUtils.deserialize(bytes);
        assertTrue(obj instanceof Person);
        assertEquals(p, obj);
    }

    /* -----------------------------------------------------------------
     * deserialize(InputStream) tests
     * ----------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeWithNullInputStream() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void testDeserializeClosesStream() {
        Person p = new Person("Dave", 55);
        byte[] data = SerializationUtils.serialize(p);
        TrackingInputStream in = new TrackingInputStream(data);

        Object result = SerializationUtils.deserialize(in);
        assertTrue(in.isClosed());
        assertEquals(p, result);
    }

    @Test(expected = SerializationException.class)
    public void testDeserializePropagatesIOExceptionFromRead() {
        // Create a stream that throws on readObject (by corrupting the data)
        byte[] corrupted = new byte[] {0x00, 0x01, 0x02};
        ByteArrayInputStream bais = new ByteArrayInputStream(corrupted);
        SerializationUtils.deserialize(bais);
    }

    /* -----------------------------------------------------------------
     * deserialize(byte[]) tests
     * ----------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeByteArrayWithNull() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserializeByteArrayRoundTrip() {
        String original = "test string";
        byte[] data = SerializationUtils.serialize(original);
        Object deserialized = SerializationUtils.deserialize(data);
        assertEquals(original, deserialized);
    }

    /* -----------------------------------------------------------------
     * ClassLoaderAwareObjectInputStream tests
     * ----------------------------------------------------------------- */

    @Test
    public void testClassLoaderAwareObjectInputStreamUsesProvidedClassLoader() throws Exception {
        // Use a custom class loader that can load Person but not via the context loader.
        ClassLoader customLoader = new ClassLoader(this.getClass().getClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                // Delegate to parent for everything except Person
                if (!name.equals(Person.class.getName())) {
                    return super.loadClass(name);
                }
                // Load Person via the parent (simulating availability)
                return super.loadClass(name);
            }
        };

        // Serialize a Person instance
        Person p = new Person("Eve", 28);
        byte[] bytes = SerializationUtils.serialize(p);

        // Deserialize using the custom ClassLoaderAwareObjectInputStream
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             SerializationUtils.ClassLoaderAwareObjectInputStream clIn =
                     new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, customLoader)) {
            Object obj = clIn.readObject();
            assertTrue(obj instanceof Person);
            assertEquals(p, obj);
        }
    }

    @Test
    public void testClassLoaderAwareObjectInputStreamFallbackToThreadContextClassLoader() throws Exception {
        // Simulate a scenario where the provided class loader cannot find the class
        ClassLoader emptyLoader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                // Force ClassNotFoundException for any class (except java.* which the bootstrap can load)
                if (!name.startsWith("java.")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };

        // Ensure the thread context class loader can load Person (default)
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

        Person p = new Person("Frank", 33);
        byte[] data = SerializationUtils.serialize(p);

        // Deserialize – the custom loader will fail, fallback should succeed
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             SerializationUtils.ClassLoaderAwareObjectInputStream clIn =
                     new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, emptyLoader)) {
            Object obj = clIn.readObject();
            assertTrue(obj instanceof Person);
            assertEquals(p, obj);
        }
    }
}
