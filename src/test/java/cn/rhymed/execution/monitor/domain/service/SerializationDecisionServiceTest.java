package cn.rhymed.execution.monitor.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SerializationDecisionService 单元测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class SerializationDecisionServiceTest {

    private SerializationDecisionService service;

    @BeforeEach
    void setUp() {
        service = new SerializationDecisionService();
    }

    @Test
    void should_allow_serialization_of_primitive_types() {
        // Given
        Object[] args = new Object[]{1, "test", true, 3.14};

        // When
        boolean canSerialize = service.canSerialize(args);

        // Then
        assertTrue(canSerialize);
    }

    @Test
    void should_allow_serialization_of_simple_objects() {
        // Given
        Object[] args = new Object[]{
                new SerializableDto("test", 123),
                Arrays.asList("a", "b", "c")
        };

        // When
        boolean canSerialize = service.canSerialize(args);

        // Then
        assertTrue(canSerialize);
    }

    @Test
    void should_reject_serialization_of_non_serializable_objects() {
        // Given
        Object[] args = new Object[]{new NonSerializableObject()};

        // When
        boolean canSerialize = service.canSerialize(args);

        // Then
        assertFalse(canSerialize);
    }

    @Test
    void should_reject_serialization_of_input_output_streams() {
        // Given
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[]{1, 2, 3});
        Object[] args = new Object[]{inputStream};

        // When
        boolean canSerialize = service.canSerialize(args);

        // Then
        assertFalse(canSerialize);
    }

    @Test
    void should_reject_serialization_of_files() {
        // Given
        File file = new File("test.txt");
        Object[] args = new Object[]{file};

        // When
        boolean canSerialize = service.canSerialize(args);

        // Then
        assertFalse(canSerialize);
    }

    @Test
    void should_reject_serialization_when_null_args() {
        // When
        boolean canSerialize = service.canSerialize(null);

        // Then
        assertFalse(canSerialize);
    }

    @Test
    void should_reject_serialization_when_empty_args() {
        // When
        boolean canSerialize = service.canSerialize(new Object[]{});

        // Then
        assertFalse(canSerialize);
    }

    @Test
    void should_allow_null_elements_in_args() {
        // Given
        Object[] args = new Object[]{"test", null, 123};

        // When
        boolean canSerialize = service.canSerialize(args);

        // Then
        assertTrue(canSerialize);
    }

    @Test
    void should_detect_circular_references() {
        // Given
        CircularRef obj1 = new CircularRef("obj1");
        CircularRef obj2 = new CircularRef("obj2");
        obj1.ref = obj2;
        obj2.ref = obj1;
        Object[] args = new Object[]{obj1};

        // When
        boolean canSerialize = service.canSerialize(args);

        // Then
        assertFalse(canSerialize); // 循环引用应该被拒绝
    }

    @Test
    void should_get_serialization_reason() {
        // Given - 不可序列化对象
        Object[] args = new Object[]{new NonSerializableObject()};
        service.canSerialize(args);

        // When
        String reason = service.getLastDecisionReason();

        // Then
        assertNotNull(reason);
        assertTrue(reason.contains("不可序列化") || reason.contains("NonSerializable"));
    }

    @Test
    void should_allow_common_collection_types() {
        // Given
        Object[] args = new Object[]{
                new ArrayList<>(Arrays.asList(1, 2, 3)),
                new HashMap<String, Integer>() {{
                    put("a", 1);
                    put("b", 2);
                }},
                new HashSet<>(Arrays.asList("x", "y"))
        };

        // When
        boolean canSerialize = service.canSerialize(args);

        // Then
        assertTrue(canSerialize);
    }

    @Test
    void should_reject_thread_objects() {
        // Given
        Thread thread = new Thread();
        Object[] args = new Object[]{thread};

        // When
        boolean canSerialize = service.canSerialize(args);

        // Then
        assertFalse(canSerialize);
    }

    // 测试用的DTO类
    static class SerializableDto implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final int value;

        public SerializableDto(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    // 不可序列化的对象
    static class NonSerializableObject {
        private final String data = "test";
    }

    // 循环引用对象
    static class CircularRef implements Serializable {
        private static final long serialVersionUID = 1L;
        String name;
        CircularRef ref;

        public CircularRef(String name) {
            this.name = name;
        }
    }
}
