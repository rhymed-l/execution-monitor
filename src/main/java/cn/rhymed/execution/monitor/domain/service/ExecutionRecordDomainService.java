package cn.rhymed.execution.monitor.domain.service;

import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.aggregate.RecoveryPolicy;
import cn.rhymed.execution.monitor.domain.model.BizKey;
import cn.rhymed.execution.monitor.domain.model.ErrorInfo;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import cn.rhymed.execution.monitor.domain.model.SerializedParams;

/**
 * 任务执行领域服务
 * 封装任务执行相关的领域逻辑
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class ExecutionRecordDomainService {

    /**
     * 开始任务
     */
    public ExecutionRecord startExecution(ExecutionName executionName, BizKey bizKey, SerializedParams params, int maxRetry) {
        return ExecutionRecord.create(executionName, bizKey, params, maxRetry);
    }

    /**
     * 完成任务
     */
    public void completeExecution(ExecutionRecord execution) {
        execution.complete();
    }

    /**
     * 失败任务
     */
    public void failExecution(ExecutionRecord execution, Throwable throwable) {
        ErrorInfo errorInfo = ErrorInfo.fromThrowable(throwable);
        execution.fail(errorInfo);
    }

    /**
     * 中断任务
     */
    public void interruptExecution(ExecutionRecord execution) {
        execution.interrupt();
    }

    /**
     * 标记心跳超时
     */
    public void markHeartbeatTimeout(ExecutionRecord execution) {
        execution.markHeartbeatTimeout();
    }

    /**
     * 标记为重试
     */
    public void markForRetry(ExecutionRecord execution) {
        execution.markForRetry();
    }

    /**
     * 判断是否应该重试
     * 综合考虑重试次数、异常类型、恢复策略
     */
    public boolean shouldRetry(ExecutionRecord execution, RecoveryPolicy policy) {
        // 检查是否还能重试
        if (!execution.canRetry()) {
            return false;
        }

        // 获取错误信息
        ErrorInfo errorInfo = execution.getErrorInfo();
        if (errorInfo == null) {
            return false;
        }

        // 可忽略的异常不重试
        if (policy.isIgnorableException(errorInfo)) {
            return false;
        }

        // 如果配置了可重试异常列表,只重试这些异常
        if (policy.getExceptionClassification().hasRetryRules()) {
            return policy.isRetryableException(errorInfo);
        }

        // 默认可以重试
        return true;
    }

    /**
     * 重新开始任务(用于重试)
     */
    public ExecutionRecord restartExecution(ExecutionRecord execution) {
        return execution.restart();
    }

    /**
     * 启用心跳
     */
    public void enableHeartbeat(ExecutionRecord execution, int intervalSeconds) {
        execution.enableHeartbeat(intervalSeconds);
    }

    /**
     * 更新心跳
     */
    public void updateHeartbeat(ExecutionRecord execution) {
        execution.updateHeartbeat();
    }
}
