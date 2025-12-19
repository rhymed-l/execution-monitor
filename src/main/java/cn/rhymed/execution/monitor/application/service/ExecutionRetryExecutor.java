package cn.rhymed.execution.monitor.application.service;

import cn.rhymed.execution.monitor.application.dto.ExecutionLogDTO;
import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.ErrorInfo;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.domain.service.ExecutionLockService;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertDispatcher;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
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
    /**
     * 每次批量处理的最大任务数
     * 避免一次性查询太多数据导致内存问题
     */
    private static final int BATCH_SIZE = 100;
    /**
     * 批量处理锁的ID（全局唯一）
     * 用于防止多个实例同时进行批量重试扫描
     */
    private static final String BATCH_PROCESSING_LOCK_ID = "BATCH_RETRY_PROCESSING";
    private final ExecutionLockService lockService;
    private final AlertDispatcher alertDispatcher; // 可选的告警分发器

    public ExecutionRetryExecutor(ExecutionRecordRepository repository,
                                  RecoveryHandlerRegistry handlerRegistry,
                                  ExecutionLockService lockService,
                                  AlertDispatcher alertDispatcher) {
        this.repository = repository;
        this.handlerRegistry = handlerRegistry;
        this.lockService = lockService;
        this.alertDispatcher = alertDispatcher;

        if (alertDispatcher == null) {
            log.warn("⚠️  告警服务未配置！任务失败时将只记录日志，不会发送告警通知。" +
                    "建议配置 execution.monitor.alert.dingtalk.enabled=true 或 execution.monitor.alert.feishu.enabled=true 以接收告警。");
        }
    }

    /**
     * 查找准备重试的任务
     * 只返回状态为 AWAITING_RETRY 且 nextRetryTime 已到达的任务
     */
    public List<ExecutionRecord> findExecutionsReadyForRetry() {
        return repository.findReadyForRetry(LocalDateTime.now());
    }

    /**
     * 为重试准备新的任务执行
     */
    public ExecutionRecord prepareForRetry(ExecutionRecord original) {
        if (original == null) {
            throw new IllegalArgumentException("原任务不能为空");
        }

        if (original.getStatus() != ExecutionStatus.AWAITING_RETRY) {
            throw new IllegalStateException("只有AWAITING_RETRY状态的任务才能重试");
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
     * 执行任务重试
     * 支持自定义恢复处理器
     */
    public boolean executeRetry(ExecutionRecord execution) {
        try {
            // 在开始重试前递增 retryCount
            // retryCount 表示"正在进行第几次执行"（0=首次，1=第1次重试，2=第2次重试）
            int retryCountBeforeIncrement = execution.getRetryCount();
            if (execution.getStatus() == ExecutionStatus.AWAITING_RETRY) {
                execution.incrementRetryCount();
                repository.update(execution);
                log.debug("开始执行重试: executionId={}, retryCount从{}递增到{}",
                        execution.getExecutionId(), retryCountBeforeIncrement, execution.getRetryCount());
            }

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
     * 执行默认重试逻辑（通过反射自动调用原方法）
     */
    private boolean executeDefaultRetry(ExecutionRecord execution) {
        try {
            // 检查是否有方法元信息
            if (execution.getMethodMetadata() == null || execution.getMethodMetadata().isEmpty()) {
                log.warn("任务缺少方法元信息，无法自动重试: {}", execution.getExecutionId());
                return false;
            }

            // 通过反射调用原方法
            cn.rhymed.execution.monitor.infrastructure.util.ReflectionInvoker.invokeByMetadata(
                    execution.getMethodMetadata(),
                    execution.getParams()
            );

            // 标记为成功
            execution.complete();
            repository.update(execution);

            log.info("自动重试执行成功: {}", execution.getExecutionId());
            return true;

        } catch (Exception e) {
            log.error("自动重试执行失败: {}", execution.getExecutionId(), e);

            // 获取真实的异常（如果是包装异常，则取出cause）
            // 每次重试失败都记录当前的真实异常，不使用通用错误消息
            Throwable rootCause = getRootCause(e);
            cn.rhymed.execution.monitor.domain.model.ErrorInfo errorInfo =
                    new cn.rhymed.execution.monitor.domain.model.ErrorInfo(
                            rootCause.getMessage(),
                            rootCause.getClass().getName(),
                            getStackTrace(rootCause)
                    );

            // 判断是否还有重试机会
            // retryCount 表示当前执行的次数（0=首次，1=第1次重试，2=第2次重试）
            int currentRetryCount = execution.getRetryCount();
            boolean hasMoreRetries = currentRetryCount < execution.getMaxRetry();

            if (hasMoreRetries) {
                // 还有重试机会，标记为可重试失败
                execution.retryableFail(errorInfo);

                log.warn("任务重试失败，还有重试机会: executionId={}, retryCount={}, 下次将是第{}次重试（最多{}次）, 异常: {}: {}",
                        execution.getExecutionId(),
                        currentRetryCount,
                        currentRetryCount + 1,
                        execution.getMaxRetry(),
                        errorInfo.getExceptionType(),
                        errorInfo.getErrorMessage());

                // 发送可重试失败告警
                sendRetryableFailureAlert(execution, currentRetryCount);

                // 标记为重试状态
                execution.markForRetry();
                repository.update(execution);
            } else {
                // 没有重试机会了，标记为最终失败
                execution.fail(errorInfo);
                repository.update(execution);

                log.error("任务达到最大重试次数，最终失败: executionId={}, retryCount={}/{}, 最终异常: {}: {}",
                        execution.getExecutionId(),
                        execution.getRetryCount(),
                        execution.getMaxRetry(),
                        errorInfo.getExceptionType(),
                        errorInfo.getErrorMessage());

                // 发送最终失败告警（级别最高）
                sendFinalFailureAlert(execution);
            }

            return false;
        }
    }

    /**
     * 获取根异常
     * 如果异常被包装（如RuntimeException），则返回最底层的真实异常
     */
    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * 获取异常堆栈
     */
    private String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        java.io.StringWriter sw = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(sw));
        String trace = sw.toString();
        return trace.length() > 2000 ? trace.substring(0, 2000) : trace;
    }

    /**
     * 统计重试队列中的任务数量
     */
    public long countExecutionsInRetryQueue() {
        return repository.countByStatus(ExecutionStatus.AWAITING_RETRY);
    }

    /**
     * 批量执行重试（统一的批量处理方法）
     * 每次固定处理 100 条，如果还有更多待处理任务，下次轮询会继续处理
     * <p>
     * 使用全局批处理锁，确保同一时刻只有一个实例在扫描和处理任务
     *
     * @return 成功执行的任务数量
     */
    public int processBatchRetry() {
        // 尝试获取全局批处理锁
        ExecutionId batchLockId = ExecutionId.of(BATCH_PROCESSING_LOCK_ID);
        boolean batchLockAcquired = false;

        try {
            batchLockAcquired = lockService.tryLock(batchLockId);

            if (!batchLockAcquired) {
                log.debug("全局批处理锁已被其他实例持有，跳过本次扫描");
                return 0;
            }

            log.debug("成功获取全局批处理锁，开始处理任务");

            // 执行批量重试
            return executeBatchRetry(BATCH_SIZE);

        } finally {
            // 释放全局批处理锁
            if (batchLockAcquired) {
                try {
                    lockService.unlock(batchLockId);
                    log.debug("释放全局批处理锁");
                } catch (Exception e) {
                    log.error("释放全局批处理锁失败", e);
                }
            }
        }
    }

    /**
     * 批量执行重试（内部方法）
     * 由于已有全局批处理锁，此方法中不再对单个任务加锁
     */
    private int executeBatchRetry(int batchSize) {
        List<ExecutionRecord> executionsToRetry = findExecutionsReadyForRetry();

        if (executionsToRetry.isEmpty()) {
            log.debug("没有需要重试的任务");
            return 0;
        }

        int executed = 0;
        int failed = 0;
        int skipped = 0;
        int limit = Math.min(executionsToRetry.size(), batchSize);

        log.info("开始批量重试，待处理任务数: {}, 本次处理: {}", executionsToRetry.size(), limit);

        for (int i = 0; i < limit; i++) {
            ExecutionRecord execution = executionsToRetry.get(i);

            try {
                // 检查是否还能重试（防止无限循环）
                if (!execution.canRetry()) {
                    log.warn("任务 {} 已达到最大重试次数 {}/{}，标记为最终失败",
                            execution.getExecutionId(),
                            execution.getRetryCount(),
                            execution.getMaxRetry());

                    // 标记为最终失败，保留原始的错误信息
                    // 注意：这里的 execution 应该已经有 errorInfo（之前失败时记录的）
                    // 如果没有 errorInfo（理论上不应该发生），则创建一个兜底的错误信息
                    if (execution.getErrorInfo() != null) {
                        execution.fail(execution.getErrorInfo());
                    } else {
                        execution.fail(new ErrorInfo(
                                "已达到最大重试次数，但未记录原始异常",
                                "MaxRetryExceeded",
                                null
                        ));
                    }
                    repository.update(execution);

                    // 发送最终失败告警
                    sendFinalFailureAlert(execution);

                    skipped++;
                    continue;
                }

                // 执行重试
                if (executeRetry(execution)) {
                    executed++;
                } else {
                    failed++;
                }

            } catch (Exception e) {
                failed++;
                log.error("批量重试异常: {}", execution.getExecutionId(), e);
            }
        }

        log.info("批量重试完成 - 成功: {}, 失败: {}, 跳过: {}, 总计: {}",
                executed, failed, skipped, limit);
        return executed;
    }

    /**
     * 发送可重试失败告警（还有重试机会）
     * 这是一个较低级别的告警，表示任务暂时失败但还会继续重试
     *
     * @param execution         执行记录
     * @param currentRetryCount 当前已完成的重试次数（首次执行失败为0，第1次重试失败为1，以此类推）
     */
    private void sendRetryableFailureAlert(ExecutionRecord execution, int currentRetryCount) {
        try {
            if (alertDispatcher != null) {
                alertDispatcher.sendRetryAlert(execution, currentRetryCount);
            } else {
                // 如果没有配置告警服务，打印WARN级别日志
                log.warn("⚠️  【告警】任务重试失败（还有重试机会） - executionId: {}, executionName: {}, " +
                                "retryCount: {}/{}, error: {}: {}",
                        execution.getExecutionId().getValue(),
                        execution.getExecutionName().getValue(),
                        currentRetryCount,
                        execution.getMaxRetry(),
                        execution.getErrorInfo() != null ? execution.getErrorInfo().getExceptionType() : "Unknown",
                        execution.getErrorInfo() != null ? execution.getErrorInfo().getErrorMessage() : "No error message");
            }
        } catch (Exception e) {
            log.error("发送可重试失败告警异常", e);
        }
    }

    /**
     * 发送最终失败告警（没有重试机会了）
     * 这是最高级别的告警，表示任务已达到最大重试次数，最终失败
     */
    private void sendFinalFailureAlert(ExecutionRecord execution) {
        try {
            if (alertDispatcher != null) {
                alertDispatcher.sendFailureAlert(execution);
            } else {
                // 如果没有配置告警服务，打印ERROR级别日志
                log.error("⚠️  【告警】任务最终失败（无重试机会） - executionId: {}, executionName: {}, " +
                                "retryCount: {}/{}, error: {}: {}. " +
                                "建议配置告警服务以接收实时通知！",
                        execution.getExecutionId().getValue(),
                        execution.getExecutionName().getValue(),
                        execution.getRetryCount(),
                        execution.getMaxRetry(),
                        execution.getErrorInfo() != null ? execution.getErrorInfo().getExceptionType() : "Unknown",
                        execution.getErrorInfo() != null ? execution.getErrorInfo().getErrorMessage() : "No error message");
            }
        } catch (Exception e) {
            log.error("发送最终失败告警异常", e);
        }
    }
}
