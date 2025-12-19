package cn.rhymed.execution.monitor.infrastructure.scheduler;

import cn.rhymed.execution.monitor.application.service.ExecutionRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 重试任务调度器
 * 定期检查并执行重试队列中的任务，每次固定处理 100 条
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class RetryExecutionScheduler {

    private final ExecutionRetryExecutor retryExecutor;

    public RetryExecutionScheduler(ExecutionRetryExecutor retryExecutor) {
        this.retryExecutor = retryExecutor;
    }

    /**
     * 定期执行重试任务
     * 默认每 5 分钟执行一次，可通过配置调整
     */
    @Scheduled(
            fixedDelayString = "${execution.monitor.retry.scheduled-interval-ms:300000}",
            initialDelayString = "${execution.monitor.retry.scheduled-initial-delay-ms:60000}"
    )
    public void processRetryQueue() {
        log.debug("定时扫描待重试任务开始...");

        try {
            // 调用统一的批量处理方法（固定 100 条）
            int executed = retryExecutor.processBatchRetry();

            if (executed > 0) {
                log.info("定时重试完成，成功处理 {} 个任务", executed);
            }

            // 检查是否还有待处理的任务
            long remaining = retryExecutor.countExecutionsInRetryQueue();
            if (remaining > 0) {
                log.debug("仍有 {} 个任务待重试", remaining);
            }

        } catch (Exception e) {
            log.error("定时重试调度失败", e);
        }
    }

    /**
     * 手动触发重试处理(用于测试)
     */
    public void triggerRetryProcessing() {
        processRetryQueue();
    }
}
