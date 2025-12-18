package cn.rhymed.task.monitor.contract;

import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.TaskName;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.interfaces.annotation.EnableTaskMonitor;
import cn.rhymed.task.monitor.interfaces.annotation.TaskMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @TaskMonitor注解契约测试 验证注解的基本使用场景符合用户期望
 * TDD: 测试先行
 * @since 2025-12-10 11:44
 */
@SpringBootTest(classes = TaskMonitorAnnotationContractTest.TestConfig.class)
class TaskMonitorAnnotationContractTest {

    @Autowired
    private SampleService sampleService;

    @Autowired
    private TaskExecutionRepository repository;

    @Test
    void contract_basic_task_monitoring() {
        // GIVEN: A method annotated with @TaskMonitor
        // WHEN: The method is invoked and completes successfully
        String result = sampleService.processOrder("ORD-001");

        // THEN: A task record should be created with RUNNING → SUCCESS transition
        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("processOrder"));
        assertEquals(1, executions.size());

        TaskExecution execution = executions.get(0);
        assertEquals(TaskStatus.SUCCESS, execution.getStatus());
        assertNotNull(execution.getTaskId());
        assertNotNull(execution.getStartTime());
        assertNotNull(execution.getEndTime());
        assertTrue(execution.getDurationSeconds() >= 0);
    }

    @Test
    void contract_task_failure_recording() {
        // GIVEN: A method annotated with @TaskMonitor that throws exception
        // WHEN: The method is invoked and throws exception
        assertThrows(IllegalArgumentException.class,
                () -> sampleService.validateInput(null));

        // THEN: Task should be recorded with FAILED status and error info
        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("validateInput"));
        assertEquals(1, executions.size());

        TaskExecution execution = executions.get(0);
        assertEquals(TaskStatus.FAILED, execution.getStatus());
        assertNotNull(execution.getErrorInfo());
        assertEquals("IllegalArgumentException", execution.getErrorInfo().getExceptionType());
        assertTrue(execution.getErrorInfo().getStackTrace().contains("validateInput"));
    }

    @Test
    void contract_business_key_extraction() {
        // GIVEN: A method with bizKey expression #orderId
        // WHEN: The method is invoked with orderId parameter
        sampleService.shipOrder("ORD-002", "EXPRESS");

        // THEN: The bizKey should be captured in the task record
        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("shipOrder"));
        assertEquals(1, executions.size());

        TaskExecution execution = executions.get(0);
        assertTrue(execution.getBizKey().isPresent());
        assertEquals("ORD-002", execution.getBizKey().getValue());
    }

    @Test
    void contract_parameter_serialization() {
        // GIVEN: A method with serializeParams=true
        // WHEN: The method is invoked with parameters
        sampleService.calculateTotal(100, 10);

        // THEN: Parameters should be serialized and stored
        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("calculateTotal"));
        assertEquals(1, executions.size());

        TaskExecution execution = executions.get(0);
        assertFalse(execution.getParams().isEmpty());
        assertTrue(execution.getParams().getSizeBytes() > 0);
    }

    @Test
    void contract_retry_configuration() {
        // GIVEN: A method with maxRetry=5
        // WHEN: The method is invoked
        sampleService.callExternalApi();

        // THEN: Task should be recorded with maxRetry=5
        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("callExternalApi"));
        assertEquals(1, executions.size());

        TaskExecution execution = executions.get(0);
        assertEquals(5, execution.getMaxRetry());
    }

    @Configuration
    @EnableTaskMonitor
    static class TestConfig {
        @Bean
        public SampleService sampleService() {
            return new SampleService();
        }
    }

    static class SampleService {
        @TaskMonitor(taskName = "processOrder")
        public String processOrder(String orderId) {
            return "Processed: " + orderId;
        }

        @TaskMonitor(taskName = "validateInput")
        public void validateInput(String input) {
            if (input == null) {
                throw new IllegalArgumentException("Input cannot be null");
            }
        }

        @TaskMonitor(taskName = "shipOrder", bizKey = "#orderId")
        public void shipOrder(String orderId, String shippingMethod) {
            // Shipping logic
        }

        @TaskMonitor(taskName = "calculateTotal", serializeParams = true)
        public int calculateTotal(int price, int quantity) {
            return price * quantity;
        }

        @TaskMonitor(taskName = "callExternalApi", maxRetry = 5)
        public void callExternalApi() {
            // API call logic
        }
    }
}
