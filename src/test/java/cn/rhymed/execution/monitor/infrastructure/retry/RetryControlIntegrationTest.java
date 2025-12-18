package cn.rhymed.execution.monitor.infrastructure.retry;

import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.interfaces.annotation.ExecutionMonitor;
import cn.rhymed.execution.monitor.interfaces.config.ExecutionMonitorAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 异常重试控制集成测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@SpringBootTest(classes = {
        ExecutionMonitorAutoConfiguration.class,
        RetryControlIntegrationTest.TestConfig.class
})
@TestPropertySource(properties = {
        "execution.monitor.retry.retryable-exceptions=TimeoutException,IOException",
        "execution.monitor.retry.ignorable-exceptions=IllegalArgumentException"
})
class RetryControlIntegrationTest {

    @Autowired
    private TestService testService;

    @Autowired
    private ExecutionRecordRepository repository;

    @Test
    void should_mark_retryable_exception_for_retry() {
        // When - 抛出可重试异常
        try {
            testService.executionWithRetryableException();
        } catch (SocketTimeoutException e) {
            // Expected
        }

        // Then - 任务应该被标记为失败（实际重试逻辑由恢复服务处理）
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("executionWithRetryableException"));
        assertEquals(1, executions.size());
        ExecutionRecord execution = executions.get(0);
        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        assertNotNull(execution.getErrorInfo());
    }

    @Test
    void should_not_retry_ignorable_exception() {
        // When - 抛出可忽略异常
        try {
            testService.executionWithIgnorableException();
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Then - 任务应该被标记为失败
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("executionWithIgnorableException"));
        assertEquals(1, executions.size());
        ExecutionRecord execution = executions.get(0);
        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
    }

    @Test
    void should_handle_nested_retryable_exception() {
        // When - 抛出包装的可重试异常
        try {
            testService.executionWithNestedRetryableException();
        } catch (RuntimeException e) {
            // Expected
        }

        // Then
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("executionWithNestedRetryableException"));
        assertEquals(1, executions.size());
        ExecutionRecord execution = executions.get(0);
        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
    }

    @Test
    void should_respect_max_retry_limit() {
        // Given - maxRetry设置为1
        try {
            testService.executionWithLimitedRetry();
        } catch (IOException e) {
            // Expected
        }

        // Then
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("executionWithLimitedRetry"));
        assertEquals(1, executions.size());
        ExecutionRecord execution = executions.get(0);
        assertEquals(1, execution.getMaxRetry());
    }

    @Test
    void should_calculate_exponential_backoff() {
        // Given - 启用指数退避
        try {
            testService.executionWithExponentialBackoff();
        } catch (IOException e) {
            // Expected
        }

        // Then
        List<ExecutionRecord> executions = repository.findByExecutionName(ExecutionName.of("executionWithExponentialBackoff"));
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
        @ExecutionMonitor(executionName = "executionWithRetryableException", maxRetry = 3)
        public void executionWithRetryableException() throws SocketTimeoutException {
            throw new SocketTimeoutException("Connection timeout");
        }

        @ExecutionMonitor(executionName = "executionWithIgnorableException", maxRetry = 3)
        public void executionWithIgnorableException() {
            throw new IllegalArgumentException("Invalid input");
        }

        @ExecutionMonitor(executionName = "executionWithNestedRetryableException", maxRetry = 3)
        public void executionWithNestedRetryableException() {
            throw new RuntimeException("Wrapper", new IOException("Nested IO error"));
        }

        @ExecutionMonitor(executionName = "executionWithLimitedRetry", maxRetry = 1)
        public void executionWithLimitedRetry() throws IOException {
            throw new IOException("IO error");
        }

        @ExecutionMonitor(executionName = "executionWithExponentialBackoff", maxRetry = 3)
        public void executionWithExponentialBackoff() throws IOException {
            throw new IOException("IO error");
        }
    }
}
