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
 * The tests cover:
 * <ul>
 *   <li>basic functional behaviour of each public method</li>
 *   <li>edge‑case handling (null arguments, null objects)</li>
 *   <li>exception handling when serialization / deserialization fails</li>
 * </ul>
 *
 * The suite deliberately uses simple POJOs that implement {@link Serializable}
 * to keep the tests deterministic and fast.
 */
public class SerializationUtilsTest {

    /** Simple mutable bean used for clone / serialize / deserialize tests. */
    public static class TestBean implements Serializable {
        private static final long serialVersionUID = 1L;

        private int id;
        private String name;

        public TestBean() {}

        public TestBean(int id, String name) {
            this.id = id;
            this.name = name;
        }

        // getters / setters (not strictly required for the tests)
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestBean)) return false;
            TestBean other = (TestBean) o;
            return id == other.id && ((name == null && other.name == null) ||
                    (name != null && name.equals(other.name)));
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(id);
            result = 31 * result + (name != null ? name.hashCode() : 0);
            return result;
        }
    }

    /** Bean that deliberately fails serialization because it contains a non‑serializable field. */
    public static class BadBean implements Serializable {
        private static final long serialVersionUID = 1L;

        // Object does NOT implement Serializable → NotSerializableException expected
        private Object nonSerializable = new Object();

        public BadBean() {}
    }

    /* --------------------------------------------------------------------- */
    /*  Constructor test                                                      */
    /* --------------------------------------------------------------------- */

    @Test
    public void testPublicConstructor() {
        SerializationUtils utils = new SerializationUtils();
        assertNotNull("Constructor should create a non‑null instance", utils);
    }

    /* --------------------------------------------------------------------- */
    /*  clone() tests                                                         */
    /* --------------------------------------------------------------------- */

    @Test
    public void testCloneWithValidObject() {
        TestBean original = new TestBean(42, "foo");
        TestBean cloned = SerializationUtils.clone(original);
        assertNotNull("Cloned object must not be null", cloned);
        assertEquals("Cloned object must be equal to the original", original, cloned);
        assertNotSame("Cloned object should be a different instance", original, cloned);
    }

    @Test
    public void testCloneWithNull() {
        assertNull("Cloning null should return null", SerializationUtils.clone(null));
    }

    @Test(expected = SerializationException.class)
    public void testCloneSerializationFailure() {
        BadBean bad = new BadBean();
        // This call should wrap the NotSerializableException in a SerializationException
        SerializationUtils.clone(bad);
    }

    /* --------------------------------------------------------------------- */
    /*  serialize(Serializable, OutputStream) tests                           */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeToOutputStream() throws IOException {
        TestBean bean = new TestBean(7, "bar");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(bean, baos);
        byte[] data = baos.toByteArray();
        assertTrue("Serialized byte array should contain data", data.length > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSerializeToOutputStream_NullOutputStream() {
        TestBean bean = new TestBean(1, "x");
        SerializationUtils.serialize(bean, (OutputStream) null);
    }

    @Test(expected = SerializationException.class)
    public void testSerializeToOutputStream_SerializationFailure() {
        BadBean bad = new BadBean();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(bad, baos);
    }

    /* --------------------------------------------------------------------- */
    /*  serialize(Serializable) tests                                          */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeReturnsByteArray() {
        TestBean bean = new TestBean(99, "baz");
        byte[] data = SerializationUtils.serialize(bean);
        assertNotNull("Returned byte array must not be null", data);
        assertTrue("Byte array should have length > 0", data.length > 0);
    }

    @Test
    public void testSerializeNullObject() {
        // Serializing a null reference is legal – ObjectOutputStream writes a NULL marker.
        byte[] data = SerializationUtils.serialize(null);
        assertNotNull("Byte array for null object must not be null", data);
        // Deserializing the produced data must yield null again.
        Object deserialized = SerializationUtils.deserialize(data);
        assertNull("Deserialized value of a serialized null must be null", deserialized);
    }

    /* --------------------------------------------------------------------- */
    /*  deserialize(InputStream) tests                                        */
    /* --------------------------------------------------------------------- */

    @Test
    public void testDeserializeFromInputStream() {
        TestBean bean = new TestBean(123, "qux");
        byte[] serialized = SerializationUtils.serialize(bean);
        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        Object result = SerializationUtils.deserialize(bais);
        assertTrue("Deserialized object should be instance of TestBean", result instanceof TestBean);
        assertEquals(bean, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeFromInputStream_NullInputStream() {
        SerializationUtils.deserialize((ByteArrayInputStream) null);
    }

    @Test(expected = SerializationException.class)
    public void testDeserializeFromInputStream_CorruptedData() {
        // Random data that does not represent a serialized object
        byte[] garbage = new byte[] {0x01, 0x02, 0x03, 0x04};
        ByteArrayInputStream bais = new ByteArrayInputStream(garbage);
        SerializationUtils.deserialize(bais);
    }

    /* --------------------------------------------------------------------- */
    /*  deserialize(byte[]) tests                                             */
    /* --------------------------------------------------------------------- */

    @Test
    public void testDeserializeFromByteArray() {
        TestBean bean = new TestBean(555, "zeta");
        byte[] serialized = SerializationUtils.serialize(bean);
        Object result = SerializationUtils.deserialize(serialized);
        assertTrue("Result should be a TestBean", result instanceof TestBean);
        assertEquals(bean, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeFromByteArray_NullArray() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test(expected = SerializationException.class)
    public void testDeserializeFromByteArray_CorruptedData() {
        byte[] garbage = new byte[] {0x10, 0x20, 0x30};
        SerializationUtils.deserialize(garbage);
    }
}
