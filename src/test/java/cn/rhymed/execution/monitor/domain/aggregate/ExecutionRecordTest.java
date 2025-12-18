package cn.rhymed.execution.monitor.domain.aggregate;

import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.model.BizKey;
import cn.rhymed.execution.monitor.domain.model.ErrorInfo;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import cn.rhymed.execution.monitor.domain.model.SerializedParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionRecord聚合根状态转换测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class ExecutionRecordTest {

    @Test
    void should_create_execution_execution_with_running_status() {
        // Given
        ExecutionName executionName = ExecutionName.of("testExecution");
        BizKey bizKey = BizKey.of("order123");
        SerializedParams params = SerializedParams.empty();

        // When
        ExecutionRecord execution = ExecutionRecord.create(executionName, bizKey, params, 3);

        // Then
        assertNotNull(execution.getExecutionId());
        assertEquals(executionName, execution.getExecutionName());
        assertEquals(bizKey, execution.getBizKey());
        assertEquals(ExecutionStatus.RUNNING, execution.getStatus());
        assertEquals(0, execution.getRetryCount());
        assertNotNull(execution.getStartTime());
        assertNull(execution.getEndTime());
    }

    @Test
    void should_transition_to_success_when_complete() {
        // Given
        ExecutionRecord execution = createRunningExecution();

        // When
        execution.complete();

        // Then
        assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
        assertNotNull(execution.getEndTime());
        assertNull(execution.getErrorInfo());
    }

    @Test
    void should_transition_to_failed_when_fail() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        ErrorInfo errorInfo = ErrorInfo.fromThrowable(new RuntimeException("test error"));

        // When
        execution.fail(errorInfo);

        // Then
        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        assertNotNull(execution.getEndTime());
        assertEquals(errorInfo, execution.getErrorInfo());
    }

    @Test
    void should_transition_to_interrupted_when_interrupt() {
        // Given
        ExecutionRecord execution = createRunningExecution();

        // When
        execution.interrupt();

        // Then
        assertEquals(ExecutionStatus.INTERRUPTED, execution.getStatus());
        assertNotNull(execution.getEndTime());
    }

    @Test
    void should_transition_to_heartbeat_timeout() {
        // Given
        ExecutionRecord execution = createRunningExecution();

        // When
        execution.markHeartbeatTimeout();

        // Then
        assertEquals(ExecutionStatus.HEARTBEAT_TIMEOUT, execution.getStatus());
        assertNotNull(execution.getEndTime());
    }

    @Test
    void should_transition_to_retry_and_increment_retry_count() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        ErrorInfo errorInfo = ErrorInfo.fromThrowable(new RuntimeException("test error"));
        execution.fail(errorInfo);

        // When
        execution.markForRetry();

        // Then
        assertEquals(ExecutionStatus.RETRY, execution.getStatus());
        assertEquals(1, execution.getRetryCount());
    }

    @Test
    void should_create_new_execution_when_restart() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException("test")));
        execution.markForRetry();

        // When
        ExecutionRecord newExecution = execution.restart();

        // Then
        assertNotEquals(execution.getExecutionId(), newExecution.getExecutionId());
        assertEquals(ExecutionStatus.RUNNING, newExecution.getStatus());
        assertEquals(execution.getExecutionName(), newExecution.getExecutionName());
        assertEquals(1, newExecution.getRetryCount());
    }

    @Test
    void should_throw_exception_when_invalid_transition() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        execution.complete();

        // When & Then
        assertThrows(IllegalStateException.class, () -> execution.fail(ErrorInfo.fromThrowable(new RuntimeException())));
    }

    @Test
    void should_update_heartbeat_when_running() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        execution.enableHeartbeat(60);

        // When
        execution.updateHeartbeat();

        // Then
        assertNotNull(execution.getLastHeartbeatTime());
    }

    @Test
    void should_throw_exception_when_update_heartbeat_not_running() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        execution.complete();

        // When & Then
        assertThrows(IllegalStateException.class, execution::updateHeartbeat);
    }

    @Test
    void should_return_true_when_can_retry() {
        // Given
        ExecutionRecord execution = ExecutionRecord.create(ExecutionName.of("test"), BizKey.empty(), SerializedParams.empty(), 3);

        // Then
        assertTrue(execution.canRetry());
    }

    @Test
    void should_return_false_when_cannot_retry() {
        // Given
        ExecutionRecord execution = ExecutionRecord.create(ExecutionName.of("test"), BizKey.empty(), SerializedParams.empty(), 0);

        // Then
        assertFalse(execution.canRetry());
    }

    @Test
    void should_return_true_when_terminated() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        execution.complete();

        // Then
        assertTrue(execution.isTerminated());
    }

    @Test
    void should_calculate_duration_seconds() {
        // Given
        ExecutionRecord execution = createRunningExecution();

        // When
        execution.complete();

        // Then
        assertNotNull(execution.getDurationSeconds());
        assertTrue(execution.getDurationSeconds() >= 0);
    }

    private ExecutionRecord createRunningExecution() {
        return ExecutionRecord.create(
                ExecutionName.of("testExecution"),
                BizKey.of("biz123"),
                SerializedParams.empty(),
                3
        );
    }
}
