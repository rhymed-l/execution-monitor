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
     * 失败
     */
    FAILED("失败"),

    /**
     * 中断
     */
    INTERRUPTED("中断"),

    /**
     * 心跳超时
     */
    HEARTBEAT_TIMEOUT("心跳超时"),

    /**
     * 待重试
     */
    RETRY("待重试");

    private final String description;

    ExecutionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
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
                return newStatus == SUCCESS || newStatus == FAILED
                        || newStatus == INTERRUPTED || newStatus == HEARTBEAT_TIMEOUT;
            case INTERRUPTED:
            case HEARTBEAT_TIMEOUT:
                return newStatus == RETRY || newStatus == FAILED;
            case RETRY:
                return newStatus == RUNNING || newStatus == FAILED;
            case FAILED:
            case SUCCESS:
                return false; // 终态不可转换
            default:
                return false;
        }
    }
}
