package cn.rhymed.task.monitor.application.service;

import cn.rhymed.task.monitor.domain.aggregate.RecoveryPolicy;
import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.RecoveryStrategy;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.*;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.domain.service.RecoveryDecisionService;
import cn.rhymed.task.monitor.domain.service.TaskExecutionDomainService;
import cn.rhymed.task.monitor.infrastructure.persistence.memory.MemoryTaskExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskRecoveryService 集成测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class TaskRecoveryServiceTest {

    private TaskRecoveryService recoveryService;
    private TaskExecutionRepository repository;
    private RecoveryDecisionService decisionService;
    private TaskExecutionDomainService domainService;

    @BeforeEach
    void setUp() {
        repository = new MemoryTaskExecutionRepository();
        decisionService = new RecoveryDecisionService();
        domainService = new TaskExecutionDomainService();
        recoveryService = new TaskRecoveryService(repository, decisionService, domainService);
    }

    @Test
    void should_find_interrupted_tasks_on_startup() {
        // Given - 创建中断的任务
        TaskExecution interrupted = createInterruptedTask();
        repository.save(interrupted);

        // When
        List<TaskExecution> recoverableTasks = recoveryService.findRecoverableTasks();

        // Then
        assertEquals(1, recoverableTasks.size());
        assertEquals(interrupted.getTaskId(), recoverableTasks.get(0).getTaskId());
    }

    @Test
    void should_find_heartbeat_timeout_tasks() {
        // Given
        TaskExecution timeoutTask = createRunningTask();
        timeoutTask.markHeartbeatTimeout();
        repository.save(timeoutTask);

        // When
        List<TaskExecution> recoverableTasks = recoveryService.findRecoverableTasks();

        // Then
        assertEquals(1, recoverableTasks.size());
        assertEquals(timeoutTask.getTaskId(), recoverableTasks.get(0).getTaskId());
    }

    @Test
    void should_not_find_succeeded_tasks() {
        // Given
        TaskExecution succeeded = createRunningTask();
        succeeded.complete();
        repository.save(succeeded);

        // When
        List<TaskExecution> recoverableTasks = recoveryService.findRecoverableTasks();

        // Then
        assertTrue(recoverableTasks.isEmpty());
    }

    @Test
    void should_schedule_task_for_retry() {
        // Given
        TaskExecution interrupted = createInterruptedTask();
        repository.save(interrupted);

        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(interrupted.getTaskName());

        // When
        recoveryService.scheduleForRetry(interrupted, policy);

        // Then
        TaskExecution updated = repository.findById(interrupted.getTaskId()).get();
        assertEquals(TaskStatus.RETRY, updated.getStatus());
        assertEquals(1, updated.getRetryCount());
    }

    @Test
    void should_not_schedule_when_max_retry_reached() {
        // Given
        TaskExecution task = createTaskWithMaxRetry();
        repository.save(task);

        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(task.getTaskName());

        // When
        boolean scheduled = recoveryService.scheduleForRetry(task, policy);

        // Then
        assertFalse(scheduled);
    }

    @Test
    void should_recover_all_applicable_tasks() {
        // Given - 创建多个不同状态的任务
        TaskExecution interrupted = createInterruptedTask();
        TaskExecution timeout = createRunningTask();
        timeout.markHeartbeatTimeout();
        TaskExecution succeeded = createRunningTask();
        succeeded.complete();

        repository.save(interrupted);
        repository.save(timeout);
        repository.save(succeeded);

        // When
        int recoveredCount = recoveryService.recoverAllTasks();

        // Then
        assertEquals(2, recoveredCount); // 只恢复interrupted和timeout
    }

    @Test
    void should_respect_recovery_strategy() {
        // Given
        TaskExecution failed = createRunningTask();
        failed.fail(ErrorInfo.fromThrowable(new RuntimeException()));
        repository.save(failed);

        RecoveryPolicy neverPolicy = RecoveryPolicy.defaultPolicy(failed.getTaskName())
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
        TaskExecution failed = createRunningTask();
        failed.fail(ErrorInfo.fromThrowable(new IllegalArgumentException("Ignorable")));
        repository.save(failed);

        ExceptionClassification classification = ExceptionClassification.of(
                Collections.emptyList(),
                Collections.singletonList("IllegalArgumentException")
        );
        RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(failed.getTaskName())
                .toBuilder()
                .exceptionClassification(classification)
                .build();

        // When
        boolean shouldRecover = decisionService.shouldRecover(failed, policy);

        // Then
        assertFalse(shouldRecover);
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

    private TaskExecution createTaskWithMaxRetry() {
        TaskExecution execution = TaskExecution.create(
                TaskName.of("testTask"),
                BizKey.empty(),
                SerializedParams.empty(),
                1
        );
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException()));
        execution.markForRetry();
        return execution;
    }
}
