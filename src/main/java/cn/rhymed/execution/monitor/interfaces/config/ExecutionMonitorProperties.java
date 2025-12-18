package cn.rhymed.execution.monitor.interfaces.config;

import cn.rhymed.execution.monitor.common.enums.RecoveryStrategy;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行监控配置属性
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "execution.monitor")
public class ExecutionMonitorProperties {

    /**
     * 是否启用执行监控
     */
    private boolean enabled = true;

    /**
     * 存储类型: memory, redis, database
     */
    private String storageType = "memory";

    /**
     * 心跳配置
     */
    private Heartbeat heartbeat = new Heartbeat();

    /**
     * 重试配置
     */
    private Retry retry = new Retry();

    /**
     * 恢复配置
     */
    private Recovery recovery = new Recovery();

    /**
     * 序列化配置
     */
    private Serialization serialization = new Serialization();

    /**
     * 清理配置
     */
    private Cleanup cleanup = new Cleanup();

    /**
     * 心跳配置
     */
    @Setter
    @Getter
    public static class Heartbeat {
        /**
         * 是否启用心跳监控
         */
        private boolean enabled = false;

        /**
         * 心跳间隔(秒)
         */
        private int intervalSeconds = 60;

        /**
         * 心跳检测频率(秒)
         */
        private int checkIntervalSeconds = 30;

    }

    /**
     * 重试配置
     */
    @Setter
    @Getter
    public static class Retry {
        /**
         * 最大重试次数
         */
        private int maxRetry = 3;

        /**
         * 基础重试间隔(秒)
         */
        private int baseIntervalSeconds = 60;

        /**
         * 是否启用指数退避
         */
        private boolean exponentialBackoff = true;

        /**
         * 可重试的异常类型(类名包含即匹配)
         */
        private List<String> retryableExceptions = new ArrayList<>();

        /**
         * 可忽略的异常类型(类名包含即匹配)
         */
        private List<String> ignorableExceptions = new ArrayList<>();

    }

    /**
     * 恢复配置
     */
    @Setter
    @Getter
    public static class Recovery {
        /**
         * 是否启用自动恢复
         */
        private boolean enabled = false;

        /**
         * 恢复策略: AUTO, ALWAYS, NEVER, CUSTOM
         */
        private RecoveryStrategy strategy = RecoveryStrategy.AUTO;

        /**
         * 启动时是否立即执行恢复检查
         */
        private boolean checkOnStartup = true;

        /**
         * 定期恢复检查间隔(秒), 0表示不启用定期检查
         */
        private int checkIntervalSeconds = 0;

    }

    /**
     * 序列化配置
     */
    @Setter
    @Getter
    public static class Serialization {
        /**
         * 是否启用智能序列化
         */
        private boolean enabled = true;

        /**
         * 参数大小限制(字节), 超过则不序列化
         */
        private int maxSizeBytes = 10240; // 10KB

    }

    /**
     * 清理配置
     */
    @Setter
    @Getter
    public static class Cleanup {
        /**
         * 是否启用自动清理
         */
        private boolean enabled = false;

        /**
         * 保留天数
         */
        private int retentionDays = 7;

        /**
         * 清理间隔(秒)
         */
        private int intervalSeconds = 86400; // 1天

    }
}
