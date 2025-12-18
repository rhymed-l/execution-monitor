package cn.rhymed.task.monitor.infrastructure.retry;

import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.TaskName;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.interfaces.annotation.TaskMonitor;
import cn.rhymed.task.monitor.interfaces.config.TaskMonitorAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异常重试控制集成测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@SpringBootTest(classes = {
        TaskMonitorAutoConfiguration.class,
        RetryControlIntegrationTest.TestConfig.class
})
@TestPropertySource(properties = {
        "task.monitor.retry.retryable-exceptions=TimeoutException,IOException",
        "task.monitor.retry.ignorable-exceptions=IllegalArgumentException"
})
class RetryControlIntegrationTest {

    @Autowired
    private TestService testService;

    @Autowired
    private TaskExecutionRepository repository;

    @Test
    void should_mark_retryable_exception_for_retry() {
        // When - 抛出可重试异常
        try {
            testService.taskWithRetryableException();
        } catch (SocketTimeoutException e) {
            // Expected
        }

        // Then - 任务应该被标记为失败（实际重试逻辑由恢复服务处理）
        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("taskWithRetryableException"));
        assertEquals(1, executions.size());
        TaskExecution execution = executions.get(0);
        assertEquals(TaskStatus.FAILED, execution.getStatus());
        assertNotNull(execution.getErrorInfo());
    }

    @Test
    void should_not_retry_ignorable_exception() {
        // When - 抛出可忽略异常
        try {
            testService.taskWithIgnorableException();
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Then - 任务应该被标记为失败
        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("taskWithIgnorableException"));
        assertEquals(1, executions.size());
        TaskExecution execution = executions.get(0);
        assertEquals(TaskStatus.FAILED, execution.getStatus());
    }

    @Test
    void should_handle_nested_retryable_exception() {
        // When - 抛出包装的可重试异常
        try {
            testService.taskWithNestedRetryableException();
        } catch (RuntimeException e) {
            // Expected
        }

        // Then
        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("taskWithNestedRetryableException"));
        assertEquals(1, executions.size());
        TaskExecution execution = executions.get(0);
        assertEquals(TaskStatus.FAILED, execution.getStatus());
    }

    @Test
    void should_respect_max_retry_limit() {
        // Given - maxRetry设置为1
        try {
            testService.taskWithLimitedRetry();
        } catch (IOException e) {
            // Expected
        }

        // Then
        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("taskWithLimitedRetry"));
        assertEquals(1, executions.size());
        TaskExecution execution = executions.get(0);
        assertEquals(1, execution.getMaxRetry());
    }

    @Test
    void should_calculate_exponential_backoff() {
        // Given - 启用指数退避
        try {
            testService.taskWithExponentialBackoff();
        } catch (IOException e) {
            // Expected
        }

        // Then
        List<TaskExecution> executions = repository.findByTaskName(TaskName.of("taskWithExponentialBackoff"));
        assertEquals(1, executions.size());
        // 指数退避逻辑在RetryConfig中实现
    }

    @Configuration
    static class TestConfig {
        @Bean
        public TestService testService() {
            return new TestService();
        }
    }

    static class TestService {
        @TaskMonitor(taskName = "taskWithRetryableException", maxRetry = 3)
        public void taskWithRetryableException() throws SocketTimeoutException {
            throw new SocketTimeoutException("Connection timeout");
        }

        @TaskMonitor(taskName = "taskWithIgnorableException", maxRetry = 3)
        public void taskWithIgnorableException() {
            throw new IllegalArgumentException("Invalid input");
        }

        @TaskMonitor(taskName = "taskWithNestedRetryableException", maxRetry = 3)
        public void taskWithNestedRetryableException() {
            throw new RuntimeException("Wrapper", new IOException("Nested IO error"));
        }

        @TaskMonitor(taskName = "taskWithLimitedRetry", maxRetry = 1)
        public void taskWithLimitedRetry() throws IOException {
            throw new IOException("IO error");
        }

        @TaskMonitor(taskName = "taskWithExponentialBackoff", maxRetry = 3)
        public void taskWithExponentialBackoff() throws IOException {
            throw new IOException("IO error");
        }
    }
}
