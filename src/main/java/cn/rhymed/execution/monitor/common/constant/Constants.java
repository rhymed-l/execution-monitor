package cn.rhymed.execution.monitor.common.constant;

/**
 * 常量定义
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public final class Constants {

    private Constants() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }

    /**
     * 默认值
     */
    public static final class Defaults {
        public static final int MAX_RETRY = 3;
        public static final int BASE_INTERVAL_SECONDS = 60;
        public static final int HEARTBEAT_INTERVAL_SECONDS = 60;
        public static final int MAX_PARAM_SIZE_BYTES = 10240; // 10KB
        public static final int RETENTION_DAYS = 7;
    }

    /**
     * 限制值
     */
    public static final class Limits {
        public static final int MAX_TASK_NAME_LENGTH = 200;
        public static final int MAX_BIZ_KEY_LENGTH = 200;
        public static final int MAX_STACK_TRACE_LENGTH = 2000;
        public static final int MAX_PARAM_SIZE_BYTES = 1048576; // 1MB
    }

    /**
     * 存储类型
     */
    public static final class StorageType {
        public static final String MEMORY = "memory";
        public static final String REDIS = "redis";
        public static final String DATABASE = "database";
    }

    /**
     * 事件主题
     */
    public static final class EventTopic {
        public static final String TASK_STARTED = "execution.started";
        public static final String TASK_COMPLETED = "execution.completed";
        public static final String TASK_FAILED = "execution.failed";
        public static final String TASK_INTERRUPTED = "execution.interrupted";
        public static final String HEARTBEAT_TIMEOUT = "heartbeat.timeout";
        public static final String TASK_RETRY = "execution.retry";
        public static final String TASK_RECOVERED = "execution.recovered";
    }

    /**
     * 配置键
     */
    public static final class ConfigKey {
        public static final String PREFIX = "execution.monitor";
        public static final String ENABLED = PREFIX + ".enabled";
        public static final String STORAGE_TYPE = PREFIX + ".storage-type";
        public static final String HEARTBEAT_ENABLED = PREFIX + ".heartbeat.enabled";
        public static final String RECOVERY_ENABLED = PREFIX + ".recovery.enabled";
    }
}
