package cn.rhymed.execution.monitor.infrastructure.aop;

import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.common.enums.SerializationMode;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.interfaces.annotation.Monitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MonitorAspect 集成测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@SpringBootTest(classes = MonitorAspectIntegrationTest.TestConfig.class)
class MonitorAspectIntegrationTest {

    @Autowired
    private TestService testService;

    @Autowired
    private ExecutionRecordRepository repository;

    @Test
    void should_record_successful_execution_execution() {
        // When
        String result = testService.successfulExecution("test");

        // Then
        assertEquals("success: test", result);

        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("successfulExecution"));
        assertEquals(1, executions.size());

        ExecutionRecord execution = executions.get(0);
        assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
        assertNotNull(execution.getStartTime());
        assertNotNull(execution.getEndTime());
    }

    @Test
    void should_record_failed_execution_execution() {
        // When & Then
        assertThrows(RuntimeException.class, () -> testService.failingExecution());

        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("failingExecution"));
        assertEquals(1, executions.size());

        ExecutionRecord execution = executions.get(0);
        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        assertNotNull(execution.getErrorInfo());
        assertTrue(execution.getErrorInfo().getErrorMessage().contains("Execution failed"));
    }

    @Test
    void should_capture_biz_key_from_parameter() {
        // When
        testService.executionWithBizKey("order123", "data");

        // Then
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("executionWithBizKey"));
        assertEquals(1, executions.size());

        ExecutionRecord execution = executions.get(0);
        assertTrue(execution.getBizKey().isPresent());
        assertEquals("order123", execution.getBizKey().getValue());
    }

    @Test
    void should_serialize_parameters_when_enabled() {
        // When
        testService.executionWithParams("arg1", "arg2");

        // Then
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("executionWithParams"));
        assertEquals(1, executions.size());

        ExecutionRecord execution = executions.get(0);
        assertFalse(execution.getParams().isEmpty());
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        public TestService testService() {
            return new TestService();
        }

        // MonitorAspect bean will be registered by implementation
        // ExecutionRecordRepository bean will be registered by implementation
        // MonitorService bean will be registered by implementation
    }

    static class TestService {
        @Monitor(name = "successfulExecution")
        public String successfulExecution(String input) {
            return "success: " + input;
        }

        @Monitor(name = "failingExecution")
        public void failingExecution() {
            throw new RuntimeException("Execution failed");
        }

        @Monitor(name = "executionWithBizKey", bizKey = "#orderId")
        public void executionWithBizKey(String orderId, String data) {
            // Business logic
        }

        @Monitor(name = "executionWithParams", serializeParams = SerializationMode.AUTO)
        public void executionWithParams(String arg1, String arg2) {
            // Business logic
        }
    }
}
