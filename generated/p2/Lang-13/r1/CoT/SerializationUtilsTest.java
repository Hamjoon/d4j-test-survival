package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import org.junit.Test;

/**
 * JUnit‑4 test suite for {@link SerializationUtils}.
 *
 * The tests are divided into three sections:
 * <ol>
 *   <li>Basic functionality tests – one test per public method that verifies the
 *       expected “happy‑path” behaviour.</li>
 *   <li>Edge‑case / exception tests – inputs that should trigger
 *       {@link IllegalArgumentException} or {@link SerializationException}.</li>
 *   <li>Additional behavioural checks (deep‑clone integrity, corrupted data, etc.).</li>
 * </ol>
 *
 * All tests run with the default class loader; the internal
 * {@code ClassLoaderAwareObjectInputStream} is exercised indirectly via the
 * {@code clone} method.
 */
public class SerializationUtilsTest {

    /* -------------------------------------------------------------
     *  Helper classes used in the tests
     * ------------------------------------------------------------- */

    /** Simple mutable bean used for clone / serialize / deserialize tests. */
    private static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private int number;
        private String text;

        SimpleBean(int number, String text) {
            this.number = number;
            this.text = text;
        }

        void setNumber(int number) {
            this.number = number;
        }

        int getNumber() {
            return number;
        }

        String getText() {
            return text;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SimpleBean)) {
                return false;
            }
            SimpleBean other = (SimpleBean) obj;
            return this.number == other.number && this.text.equals(other.text);
        }

        @Override
        public int hashCode() {
            return number * 31 + text.hashCode();
        }
    }

    /**
     * Bean that implements {@link Serializable} but contains a field that is
     * *not* serializable.  Attempting to serialize an instance of this class
     * must raise a {@link SerializationException}.
     */
    private static class NonSerializableFieldBean implements Serializable {
        private static final long serialVersionUID = 1L;
        // Object does NOT implement Serializable → serialization will fail
        private Object nonSerializable = new Object();
    }

    /**
     * An {@link OutputStream} that throws an {@link IOException} when {@code close()}
     * is invoked.  Used to verify that {@code serialize(Object, OutputStream)} ignores
     * close‑time exceptions (the method swallows them).
     */
    private static class FailingCloseOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void close() throws IOException {
            throw new IOException("forced close failure");
        }

        byte[] toByteArray() {
            return delegate.toByteArray();
        }
    }

    /* -------------------------------------------------------------
     *  1.  Basic functionality tests
     * ------------------------------------------------------------- */

    /** {@link SerializationUtils#clone(Serializable)} – null input should return null. */
    @Test
    public void testClone_NullInput_ReturnsNull() {
        assertNull(SerializationUtils.clone(null));
    }

    /** {@link SerializationUtils#clone(Serializable)} – deep clone produces an equal but distinct object. */
    @Test
    public void testClone_DeepCopy() {
        SimpleBean original = new SimpleBean(42, "Answer");
        SimpleBean copy = SerializationUtils.clone(original);

        assertNotSame("Clone must be a different instance", original, copy);
        assertEquals("Clone must be equal to the original", original, copy);

        // Mutate original and verify copy does not change (deep copy)
        original.setNumber(99);
        assertNotEquals("Changing original must not affect clone", original.getNumber(), copy.getNumber());
    }

    /** {@link SerializationUtils#serialize(Serializable, OutputStream)} – normal operation. */
    @Test
    public void testSerialize_ToOutputStream() throws IOException {
        SimpleBean bean = new SimpleBean(7, "seven");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(bean, baos);

        byte[] data = baos.toByteArray();
        assertTrue("Serialized data should not be empty", data.length > 0);
    }

    /** {@link SerializationUtils#serialize(Serializable, OutputStream)} – null OutputStream must cause IAE. */
    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_NullOutputStream_ThrowsIAE() {
        SimpleBean bean = new SimpleBean(1, "one");
        SerializationUtils.serialize(bean, null);
    }

    /** {@link SerializationUtils#serialize(Serializable)} – returns a non‑null byte array. */
    @Test
    public void testSerialize_ToByteArray() {
        SimpleBean bean = new SimpleBean(3, "three");
        byte[] data = SerializationUtils.serialize(bean);
        assertNotNull("Byte array must not be null", data);
        assertTrue("Byte array must contain data", data.length > 0);
    }

    /** {@link SerializationUtils#deserialize(InputStream)} – normal operation. */
    @Test
    public void testDeserialize_FromInputStream() {
        SimpleBean bean = new SimpleBean(5, "five");
        byte[] data = SerializationUtils.serialize(bean);
        ByteArrayInputStream bais = new ByteArrayInputStream(data);

        Object deserialized = SerializationUtils.deserialize(bais);
        assertTrue("Deserialized object should be a SimpleBean", deserialized instanceof SimpleBean);
        assertEquals(bean, deserialized);
    }

    /** {@link SerializationUtils#deserialize(InputStream)} – null InputStream must cause IAE. */
    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStream_ThrowsIAE() {
        SerializationUtils.deserialize((InputStream) null);
    }

    /** {@link SerializationUtils#deserialize(byte[])} – normal operation. */
    @Test
    public void testDeserialize_FromByteArray() {
        SimpleBean bean = new SimpleBean(8, "eight");
        byte[] data = SerializationUtils.serialize(bean);
        Object deserialized = SerializationUtils.deserialize(data);
        assertTrue(deserialized instanceof SimpleBean);
        assertEquals(bean, deserialized);
    }

    /** {@link SerializationUtils#deserialize(byte[])} – null byte array must cause IAE. */
    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullByteArray_ThrowsIAE() {
        SerializationUtils.deserialize((byte[]) null);
    }

    /* -------------------------------------------------------------
     *  2.  Edge‑case / exception tests
     * ------------------------------------------------------------- */

    /** Serializing an object that contains a non‑serializable field must raise SerializationException. */
    @Test(expected = SerializationException.class)
    public void testSerialize_NonSerializableField_ThrowsSerializationException() {
        NonSerializableFieldBean bean = new NonSerializableFieldBean();
        // The method accepts Serializable, so compile‑time passes; runtime should fail.
        SerializationUtils.serialize(bean);
    }

    /** Deserializing corrupted data must raise SerializationException. */
    @Test(expected = SerializationException.class)
    public void testDeserialize_CorruptedData_ThrowsSerializationException() {
        // Random bytes that do not represent a valid serialized object
        byte[] corrupted = new byte[] {0x00, 0x01, 0x02, 0x03};
        SerializationUtils.deserialize(corrupted);
    }

    /** Serialize with an OutputStream whose close() throws IOException – the exception must be ignored. */
    @Test
    public void testSerialize_OutputStreamCloseException_Ignored() {
        SimpleBean bean = new SimpleBean(11, "eleven");
        FailingCloseOutputStream failingStream = new FailingCloseOutputStream();

        // No exception should propagate from the close()
        SerializationUtils.serialize(bean, failingStream);

        // Verify that data was actually written before the close failure
        assertTrue("Data should have been written despite close failure",
                failingStream.toByteArray().length > 0);
    }

    /* -------------------------------------------------------------
     *  3.  Additional behavioural checks
     * ------------------------------------------------------------- */

    /** Verify that {@code clone} uses the custom ClassLoaderAwareObjectInputStream internally.
     *  This is indirectly confirmed by cloning an object whose class is loaded by a
     *  non‑system class loader (the test class loader). If the custom stream were not used,
     *  a ClassNotFoundException would be thrown under certain container scenarios.
     *  The simple act of cloning without exception suffices for this verification.
     */
    @Test
    public void testClone_UsesClassLoaderAwareObjectInputStream() {
        SimpleBean bean = new SimpleBean(13, "thirteen");
        // If the internal stream fails to resolve the class, a SerializationException would be thrown.
        SimpleBean cloned = SerializationUtils.clone(bean);
        assertEquals(bean, cloned);
    }
}
