package cn.rhymed.task.monitor.infrastructure.aop;

import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.TaskName;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.interfaces.annotation.TaskMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskMonitorAspect 集成测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@SpringBootTest(classes = TaskMonitorAspectIntegrationTest.TestConfig.class)
class TaskMonitorAspectIntegrationTest {

    @Autowired
    private TestService testService;

    @Autowired
    private TaskExecutionRepository repository;

    @Test
    void should_record_successful_task_execution() {
        // When
        String result = testService.successfulTask("test");

        // Then
        assertEquals("success: test", result);

        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("successfulTask"));
        assertEquals(1, executions.size());

        TaskExecution execution = executions.get(0);
        assertEquals(TaskStatus.SUCCESS, execution.getStatus());
        assertNotNull(execution.getStartTime());
        assertNotNull(execution.getEndTime());
    }

    @Test
    void should_record_failed_task_execution() {
        // When & Then
        assertThrows(RuntimeException.class, () -> testService.failingTask());

        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("failingTask"));
        assertEquals(1, executions.size());

        TaskExecution execution = executions.get(0);
        assertEquals(TaskStatus.FAILED, execution.getStatus());
        assertNotNull(execution.getErrorInfo());
        assertTrue(execution.getErrorInfo().getErrorMessage().contains("Task failed"));
    }

    @Test
    void should_capture_biz_key_from_parameter() {
        // When
        testService.taskWithBizKey("order123", "data");

        // Then
        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("taskWithBizKey"));
        assertEquals(1, executions.size());

        TaskExecution execution = executions.get(0);
        assertTrue(execution.getBizKey().isPresent());
        assertEquals("order123", execution.getBizKey().getValue());
    }

    @Test
    void should_serialize_parameters_when_enabled() {
        // When
        testService.taskWithParams("arg1", "arg2");

        // Then
        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("taskWithParams"));
        assertEquals(1, executions.size());

        TaskExecution execution = executions.get(0);
        assertFalse(execution.getParams().isEmpty());
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        public TestService testService() {
            return new TestService();
        }

        // TaskMonitorAspect bean will be registered by implementation
        // TaskExecutionRepository bean will be registered by implementation
        // TaskMonitorService bean will be registered by implementation
    }

    static class TestService {
        @TaskMonitor(taskName = "successfulTask")
        public String successfulTask(String input) {
            return "success: " + input;
        }

        @TaskMonitor(taskName = "failingTask")
        public void failingTask() {
            throw new RuntimeException("Task failed");
        }

        @TaskMonitor(taskName = "taskWithBizKey", bizKey = "#orderId")
        public void taskWithBizKey(String orderId, String data) {
            // Business logic
        }

        @TaskMonitor(taskName = "taskWithParams", serializeParams = true)
        public void taskWithParams(String arg1, String arg2) {
            // Business logic
        }
    }
}
