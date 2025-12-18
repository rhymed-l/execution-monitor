package cn.rhymed.execution.monitor.domain.service;

import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.common.enums.RecoveryStrategy;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.aggregate.RecoveryPolicy;
import cn.rhymed.execution.monitor.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionRecordDomainService 测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class ExecutionRecordDomainServiceTest {

    private ExecutionRecordDomainService domainService;

    @BeforeEach
    void setUp() {
        domainService = new ExecutionRecordDomainService();
    }

    @Test
    void should_create_execution_execution() {
        // Given
        ExecutionName executionName = ExecutionName.of("testExecution");
        BizKey bizKey = BizKey.of("order123");
        SerializedParams params = SerializedParams.of("[\"arg1\",\"arg2\"]");
        int maxRetry = 3;

        // When
        ExecutionRecord execution = domainService.startExecution(executionName, bizKey, params, maxRetry);

        // Then
        assertNotNull(execution);
        assertEquals(ExecutionStatus.RUNNING, execution.getStatus());
        assertEquals(executionName, execution.getExecutionName());
        assertEquals(bizKey, execution.getBizKey());
        assertEquals(params, execution.getParams());
        assertEquals(0, execution.getRetryCount());
        assertEquals(maxRetry, execution.getMaxRetry());
    }

    @Test
    void should_complete_execution_successfully() {
        // Given
        ExecutionRecord execution = createRunningExecution();

        // When
        domainService.completeExecution(execution);

        // Then
        assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
        assertNotNull(execution.getEndTime());
    }

    @Test
    void should_fail_execution_with_error_info() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        Throwable error = new RuntimeException("test error");

        // When
        domainService.failExecution(execution, error);

        // Then
        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        assertNotNull(execution.getErrorInfo());
        assertEquals("test error", execution.getErrorInfo().getErrorMessage());
    }

    @Test
    void should_decide_retry_based_on_policy_and_retryable_exception() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException("Retryable error")));

        RetryConfig retryConfig = RetryConfig.of(3, 60, true);
        ExceptionClassification classification = ExceptionClassification.of(
                Collections.singletonList("RuntimeException"),
                Collections.emptyList()
        );
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getExecutionName())
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
        ExecutionRecord execution = createExecutionWithRetryCount(3, 3);
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getExecutionName());

        // When
        boolean shouldRetry = domainService.shouldRetry(execution, policy);

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void should_not_retry_ignorable_exception() {
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
        boolean shouldRetry = domainService.shouldRetry(execution, policy);

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void should_mark_for_retry() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException()));

        // When
        domainService.markForRetry(execution);

        // Then
        assertEquals(ExecutionStatus.RETRY, execution.getStatus());
        assertEquals(1, execution.getRetryCount());
    }

    @Test
    void should_mark_heartbeat_timeout() {
        // Given
        ExecutionRecord execution = createRunningExecution();

        // When
        domainService.markHeartbeatTimeout(execution);

        // Then
        assertEquals(ExecutionStatus.HEARTBEAT_TIMEOUT, execution.getStatus());
    }

    private ExecutionRecord createRunningExecution() {
        return ExecutionRecord.create(
                ExecutionName.of("testExecution"),
                BizKey.of("biz123"),
                SerializedParams.empty(),
                3
        );
    }

    private ExecutionRecord createExecutionWithRetryCount(int retryCount, int maxRetry) {
        ExecutionRecord execution = ExecutionRecord.create(
                ExecutionName.of("testExecution"),
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
