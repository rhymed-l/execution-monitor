package cn.rhymed.execution.monitor.application.service;

import cn.rhymed.execution.monitor.application.dto.ExecutionLogDTO;
import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * 任务重试执行器
 * 负责执行标记为RETRY状态的任务
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class ExecutionRetryExecutor {

    private final ExecutionRecordRepository repository;
    private final RecoveryHandlerRegistry handlerRegistry;

    public ExecutionRetryExecutor(ExecutionRecordRepository repository, RecoveryHandlerRegistry handlerRegistry) {
        this.repository = repository;
        this.handlerRegistry = handlerRegistry;
    }

    /**
     * 查找准备重试的任务
     */
    public List<ExecutionRecord> findExecutionsReadyForRetry() {
        return repository.findByStatus(ExecutionStatus.RETRY);
    }

    /**
     * 为重试准备新的任务执行
     */
    public ExecutionRecord prepareForRetry(ExecutionRecord original) {
        if (original == null) {
            throw new IllegalArgumentException("原任务不能为空");
        }

        if (original.getStatus() != ExecutionStatus.RETRY) {
            throw new IllegalStateException("只有RETRY状态的任务才能重试");
        }

        // 通过聚合根的restart方法创建新的执行实例
        ExecutionRecord newExecution = original.restart();

        log.debug("准备重试任务: {} -> {}, 重试次数: {}",
                original.getExecutionId(),
                newExecution.getExecutionId(),
                newExecution.getRetryCount());

        return newExecution;
    }

    /**
     * 执行任务重试
     * 支持自定义恢复处理器
     */
    public boolean executeRetry(ExecutionRecord execution) {
        try {
            // 检查是否有自定义恢复处理器
            String name = execution.getExecutionName().getValue();
            Optional<RecoveryHandlerRegistry.HandlerMethod> handler = handlerRegistry.getHandler(name);

            // 使用自定义恢复处理器
            return handler.map(handlerMethod -> executeCustomRecovery(execution, handlerMethod)).orElseGet(() -> executeDefaultRetry(execution));

        } catch (Exception e) {
            log.error("执行任务重试失败: {}", execution.getExecutionId(), e);
            return false;
        }
    }

    /**
     * 执行自定义恢复逻辑
     */
    private boolean executeCustomRecovery(ExecutionRecord execution, RecoveryHandlerRegistry.HandlerMethod handler) {
        try {
            // 转换为DTO传递给自定义处理器
            ExecutionLogDTO executionLog = ExecutionLogDTO.fromExecutionRecord(execution);

            // 调用自定义处理器
            handler.invoke(executionLog);

            // 标记为成功
            execution.complete();
            repository.update(execution);

            log.info("自定义恢复处理器执行成功: {}, handler: {}.{}",
                    execution.getExecutionId(),
                    handler.getBean().getClass().getSimpleName(),
                    handler.getMethod().getName());

            return true;

        } catch (Exception e) {
            log.error("自定义恢复处理器执行失败: {}", execution.getExecutionId(), e);
            return false;
        }
    }

    /**
     * 执行默认重试逻辑
     */
    private boolean executeDefaultRetry(ExecutionRecord execution) {
        try {
            // 准备新的执行
            ExecutionRecord newExecution = prepareForRetry(execution);

            // 保存新的执行记录
            repository.save(newExecution);

            // TODO: 通过反射调用原方法
            // 这需要知道原方法的类、方法名、参数等信息
            // 实际实现中应该通过 ReflectionInvoker 来完成

            log.info("任务重试执行准备完成: {}", newExecution.getExecutionId());
            return true;

        } catch (Exception e) {
            log.error("默认重试逻辑执行失败: {}", execution.getExecutionId(), e);
            return false;
        }
    }

    /**
     * 统计重试队列中的任务数量
     */
    public long countExecutionsInRetryQueue() {
        return repository.countByStatus(ExecutionStatus.RETRY);
    }

    /**
     * 批量执行重试
     */
    public int executeBatchRetry(int batchSize) {
        List<ExecutionRecord> executionsToRetry = findExecutionsReadyForRetry();

        if (executionsToRetry.isEmpty()) {
            return 0;
        }

        int executed = 0;
        int limit = Math.min(executionsToRetry.size(), batchSize);

        for (int i = 0; i < limit; i++) {
            ExecutionRecord execution = executionsToRetry.get(i);
            try {
                if (executeRetry(execution)) {
                    executed++;
                }
            } catch (Exception e) {
                log.error("批量重试失败: {}", execution.getExecutionId(), e);
            }
        }

        log.info("批量重试执行完成: {}/{}", executed, limit);
        return executed;
    }
}
