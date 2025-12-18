package cn.rhymed.task.monitor.infrastructure.scheduler;

import cn.rhymed.task.monitor.application.service.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 重试任务调度器
 * 定期检查并执行重试队列中的任务
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class RetryTaskScheduler {
    private static final int DEFAULT_BATCH_SIZE = 10;

    private final TaskRetryExecutor retryExecutor;
    private final int batchSize;

    public RetryTaskScheduler(TaskRetryExecutor retryExecutor) {
        this(retryExecutor, DEFAULT_BATCH_SIZE);
    }

    public RetryTaskScheduler(TaskRetryExecutor retryExecutor, int batchSize) {
        this.retryExecutor = retryExecutor;
        this.batchSize = batchSize;
    }

    /**
     * 定期执行重试任务
     * 默认每分钟执行一次
     */
    @Scheduled(fixedDelayString = "${task.monitor.retry.check-interval-seconds:60}000")
    public void processRetryQueue() {
        try {
            long queueSize = retryExecutor.countTasksInRetryQueue();

            if (queueSize == 0) {
                log.debug("重试队列为空");
                return;
            }

            log.info("开始处理重试队列, 队列大小: {}", queueSize);

            int executed = retryExecutor.executeBatchRetry(batchSize);

            log.info("重试任务处理完成, 执行: {}, 批次大小: {}", executed, batchSize);

        } catch (Exception e) {
            log.error("处理重试队列失败", e);
        }
    }

    /**
     * 手动触发重试处理(用于测试)
     */
    public void triggerRetryProcessing() {
        processRetryQueue();
    }
}
