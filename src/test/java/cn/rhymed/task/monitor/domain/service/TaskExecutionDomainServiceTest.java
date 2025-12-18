package cn.rhymed.task.monitor.domain.service;

import cn.rhymed.task.monitor.domain.aggregate.RecoveryPolicy;
import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.RecoveryStrategy;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskExecutionDomainService 测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class TaskExecutionDomainServiceTest {

    private TaskExecutionDomainService domainService;

    @BeforeEach
    void setUp() {
        domainService = new TaskExecutionDomainService();
    }

    @Test
    void should_create_task_execution() {
        // Given
        TaskName taskName = TaskName.of("testTask");
        BizKey bizKey = BizKey.of("order123");
        SerializedParams params = SerializedParams.of("[\"arg1\",\"arg2\"]");
        int maxRetry = 3;

        // When
        TaskExecution execution = domainService.startTask(taskName, bizKey, params, maxRetry);

        // Then
        assertNotNull(execution);
        assertEquals(TaskStatus.RUNNING, execution.getStatus());
        assertEquals(taskName, execution.getTaskName());
        assertEquals(bizKey, execution.getBizKey());
        assertEquals(params, execution.getParams());
        assertEquals(0, execution.getRetryCount());
        assertEquals(maxRetry, execution.getMaxRetry());
    }

    @Test
    void should_complete_task_successfully() {
        // Given
        TaskExecution execution = createRunningTask();

        // When
        domainService.completeTask(execution);

        // Then
        assertEquals(TaskStatus.SUCCESS, execution.getStatus());
        assertNotNull(execution.getEndTime());
    }

    @Test
    void should_fail_task_with_error_info() {
        // Given
        TaskExecution execution = createRunningTask();
        Throwable error = new RuntimeException("test error");

        // When
        domainService.failTask(execution, error);

        // Then
        assertEquals(TaskStatus.FAILED, execution.getStatus());
        assertNotNull(execution.getErrorInfo());
        assertEquals("test error", execution.getErrorInfo().getErrorMessage());
    }

    @Test
    void should_decide_retry_based_on_policy_and_retryable_exception() {
        // Given
        TaskExecution execution = createRunningTask();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException("Retryable error")));

        RetryConfig retryConfig = RetryConfig.of(3, 60, true);
        ExceptionClassification classification = ExceptionClassification.of(
                Collections.singletonList("RuntimeException"),
                Collections.emptyList()
        );
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getTaskName())
                .toBuilder()
                .retryConfig(retryConfig)
                .exceptionClassification(classification)
                .strategy(RecoveryStrategy.AUTO)
                .build();

        // When
        boolean shouldRetry = domainService.shouldRetry(execution, policy);

        // Then
        assertTrue(shouldRetry);
    }

    @Test
    void should_not_retry_when_max_retry_reached() {
        // Given
        TaskExecution execution = createTaskWithRetryCount(3, 3);
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getTaskName());

        // When
        boolean shouldRetry = domainService.shouldRetry(execution, policy);

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void should_not_retry_ignorable_exception() {
        // Given
        TaskExecution execution = createRunningTask();
        execution.fail(ErrorInfo.fromThrowable(new IllegalArgumentException("Ignorable")));

        ExceptionClassification classification = ExceptionClassification.of(
                Collections.emptyList(),
                Collections.singletonList("IllegalArgumentException")
        );
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getTaskName())
                .toBuilder()
                .exceptionClassification(classification)
                .build();

        // When
        boolean shouldRetry = domainService.shouldRetry(execution, policy);

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void should_mark_for_retry() {
        // Given
        TaskExecution execution = createRunningTask();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException()));

        // When
        domainService.markForRetry(execution);

        // Then
        assertEquals(TaskStatus.RETRY, execution.getStatus());
        assertEquals(1, execution.getRetryCount());
    }

    @Test
    void should_mark_heartbeat_timeout() {
        // Given
        TaskExecution execution = createRunningTask();

        // When
        domainService.markHeartbeatTimeout(execution);

        // Then
        assertEquals(TaskStatus.HEARTBEAT_TIMEOUT, execution.getStatus());
    }

    private TaskExecution createRunningTask() {
        return TaskExecution.create(
                TaskName.of("testTask"),
                BizKey.of("biz123"),
                SerializedParams.empty(),
                3
        );
    }

    private TaskExecution createTaskWithRetryCount(int retryCount, int maxRetry) {
        TaskExecution execution = TaskExecution.create(
                TaskName.of("testTask"),
                BizKey.empty(),
                SerializedParams.empty(),
                maxRetry
        );

        // Simulate retry count
        for (int i = 0; i < retryCount; i++) {
            execution.fail(ErrorInfo.fromThrowable(new RuntimeException()));
            execution.markForRetry();
        }

        return execution;
    }
}
