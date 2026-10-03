###Test START##
```java
/**
 * JUnit 4 test suite for {@link org.apache.commons.lang3.SerializationUtils}.
 *
 * The tests cover:
 * <ul>
 *   <li>Basic functionality of all public static methods.</li>
 *   <li>Edge‑case handling (null arguments, corrupt data, non‑serializable fields).</li>
 *   <li>Exception propagation (IllegalArgumentException, SerializationException).</li>
 * </ul>
 *
 * The test class is deliberately self‑contained – it defines simple
 * {@code Serializable} fixtures used throughout the suite.
 */
package org.apache.commons.lang3;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Arrays;

import static org.junit.Assert.*;

public class SerializationUtilsTest {

    /* --------------------------------------------------------------------- */
    /*  Fixtures                                                            */
    /* --------------------------------------------------------------------- */

    /** Simple bean used for happy‑path cloning/serialization tests. */
    private static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;
        private final String name;

        SimpleBean(int id, String name) {
            this.id = id;
            this.name = name;
        }

        // getters, equals, hashCode for assertions
        int getId() { return id; }
        String getName() { return name; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SimpleBean)) return false;
            SimpleBean that = (SimpleBean) o;
            return id == that.id && (name == null ? that.name == null : name.equals(that.name));
        }

        @Override
        public int hashCode() {
            return 31 * id + (name != null ? name.hashCode() : 0);
        }
    }

    /** A class that deliberately does NOT implement {@code Serializable}. */
    private static class NotSerializable {
        private final String data = "cannot be serialized";
    }

    /**
     * Bean that implements {@code Serializable} but contains a field that is
     * not serializable. Serializing such an instance must fail at runtime.
     */
    private static class BadBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private final NotSerializable payload = new NotSerializable();
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /* --------------------------------------------------------------------- */
    /*  Tests for {@code clone(...)}                                         */
    /* --------------------------------------------------------------------- */

    @Test
    public void testClone_NullInput_ReturnsNull() {
        assertNull("clone(null) should return null", SerializationUtils.clone(null));
    }

    @Test
    public void testClone_SimpleBean_ReturnsDeepCopy() {
        SimpleBean original = new SimpleBean(42, "Apache");
        SimpleBean copy = SerializationUtils.clone(original);

        assertNotSame("clone should return a different instance", original, copy);
        assertEquals("clone should be equal to the original", original, copy);
    }

    @Test
    public void testClone_BadBean_ThrowsSerializationException() {
        BadBean bad = new BadBean();

        thrown.expect(SerializationException.class);
        thrown.expectMessage("IOException while reading cloned object data");

        SerializationUtils.clone(bad);
    }

    /* --------------------------------------------------------------------- */
    /*  Tests for {@code serialize(..., OutputStream)}                       */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerialize_NullOutputStream_ThrowsIllegalArgumentException() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The OutputStream must not be null");

        SerializationUtils.serialize(new SimpleBean(1, "test"), null);
    }

    @Test
    public void testSerialize_NullObject_SerializesAndDeserializesToNull() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        Object deserialized = SerializationUtils.deserialize(bais);

        assertNull("Deserialized object should be null", deserialized);
    }

    @Test
    public void testSerialize_SimpleBean_RoundTrip() throws Exception {
        SimpleBean bean = new SimpleBean(7, "roundtrip");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(bean, baos);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        Object result = SerializationUtils.deserialize(bais);

        assertTrue("Deserialized object must be a SimpleBean", result instanceof SimpleBean);
        assertEquals(bean, result);
    }

    /* --------------------------------------------------------------------- */
    /*  Tests for {@code serialize(Serializable)} (byte‑array overload)        */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeToByteArray_NullObject_ReturnsNonNullArray() {
        byte[] data = SerializationUtils.serialize(null);
        assertNotNull("serialize(null) must return a non‑null byte array", data);
        // The array should be deserializable back to null
        assertNull("Deserializing the byte array should yield null",
                SerializationUtils.deserialize(data));
    }

    @Test
    public void testSerializeToByteArray_SimpleBean_RoundTrip() {
        SimpleBean bean = new SimpleBean(99, "bytes");
        byte[] data = SerializationUtils.serialize(bean);
        assertNotNull("Byte array must not be null", data);
        assertTrue("Byte array should contain data", data.length > 0);

        Object deserialized = SerializationUtils.deserialize(data);
        assertEquals(bean, deserialized);
    }

    /* --------------------------------------------------------------------- */
    /*  Tests for {@code deserialize(InputStream)}                           */
    /* --------------------------------------------------------------------- */

    @Test
    public void testDeserialize_NullInputStream_ThrowsIllegalArgumentException() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The InputStream must not be null");

        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void testDeserialize_CorruptStream_ThrowsSerializationException() {
        // Create a stream that does not contain a valid serialization header
        byte[] garbage = new byte[] {0x01, 0x02, 0x03, 0x04};
        ByteArrayInputStream bais = new ByteArrayInputStream(garbage);

        thrown.expect(SerializationException.class);
        SerializationUtils.deserialize(bais);
    }

    /* --------------------------------------------------------------------- */
    /*  Tests for {@code deserialize(byte[])} (byte‑array overload)           */
    /* --------------------------------------------------------------------- */

    @Test
    public void testDeserializeByteArray_NullArray_ThrowsIllegalArgumentException() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The byte[] must not be null");

        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserializeByteArray_CorruptData_ThrowsSerializationException() {
        byte[] garbage = new byte[] {0x0A, 0x0B, 0x0C};

        thrown.expect(SerializationException.class);
        SerializationUtils.deserialize(garbage);
    }

    @Test
    public void testDeserializeByteArray_SimpleBean_RoundTrip() {
        SimpleBean bean = new SimpleBean(123, "array");
        byte[] data = SerializationUtils.serialize(bean);

        Object result = SerializationUtils.deserialize(data);
        assertEquals(bean, result);
    }

    /* --------------------------------------------------------------------- */
    /*  Additional sanity checks                                            */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeAndDeserialize_MultipleObjects_Independence() throws Exception {
        SimpleBean bean1 = new SimpleBean(1, "first");
        SimpleBean bean2 = new SimpleBean(2, "second");

        // Serialize both objects sequentially into the same stream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(bean1, baos);
        SerializationUtils.serialize(bean2, baos);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

        // Deserialize them back in the same order
        Object o1 = SerializationUtils.deserialize(bais);
        Object o2 = SerializationUtils.deserialize(bais);

        assertEquals(bean1, o1);
        assertEquals(bean2, o2);
    }

    @Test
    public void testClone_ImmutableObject_ReturnsSameLogicalValue() {
        // Strings are immutable and serializable.
        String original = "immutable";
        String copy = SerializationUtils.clone(original);
        assertEquals("Cloned string should be equal to original", original, copy);
        // For immutable objects, the same instance is often returned,
        // but the contract does not require it, so we only check equality.
    }
}
```
###Test END##