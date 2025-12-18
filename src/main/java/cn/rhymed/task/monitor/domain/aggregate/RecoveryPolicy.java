package cn.rhymed.task.monitor.domain.aggregate;

import cn.rhymed.task.monitor.common.enums.RecoveryStrategy;
import cn.rhymed.task.monitor.domain.model.ExceptionClassification;
import cn.rhymed.task.monitor.domain.model.RetryConfig;
import cn.rhymed.task.monitor.domain.model.TaskName;
import cn.rhymed.task.monitor.domain.model.ErrorInfo;

import java.io.Serializable;
import java.util.Objects;

/**
 * 恢复策略聚合根
 * 定义特定任务类型的恢复和重试策略
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class RecoveryPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务名称
     * 作为聚合根的唯一标识,关联到具体的任务类型
     */
    private final TaskName taskName;

    /**
     * 恢复策略
     * 定义任务失败后的恢复方式(自动/总是/从不/自定义)
     */
    private final RecoveryStrategy strategy;

    /**
     * 重试配置
     * 包含最大重试次数、重试间隔、退避策略等信息
     */
    private final RetryConfig retryConfig;

    /**
     * 异常分类规则
     * 定义哪些异常可以重试,哪些异常应该忽略
     */
    private final ExceptionClassification exceptionClassification;

    /**
     * 自定义恢复处理器类名
     * 当strategy为CUSTOM时,指定要调用的处理器类
     * 格式为完全限定类名,如: com.example.CustomHandler
     */
    private final String customRecoveryHandler;

    private RecoveryPolicy(Builder builder) {
        this.taskName = builder.taskName;
        this.strategy = builder.strategy;
        this.retryConfig = builder.retryConfig;
        this.exceptionClassification = builder.exceptionClassification;
        this.customRecoveryHandler = builder.customRecoveryHandler;

        validatePolicy();
    }

    /**
     * 创建默认策略
     */
    public static RecoveryPolicy defaultPolicy(TaskName taskName) {
        return new Builder()
                .taskName(taskName)
                .strategy(RecoveryStrategy.AUTO)
                .retryConfig(RetryConfig.defaultConfig())
                .exceptionClassification(ExceptionClassification.empty())
                .build();
    }

    private void validatePolicy() {
        if (strategy == RecoveryStrategy.CUSTOM && customRecoveryHandler == null) {
            throw new IllegalArgumentException("CUSTOM策略必须指定customRecoveryHandler");
        }
    }

    /**
     * 判断任务是否应该被恢复
     */
    public boolean shouldRecover(TaskExecution taskExecution) {
        if (taskExecution == null) {
            return false;
        }

        switch (strategy) {
            case ALWAYS:
                return true;
            case NEVER:
                return false;
            case AUTO:
                return shouldAutoRecover(taskExecution);
            case CUSTOM:
                // 自定义策略需要通过CustomRecoveryHandler判断
                return false;
            default:
                return false;
        }
    }

    /**
     * 自动恢复逻辑
     * 根据异常类型和重试次数判断
     */
    private boolean shouldAutoRecover(TaskExecution taskExecution) {
        ErrorInfo errorInfo = taskExecution.getErrorInfo();

        // 没有错误信息的任务不恢复(如正常完成的任务)
        if (errorInfo == null) {
            return false;
        }

        // 可忽略的异常不恢复
        if (exceptionClassification.isIgnorable(errorInfo)) {
            return false;
        }

        // 如果配置了可重试异常,只恢复这些异常
        if (exceptionClassification.hasRetryRules()) {
            if (!exceptionClassification.isRetryable(errorInfo)) {
                return false;
            }
        }

        // 检查是否还能重试
        return taskExecution.canRetry();
    }

    /**
     * 计算下次重试时间
     */
    public long calculateNextRetryTime(int currentRetryCount, long currentTimeMillis) {
        return retryConfig.calculateNextRetryTime(currentRetryCount, currentTimeMillis);
    }

    /**
     * 判断异常是否可重试
     */
    public boolean isRetryableException(ErrorInfo errorInfo) {
        return exceptionClassification.isRetryable(errorInfo);
    }

    /**
     * 判断异常是否可忽略
     */
    public boolean isIgnorableException(ErrorInfo errorInfo) {
        return exceptionClassification.isIgnorable(errorInfo);
    }

    /**
     * 获取最大重试次数
     */
    public int getMaxRetry() {
        return retryConfig.getMaxRetry();
    }

    // Getters
    public TaskName getTaskName() {
        return taskName;
    }

    public RecoveryStrategy getStrategy() {
        return strategy;
    }

    public RetryConfig getRetryConfig() {
        return retryConfig;
    }

    public ExceptionClassification getExceptionClassification() {
        return exceptionClassification;
    }

    public String getCustomRecoveryHandler() {
        return customRecoveryHandler;
    }

    /**
     * 创建Builder用于修改策略
     */
    public Builder toBuilder() {
        return new Builder()
                .taskName(this.taskName)
                .strategy(this.strategy)
                .retryConfig(this.retryConfig)
                .exceptionClassification(this.exceptionClassification)
                .customRecoveryHandler(this.customRecoveryHandler);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecoveryPolicy that = (RecoveryPolicy) o;
        return Objects.equals(taskName, that.taskName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskName);
    }

    @Override
    public String toString() {
        return "RecoveryPolicy{" +
                "taskName=" + taskName +
                ", strategy=" + strategy +
                ", maxRetry=" + retryConfig.getMaxRetry() +
                '}';
    }

    // Builder
    public static class Builder {
        private TaskName taskName;
        private RecoveryStrategy strategy = RecoveryStrategy.AUTO;
        private RetryConfig retryConfig = RetryConfig.defaultConfig();
        private ExceptionClassification exceptionClassification = ExceptionClassification.empty();
        private String customRecoveryHandler;

        public Builder taskName(TaskName taskName) {
            this.taskName = taskName;
            return this;
        }

        public Builder strategy(RecoveryStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder retryConfig(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        public Builder exceptionClassification(ExceptionClassification exceptionClassification) {
            this.exceptionClassification = exceptionClassification;
            return this;
        }

        public Builder customRecoveryHandler(String customRecoveryHandler) {
            this.customRecoveryHandler = customRecoveryHandler;
            return this;
        }

        public RecoveryPolicy build() {
            if (taskName == null) {
                throw new IllegalArgumentException("taskName不能为空");
            }
            return new RecoveryPolicy(this);
        }
    }
}
