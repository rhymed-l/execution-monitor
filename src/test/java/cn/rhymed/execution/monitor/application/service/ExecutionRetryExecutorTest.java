package cn.rhymed.execution.monitor.application.service;

import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.BizKey;
import cn.rhymed.execution.monitor.domain.model.ErrorInfo;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import cn.rhymed.execution.monitor.domain.model.SerializedParams;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.infrastructure.persistence.memory.MemoryExecutionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionRetryExecutor 集成测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class ExecutionRetryExecutorTest {

    private ExecutionRetryExecutor retryExecutor;
    private ExecutionRecordRepository repository;
    private RecoveryHandlerRegistry handlerRegistry;

    @BeforeEach
    void setUp() {
        repository = new MemoryExecutionRecordRepository();
        handlerRegistry = new RecoveryHandlerRegistry();
        retryExecutor = new ExecutionRetryExecutor(repository, handlerRegistry);
    }

    @Test
    void should_find_executions_ready_for_retry() {
        // Given - 创建标记为RETRY的任务
        ExecutionRecord retryExecution = createRetryExecution();
        repository.save(retryExecution);

        // When
        List<ExecutionRecord> executionsToRetry = retryExecutor.findExecutionsReadyForRetry();

        // Then
        assertEquals(1, executionsToRetry.size());
        assertEquals(retryExecution.getExecutionId(), executionsToRetry.get(0).getExecutionId());
    }

    @Test
    void should_not_find_running_executions() {
        // Given
        ExecutionRecord runningExecution = createRunningExecution();
        repository.save(runningExecution);

        // When
        List<ExecutionRecord> executionsToRetry = retryExecutor.findExecutionsReadyForRetry();

        // Then
        assertTrue(executionsToRetry.isEmpty());
    }

    @Test
    void should_prepare_execution_for_retry() {
        // Given
        ExecutionRecord original = createRetryExecution();
        repository.save(original);

        // When
        ExecutionRecord newExecution = retryExecutor.prepareForRetry(original);

        // Then
        assertNotNull(newExecution);
        assertNotEquals(original.getExecutionId(), newExecution.getExecutionId());
        assertEquals(original.getExecutionName(), newExecution.getExecutionName());
        assertEquals(original.getBizKey(), newExecution.getBizKey());
        assertEquals(ExecutionStatus.RUNNING, newExecution.getStatus());
        assertEquals(original.getRetryCount(), newExecution.getRetryCount());
    }

    @Test
    void should_maintain_retry_count() {
        // Given
        ExecutionRecord original = createRetryExecution();
        int originalRetryCount = original.getRetryCount();

        // When
        ExecutionRecord newExecution = retryExecutor.prepareForRetry(original);

        // Then
        assertEquals(originalRetryCount, newExecution.getRetryCount());
    }

    @Test
    void should_preserve_serialized_params() {
        // Given
        SerializedParams params = SerializedParams.of("[\"arg1\",\"arg2\"]");
        ExecutionRecord original = ExecutionRecord.create(
                ExecutionName.of("testExecution"),
                BizKey.of("biz123"),
                params,
                3
        );
        original.fail(ErrorInfo.fromThrowable(new RuntimeException()));
        original.markForRetry();

        // When
        ExecutionRecord newExecution = retryExecutor.prepareForRetry(original);

        // Then
        assertEquals(params.getJsonData(), newExecution.getParams().getJsonData());
    }

    @Test
    void should_preserve_biz_key() {
        // Given
        BizKey bizKey = BizKey.of("order123");
        ExecutionRecord original = ExecutionRecord.create(
                ExecutionName.of("testExecution"),
                bizKey,
                SerializedParams.empty(),
                3
        );
        original.fail(ErrorInfo.fromThrowable(new RuntimeException()));
        original.markForRetry();

        // When
        ExecutionRecord newExecution = retryExecutor.prepareForRetry(original);

        // Then
        assertEquals(bizKey, newExecution.getBizKey());
    }

    @Test
    void should_execute_retry_for_execution() {
        // Given
        ExecutionRecord retryExecution = createRetryExecution();
        repository.save(retryExecution);

        // When
        boolean executed = retryExecutor.executeRetry(retryExecution);

        // Then
        // 注意: 实际重试执行需要反射调用原方法,这里只测试准备阶段
        // 在没有实际方法可调用的情况下，executeRetry应该返回false或抛出异常
        assertFalse(executed);
    }

    @Test
    void should_count_executions_in_retry_queue() {
        // Given
        ExecutionRecord retry1 = createRetryExecution();
        ExecutionRecord retry2 = createRetryExecution();
        repository.save(retry1);
        repository.save(retry2);

        // When
        long count = retryExecutor.countExecutionsInRetryQueue();

        // Then
        assertEquals(2, count);
    }

    private ExecutionRecord createRunningExecution() {
        return ExecutionRecord.create(
                ExecutionName.of("testExecution"),
                BizKey.empty(),
                SerializedParams.empty(),
                3
        );
    }

    private ExecutionRecord createRetryExecution() {
        ExecutionRecord execution = createRunningExecution();
        execution.fail(ErrorInfo.fromThrowable(new RuntimeException("test")));
        execution.markForRetry();
        return execution;
    }
}
