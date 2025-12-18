package cn.rhymed.task.monitor.application.service;

import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.*;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.infrastructure.persistence.memory.MemoryTaskExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskRetryExecutor 集成测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class TaskRetryExecutorTest {

    private TaskRetryExecutor retryExecutor;
    private TaskExecutionRepository repository;
    private RecoveryHandlerRegistry handlerRegistry;

    @BeforeEach
    void setUp() {
        repository = new MemoryTaskExecutionRepository();
        handlerRegistry = new RecoveryHandlerRegistry();
        retryExecutor = new TaskRetryExecutor(repository, handlerRegistry);
    }

    @Test
    void should_find_tasks_ready_for_retry() {
        // Given - 创建标记为RETRY的任务
        TaskExecution retryTask = createRetryTask();
        repository.save(retryTask);

        // When
        List<TaskExecution> tasksToRetry = retryExecutor.findTasksReadyForRetry();

        // Then
        assertEquals(1, tasksToRetry.size());
        assertEquals(retryTask.getTaskId(), tasksToRetry.get(0).getTaskId());
    }

    @Test
    void should_not_find_running_tasks() {
        // Given
        TaskExecution runningTask = createRunningTask();
        repository.save(runningTask);

        // When
        List<TaskExecution> tasksToRetry = retryExecutor.findTasksReadyForRetry();

        // Then
        assertTrue(tasksToRetry.isEmpty());
    }

    @Test
    void should_prepare_task_for_retry() {
        // Given
        TaskExecution original = createRetryTask();
        repository.save(original);

        // When
        TaskExecution newExecution = retryExecutor.prepareForRetry(original);

        // Then
        assertNotNull(newExecution);
        assertNotEquals(original.getTaskId(), newExecution.getTaskId());
        assertEquals(original.getTaskName(), newExecution.getTaskName());
        assertEquals(original.getBizKey(), newExecution.getBizKey());
        assertEquals(TaskStatus.RUNNING, newExecution.getStatus());
        assertEquals(original.getRetryCount(), newExecution.getRetryCount());
    }

    @Test
    void should_maintain_retry_count() {
        // Given
        TaskExecution original = createRetryTask();
        int originalRetryCount = original.getRetryCount();

        // When
        TaskExecution newExecution = retryExecutor.prepareForRetry(original);

        // Then
        assertEquals(originalRetryCount, newExecution.getRetryCount());
    }

    @Test
    void should_preserve_serialized_params() {
        // Given
        SerializedParams params = SerializedParams.of("[\"arg1\",\"arg2\"]");
        TaskExecution original = TaskExecution.create(
                TaskName.of("testTask"),
                BizKey.of("biz123"),
                params,
                3
        );
        original.fail(ErrorInfo.fromThrowable(new RuntimeException()));
        original.markForRetry();

        // When
        TaskExecution newExecution = retryExecutor.prepareForRetry(original);

        // Then
        assertEquals(params.getJsonData(), newExecution.getParams().getJsonData());
    }

    @Test
    void should_preserve_biz_key() {
        // Given
        BizKey bizKey = BizKey.of("order123");
        TaskExecution original = TaskExecution.create(
                TaskName.of("testTask"),
                bizKey,
                SerializedParams.empty(),
                3
        );
        original.fail(ErrorInfo.fromThrowable(new RuntimeException()));
        original.markForRetry();

        // When
        TaskExecution newExecution = retryExecutor.prepareForRetry(original);

        // Then
        assertEquals(bizKey, newExecution.getBizKey());
    }

    @Test
    void should_execute_retry_for_task() {
        // Given
        TaskExecution retryTask = createRetryTask();
        repository.save(retryTask);

        // When
        boolean executed = retryExecutor.executeRetry(retryTask);

        // Then
        // 注意: 实际重试执行需要反射调用原方法,这里只测试准备阶段
        // 在没有实际方法可调用的情况下，executeRetry应该返回false或抛出异常
        assertFalse(executed);
    }

    @Test
    void should_count_tasks_in_retry_queue() {
        // Given
        TaskExecution retry1 = createRetryTask();
        TaskExecution retry2 = createRetryTask();
        repository.save(retry1);
        repository.save(retry2);

        // When
        long count = retryExecutor.countTasksInRetryQueue();

        // Then
        assertEquals(2, count);
    }

    private TaskExecution createRunningTask() {
        return TaskExecution.create(
                TaskName.of("testTask"),
                BizKey.empty(),
                SerializedParams.empty(),
                3
        );
    }

    private TaskExecution createRetryTask() {
        TaskExecution execution = createRunningTask();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException("test")));
        execution.markForRetry();
        return execution;
    }
}
