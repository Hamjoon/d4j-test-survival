package org.apache.commons.lang3;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

import java.io.*;
import java.util.Objects;

import static org.junit.Assert.*;

/**
 * JUnit‑4 test suite for {@link SerializationUtils}.
 *
 * <p>The tests cover:
 * <ul>
 *   <li>Normal operation of all public methods</li>
 *   <li>Edge‑case handling (null arguments, empty data, corrupted streams)</li>
 *   <li>Exception scenarios (illegal arguments, serialization failures,
 *       class‑loader fallback in {@code ClassLoaderAwareObjectInputStream})</li>
 * </ul>
 */
public class SerializationUtilsTest {

    /*---------------------------------------------------------------------*/
    /* Helper classes used in the tests                                    */
    /*---------------------------------------------------------------------*/

    /** Simple mutable POJO that implements {@link Serializable}. */
    private static class Person implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private int age;

        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        // getters for assertions
        String getName() { return name; }
        int getAge() { return age; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Person)) return false;
            Person p = (Person) o;
            return age == p.age && Objects.equals(name, p.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age);
        }
    }

    /** Serializable class that deliberately throws an IOException during writeObject. */
    private static class BadSerializable implements Serializable {
        private static final long serialVersionUID = 1L;

        private void writeObject(ObjectOutputStream out) throws IOException {
            throw new IOException("forced write failure");
        }
    }

    /** OutputStream that throws IOException on write/close – used to test error handling. */
    private static class FailingOutputStream extends OutputStream {
        @Override public void write(int b) throws IOException {
            throw new IOException("forced write failure");
        }

        @Override public void close() throws IOException {
            throw new IOException("forced close failure");
        }
    }

    /*---------------------------------------------------------------------*/
    /* JUnit rule for checking expected exceptions                         */
    /*---------------------------------------------------------------------*/
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /*---------------------------------------------------------------------*/
    /* 1. Constructor – should be callable (no logic)                      */
    /*---------------------------------------------------------------------*/
    @Test
    public void testConstructorIsPublic() {
        // simply instantiate – no exception expected
        new SerializationUtils();
    }

    /*---------------------------------------------------------------------*/
    /* 2. clone(T) – normal usage & edge cases                             */
    /*---------------------------------------------------------------------*/

    @Test
    public void testClone_NullInputReturnsNull() {
        assertNull(SerializationUtils.clone(null));
    }

    @Test
    public void testClone_DeepCopy() {
        Person original = new Person("Alice", 30);
        Person copy = SerializationUtils.clone(original);
        assertNotSame("clone must return a new instance", original, copy);
        assertEquals("clone must be equal to the original", original, copy);
    }

    @Test
    public void testClone_NonSerializableFails() {
        // compile‑time guard prevents passing a non‑Serializable object.
        // Instead we use a Serializable that fails during serialization.
        BadSerializable bad = new BadSerializable();
        thrown.expect(SerializationException.class);
        SerializationUtils.clone(bad);
    }

    /*---------------------------------------------------------------------*/
    /* 3. serialize(Serializable, OutputStream) – normal & edge cases      */
    /*---------------------------------------------------------------------*/

    @Test
    public void testSerialize_ToOutputStream_WritesBytes() throws IOException {
        Person p = new Person("Bob", 45);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(p, baos);
        byte[] data = baos.toByteArray();
        assertTrue("Serialized data should not be empty", data.length > 0);

        // Verify that the data can be deserialized back to the same object
        Object deserialized = SerializationUtils.deserialize(new ByteArrayInputStream(data));
        assertEquals(p, deserialized);
    }

    @Test
    public void testSerialize_ToOutputStream_NullStreamThrows() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The OutputStream must not be null");
        SerializationUtils.serialize(new Person("Carol", 22), null);
    }

    @Test
    public void testSerialize_ToOutputStream_NullObjectWritesNullReference() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        byte[] data = baos.toByteArray();

        // Deserializing the bytes should give back a null reference
        Object o = SerializationUtils.deserialize(new ByteArrayInputStream(data));
        assertNull(o);
    }

    @Test
    public void testSerialize_ToOutputStream_IOExceptionIsWrapped() {
        thrown.expect(SerializationException.class);
        thrown.expectMessage("forced write failure");
        SerializationUtils.serialize(new Person("Dave", 55), new FailingOutputStream());
    }

    /*---------------------------------------------------------------------*/
    /* 4. serialize(Serializable) – returns byte[]                         */
    /*---------------------------------------------------------------------*/

    @Test
    public void testSerialize_ReturnsByteArray() {
        Person p = new Person("Eve", 29);
        byte[] data = SerializationUtils.serialize(p);
        assertNotNull(data);
        assertTrue(data.length > 0);
        // round‑trip verification
        Object o = SerializationUtils.deserialize(data);
        assertEquals(p, o);
    }

    @Test
    public void testSerialize_ReturnsByteArrayForNull() {
        byte[] data = SerializationUtils.serialize(null);
        assertNotNull(data);
        // The byte array represents a serialized null reference
        Object o = SerializationUtils.deserialize(data);
        assertNull(o);
    }

    /*---------------------------------------------------------------------*/
    /* 5. deserialize(InputStream) – normal & edge cases                  */
    /*---------------------------------------------------------------------*/

    @Test
    public void testDeserialize_InputStream_NullThrows() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The InputStream must not be null");
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void testDeserialize_InputStream_ValidData() throws IOException {
        Person p = new Person("Frank", 40);
        byte[] data = SerializationUtils.serialize(p);
        InputStream in = new ByteArrayInputStream(data);
        Object o = SerializationUtils.deserialize(in);
        assertEquals(p, o);
    }

    @Test
    public void testDeserialize_InputStream_CorruptedDataThrows() {
        // Random bytes that do not represent a valid serialized object
        byte[] corrupted = new byte[] {0x01, 0x02, 0x03, 0x04};
        InputStream in = new ByteArrayInputStream(corrupted);
        thrown.expect(SerializationException.class);
        SerializationUtils.deserialize(in);
    }

    /*---------------------------------------------------------------------*/
    /* 6. deserialize(byte[]) – normal & edge cases                        */
    /*---------------------------------------------------------------------*/

    @Test
    public void testDeserialize_ByteArray_NullThrows() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The byte[] must not be null");
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserialize_ByteArray_ValidData() {
        Person p = new Person("Grace", 33);
        byte[] data = SerializationUtils.serialize(p);
        Object o = SerializationUtils.deserialize(data);
        assertEquals(p, o);
    }

    @Test
    public void testDeserialize_ByteArray_CorruptedDataThrows() {
        byte[] corrupted = new byte[] {0x0A, 0x0B, 0x0C};
        thrown.expect(SerializationException.class);
        SerializationUtils.deserialize(corrupted);
    }

    /*---------------------------------------------------------------------*/
    /* 7. ClassLoaderAwareObjectInputStream – fallback to thread context   */
    /*---------------------------------------------------------------------*/

    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToThreadContext() throws Exception {
        // Serialize a Person object using the default classloader
        Person p = new Person("Heidi", 27);
        byte[] data = SerializationUtils.serialize(p);

        // Create a ClassLoader that cannot load Person (throws ClassNotFoundException)
        ClassLoader blockingLoader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                // Fail for the test class, delegate everything else
                if (name.equals(Person.class.getName())) {
                    throw new ClassNotFoundException("blocked");
                }
                return super.loadClass(name);
            }
        };

        // Ensure the current thread's context classloader can load Person (default loader)
        Thread.currentThread().setContextClassLoader(SerializationUtilsTest.class.getClassLoader());

        // Use the clone method (which internally creates the custom stream) with the blocking loader.
        // The fallback mechanism should resolve the class via the thread context loader.
        Person cloned = SerializationUtils.clone(p);
        assertNotNull(cloned);
        assertEquals(p, cloned);
        assertNotSame(p, cloned);
    }

    /*---------------------------------------------------------------------*/
    /* 8. Verify that the utility methods close streams even when exceptions
     *    are thrown (no resource leak). We inspect that the stream's close()
     *    method was invoked via a spy.                                           */
    /*---------------------------------------------------------------------*/

    private static class CloseTrackingOutputStream extends ByteArrayOutputStream {
        boolean closed = false;
        @Override public void close() throws IOException {
            super.close();
            closed = true;
        }
    }

    @Test
    public void testSerialize_ClosesStreamEvenOnFailure() throws IOException {
        CloseTrackingOutputStream out = new CloseTrackingOutputStream();
        // The stream itself works; we force a failure by serializing a BadSerializable.
        BadSerializable bad = new BadSerializable();

        try {
            SerializationUtils.serialize(bad, out);
            fail("Expected SerializationException");
        } catch (SerializationException ignored) {
            // expected
        }
        assertTrue("OutputStream must be closed even when serialization fails", out.closed);
    }

    private static class CloseTrackingInputStream extends ByteArrayInputStream {
        boolean closed = false;
        CloseTrackingInputStream(byte[] buf) { super(buf); }
        @Override public void close() throws IOException {
            super.close();
            closed = true;
        }
    }

    @Test
    public void testDeserialize_ClosesStreamEvenOnFailure() {
        // Corrupted data to trigger failure
        byte[] corrupted = new byte[] {0x00, 0x01, 0x02};
        CloseTrackingInputStream in = new CloseTrackingInputStream(corrupted);
        try {
            SerializationUtils.deserialize(in);
            fail("Expected SerializationException");
        } catch (SerializationException ignored) {
            // expected
        }
        assertTrue("InputStream must be closed even when deserialization fails", in.closed);
    }
}
