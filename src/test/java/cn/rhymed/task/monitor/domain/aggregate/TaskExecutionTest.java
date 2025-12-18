package cn.rhymed.task.monitor.domain.aggregate;

import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskExecution聚合根状态转换测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class TaskExecutionTest {

    @Test
    void should_create_task_execution_with_running_status() {
        // Given
        TaskName taskName = TaskName.of("testTask");
        BizKey bizKey = BizKey.of("order123");
        SerializedParams params = SerializedParams.empty();

        // When
        TaskExecution execution = TaskExecution.create(taskName, bizKey, params, 3);

        // Then
        assertNotNull(execution.getTaskId());
        assertEquals(taskName, execution.getTaskName());
        assertEquals(bizKey, execution.getBizKey());
        assertEquals(TaskStatus.RUNNING, execution.getStatus());
        assertEquals(0, execution.getRetryCount());
        assertNotNull(execution.getStartTime());
        assertNull(execution.getEndTime());
    }

    @Test
    void should_transition_to_success_when_complete() {
        // Given
        TaskExecution execution = createRunningTask();

        // When
        execution.complete();

        // Then
        assertEquals(TaskStatus.SUCCESS, execution.getStatus());
        assertNotNull(execution.getEndTime());
        assertNull(execution.getErrorInfo());
    }

    @Test
    void should_transition_to_failed_when_fail() {
        // Given
        TaskExecution execution = createRunningTask();
        ErrorInfo errorInfo = ErrorInfo.fromThrowable(new RuntimeException("test error"));

        // When
        execution.fail(errorInfo);

        // Then
        assertEquals(TaskStatus.FAILED, execution.getStatus());
        assertNotNull(execution.getEndTime());
        assertEquals(errorInfo, execution.getErrorInfo());
    }

    @Test
    void should_transition_to_interrupted_when_interrupt() {
        // Given
        TaskExecution execution = createRunningTask();

        // When
        execution.interrupt();

        // Then
        assertEquals(TaskStatus.INTERRUPTED, execution.getStatus());
        assertNotNull(execution.getEndTime());
    }

    @Test
    void should_transition_to_heartbeat_timeout() {
        // Given
        TaskExecution execution = createRunningTask();

        // When
        execution.markHeartbeatTimeout();

        // Then
        assertEquals(TaskStatus.HEARTBEAT_TIMEOUT, execution.getStatus());
        assertNotNull(execution.getEndTime());
    }

    @Test
    void should_transition_to_retry_and_increment_retry_count() {
        // Given
        TaskExecution execution = createRunningTask();
        ErrorInfo errorInfo = ErrorInfo.fromThrowable(new RuntimeException("test error"));
        execution.fail(errorInfo);

        // When
        execution.markForRetry();

        // Then
        assertEquals(TaskStatus.RETRY, execution.getStatus());
        assertEquals(1, execution.getRetryCount());
    }

    @Test
    void should_create_new_task_when_restart() {
        // Given
        TaskExecution execution = createRunningTask();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException("test")));
        execution.markForRetry();

        // When
        TaskExecution newExecution = execution.restart();

        // Then
        assertNotEquals(execution.getTaskId(), newExecution.getTaskId());
        assertEquals(TaskStatus.RUNNING, newExecution.getStatus());
        assertEquals(execution.getTaskName(), newExecution.getTaskName());
        assertEquals(1, newExecution.getRetryCount());
    }

    @Test
    void should_throw_exception_when_invalid_transition() {
        // Given
        TaskExecution execution = createRunningTask();
        execution.complete();

        // When & Then
        assertThrows(IllegalStateException.class, () -> execution.fail(ErrorInfo.fromThrowable(new RuntimeException())));
    }

    @Test
    void should_update_heartbeat_when_running() {
        // Given
        TaskExecution execution = createRunningTask();
        execution.enableHeartbeat(60);

        // When
        execution.updateHeartbeat();

        // Then
        assertNotNull(execution.getLastHeartbeatTime());
    }

    @Test
    void should_throw_exception_when_update_heartbeat_not_running() {
        // Given
        TaskExecution execution = createRunningTask();
        execution.complete();

        // When & Then
        assertThrows(IllegalStateException.class, execution::updateHeartbeat);
    }

    @Test
    void should_return_true_when_can_retry() {
        // Given
        TaskExecution execution = TaskExecution.create(TaskName.of("test"), BizKey.empty(), SerializedParams.empty(), 3);

        // Then
        assertTrue(execution.canRetry());
    }

    @Test
    void should_return_false_when_cannot_retry() {
        // Given
        TaskExecution execution = TaskExecution.create(TaskName.of("test"), BizKey.empty(), SerializedParams.empty(), 0);

        // Then
        assertFalse(execution.canRetry());
    }

    @Test
    void should_return_true_when_terminated() {
        // Given
        TaskExecution execution = createRunningTask();
        execution.complete();

        // Then
        assertTrue(execution.isTerminated());
    }

    @Test
    void should_calculate_duration_seconds() {
        // Given
        TaskExecution execution = createRunningTask();

        // When
        execution.complete();

        // Then
        assertNotNull(execution.getDurationSeconds());
        assertTrue(execution.getDurationSeconds() >= 0);
    }

    private TaskExecution createRunningTask() {
        return TaskExecution.create(
                TaskName.of("testTask"),
                BizKey.of("biz123"),
                SerializedParams.empty(),
                3
        );
    }
}
