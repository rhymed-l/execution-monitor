package cn.rhymed.execution.monitor.domain.service;

import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.common.enums.RecoveryStrategy;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.aggregate.RecoveryPolicy;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 恢复决策服务
 * 判断任务是否应该被恢复
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class RecoveryDecisionService {

    /**
     * 可恢复的任务状态
     */
    private static final Set<ExecutionStatus> RECOVERABLE_STATUSES = new HashSet<>(Arrays.asList(
            ExecutionStatus.INTERRUPTED,
            ExecutionStatus.HEARTBEAT_TIMEOUT,
            ExecutionStatus.FAILED,
            ExecutionStatus.RETRY
    ));

    /**
     * 判断任务是否应该恢复
     */
    public boolean shouldRecover(ExecutionRecord execution, RecoveryPolicy policy) {
        if (execution == null || policy == null) {
            return false;
        }

        // 根据策略判断
        RecoveryStrategy strategy = policy.getStrategy();
        switch (strategy) {
            case ALWAYS:
                return shouldAlwaysRecover(execution);
            case NEVER:
                return false;
            case AUTO:
                return shouldAutoRecover(execution, policy);
            case CUSTOM:
                // 自定义策略需要外部处理器判断
                return false;
            default:
                return false;
        }
    }

    /**
     * ALWAYS策略: 只要任务未完成就恢复
     */
    private boolean shouldAlwaysRecover(ExecutionRecord execution) {
        // 已成功的任务不恢复
        if (execution.getStatus() == ExecutionStatus.SUCCESS) {
            return false;
        }

        // 运行中的任务不恢复(可能正在执行)
        if (execution.getStatus() == ExecutionStatus.RUNNING) {
            return false;
        }

        // 检查是否还能重试
        return execution.canRetry();
    }

    /**
     * AUTO策略: 智能判断
     */
    private boolean shouldAutoRecover(ExecutionRecord execution, RecoveryPolicy policy) {
        // 检查状态是否可恢复
        if (!isRecoverableStatus(execution.getStatus())) {
            return false;
        }

        // 检查是否还能重试
        if (!execution.canRetry()) {
            return false;
        }

        // 如果有错误信息,检查异常分类
        if (execution.getErrorInfo() != null) {
            // 可忽略的异常不恢复
            if (policy.isIgnorableException(execution.getErrorInfo())) {
                return false;
            }

            // 如果配置了可重试异常,只恢复这些异常
            if (policy.getExceptionClassification().hasRetryRules()) {
                return policy.isRetryableException(execution.getErrorInfo());
            }
        }

        return true;
    }

    /**
     * 判断状态是否可恢复
     */
    public boolean isRecoverableStatus(ExecutionStatus status) {
        return RECOVERABLE_STATUSES.contains(status);
    }

    /**
     * 计算下次重试时间
     */
    public long calculateNextRetryTime(ExecutionRecord execution, RecoveryPolicy policy, long currentTimeMillis) {
        return policy.calculateNextRetryTime(execution.getRetryCount(), currentTimeMillis);
    }

    /**
     * 判断是否到达重试时间
     */
    public boolean isRetryTimeReached(long nextRetryTime) {
        return System.currentTimeMillis() >= nextRetryTime;
    }
}
