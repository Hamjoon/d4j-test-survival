package org.apache.commons.lang3;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JUnit 4 test suite for {@link SerializationUtils}.
 *
 * Covers:
 * <ul>
 *   <li>Normal usage of all public methods</li>
 *   <li>Edge cases (null arguments, empty data, etc.)</li>
 *   <li>Exception handling (IllegalArgumentException, SerializationException)</li>
 * </ul>
 */
public class SerializationUtilsTest {

    /** Simple serializable bean used for cloning / (de)serialization tests. */
    private static class TestBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private int   number;
        private List<String> tags = new ArrayList<>();

        TestBean(String name, int number) {
            this.name = name;
            this.number = number;
        }

        void addTag(String tag) {
            tags.add(tag);
        }

        String getName()   { return name;   }
        int    getNumber() { return number; }
        List<String> getTags() { return tags; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TestBean)) {
                return false;
            }
            TestBean other = (TestBean) o;
            return name.equals(other.name) &&
                   number == other.number &&
                   tags.equals(other.tags);
        }
    }

    /** OutputStream that records whether {@code close()} was invoked. */
    private static class CloseTrackingOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private boolean closed = false;

        @Override public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override public void close() throws IOException {
            closed = true;
            super.close();
        }

        byte[] toByteArray() {
            return delegate.toByteArray();
        }

        boolean isClosed() {
            return closed;
        }
    }

    private TestBean bean;

    @Before
    public void setUp() {
        bean = new TestBean("test", 42);
        bean.addTag("alpha");
        bean.addTag("beta");
    }

    /* --------------------------------------------------------------------- */
    /*  clone(T)                                                             */
    /* --------------------------------------------------------------------- */

    @Test
    public void testCloneNormal() {
        TestBean cloned = SerializationUtils.clone(bean);
        assertNotSame("Clone must be a different instance", bean, cloned);
        assertEquals("Clone must be equal to original", bean, cloned);
        // verify deep copy – modify original after cloning
        bean.addTag("gamma");
        assertFalse("Clone should not see modifications to original", cloned.getTags().contains("gamma"));
    }

    @Test
    public void testCloneNull() {
        assertNull("Cloning null should return null", SerializationUtils.clone(null));
    }

    /* --------------------------------------------------------------------- */
    /*  serialize(Serializable, OutputStream)                               */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeToOutputStreamNormal() throws Exception {
        CloseTrackingOutputStream out = new CloseTrackingOutputStream();
        SerializationUtils.serialize(bean, out);
        assertTrue("OutputStream must be closed after serialize()", out.isClosed());

        // deserialize to verify content
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Object deserialized = SerializationUtils.deserialize(in);
        assertEquals("Deserialized object must equal original", bean, deserialized);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSerializeToOutputStreamNullStream() {
        SerializationUtils.serialize(bean, null);
    }

    @Test
    public void testSerializeNullObject() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        Object obj = SerializationUtils.deserialize(bais);
        assertNull("Deserialized null object must be null", obj);
    }

    /* --------------------------------------------------------------------- */
    /*  serialize(Serializable) -> byte[]                                    */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeToByteArray() throws Exception {
        byte[] data = SerializationUtils.serialize(bean);
        assertNotNull("Byte array must not be null", data);
        assertTrue("Byte array must contain data", data.length > 0);

        Object obj = SerializationUtils.deserialize(data);
        assertEquals("Deserialized object must equal original", bean, obj);
    }

    /* --------------------------------------------------------------------- */
    /*  deserialize(InputStream)                                             */
    /* --------------------------------------------------------------------- */

    @Test
    public void testDeserializeFromInputStreamNormal() throws Exception {
        byte[] data = SerializationUtils.serialize(bean);
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        Object result = SerializationUtils.deserialize(in);
        assertEquals("Deserialized object must equal original", bean, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeFromInputStreamNull() {
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test(expected = SerializationException.class)
    public void testDeserializeFromInputStreamCorrupted() throws Exception {
        byte[] corrupted = new byte[] {0x00, 0x01, 0x02, 0x03};
        ByteArrayInputStream in = new ByteArrayInputStream(corrupted);
        SerializationUtils.deserialize(in);
    }

    /* --------------------------------------------------------------------- */
    /*  deserialize(byte[])                                                 */
    /* --------------------------------------------------------------------- */

    @Test
    public void testDeserializeFromByteArrayNormal() throws Exception {
        byte[] data = SerializationUtils.serialize(bean);
        Object result = SerializationUtils.deserialize(data);
        assertEquals("Deserialized object must equal original", bean, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeFromByteArrayNull() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test(expected = SerializationException.class)
    public void testDeserializeFromByteArrayCorrupted() throws Exception {
        byte[] corrupted = new byte[] {0x10, 0x20, 0x30};
        SerializationUtils.deserialize(corrupted);
    }

    /* --------------------------------------------------------------------- */
    /*  Additional edge case: non‑serializable object via raw type (should fail) */
    /* --------------------------------------------------------------------- */

    @Test(expected = SerializationException.class)
    public void testSerializeNonSerializableObject() throws Exception {
        // Force a non‑serializable object using raw type cast
        Object nonSerializable = new Object();
        // The method signature expects Serializable; we bypass via raw cast
        SerializationUtils.serialize((Serializable) nonSerializable);
    }

}
