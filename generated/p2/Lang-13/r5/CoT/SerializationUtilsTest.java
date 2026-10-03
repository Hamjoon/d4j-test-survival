package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Comprehensive JUnit‑4 test suite for {@link SerializationUtils}.
 * <p>
 * The tests cover:
 * <ul>
 *   <li>normal behaviour of each public method</li>
 *   <li>edge‑cases such as {@code null} arguments</li>
 *   <li>exception handling (IllegalArgumentException, SerializationException)</li>
 *   <li>deep‑clone semantics</li>
 * </ul>
 */
public class SerializationUtilsTest {

    /* --------------------------------------------------------------------- */
    /* Helper classes used in the test suite                                 */
    /* --------------------------------------------------------------------- */

    /** Simple mutable POJO that is serializable. */
    private static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private List<String> items = new ArrayList<>();

        SimpleBean(String name) {
            this.name = name;
        }

        void addItem(String item) {
            items.add(item);
        }

        String getName() {
            return name;
        }

        List<String> getItems() {
            return items;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SimpleBean)) {
                return false;
            }
            SimpleBean other = (SimpleBean) o;
            return name.equals(other.name) && items.equals(other.items);
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + items.hashCode();
        }
    }

    /** Serializable that deliberately fails during serialization. */
    private static class BadBean implements Serializable {
        private static final long serialVersionUID = 1L;

        private void writeObject(java.io.ObjectOutputStream out) throws IOException {
            throw new IOException("forced failure during writeObject");
        }
    }

    /** OutputStream that throws IOException on close – used to verify that
     *  SerializationUtils swallows close‑exception (no test fails because of it). */
    private static class ThrowOnCloseOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void close() throws IOException {
            throw new IOException("close failure");
        }

        byte[] toByteArray() {
            return delegate.toByteArray();
        }
    }

    /* --------------------------------------------------------------------- */
    /* 1. clone(T)                                                            */
    /* --------------------------------------------------------------------- */

    @Test
    public void testClone_NullInput_ReturnsNull() {
        SimpleBean result = SerializationUtils.clone(null);
        assertNull(result);
    }

    @Test
    public void testClone_DeepCopy() {
        SimpleBean original = new SimpleBean("orig");
        original.addItem("A");
        original.addItem("B");

        SimpleBean copy = SerializationUtils.clone(original);
        assertNotSame(original, copy);
        assertEquals(original, copy);

        // mutate original – copy must stay unchanged
        original.addItem("C");
        assertFalse(original.equals(copy));
        assertEquals(3, original.getItems().size());
        assertEquals(2, copy.getItems().size());
    }

    @Test(expected = SerializationException.class)
    public void testClone_SerializationFailure_ThrowsSerializationException() {
        BadBean bad = new BadBean();
        SerializationUtils.clone(bad);
    }

    /* --------------------------------------------------------------------- */
    /* 2. serialize(Serializable, OutputStream)                               */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testSerialize_NullOutputStream_ThrowsIllegalArgumentException() {
        SerializationUtils.serialize("test", null);
    }

    @Test
    public void testSerialize_NullObject_WritesNullReference() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        // The resulting byte[] should be a valid serialization stream that
        // can be deserialized back to a null reference.
        Object deserialized = SerializationUtils.deserialize(new ByteArrayInputStream(baos.toByteArray()));
        assertNull(deserialized);
    }

    @Test
    public void testSerialize_AndCloseException_IsSwallowed() {
        ThrowOnCloseOutputStream out = new ThrowOnCloseOutputStream();
        // The method must not propagate the IOException from close().
        SerializationUtils.serialize("hello", out);
        // Verify that something was indeed written.
        assertTrue(out.toByteArray().length > 0);
    }

    /* --------------------------------------------------------------------- */
    /* 3. serialize(Serializable) → byte[]                                    */
    /* --------------------------------------------------------------------- */

    @Test
    public void testSerializeToByteArray_RoundTrip() {
        SimpleBean bean = new SimpleBean("bean");
        bean.addItem("x");
        bean.addItem("y");

        byte[] data = SerializationUtils.serialize(bean);
        assertNotNull(data);
        assertTrue(data.length > 0);

        Object obj = SerializationUtils.deserialize(data);
        assertTrue(obj instanceof SimpleBean);
        assertEquals(bean, obj);
    }

    /* --------------------------------------------------------------------- */
    /* 4. deserialize(InputStream)                                            */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserialize_NullInputStream_ThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((java.io.InputStream) null);
    }

    @Test
    public void testDeserialize_ValidStream_ReturnsObject() {
        SimpleBean bean = new SimpleBean("streamBean");
        byte[] data = SerializationUtils.serialize(bean);
        ByteArrayInputStream bais = new ByteArrayInputStream(data);

        Object result = SerializationUtils.deserialize(bais);
        assertEquals(bean, result);
    }

    @Test(expected = SerializationException.class)
    public void testDeserialize_CorruptData_ThrowsSerializationException() {
        byte[] garbage = new byte[] {0, 1, 2, 3, 4, 5};
        ByteArrayInputStream bais = new ByteArrayInputStream(garbage);
        SerializationUtils.deserialize(bais);
    }

    /* --------------------------------------------------------------------- */
    /* 5. deserialize(byte[])                                                 */
    /* --------------------------------------------------------------------- */

    @Test(expected = IllegalArgumentException.class)
    public void testDeserializeByteArray_NullInput_ThrowsIllegalArgumentException() {
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void testDeserializeByteArray_RoundTrip() {
        SimpleBean bean = new SimpleBean("arrayBean");
        bean.addItem("foo");
        byte[] data = SerializationUtils.serialize(bean);

        Object result = SerializationUtils.deserialize(data);
        assertEquals(bean, result);
    }

    @Test(expected = SerializationException.class)
    public void testDeserializeByteArray_CorruptData_ThrowsSerializationException() {
        byte[] garbage = new byte[] {9, 8, 7, 6};
        SerializationUtils.deserialize(garbage);
    }

    /* --------------------------------------------------------------------- */
    /* 6. Constructor – ensure it can be instantiated (even though it is
     *    not meant for normal use).                                           */
    /* --------------------------------------------------------------------- */

    @Test
    public void testConstructorInstantiates() {
        SerializationUtils utils = new SerializationUtils();
        assertNotNull(utils);
    }
}
