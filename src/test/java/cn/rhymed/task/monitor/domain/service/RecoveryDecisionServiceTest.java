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
 * RecoveryDecisionService 单元测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class RecoveryDecisionServiceTest {

    private RecoveryDecisionService service;

    @BeforeEach
    void setUp() {
        service = new RecoveryDecisionService();
    }

    @Test
    void should_recover_interrupted_task_with_auto_strategy() {
        // Given
        TaskExecution execution = createInterruptedTask();
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getTaskName())
                .toBuilder()
                .strategy(RecoveryStrategy.AUTO)
                .build();

        // When
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertTrue(shouldRecover);
    }

    @Test
    void should_recover_heartbeat_timeout_task() {
        // Given
        TaskExecution execution = createRunningTask();
        execution.markHeartbeatTimeout();
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getTaskName());

        // When
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertTrue(shouldRecover);
    }

    @Test
    void should_always_recover_with_always_strategy() {
        // Given
        TaskExecution execution = createFailedTask();
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getTaskName())
                .toBuilder()
                .strategy(RecoveryStrategy.ALWAYS)
                .build();

        // When
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertTrue(shouldRecover);
    }

    @Test
    void should_never_recover_with_never_strategy() {
        // Given
        TaskExecution execution = createInterruptedTask();
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getTaskName())
                .toBuilder()
                .strategy(RecoveryStrategy.NEVER)
                .build();

        // When
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertFalse(shouldRecover);
    }

    @Test
    void should_not_recover_succeeded_task() {
        // Given
        TaskExecution execution = createRunningTask();
        execution.complete();
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getTaskName());

        // When
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertFalse(shouldRecover);
    }

    @Test
    void should_not_recover_when_max_retry_reached() {
        // Given
        TaskExecution execution = createTaskWithMaxRetry();
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getTaskName());

        // When
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertFalse(shouldRecover);
    }

    @Test
    void should_not_recover_ignorable_exception() {
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
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertFalse(shouldRecover);
    }

    @Test
    void should_recover_retryable_exception() {
        // Given
        TaskExecution execution = createRunningTask();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException("Retryable")));

        ExceptionClassification classification = ExceptionClassification.of(
                Collections.singletonList("RuntimeException"),
                Collections.emptyList()
        );
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getTaskName())
                .toBuilder()
                .exceptionClassification(classification)
                .build();

        // When
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertTrue(shouldRecover);
    }

    @Test
    void should_identify_recoverable_statuses() {
        // When & Then
        assertTrue(service.isRecoverableStatus(TaskStatus.INTERRUPTED));
        assertTrue(service.isRecoverableStatus(TaskStatus.HEARTBEAT_TIMEOUT));
        assertTrue(service.isRecoverableStatus(TaskStatus.FAILED));
        assertTrue(service.isRecoverableStatus(TaskStatus.RETRY));

        assertFalse(service.isRecoverableStatus(TaskStatus.SUCCESS));
        assertFalse(service.isRecoverableStatus(TaskStatus.RUNNING));
    }

    @Test
    void should_calculate_next_retry_time() {
        // Given
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(TaskName.of("test"));
        TaskExecution execution = createInterruptedTask();
        long currentTime = System.currentTimeMillis();

        // When
        long nextRetryTime = service.calculateNextRetryTime(execution, policy, currentTime);

        // Then
        assertTrue(nextRetryTime > currentTime);
    }

    @Test
    void should_determine_if_retry_time_reached() {
        // Given
        long pastTime = System.currentTimeMillis() - 10000; // 10秒前
        long futureTime = System.currentTimeMillis() + 10000; // 10秒后

        // When & Then
        assertTrue(service.isRetryTimeReached(pastTime));
        assertFalse(service.isRetryTimeReached(futureTime));
    }

    private TaskExecution createRunningTask() {
        return TaskExecution.create(
                TaskName.of("testTask"),
                BizKey.empty(),
                SerializedParams.empty(),
                3
        );
    }

    private TaskExecution createInterruptedTask() {
        TaskExecution execution = createRunningTask();
        execution.interrupt();
        return execution;
    }

    private TaskExecution createFailedTask() {
        TaskExecution execution = createRunningTask();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException("test")));
        return execution;
    }

    private TaskExecution createTaskWithMaxRetry() {
        TaskExecution execution = TaskExecution.create(
                TaskName.of("testTask"),
                BizKey.empty(),
                SerializedParams.empty(),
                2
        );

        // 达到最大重试次数
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException()));
        execution.markForRetry();
        execution = execution.restart();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException()));
        execution.markForRetry();

        return execution;
    }
}
