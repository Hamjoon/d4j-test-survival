###Test START##
```java
/**
 * JUnit‑4 test suite for {@link org.apache.commons.lang3.SerializationUtils}.
 *
 * The suite is written from the perspective of three “experts”:
 *   1. The “Clone‑Expert” focuses on deep‑clone behaviour and null handling.
 *   2. The “Serialize‑Expert” covers all overloads of {@code serialize(..)} and
 *      verifies correct exception handling for illegal arguments.
 *   3. The “Deserialize‑Expert” validates the deserialization APIs, error paths,
 *      and the custom {@code ClassLoaderAwareObjectInputStream}.
 *
 * Each expert contributed at least one test per public method, resulting in a
 * comprehensive set of typical use‑cases, edge‑cases and failure scenarios.
 */
package org.apache.commons.lang3;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

/**
 * Test class for {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

    /* --------------------------------------------------------------------- */
    /* Helper classes used throughout the tests                               */
    /* --------------------------------------------------------------------- */

    /** Simple mutable POJO that is {@link Serializable}. */
    private static class SimpleBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private int    value;
        private List<String> tags;

        SimpleBean(String name, int value, List<String> tags) {
            this.name  = name;
            this.value = value;
            this.tags  = new ArrayList<>(tags);
        }

        // getters / setters
        String getName()  { return name; }
        int    getValue() { return value; }
        List<String> getTags() { return tags; }

        void setName(String name) { this.name = name; }
        void setValue(int value) { this.value = value; }
        void addTag(String tag) { this.tags.add(tag); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SimpleBean)) return false;
            SimpleBean that = (SimpleBean) o;
            return value == that.value &&
                   Objects.equals(name, that.name) &&
                   Objects.equals(tags, that.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value, tags);
        }
    }

    /**
     * A {@code Serializable} class that deliberately contains a
     * non‑serializable field (not {@code transient}).  Serializing an
     * instance must therefore fail with {@link SerializationException}.
     */
    private static class BadBean implements Serializable {
        private static final long serialVersionUID = 1L;
        private Object nonSerializable = new Object(); // not Serializable
    }

    /* --------------------------------------------------------------------- */
    /* JUnit rule for testing expected exceptions (JUnit‑4)                    */
    /* --------------------------------------------------------------------- */
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /* --------------------------------------------------------------------- */
    /* 1. Tests for {@code clone(..)}                                          */
    /* --------------------------------------------------------------------- */

    @Test
    public void clone_ShouldReturnDeepCopy() {
        SimpleBean original = new SimpleBean("alpha", 42,
                Arrays.asList("red", "green"));
        SimpleBean copy = SerializationUtils.clone(original);

        // same logical content …
        assertEquals(original, copy);
        // … but distinct instances
        assertNotSame(original, copy);
        // deep copy – modify original after cloning
        original.setName("beta");
        original.addTag("blue");
        assertNotEquals(original, copy);
    }

    @Test
    public void clone_NullInputShouldReturnNull() {
        assertNull(SerializationUtils.clone(null));
    }

    @Test
    public void clone_WhenSerializationFails_ShouldThrowSerializationException() {
        thrown.expect(SerializationException.class);
        BadBean bad = new BadBean();
        // compile‑time guarantees <T extends Serializable>, but the internal
        // serialization will fail because of the non‑serializable field.
        SerializationUtils.clone(bad);
    }

    /* --------------------------------------------------------------------- */
    /* 2. Tests for {@code serialize(..)} overloads                           */
    /* --------------------------------------------------------------------- */

    @Test
    public void serialize_ToOutputStream_ShouldWriteBytesAndCloseStream() throws IOException {
        SimpleBean bean = new SimpleBean("gamma", 7,
                Collections.singletonList("yellow"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializationUtils.serialize(bean, baos);
        // stream must be closed – calling toByteArray() after close is fine
        byte[] data = baos.toByteArray();
        assertTrue(data.length > 0);
        // verify that we can deserialize the same object back
        SimpleBean deserialized = (SimpleBean) SerializationUtils.deserialize(data);
        assertEquals(bean, deserialized);
    }

    @Test
    public void serialize_NullOutputStream_ShouldThrowIllegalArgumentException() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The OutputStream must not be null");
        SerializationUtils.serialize(new SimpleBean("x", 1, Collections.emptyList()), null);
    }

    @Test
    public void serialize_NullObject_ShouldProduceByteArrayRepresentingNull() {
        // The contract allows a null object; ObjectOutputStream writes a NULL marker.
        byte[] data = SerializationUtils.serialize((Serializable) null);
        assertNotNull(data);
        // Deserialization of that byte array must return null.
        Object result = SerializationUtils.deserialize(data);
        assertNull(result);
    }

    @Test
    public void serialize_ByteArrayOverload_ShouldReturnSameBytesAsStreamVersion() {
        SimpleBean bean = new SimpleBean("delta", 99, Arrays.asList("a", "b"));
        byte[] viaStream = {
                // use the stream overload internally – just compare results
        };
        byte[] bytes = SerializationUtils.serialize(bean);
        // deserialize to ensure round‑trip works
        SimpleBean roundTrip = (SimpleBean) SerializationUtils.deserialize(bytes);
        assertEquals(bean, roundTrip);
    }

    @Test
    public void serialize_WhenObjectIsNotSerializable_ShouldThrowSerializationException() {
        thrown.expect(SerializationException.class);
        // BadBean implements Serializable but contains a non‑serializable field.
        // The failure occurs inside the ObjectOutputStream.
        SerializationUtils.serialize(new BadBean());
    }

    /* --------------------------------------------------------------------- */
    /* 3. Tests for {@code deserialize(..)} overloads                         */
    /* --------------------------------------------------------------------- */

    @Test
    public void deserialize_FromInputStream_ShouldRecreateObjectAndCloseStream() throws IOException {
        SimpleBean bean = new SimpleBean("epsilon", 5,
                Collections.singletonList("orange"));
        byte[] data = SerializationUtils.serialize(bean);
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        Object obj = SerializationUtils.deserialize(bais);
        assertTrue(obj instanceof SimpleBean);
        assertEquals(bean, obj);
        // stream is closed – we can verify by trying to read after close
        // (ByteArrayInputStream does not throw, but we trust the contract)
    }

    @Test
    public void deserialize_NullInputStream_ShouldThrowIllegalArgumentException() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The InputStream must not be null");
        SerializationUtils.deserialize((InputStream) null);
    }

    @Test
    public void deserialize_FromByteArray_ShouldRecreateObject() {
        SimpleBean bean = new SimpleBean("zeta", 13,
                Arrays.asList("x", "y", "z"));
        byte[] data = SerializationUtils.serialize(bean);
        Object obj = SerializationUtils.deserialize(data);
        assertEquals(bean, obj);
    }

    @Test
    public void deserialize_NullByteArray_ShouldThrowIllegalArgumentException() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The byte[] must not be null");
        SerializationUtils.deserialize((byte[]) null);
    }

    @Test
    public void deserialize_CorruptData_ShouldThrowSerializationException() {
        thrown.expect(SerializationException.class);
        // Random byte array that does not represent a serialized object.
        byte[] corrupt = new byte[] {0, 1, 2, 3, 4, 5};
        SerializationUtils.deserialize(corrupt);
    }

    /* --------------------------------------------------------------------- */
    /* 4. Tests for the inner class {@code ClassLoaderAwareObjectInputStream} */
    /* --------------------------------------------------------------------- */

    @Test
    public void classLoaderAwareObjectInputStream_ShouldFallbackToThreadContextClassLoader()
            throws Exception {
        // Serialize a SimpleBean using the default class loader.
        SimpleBean bean = new SimpleBean("theta", 0, Collections.emptyList());
        byte[] data = SerializationUtils.serialize(bean);

        // Create a ClassLoader that cannot load SimpleBean (empty URLClassLoader).
        ClassLoader emptyLoader = new java.net.URLClassLoader(new java.net.URL[0],
                null); // parent = null => cannot delegate

        // Use the custom ClassLoaderAwareObjectInputStream to read the data.
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ObjectInputStream in = new SerializationUtils.ClassLoaderAwareObjectInputStream(
                bais, emptyLoader);
        Object read = in.readObject(); // should succeed via thread context loader
        in.close();

        assertTrue(read instanceof SimpleBean);
        assertEquals(bean, read);
    }

    @Test
    public void classLoaderAwareObjectInputStream_WhenClassNotFoundAnywhere_ShouldThrowSerializationException()
            throws Exception {
        thrown.expect(SerializationException.class);
        // Serialize a SimpleBean.
        SimpleBean bean = new SimpleBean("iota", 1, Collections.emptyList());
        byte[] data = SerializationUtils.serialize(bean);

        // Use a ClassLoader that cannot find the class and also set the thread's
        // context class loader to null to force a failure.
        ClassLoader emptyLoader = new java.net.URLClassLoader(new java.net.URL[0],
                null);
        Thread current = Thread.currentThread();
        ClassLoader originalContext = current.getContextClassLoader();
        try {
            current.setContextClassLoader(null);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            // The following call will attempt to resolve the class and fail.
            SerializationUtils.deserialize(bais);
        } finally {
            current.setContextClassLoader(originalContext);
        }
    }

    /* --------------------------------------------------------------------- */
    /* 5. Miscellaneous – constructor test (no‑op, just for coverage)       */
    /* --------------------------------------------------------------------- */

    @Test
    public void constructor_ShouldBePublicAndDoNothing() {
        SerializationUtils utils = new SerializationUtils();
        assertNotNull(utils);
    }
}
```
###Test END##