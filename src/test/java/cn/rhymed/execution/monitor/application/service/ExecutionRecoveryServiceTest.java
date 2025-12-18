package cn.rhymed.execution.monitor.application.service;

import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.common.enums.RecoveryStrategy;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.aggregate.RecoveryPolicy;
import cn.rhymed.execution.monitor.domain.model.*;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.domain.service.ExecutionRecordDomainService;
import cn.rhymed.execution.monitor.domain.service.RecoveryDecisionService;
import cn.rhymed.execution.monitor.infrastructure.persistence.memory.MemoryExecutionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionRecoveryService 集成测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class ExecutionRecoveryServiceTest {

    private ExecutionRecoveryService recoveryService;
    private ExecutionRecordRepository repository;
    private RecoveryDecisionService decisionService;
    private ExecutionRecordDomainService domainService;

    @BeforeEach
    void setUp() {
        repository = new MemoryExecutionRecordRepository();
        decisionService = new RecoveryDecisionService();
        domainService = new ExecutionRecordDomainService();
        recoveryService = new ExecutionRecoveryService(repository, decisionService, domainService);
    }

    @Test
    void should_find_interrupted_executions_on_startup() {
        // Given - 创建中断的任务
        ExecutionRecord interrupted = createInterruptedExecution();
        repository.save(interrupted);

        // When
        List<ExecutionRecord> recoverableExecutions = recoveryService.findRecoverableExecutions();

        // Then
        assertEquals(1, recoverableExecutions.size());
        assertEquals(interrupted.getExecutionId(), recoverableExecutions.get(0).getExecutionId());
    }

    @Test
    void should_find_heartbeat_timeout_executions() {
        // Given
        ExecutionRecord timeoutExecution = createRunningExecution();
        timeoutExecution.markHeartbeatTimeout();
        repository.save(timeoutExecution);

        // When
        List<ExecutionRecord> recoverableExecutions = recoveryService.findRecoverableExecutions();

        // Then
        assertEquals(1, recoverableExecutions.size());
        assertEquals(timeoutExecution.getExecutionId(), recoverableExecutions.get(0).getExecutionId());
    }

    @Test
    void should_not_find_succeeded_executions() {
        // Given
        ExecutionRecord succeeded = createRunningExecution();
        succeeded.complete();
        repository.save(succeeded);

        // When
        List<ExecutionRecord> recoverableExecutions = recoveryService.findRecoverableExecutions();

        // Then
        assertTrue(recoverableExecutions.isEmpty());
    }

    @Test
    void should_schedule_execution_for_retry() {
        // Given
        ExecutionRecord interrupted = createInterruptedExecution();
        repository.save(interrupted);

        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(interrupted.getExecutionName());

        // When
        recoveryService.scheduleForRetry(interrupted, policy);

        // Then
        ExecutionRecord updated = repository.findById(interrupted.getExecutionId()).get();
        assertEquals(ExecutionStatus.RETRY, updated.getStatus());
        assertEquals(1, updated.getRetryCount());
    }

    @Test
    void should_not_schedule_when_max_retry_reached() {
        // Given
        ExecutionRecord execution = createExecutionWithMaxRetry();
        repository.save(execution);

        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getExecutionName());

        // When
        boolean scheduled = recoveryService.scheduleForRetry(execution, policy);

        // Then
        assertFalse(scheduled);
    }

    @Test
    void should_recover_all_applicable_executions() {
        // Given - 创建多个不同状态的任务
        ExecutionRecord interrupted = createInterruptedExecution();
        ExecutionRecord timeout = createRunningExecution();
        timeout.markHeartbeatTimeout();
        ExecutionRecord succeeded = createRunningExecution();
        succeeded.complete();

        repository.save(interrupted);
        repository.save(timeout);
        repository.save(succeeded);

        // When
        int recoveredCount = recoveryService.recoverAllExecutions();

        // Then
        assertEquals(2, recoveredCount); // 只恢复interrupted和timeout
    }

    @Test
    void should_respect_recovery_strategy() {
        // Given
        ExecutionRecord failed = createRunningExecution();
        failed.fail(ErrorInfo.fromThrowable(new RuntimeException()));
        repository.save(failed);

        RecoveryPolicy neverPolicy = RecoveryPolicy.defaultPolicy(failed.getExecutionName())
                .toBuilder()
                .strategy(RecoveryStrategy.NEVER)
                .build();

        // When
        boolean shouldRecover = decisionService.shouldRecover(failed, neverPolicy);

        // Then
        assertFalse(shouldRecover);
    }

    @Test
    void should_apply_exception_classification() {
        // Given
        ExecutionRecord failed = createRunningExecution();
        failed.fail(ErrorInfo.fromThrowable(new IllegalArgumentException("Ignorable")));
        repository.save(failed);

        ExceptionClassification classification = ExceptionClassification.of(
                Collections.emptyList(),
                Collections.singletonList("IllegalArgumentException")
        );
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(failed.getExecutionName())
                .toBuilder()
                .exceptionClassification(classification)
                .build();

        // When
        boolean shouldRecover = decisionService.shouldRecover(failed, policy);

        // Then
        assertFalse(shouldRecover);
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

    private ExecutionRecord createExecutionWithMaxRetry() {
        ExecutionRecord execution = ExecutionRecord.create(
                ExecutionName.of("testExecution"),
                BizKey.empty(),
                SerializedParams.empty(),
                1
        );
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException()));
        execution.markForRetry();
        return execution;
    }
}
