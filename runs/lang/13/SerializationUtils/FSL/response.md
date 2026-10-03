###Test START##
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Comprehensive unit tests for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /** Simple serializable bean used for cloning / (de)serialization tests. */
    public static class TestBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private List<String> items;

        public TestBean(String name) {
            this.name = name;
            this.items = new ArrayList<>();
        }

        public String getName() {
            return name;
        }

        public List<String> getItems() {
            return items;
        }

        public void addItem(String item) {
            items.add(item);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TestBean)) {
                return false;
            }
            TestBean other = (TestBean) obj;
            return name.equals(other.name) && items.equals(other.items);
        }
    }

    /** Serializable object that deliberately throws on serialization to test error handling. */
    public static class BadSerializable implements Serializable {
        private static final long serialVersionUID = 1L;

        private void writeObject(java.io.ObjectOutputStream out) throws IOException {
            throw new IOException("forced write failure");
        }
    }

    /** OutputStream that throws an IOException on any write operation. */
    private static class FailingOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            throw new IOException("forced write failure");
        }
    }

    /** InputStream that provides no data, causing EOFException on readObject(). */
    private static class EmptyInputStream extends InputStream {
        @Override
        public int read() {
            return -1; // end of stream
        }
    }

    /** ClassLoader that cannot load {@link TestBean}, forcing fallback to the context ClassLoader. */
    private static class FailingClassLoader extends ClassLoader {
        public FailingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            // Force failure only for the TestBean class
            if (name.equals(TestBean.class.getName())) {
                throw new ClassNotFoundException("forced failure for " + name);
            }
            return super.loadClass(name);
        }
    }

    // ----------------------------------------------------------------------
    // serialize(Object, OutputStream)
    // ----------------------------------------------------------------------
    @Test
    public void testSerializeToOutputStream_NullStream() {
        try {
            SerializationUtils.serialize("test", null);
            fail("Expected IllegalArgumentException for null OutputStream");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test(expected = SerializationException.class)
    public void testSerializeToOutputStream_IOExceptionDuringWrite() {
        // The stream will throw IOException on write → SerializationException expected
        SerializationUtils.serialize("will fail", new FailingOutputStream());
    }

    @Test
    public void testSerializeToOutputStream_NullObject() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(null, baos);
        // Serialized form of a null object is a valid stream; deserialization should return null
        Object deserialized = SerializationUtils.deserialize(new ByteArrayInputStream(baos.toByteArray()));
        assertNull(deserialized);
    }

    // ----------------------------------------------------------------------
    // serialize(Object) → byte[]
    // ----------------------------------------------------------------------
    @Test
    public void testSerializeToByteArray() {
        TestBean bean = new TestBean("alpha");
        bean.addItem("one");
        bean.addItem("two");

        byte[] data = SerializationUtils.serialize(bean);
        assertNotNull(data);
        assertTrue(data.length > 0);

        Object result = SerializationUtils.deserialize(data);
        assertTrue(result instanceof TestBean);
        assertEquals(bean, result);
    }

    @Test(expected = SerializationException.class)
    public void testSerializeToByteArray_BadObject() {
        BadSerializable bad = new BadSerializable();
        SerializationUtils.serialize(bad);
    }

    // ----------------------------------------------------------------------
    // deserialize(InputStream)
    // ----------------------------------------------------------------------
    @Test
    public void testDeserialize_InputStream_Null() {
        try {
            SerializationUtils.deserialize((InputStream) null);
            fail("Expected IllegalArgumentException for null InputStream");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test(expected = SerializationException.class)
    public void testDeserialize_InputStream_MalformedData() {
        // Empty stream => ObjectInputStream.readObject() throws EOFException → SerializationException
        SerializationUtils.deserialize(new EmptyInputStream());
    }

    // ----------------------------------------------------------------------
    // deserialize(byte[])
    // ----------------------------------------------------------------------
    @Test
    public void testDeserialize_ByteArray_Null() {
        try {
            SerializationUtils.deserialize((byte[]) null);
            fail("Expected IllegalArgumentException for null byte[]");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testDeserialize_ByteArray_RoundTrip() {
        TestBean original = new TestBean("beta");
        original.addItem("A");
        original.addItem("B");
        byte[] data = SerializationUtils.serialize(original);

        Object restored = SerializationUtils.deserialize(data);
        assertTrue(restored instanceof TestBean);
        assertEquals(original, restored);
    }

    // ----------------------------------------------------------------------
    // clone(T)
    // ----------------------------------------------------------------------
    @Test
    public void testClone_NullInput() {
        assertNull(SerializationUtils.clone(null));
    }

    @Test
    public void testClone_DeepCopy() {
        TestBean original = new TestBean("gamma");
        original.addItem("x");
        original.addItem("y");

        TestBean copy = SerializationUtils.clone(original);
        assertNotSame(original, copy);
        assertEquals(original, copy);

        // Mutate original; copy must remain unchanged (deep clone)
        original.addItem("z");
        assertFalse(original.equals(copy));
        assertEquals(3, original.getItems().size());
        assertEquals(2, copy.getItems().size());
    }

    @Test(expected = SerializationException.class)
    public void testClone_NonSerializableObject() {
        // Anonymous class without implementing Serializable
        Object nonSerializable = new Object() {
            private static final long serialVersionUID = 1L;
        };
        // This will cause a NotSerializableException wrapped in SerializationException
        SerializationUtils.clone((Serializable) nonSerializable);
    }

    // ----------------------------------------------------------------------
    // ClassLoaderAwareObjectInputStream resolveClass fallback
    // ----------------------------------------------------------------------
    @Test
    public void testClassLoaderAwareObjectInputStream_FallbackToContextClassLoader() throws Exception {
        // Serialize a TestBean instance using the normal mechanism
        TestBean bean = new TestBean("delta");
        bean.addItem("item1");
        byte[] data = SerializationUtils.serialize(bean);

        // Use a ClassLoader that deliberately fails to load TestBean
        ClassLoader failingCl = new FailingClassLoader(null);
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        SerializationUtils.ClassLoaderAwareObjectInputStream in =
                new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, failingCl);

        Object read = in.readObject();
        in.close();

        assertTrue(read instanceof TestBean);
        assertEquals(bean, read);
    }

    @Test(expected = SerializationException.class)
    public void testClassLoaderAwareObjectInputStream_UnresolvableClass() throws Exception {
        // Prepare a stream that encodes an object of a class that does NOT exist.
        // We'll create a dummy byte array that mimics an ObjectStreamHeader followed
        // by a class descriptor for a non‑existent class.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // write stream header manually (magic + version)
        baos.write(new byte[] { (byte) 0xAC, (byte) 0xED, 0x00, 0x05 });
        // Write a TC_CLASSDESC (0x72) and a bogus class name "non.existent.Foo"
        baos.write(0x72);
        byte[] nameBytes = "non.existent.Foo".getBytes("UTF-8");
        baos.write((nameBytes.length >> 8) & 0xFF);
        baos.write(nameBytes.length & 0xFF);
        baos.write(nameBytes);
        // serialVersionUID (8 bytes of zeros)
        baos.write(new byte[8]);
        // No fields (TC_ENDBLOCKDATA)
        baos.write(0x78);
        // TC_NULL for the object reference itself
        baos.write(0x70);
        byte[] bogusData = baos.toByteArray();

        ByteArrayInputStream bais = new ByteArrayInputStream(bogusData);
        SerializationUtils.ClassLoaderAwareObjectInputStream in =
                new SerializationUtils.ClassLoaderAwareObjectInputStream(bais, this.getClass().getClassLoader());
        // Attempt to read -> should eventually throw SerializationException
        in.readObject();
    }

    // ----------------------------------------------------------------------
    // Additional sanity checks for serialize/deserialize symmetry
    // ----------------------------------------------------------------------
    @Test
    public void testSerializeDeserialize_SymmetryWithMap() {
        // Use a serializable collection that contains other serializable objects
        Map<String, Integer> map = new java.util.HashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        byte[] data = SerializationUtils.serialize((Serializable) map);
        Object result = SerializationUtils.deserialize(data);
        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Integer> restored = (Map<String, Integer>) result;
        assertEquals(map, restored);
    }
}
###Test END##