package cn.rhymed.execution.monitor.domain.service;

import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.common.enums.RecoveryStrategy;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.aggregate.RecoveryPolicy;
import cn.rhymed.execution.monitor.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void should_recover_interrupted_execution_with_auto_strategy() {
        // Given
        ExecutionRecord execution = createInterruptedExecution();
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getExecutionName())
                .toBuilder()
                .strategy(RecoveryStrategy.AUTO)
                .build();

        // When
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertTrue(shouldRecover);
    }

    @Test
    void should_recover_heartbeat_timeout_execution() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        execution.markHeartbeatTimeout();
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getExecutionName());

        // When
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertTrue(shouldRecover);
    }

    @Test
    void should_always_recover_with_always_strategy() {
        // Given
        ExecutionRecord execution = createFailedExecution();
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getExecutionName())
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
        ExecutionRecord execution = createInterruptedExecution();
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getExecutionName())
                .toBuilder()
                .strategy(RecoveryStrategy.NEVER)
                .build();

        // When
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertFalse(shouldRecover);
    }

    @Test
    void should_not_recover_succeeded_execution() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        execution.complete();
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getExecutionName());

        // When
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertFalse(shouldRecover);
    }

    @Test
    void should_not_recover_when_max_retry_reached() {
        // Given
        ExecutionRecord execution = createExecutionWithMaxRetry();
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getExecutionName());

        // When
        boolean shouldRecover = service.shouldRecover(execution, policy);

        // Then
        assertFalse(shouldRecover);
    }

    @Test
    void should_not_recover_ignorable_exception() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        execution.fail(ErrorInfo.fromThrowable(new IllegalArgumentException("Ignorable")));

        ExceptionClassification classification = ExceptionClassification.of(
                Collections.emptyList(),
                Collections.singletonList("IllegalArgumentException")
        );
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getExecutionName())
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
        ExecutionRecord execution = createRunningExecution();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException("Retryable")));

        ExceptionClassification classification = ExceptionClassification.of(
                Collections.singletonList("RuntimeException"),
                Collections.emptyList()
        );
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getExecutionName())
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
        assertTrue(service.isRecoverableStatus(ExecutionStatus.INTERRUPTED));
        assertTrue(service.isRecoverableStatus(ExecutionStatus.HEARTBEAT_TIMEOUT));
        assertTrue(service.isRecoverableStatus(ExecutionStatus.FAILED));
        assertTrue(service.isRecoverableStatus(ExecutionStatus.RETRY));

        assertFalse(service.isRecoverableStatus(ExecutionStatus.SUCCESS));
        assertFalse(service.isRecoverableStatus(ExecutionStatus.RUNNING));
    }

    @Test
    void should_calculate_next_retry_time() {
        // Given
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(ExecutionName.of("test"));
        ExecutionRecord execution = createInterruptedExecution();
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

    private ExecutionRecord createRunningExecution() {
        return ExecutionRecord.create(
                ExecutionName.of("testExecution"),
                BizKey.empty(),
                SerializedParams.empty(),
                3
        );
    }

    private ExecutionRecord createInterruptedExecution() {
        ExecutionRecord execution = createRunningExecution();
        execution.interrupt();
        return execution;
    }

    private ExecutionRecord createFailedExecution() {
        ExecutionRecord execution = createRunningExecution();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException("test")));
        return execution;
    }

    private ExecutionRecord createExecutionWithMaxRetry() {
        ExecutionRecord execution = ExecutionRecord.create(
                ExecutionName.of("testExecution"),
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
