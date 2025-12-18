package cn.rhymed.execution.monitor.infrastructure.serialization;

import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import cn.rhymed.execution.monitor.domain.model.SerializedParams;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.interfaces.annotation.ExecutionMonitor;
import cn.rhymed.execution.monitor.interfaces.config.ExecutionMonitorAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 参数序列化集成测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@SpringBootTest(classes = {
        ExecutionMonitorAutoConfiguration.class,
        SerializationIntegrationTest.TestConfig.class
})
class SerializationIntegrationTest {

    @Autowired
    private TestService testService;

    @Autowired
    private ExecutionRecordRepository repository;

    @Test
    void should_serialize_simple_parameters() {
        // When
        testService.methodWithSimpleParams("test", 123);

        // Then
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("methodWithSimpleParams"));
        assertEquals(1, executions.size());

        SerializedParams params = executions.get(0).getParams();
        assertFalse(params.isEmpty());
        assertTrue(params.getJsonData().contains("test"));
        assertTrue(params.getJsonData().contains("123"));
    }

    @Test
    void should_serialize_complex_object() {
        // Given
        UserDto user = new UserDto("Alice", 25);

        // When
        testService.methodWithComplexObject(user);

        // Then
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("methodWithComplexObject"));
        assertEquals(1, executions.size());

        SerializedParams params = executions.get(0).getParams();
        assertFalse(params.isEmpty());
        assertTrue(params.getJsonData().contains("Alice"));
    }

    @Test
    void should_not_serialize_when_disabled() {
        // When
        testService.methodWithoutSerialization("data");

        // Then
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("methodWithoutSerialization"));
        assertEquals(1, executions.size());

        SerializedParams params = executions.get(0).getParams();
        assertTrue(params.isEmpty());
    }

    @Test
    void should_not_serialize_streams() {
        // Given
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});

        // When
        testService.methodWithStream(stream);

        // Then
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("methodWithStream"));
        assertEquals(1, executions.size());

        SerializedParams params = executions.get(0).getParams();
        assertTrue(params.isEmpty()); // 不可序列化的参数应该被跳过
    }

    @Test
    void should_not_serialize_files() {
        // Given
        File file = new File("test.txt");

        // When
        testService.methodWithFile(file);

        // Then
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("methodWithFile"));
        assertEquals(1, executions.size());

        SerializedParams params = executions.get(0).getParams();
        assertTrue(params.isEmpty());
    }

    @Test
    void should_respect_size_limit() {
        // Given - 创建一个大参数
        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            largeString.append("x");
        }

        // When
        testService.methodWithLargeParam(largeString.toString());

        // Then
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("methodWithLargeParam"));
        assertEquals(1, executions.size());

        SerializedParams params = executions.get(0).getParams();
        // 如果超过大小限制,应该不序列化
        assertTrue(params.isEmpty() || params.getSizeBytes() <= 10240);
    }

    @Test
    void should_handle_null_parameters() {
        // When
        testService.methodWithNullParam(null);

        // Then
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("methodWithNullParam"));
        assertEquals(1, executions.size());

        SerializedParams params = executions.get(0).getParams();
        assertFalse(params.isEmpty());
        assertTrue(params.getJsonData().contains("null"));
    }

    @Configuration
    static class TestConfig {
        @Bean
        public TestService testService() {
            return new TestService();
        }
    }

    static class TestService {
        @ExecutionMonitor(executionName = "methodWithSimpleParams", serializeParams = true)
        public void methodWithSimpleParams(String str, int num) {
            // Test method
        }

        @ExecutionMonitor(executionName = "methodWithComplexObject", serializeParams = true)
        public void methodWithComplexObject(UserDto user) {
            // Test method
        }

        @ExecutionMonitor(executionName = "methodWithoutSerialization", serializeParams = false)
        public void methodWithoutSerialization(String data) {
            // Test method
        }

        @ExecutionMonitor(executionName = "methodWithStream", serializeParams = true)
        public void methodWithStream(ByteArrayInputStream stream) {
            // Test method
        }

        @ExecutionMonitor(executionName = "methodWithFile", serializeParams = true)
        public void methodWithFile(File file) {
            // Test method
        }

        @ExecutionMonitor(executionName = "methodWithLargeParam", serializeParams = true)
        public void methodWithLargeParam(String largeData) {
            // Test method
        }

        @ExecutionMonitor(executionName = "methodWithNullParam", serializeParams = true)
        public void methodWithNullParam(String nullData) {
            // Test method
        }
    }

    static class UserDto implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final int age;

        public UserDto(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }
}
