package cn.rhymed.task.monitor.application.service;

import cn.rhymed.task.monitor.application.dto.TaskLogDTO;
import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
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
public class TaskRetryExecutor {

    private final TaskExecutionRepository repository;
    private final RecoveryHandlerRegistry handlerRegistry;

    public TaskRetryExecutor(TaskExecutionRepository repository, RecoveryHandlerRegistry handlerRegistry) {
        this.repository = repository;
        this.handlerRegistry = handlerRegistry;
    }

    /**
     * 查找准备重试的任务
     */
    public List<TaskExecution> findTasksReadyForRetry() {
        return repository.findByStatus(TaskStatus.RETRY);
    }

    /**
     * 为重试准备新的任务执行
     */
    public TaskExecution prepareForRetry(TaskExecution original) {
        if (original == null) {
            throw new IllegalArgumentException("原任务不能为空");
        }

        if (original.getStatus() != TaskStatus.RETRY) {
            throw new IllegalStateException("只有RETRY状态的任务才能重试");
        }

        // 通过聚合根的restart方法创建新的执行实例
        TaskExecution newExecution = original.restart();

        log.debug("准备重试任务: {} -> {}, 重试次数: {}",
                original.getTaskId(),
                newExecution.getTaskId(),
                newExecution.getRetryCount());

        return newExecution;
    }

    /**
     * 执行任务重试
     * 支持自定义恢复处理器
     */
    public boolean executeRetry(TaskExecution execution) {
        try {
            // 检查是否有自定义恢复处理器
            String taskName = execution.getTaskName().getValue();
            Optional<RecoveryHandlerRegistry.HandlerMethod> handler = handlerRegistry.getHandler(taskName);

            if (handler.isPresent()) {
                // 使用自定义恢复处理器
                return executeCustomRecovery(execution, handler.get());
            } else {
                // 使用默认重试逻辑
                return executeDefaultRetry(execution);
            }

        } catch (Exception e) {
            log.error("执行任务重试失败: {}", execution.getTaskId(), e);
            return false;
        }
    }

    /**
     * 执行自定义恢复逻辑
     */
    private boolean executeCustomRecovery(TaskExecution execution, RecoveryHandlerRegistry.HandlerMethod handler) {
        try {
            // 转换为DTO传递给自定义处理器
            TaskLogDTO taskLog = TaskLogDTO.fromTaskExecution(execution);

            // 调用自定义处理器
            handler.invoke(taskLog);

            // 标记任务为成功
            execution.complete();
            repository.update(execution);

            log.info("自定义恢复处理器执行成功: {}, handler: {}.{}",
                    execution.getTaskId(),
                    handler.getBean().getClass().getSimpleName(),
                    handler.getMethod().getName());

            return true;

        } catch (Exception e) {
            log.error("自定义恢复处理器执行失败: {}", execution.getTaskId(), e);
            return false;
        }
    }

    /**
     * 执行默认重试逻辑
     */
    private boolean executeDefaultRetry(TaskExecution execution) {
        try {
            // 准备新的执行
            TaskExecution newExecution = prepareForRetry(execution);

            // 保存新的执行记录
            repository.save(newExecution);

            // TODO: 通过反射调用原方法
            // 这需要知道原方法的类、方法名、参数等信息
            // 实际实现中应该通过 ReflectionInvoker 来完成

            log.info("任务重试执行准备完成: {}", newExecution.getTaskId());
            return true;

        } catch (Exception e) {
            log.error("默认重试逻辑执行失败: {}", execution.getTaskId(), e);
            return false;
        }
    }

    /**
     * 统计重试队列中的任务数量
     */
    public long countTasksInRetryQueue() {
        return repository.countByStatus(TaskStatus.RETRY);
    }

    /**
     * 批量执行重试
     */
    public int executeBatchRetry(int batchSize) {
        List<TaskExecution> tasksToRetry = findTasksReadyForRetry();

        if (tasksToRetry.isEmpty()) {
            return 0;
        }

        int executed = 0;
        int limit = Math.min(tasksToRetry.size(), batchSize);

        for (int i = 0; i < limit; i++) {
            TaskExecution task = tasksToRetry.get(i);
            try {
                if (executeRetry(task)) {
                    executed++;
                }
            } catch (Exception e) {
                log.error("批量重试失败: {}", task.getTaskId(), e);
            }
        }

        log.info("批量重试执行完成: {}/{}", executed, limit);
        return executed;
    }
}
