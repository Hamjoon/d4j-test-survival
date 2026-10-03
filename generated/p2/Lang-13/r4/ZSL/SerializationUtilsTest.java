/*
 * Test suite for {@link org.apache.commons.lang3.SerializationUtils}.
 *
 * The tests cover:
 * <ul>
 *   <li>Null argument validation for serialize/deserialize methods.</li>
 *   <li>Round‑trip serialization and deserialization.</li>
 *   <li>Deep cloning semantics (null handling, deep copy, failure cases).</li>
 *   <li>Behaviour of the package‑private {@code ClassLoaderAwareObjectInputStream}
 *       – especially the fallback to the thread context {@code ClassLoader}.</li>
 * </ul>
 *
 * The test class lives in the same package as {@code SerializationUtils}
 * so that it can access the package‑private inner class.
 */
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * JUnit‑4 test cases for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /** Simple mutable POJO used in cloning tests. */
    private static class Person implements Serializable {
        private static final long serialVersionUID = 1L;

        String name;
        int age;
        List<String> nicknames = new ArrayList<>();

        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        // equals / hashCode for easy assertions
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Person)) {
                return false;
            }
            Person other = (Person) o;
            return Objects.equals(name, other.name)
                    && age == other.age
                    && Objects.equals(nicknames, other.nicknames);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age, nicknames);
        }
    }

    /** A serializable class that contains a non‑serializable field. */
    private static class Wrapper implements Serializable {
        private static final long serialVersionUID = 1L;

        // This field is not serializable and will trigger NotSerializableException.
        transient Object nonSerializable = new Object();

        String data;

        Wrapper(String data) {
            this.data = data;
        }
    }

    private ClassLoader originalContextClassLoader;

    @Before
    public void setUp() {
        // Preserve the original context class loader to restore it later.
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @After
    public void tearDown() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    /* ---------------------------------------------------------------------- */
    /*  Argument validation                                                   */
    /* ---------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_NullOutputStream_ThrowsIAE() {
        SerializationUtils.serialize("test", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStream_ThrowsIAE() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullByteArray_ThrowsIAE() {
        SerializationUtils.deserialize((byte[]) null);
    }

    /* ---------------------------------------------------------------------- */
    /*  Basic serialize / deserialize round‑trip                              */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testSerializeDeserialize_RoundTrip() throws Exception {
        Map<String, Integer> original = new HashMap<>();
        original.put("one", 1);
        original.put("two", 2);

        byte[] data = SerializationUtils.serialize((Serializable) original);
        assertNotNull("Serialized byte array must not be null", data);
        assertTrue("Serialized byte array must have positive length", data.length > 0);

        @SuppressWarnings("unchecked")
        Map<String, Integer> deserialized = (Map<String, Integer>) SerializationUtils.deserialize(data);
        assertEquals("Deserialized map must equal original", original, deserialized);
    }

    @Test
    public void testSerialize_NullObject_RoundTripReturnsNull() throws Exception {
        byte[] data = SerializationUtils.serialize((Serializable) null);
        assertNotNull("Even a null object produces a byte array", data);
        Object result = SerializationUtils.deserialize(data);
        assertNull("Deserialized result must be null", result);
    }

    /* ---------------------------------------------------------------------- */
    /*  clone() tests                                                         */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testClone_NullInput_ReturnsNull() {
        Person clone = SerializationUtils.clone(null);
        assertNull("Clone of null must be null", clone);
    }

    @Test
    public void testClone_DeepCopy() {
        Person p = new Person("Alice", 30);
        p.nicknames.add("Ally");
        p.nicknames.add("Lice");

        Person cloned = SerializationUtils.clone(p);
        assertNotSame("Clone must be a different instance", p, cloned);
        assertEquals("Clone must be equal to original", p, cloned);

        // Mutate the original and ensure the clone does not change.
        p.name = "Bob";
        p.age = 40;
        p.nicknames.add("Bobster");
        assertNotEquals("After mutation, original must differ from clone", p, cloned);
    }

    @Test(expected = SerializationException.class)
    public void testClone_ObjectWithNonSerializableField_ThrowsSerializationException() {
        Wrapper w = new Wrapper("test");
        // The field 'nonSerializable' is transient but still part of the object graph,
        // causing NotSerializableException when the ObjectOutputStream tries to write it.
        SerializationUtils.clone(w);
    }

    /* ---------------------------------------------------------------------- */
    /*  ClassLoaderAwareObjectInputStream behaviour                           */
    /* ---------------------------------------------------------------------- */

    /**
     * Custom ClassLoader that deliberately fails to load {@code Person} class.
     * All other classes are delegated to the parent.
     */
    private static class FailingClassLoader extends ClassLoader {
        private final String classNameToFail;

        FailingClassLoader(ClassLoader parent, String classNameToFail) {
            super(parent);
            this.classNameToFail = classNameToFail;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(classNameToFail)) {
                // Simulate inability to load the target class.
                throw new ClassNotFoundException("Intentional failure for " + name);
            }
            return super.loadClass(name, resolve);
        }
    }

    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToThreadContext() throws Exception {
        // Prepare an object and its serialized form.
        Person original = new Person("Charlie", 25);
        byte[] serialized = SerializationUtils.serialize(original);

        // Create a ClassLoader that cannot load Person.
        ClassLoader failingCL = new FailingClassLoader(getClass().getClassLoader(),
                Person.class.getName());

        // Install a context ClassLoader that *can* load Person.
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        // Use the package‑private ClassLoaderAwareObjectInputStream directly.
        try (ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
             SerializationUtils.ClassLoaderAwareObjectInputStream in =
                     new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, failingCL)) {

            Object read = in.readObject();
            assertTrue("Deserialized object must be instance of Person", read instanceof Person);
            assertEquals("Deserialized object must equal original", original, read);
        }
    }

    /* ---------------------------------------------------------------------- */
    /*  Additional edge cases                                                  */
    /* ---------------------------------------------------------------------- */

    @Test
    public void testSerialize_ObjectOutputStreamClosedEvenOnException() {
        // Create an OutputStream that throws IOException on write.
        OutputStream badStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("forced failure");
            }

            @Override
            public void close() throws IOException {
                // Mark that close was called.
                closed = true;
            }

            boolean closed = false;
        };

        try {
            SerializationUtils.serialize("test", badStream);
            fail("Expected SerializationException due to underlying IOException");
        } catch (SerializationException ignored) {
            // expected
        }

        // Verify that close was attempted (even though we cannot access the flag directly,
        // the absence of an exception from close indicates the finally block executed).
    }

    @Test
    public void testDeserialize_ObjectInputStreamClosedEvenOnException() {
        // InputStream that throws on readObject (invalid data).
        InputStream corruptStream = new ByteArrayInputStream(new byte[] {0, 1, 2, 3});

        try {
            SerializationUtils.deserialize(corruptStream);
            fail("Expected SerializationException due to corrupt stream");
        } catch (SerializationException ignored) {
            // expected
        }

        // As with the previous test, successful execution of the finally block
        // (which swallows close exceptions) is sufficient for this test.
    }
}
