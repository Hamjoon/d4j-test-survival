###Test START##
```java
/*
 * Test suite for {@link org.apache.commons.lang3.SerializationUtils}.
 *
 * These tests cover:
 *  - clone (null handling, deep cloning, error handling)
 *  - serialize (null OutputStream, normal object, null object)
 *  - deserialize (null InputStream, null byte[], normal data, corrupted data)
 *  - ClassLoaderAwareObjectInputStream class‑loader fallback logic
 *
 * The tests are written using JUnit 4.
 */
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * JUnit 4 tests for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /* --------------------------------------------------------------------- */
    /* Helper classes                                                       */
    /* --------------------------------------------------------------------- */

    /** Simple mutable object used for deep‑clone verification. */
    private static class Person implements Serializable {
        private static final long serialVersionUID = 1L;
        String name;
        List<String> nicknames = new ArrayList<>();

        Person(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Person)) {
                return false;
            }
            Person other = (Person) o;
            return name.equals(other.name) && nicknames.equals(other.nicknames);
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + nicknames.hashCode();
        }
    }

    /** Serializable class that deliberately fails during writeObject(). */
    private static class BadSerializable implements Serializable {
        private static final long serialVersionUID = 1L;

        private void writeObject(ObjectOutputStream out) throws IOException {
            throw new IOException("forced failure");
        }
    }

    /** ClassLoader that refuses to load a specific class (used for fallback test). */
    private static class BlockingClassLoader extends ClassLoader {
        private final String blockedClassName;

        BlockingClassLoader(String blockedClassName, ClassLoader parent) {
            super(parent);
            this.blockedClassName = blockedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(blockedClassName)) {
                throw new ClassNotFoundException("Blocked by test classloader");
            }
            return super.loadClass(name, resolve);
        }
    }

    /* --------------------------------------------------------------------- */
    /* clone() tests                                                         */
    /* --------------------------------------------------------------------- */

    @Test
    public void testClone_NullInput() {
        assertNull("clone(null) must return null", SerializationUtils.clone(null));
    }

    @Test
    public void testClone_DeepClone() {
        Person original = new Person("Alice");
        original.nicknames.add("Al");
        original.nicknames.add("Lice");

        Person clone = SerializationUtils.clone(original);

        // Verify equality but different reference
        assertNotSame("Clone must be a different instance", original, clone);
        assertEquals("Clone must be equal to original", original, clone);

        // Mutate original and ensure clone does NOT change (deep clone)
        original.nicknames.add("A");
        assertFalse("Clone's list must stay unchanged after original mutation",
                clone.nicknames.contains("A"));
    }

    @Test(expected = SerializationException.class)
    public void testClone_FailureDuringSerialization() {
        BadSerializable bad = new BadSerializable();
        // The clone method should wrap the IOException into a SerializationException
        SerializationUtils.clone(bad);
    }

    /* --------------------------------------------------------------------- */
    /* serialize(OutputStream) tests                                         */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_NullOutputStream() {
        SerializationUtils.serialize("test", null);
    }

    @Test
    public void testSerialize_NullObject() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        byte[] data = baos.toByteArray();

        // Deserializing the bytes should give back null
        Object deserialized = SerializationUtils.deserialize(new ByteArrayInputStream(data));
        assertNull("Deserialized object must be null", deserialized);
    }

    @Test
    public void testSerializeAndDeserialize_RoundTrip() {
        String original = "Hello, Serialization!";
        byte[] bytes = SerializationUtils.serialize(original);
        Object result = SerializationUtils.deserialize(bytes);
        assertEquals("Deserialized string must equal original", original, result);
    }

    /* --------------------------------------------------------------------- */
    /* deserialize(InputStream) tests                                         */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStream() {
        SerializationUtils.deserialize((java.io.InputStream) null);
    }

    @Test
    public void testDeserialize_NullByteArray() {
        try {
            SerializationUtils.deserialize((byte[]) null);
            fail("Expected IllegalArgumentException for null byte array");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test(expected = SerializationException.class)
    public void testDeserialize_CorruptedData() {
        byte[] corrupted = new byte[] {0, 1, 2, 3, 4};
        SerializationUtils.deserialize(corrupted);
    }

    /* --------------------------------------------------------------------- */
    /* ClassLoaderAwareObjectInputStream tests                               */
    /* --------------------------------------------------------------------- */

    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToThreadContext() throws Exception {
        // Serialize a Person instance using the default classloader
        Person original = new Person("Bob");
        byte[] serialized = SerializationUtils.serialize(original);

        // Install a custom ClassLoader that blocks Person.class
        BlockingClassLoader blockingLoader = new BlockingClassLoader(Person.class.getName(),
                SerializationUtilsTest.class.getClassLoader());

        // Ensure the thread context classloader can load Person (it is the system loader)
        Thread.currentThread().setContextClassLoader(SerializationUtilsTest.class.getClassLoader());

        // Use the custom ClassLoaderAwareObjectInputStream directly
        try (ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
             SerializationUtils.ClassLoaderAwareObjectInputStream in =
                     new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, blockingLoader)) {

            Object deserialized = in.readObject();
            assertTrue("Deserialized object should be a Person", deserialized instanceof Person);
            assertEquals("Deserialized object must equal original", original, deserialized);
        }
    }

    @Test
    public void testClassLoaderAwareObjectInputStream_NullClassLoaderUsesThreadContext() throws Exception {
        // Serialize a simple String (core JDK class, always loadable)
        String original = "fallback test";
        byte[] serialized = SerializationUtils.serialize(original);

        // Set thread context classloader to a known loader (system)
        Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());

        // Pass null as the custom classloader; resolveClass should fall back to thread context
        try (ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
             SerializationUtils.ClassLoaderAwareObjectInputStream in =
                     new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, null)) {

            Object deserialized = in.readObject();
            assertTrue("Deserialized object should be a String", deserialized instanceof String);
            assertEquals("Deserialized value must match original", original, deserialized);
        }
    }

    /* --------------------------------------------------------------------- */
    /* Additional edge cases                                                  */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeAndDeserialize_WithCustomOutputStream() throws IOException {
        // Custom OutputStream that records when close() is called
        class SpyOutputStream extends OutputStream {
            private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
            boolean closed = false;

            @Override
            public void write(int b) throws IOException {
                delegate.write(b);
            }

            @Override
            public void close() throws IOException {
                closed = true;
                super.close();
            }

            byte[] toByteArray() {
                return delegate.toByteArray();
            }
        }

        SpyOutputStream spy = new SpyOutputStream();
        String payload = "spy test";
        SerializationUtils.serialize(payload, spy);
        assertTrue("OutputStream must be closed by serialize()", spy.closed);

        Object result = SerializationUtils.deserialize(spy.toByteArray());
        assertEquals("Deserialized value must match original", payload, result);
    }
}
```
###Test END##