package cn.rhymed.execution.monitor.common.enums;

/**
 * 执行状态枚举
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public enum ExecutionStatus {

    /**
     * 执行中
     */
    RUNNING("执行中"),

    /**
     * 成功
     */
    SUCCESS("成功"),

    /**
     * 失败（最终失败，不可重试）
     */
    FAILED("失败"),

    /**
     * 可重试的失败（还有重试机会）
     */
    RETRYABLE_FAILED("可重试失败"),

    /**
     * 中断
     */
    INTERRUPTED("中断"),

    /**
     * 心跳超时
     */
    HEARTBEAT_TIMEOUT("心跳超时"),

    /**
     * 等待重试
     * 任务已失败但还有重试机会，等待调度器在指定时间执行重试
     */
    AWAITING_RETRY("等待重试");

    private final String description;

    ExecutionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 是否为终态
     * SUCCESS 是真正的终态，FAILED 可以转换为 AWAITING_RETRY
     */
    public boolean isTerminal() {
        return this == SUCCESS;
    }

    /**
     * 是否为活跃状态
     */
    public boolean isActive() {
        return this == RUNNING;
    }

    /**
     * 状态转换合法性检查
     *
     * @param newStatus 新状态
     * @return 是否可以转换
     */
    public boolean canTransitionTo(ExecutionStatus newStatus) {
        switch (this) {
            case RUNNING:
                // RUNNING 可以转换为成功、最终失败、可重试失败、中断、心跳超时
                return newStatus == SUCCESS || newStatus == FAILED || newStatus == RETRYABLE_FAILED
                        || newStatus == INTERRUPTED || newStatus == HEARTBEAT_TIMEOUT;
            case INTERRUPTED:
            case HEARTBEAT_TIMEOUT:
                // 中断和心跳超时可以转换为等待重试或最终失败
                return newStatus == AWAITING_RETRY || newStatus == FAILED;
            case AWAITING_RETRY:
                // 等待重试可以转换为执行中、最终失败、可重试失败
                return newStatus == RUNNING || newStatus == FAILED || newStatus == RETRYABLE_FAILED;
            case RETRYABLE_FAILED:
                // 可重试失败可以转换为等待重试或最终失败
                return newStatus == AWAITING_RETRY || newStatus == FAILED;
            case FAILED:
                // 最终失败是终态，不允许转换（除非手动恢复）
                return false;
            case SUCCESS:
                // 成功是终态，不允许转换
                return false;
            default:
                return false;
        }
    }
}
