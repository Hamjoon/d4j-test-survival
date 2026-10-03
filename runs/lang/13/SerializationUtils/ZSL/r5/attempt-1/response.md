###Test START##
package org.apache.commons.lang3;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.*;
import java.util.Objects;

/**
 * Comprehensive JUnit‑4 test suite for {@link SerializationUtils}.
 *
 * <p>The tests cover:
 * <ul>
 *   <li>normal operation of {@code clone}, {@code serialize} and {@code deserialize}
 *   <li>handling of {@code null} arguments
 *   <li>exception paths (invalid data, non‑serializable fields, I/O problems)
 *   <li>behaviour of the package‑private {@code ClassLoaderAwareObjectInputStream}
 * </ul>
 *
 * <p>All tests are placed in the same package as {@code SerializationUtils}
 * so that the package‑private inner class can be accessed via reflection.
 */
public class SerializationUtilsTest {

    /** Simple serializable bean used in many tests. */
    static class Person implements Serializable {
        private static final long serialVersionUID = 1L;
        String name;
        int age;

        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Person)) return false;
            Person p = (Person) o;
            return age == p.age && Objects.equals(name, p.name);
        }

        @Override public int hashCode() {
            return Objects.hash(name, age);
        }
    }

    /** Container holding a {@link Person} to test deep cloning. */
    static class Container implements Serializable {
        private static final long serialVersionUID = 1L;
        Person person;

        Container(Person person) {
            this.person = person;
        }
    }

    /**
     * A class that implements {@link Serializable} but contains a field that
     * is *not* serializable. Serializing an instance of this class must fail
     * with {@link SerializationException}.
     */
    static class Bad implements Serializable {
        private static final long serialVersionUID = 1L;
        // java.lang.Object does *not* implement Serializable
        Object nonSerializable = new Object();
    }

    /** OutputStream that records whether {@code close()} was invoked. */
    static class TrackingOutputStream extends OutputStream {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean closed = false;

        @Override public void write(int b) throws IOException {
            baos.write(b);
        }

        @Override public void close() throws IOException {
            closed = true;
            super.close();
        }

        byte[] toByteArray() {
            return baos.toByteArray();
        }
    }

    /* -------------------------------------------------
     *  clone(...)
     * ------------------------------------------------- */

    @Test
    public void testCloneNullReturnsNull() {
        assertNull("Cloning null should return null", SerializationUtils.clone(null));
    }

    @Test
    public void testCloneDeepCopy() {
        Person original = new Person("Alice", 30);
        Container container = new Container(original);

        Container copy = SerializationUtils.clone(container);
        assertNotSame("Clone must be a different Container instance", container, copy);
        assertNotSame("Contained Person must also be a different instance", container.person, copy.person);
        assertEquals("Deep‑cloned Person must be equal", container.person, copy.person);

        // Mutate the original after cloning – the clone must stay unchanged
        container.person.name = "Bob";
        assertEquals("Clone should not reflect changes in the original", "Alice", copy.person.name);
    }

    @Test
    public void testCloneObjectWithNonSerializableFieldThrowsException() {
        Bad bad = new Bad();
        try {
            SerializationUtils.clone(bad);
            fail("Expected SerializationException when cloning a non‑serializable object");
        } catch (SerializationException expected) {
            // Expected path
        }
    }

    /* -------------------------------------------------
     *  serialize(Serializable, OutputStream)
     * ------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testSerializeNullOutputStreamThrows() {
        SerializationUtils.serialize("test", null);
    }

    @Test
    public void testSerializeAndCloseOutputStream() throws IOException {
        Person p = new Person("Charlie", 25);
        TrackingOutputStream tos = new TrackingOutputStream();

        SerializationUtils.serialize(p, tos);
        assertTrue("serialize should close the supplied OutputStream", tos.closed);

        // Verify that the produced bytes can be deserialized back to an equal object
        byte[] data = tos.toByteArray();
        Object obj = SerializationUtils.deserialize(data);
        assertTrue("Deserialized object must be a Person", obj instanceof Person);
        assertEquals(p, obj);
    }

    @Test
    public void testSerializeNullObjectProducesValidStream() {
        // Serializing a null reference is legal – it should round‑trip to null
        byte[] data = SerializationUtils.serialize((Serializable) null);
        Object result = SerializationUtils.deserialize(data);
        assertNull("Deserializing a null reference must yield null", result);
    }

    @Test
    public void testSerializeObjectWithNonSerializableFieldThrowsException() {
        Bad bad = new Bad();
        try {
            SerializationUtils.serialize(bad);
            fail("Expected SerializationException because Bad contains a non‑serializable field");
        } catch (SerializationException expected) {
            // Expected
        }
    }

    /* -------------------------------------------------
     *  deserialize(InputStream) & deserialize(byte[])
     * ------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeNullInputStreamThrows() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeNullByteArrayThrows() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserializeCorruptedDataThrowsSerializationException() {
        byte[] corrupted = new byte[] {0, 1, 2, 3, 4};
        try {
            SerializationUtils.deserialize(corrupted);
            fail("Expected SerializationException for corrupted input data");
        } catch (SerializationException expected) {
            // Expected
        }
    }

    /* -------------------------------------------------
     *  ClassLoaderAwareObjectInputStream resolveClass fallback
     * ------------------------------------------------- */

    @Test
    public void testClassLoaderAwareObjectInputStreamFallback() throws Exception {
        // Serialize a simple Person
        Person original = new Person("Dana", 40);
        byte[] payload = SerializationUtils.serialize(original);

        // Create a ClassLoader that deliberately fails to load Person
        ClassLoader brokenLoader = new ClassLoader(null) {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.equals(Person.class.getName())) {
                    throw new ClassNotFoundException("forced failure for testing fallback");
                }
                return super.loadClass(name);
            }
        };

        // Instantiate the package‑private ClassLoaderAwareObjectInputStream via reflection
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);
        Class<?> cls = SerializationUtils.ClassLoaderAwareObjectInputStream.class;
        java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor(InputStream.class, ClassLoader.class);
        ctor.setAccessible(true);
        Object stream = ctor.newInstance(bais, brokenLoader);

        // Invoke readObject() – it should succeed because the fallback uses the thread context ClassLoader
        Object result = cls.getMethod("readObject").invoke(stream);
        assertTrue("Result must be a Person instance", result instanceof Person);
        assertEquals("Deserialized Person must equal the original", original, result);
    }

    /* -------------------------------------------------
     *  serialize(Serializable) – convenience method
     * ------------------------------------------------- */

    @Test
    public void testSerializeConvenienceMethodRoundTrip() {
        Person p = new Person("Eve", 22);
        byte[] data = SerializationUtils.serialize(p);
        Object obj = SerializationUtils.deserialize(data);
        assertTrue(obj instanceof Person);
        assertEquals(p, obj);
    }
}
###Test END##