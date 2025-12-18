package cn.rhymed.task.monitor.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;

/**
 * 重试配置值对象
 * 封装任务重试的规则和时间计算逻辑
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
@ToString
@EqualsAndHashCode
public class RetryConfig implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_MAX_RETRY = 3;
    private static final int DEFAULT_BASE_INTERVAL_SECONDS = 60;
    private static final boolean DEFAULT_EXPONENTIAL_BACKOFF = true;

    /**
     * 最大重试次数
     * 任务失败后允许重试的最大次数
     * 0表示不重试
     */
    private final int maxRetry;

    /**
     * 基础重试间隔(秒)
     * 第一次重试的等待时间
     * 在指数退避模式下,后续重试间隔会基于此值递增
     */
    private final int baseIntervalSeconds;

    /**
     * 是否启用指数退避
     * true: 重试间隔按指数递增 (baseInterval * 2^retryCount)
     * false: 每次重试间隔固定为baseInterval
     */
    private final boolean exponentialBackoff;

    public RetryConfig(int maxRetry, int baseIntervalSeconds, boolean exponentialBackoff) {
        if (maxRetry < 0) {
            throw new IllegalArgumentException("maxRetry不能小于0");
        }
        if (baseIntervalSeconds <= 0) {
            throw new IllegalArgumentException("baseIntervalSeconds必须大于0");
        }
        this.maxRetry = maxRetry;
        this.baseIntervalSeconds = baseIntervalSeconds;
        this.exponentialBackoff = exponentialBackoff;
    }

    public static RetryConfig defaultConfig() {
        return new RetryConfig(DEFAULT_MAX_RETRY, DEFAULT_BASE_INTERVAL_SECONDS, DEFAULT_EXPONENTIAL_BACKOFF);
    }

    public static RetryConfig of(int maxRetry, int baseIntervalSeconds, boolean exponentialBackoff) {
        return new RetryConfig(maxRetry, baseIntervalSeconds, exponentialBackoff);
    }

    /**
     * 判断是否还能重试
     */
    public boolean canRetry(int currentRetryCount) {
        return currentRetryCount < maxRetry;
    }

    /**
     * 计算下次重试的时间间隔(秒)
     * 支持指数退避策略
     */
    public long calculateNextRetryInterval(int currentRetryCount) {
        if (currentRetryCount < 0) {
            throw new IllegalArgumentException("currentRetryCount不能小于0");
        }

        if (!exponentialBackoff) {
            return baseIntervalSeconds;
        }

        // 指数退避: baseInterval * (2 ^ retryCount)
        // 使用Math.min防止溢出
        long interval = baseIntervalSeconds;
        for (int i = 0; i < currentRetryCount && interval < Integer.MAX_VALUE / 2; i++) {
            interval *= 2;
        }
        return interval;
    }

    /**
     * 计算下次重试的时间戳(毫秒)
     */
    public long calculateNextRetryTime(int currentRetryCount, long currentTimeMillis) {
        long intervalSeconds = calculateNextRetryInterval(currentRetryCount);
        return currentTimeMillis + (intervalSeconds * 1000);
    }

}
