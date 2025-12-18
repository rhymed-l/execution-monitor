package cn.rhymed.task.monitor.interfaces.config;

import cn.rhymed.task.monitor.common.enums.RecoveryStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务监控配置属性
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@ConfigurationProperties(prefix = "task.monitor")
public class TaskMonitorProperties {

    /**
     * 是否启用任务监控
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public Heartbeat getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(Heartbeat heartbeat) {
        this.heartbeat = heartbeat;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public Recovery getRecovery() {
        return recovery;
    }

    public void setRecovery(Recovery recovery) {
        this.recovery = recovery;
    }

    public Serialization getSerialization() {
        return serialization;
    }

    public void setSerialization(Serialization serialization) {
        this.serialization = serialization;
    }

    public Cleanup getCleanup() {
        return cleanup;
    }

    public void setCleanup(Cleanup cleanup) {
        this.cleanup = cleanup;
    }

    /**
     * 心跳配置
     */
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getIntervalSeconds() {
            return intervalSeconds;
        }

        public void setIntervalSeconds(int intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }

        public int getCheckIntervalSeconds() {
            return checkIntervalSeconds;
        }

        public void setCheckIntervalSeconds(int checkIntervalSeconds) {
            this.checkIntervalSeconds = checkIntervalSeconds;
        }
    }

    /**
     * 重试配置
     */
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

        public int getMaxRetry() {
            return maxRetry;
        }

        public void setMaxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
        }

        public int getBaseIntervalSeconds() {
            return baseIntervalSeconds;
        }

        public void setBaseIntervalSeconds(int baseIntervalSeconds) {
            this.baseIntervalSeconds = baseIntervalSeconds;
        }

        public boolean isExponentialBackoff() {
            return exponentialBackoff;
        }

        public void setExponentialBackoff(boolean exponentialBackoff) {
            this.exponentialBackoff = exponentialBackoff;
        }

        public List<String> getRetryableExceptions() {
            return retryableExceptions;
        }

        public void setRetryableExceptions(List<String> retryableExceptions) {
            this.retryableExceptions = retryableExceptions;
        }

        public List<String> getIgnorableExceptions() {
            return ignorableExceptions;
        }

        public void setIgnorableExceptions(List<String> ignorableExceptions) {
            this.ignorableExceptions = ignorableExceptions;
        }
    }

    /**
     * 恢复配置
     */
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RecoveryStrategy getStrategy() {
            return strategy;
        }

        public void setStrategy(RecoveryStrategy strategy) {
            this.strategy = strategy;
        }

        public boolean isCheckOnStartup() {
            return checkOnStartup;
        }

        public void setCheckOnStartup(boolean checkOnStartup) {
            this.checkOnStartup = checkOnStartup;
        }

        public int getCheckIntervalSeconds() {
            return checkIntervalSeconds;
        }

        public void setCheckIntervalSeconds(int checkIntervalSeconds) {
            this.checkIntervalSeconds = checkIntervalSeconds;
        }
    }

    /**
     * 序列化配置
     */
    public static class Serialization {
        /**
         * 是否启用智能序列化
         */
        private boolean enabled = true;

        /**
         * 参数大小限制(字节), 超过则不序列化
         */
        private int maxSizeBytes = 10240; // 10KB

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxSizeBytes() {
            return maxSizeBytes;
        }

        public void setMaxSizeBytes(int maxSizeBytes) {
            this.maxSizeBytes = maxSizeBytes;
        }
    }

    /**
     * 清理配置
     */
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public int getIntervalSeconds() {
            return intervalSeconds;
        }

        public void setIntervalSeconds(int intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }
    }
}
