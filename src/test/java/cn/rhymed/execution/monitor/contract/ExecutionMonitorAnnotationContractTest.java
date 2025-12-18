package cn.rhymed.execution.monitor.contract;

import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.interfaces.annotation.EnableExecutionMonitor;
import cn.rhymed.execution.monitor.interfaces.annotation.ExecutionMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @ExecutionMonitor注解契约测试 验证注解的基本使用场景符合用户期望
 * TDD: 测试先行
 * @since 2025-12-10 11:44
 */
@SpringBootTest(classes = ExecutionMonitorAnnotationContractTest.TestConfig.class)
class ExecutionMonitorAnnotationContractTest {

    @Autowired
    private SampleService sampleService;

    @Autowired
    private ExecutionRecordRepository repository;

    @Test
    void contract_basic_execution_monitoring() {
        // GIVEN: A method annotated with @ExecutionMonitor
        // WHEN: The method is invoked and completes successfully
        String result = sampleService.processOrder("ORD-001");

        // THEN: A execution record should be created with RUNNING → SUCCESS transition
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("processOrder"));
        assertEquals(1, executions.size());

        ExecutionRecord execution = executions.get(0);
        assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
        assertNotNull(execution.getExecutionId());
        assertNotNull(execution.getStartTime());
        assertNotNull(execution.getEndTime());
        assertTrue(execution.getDurationSeconds() >= 0);
    }

    @Test
    void contract_execution_failure_recording() {
        // GIVEN: A method annotated with @ExecutionMonitor that throws exception
        // WHEN: The method is invoked and throws exception
        assertThrows(IllegalArgumentException.class,
                () -> sampleService.validateInput(null));

        // THEN: Execution should be recorded with FAILED status and error info
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("validateInput"));
        assertEquals(1, executions.size());

        ExecutionRecord execution = executions.get(0);
        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        assertNotNull(execution.getErrorInfo());
        assertEquals("IllegalArgumentException", execution.getErrorInfo().getExceptionType());
        assertTrue(execution.getErrorInfo().getStackTrace().contains("validateInput"));
    }

    @Test
    void contract_business_key_extraction() {
        // GIVEN: A method with bizKey expression #orderId
        // WHEN: The method is invoked with orderId parameter
        sampleService.shipOrder("ORD-002", "EXPRESS");

        // THEN: The bizKey should be captured in the execution record
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("shipOrder"));
        assertEquals(1, executions.size());

        ExecutionRecord execution = executions.get(0);
        assertTrue(execution.getBizKey().isPresent());
        assertEquals("ORD-002", execution.getBizKey().getValue());
    }

    @Test
    void contract_parameter_serialization() {
        // GIVEN: A method with serializeParams=true
        // WHEN: The method is invoked with parameters
        sampleService.calculateTotal(100, 10);

        // THEN: Parameters should be serialized and stored
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("calculateTotal"));
        assertEquals(1, executions.size());

        ExecutionRecord execution = executions.get(0);
        assertFalse(execution.getParams().isEmpty());
        assertTrue(execution.getParams().getSizeBytes() > 0);
    }

    @Test
    void contract_retry_configuration() {
        // GIVEN: A method with maxRetry=5
        // WHEN: The method is invoked
        sampleService.callExternalApi();

        // THEN: Execution should be recorded with maxRetry=5
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("callExternalApi"));
        assertEquals(1, executions.size());

        ExecutionRecord execution = executions.get(0);
        assertEquals(5, execution.getMaxRetry());
    }

    @Configuration
    @EnableExecutionMonitor
    static class TestConfig {
        @Bean
        public SampleService sampleService() {
            return new SampleService();
        }
    }

    static class SampleService {
        @ExecutionMonitor(executionName = "processOrder")
        public String processOrder(String orderId) {
            return "Processed: " + orderId;
        }

        @ExecutionMonitor(executionName = "validateInput")
        public void validateInput(String input) {
            if (input == null) {
                throw new IllegalArgumentException("Input cannot be null");
            }
        }

        @ExecutionMonitor(executionName = "shipOrder", bizKey = "#orderId")
        public void shipOrder(String orderId, String shippingMethod) {
            // Shipping logic
        }

        @ExecutionMonitor(executionName = "calculateTotal", serializeParams = true)
        public int calculateTotal(int price, int quantity) {
            return price * quantity;
        }

        @ExecutionMonitor(executionName = "callExternalApi", maxRetry = 5)
        public void callExternalApi() {
            // API call logic
        }
    }
}
