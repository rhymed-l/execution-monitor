package cn.rhymed.task.monitor.common.enums;

/**
 * 恢复策略枚举
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public enum RecoveryStrategy {

    /**
     * 自动判断
     * - 无参数或参数可序列化 → 自动恢复
     * - 参数无法序列化 → 仅告警
     */
    AUTO("自动判断"),

    /**
     * 总是尝试恢复
     * 如果参数无法序列化,会调用自定义恢复处理器
     */
    ALWAYS("总是恢复"),

    /**
     * 从不恢复,仅告警
     */
    NEVER("从不恢复"),

    /**
     * 使用自定义恢复处理器
     */
    CUSTOM("自定义处理器");

    private final String description;

    RecoveryStrategy(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
