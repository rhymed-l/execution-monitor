package cn.rhymed.task.monitor.domain.service;

import cn.rhymed.task.monitor.domain.aggregate.RecoveryPolicy;
import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.domain.model.*;

/**
 * 任务执行领域服务
 * 封装任务执行相关的领域逻辑
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class TaskExecutionDomainService {

    /**
     * 开始任务
     */
    public TaskExecution startTask(TaskName taskName, BizKey bizKey, SerializedParams params, int maxRetry) {
        return TaskExecution.create(taskName, bizKey, params, maxRetry);
    }

    /**
     * 完成任务
     */
    public void completeTask(TaskExecution execution) {
        execution.complete();
    }

    /**
     * 失败任务
     */
    public void failTask(TaskExecution execution, Throwable throwable) {
        ErrorInfo errorInfo = ErrorInfo.fromThrowable(throwable);
        execution.fail(errorInfo);
    }

    /**
     * 中断任务
     */
    public void interruptTask(TaskExecution execution) {
        execution.interrupt();
    }

    /**
     * 标记心跳超时
     */
    public void markHeartbeatTimeout(TaskExecution execution) {
        execution.markHeartbeatTimeout();
    }

    /**
     * 标记为重试
     */
    public void markForRetry(TaskExecution execution) {
        execution.markForRetry();
    }

    /**
     * 判断是否应该重试
     * 综合考虑重试次数、异常类型、恢复策略
     */
    public boolean shouldRetry(TaskExecution execution, RecoveryPolicy policy) {
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
    public TaskExecution restartTask(TaskExecution execution) {
        return execution.restart();
    }

    /**
     * 启用心跳
     */
    public void enableHeartbeat(TaskExecution execution, int intervalSeconds) {
        execution.enableHeartbeat(intervalSeconds);
    }

    /**
     * 更新心跳
     */
    public void updateHeartbeat(TaskExecution execution) {
        execution.updateHeartbeat();
    }
}
