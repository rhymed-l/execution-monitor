package cn.rhymed.execution.monitor.infrastructure.scheduler;

import cn.rhymed.execution.monitor.application.service.HeartbeatManagementService;
import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.domain.service.ExecutionRecordDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * 健康检查调度器
 * 定期检查任务心跳超时情况
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class HealthCheckScheduler {

    private final HeartbeatManagementService heartbeatService;
    private final ExecutionRecordRepository executionRepository;
    private final ExecutionRecordDomainService domainService;

    public HealthCheckScheduler(HeartbeatManagementService heartbeatService,
                                ExecutionRecordRepository executionRepository,
                                ExecutionRecordDomainService domainService) {
        this.heartbeatService = heartbeatService;
        this.executionRepository = executionRepository;
        this.domainService = domainService;
    }

    /**
     * 定期检查健康状态
     * 默认每30秒执行一次
     */
    @Scheduled(fixedDelayString = "${execution.monitor.heartbeat.check-interval-seconds:30}000")
    public void checkHealthStatus() {
        try {
            List<ExecutionId> timeoutExecutions = heartbeatService.checkForTimeoutExecutions();

            if (timeoutExecutions.isEmpty()) {
                return;
            }

            log.warn("检测到 {} 个心跳超时任务", timeoutExecutions.size());

            for (ExecutionId executionId : timeoutExecutions) {
                handleTimeoutExecution(executionId);
            }
        } catch (Exception e) {
            log.error("健康检查失败", e);
        }
    }

    /**
     * 处理超时任务
     */
    private void handleTimeoutExecution(ExecutionId executionId) {
        try {
            ExecutionRecord execution = executionRepository.findById(executionId).orElse(null);
            if (execution == null) {
                log.warn("超时任务不存在: {}", executionId);
                heartbeatService.removeHeartbeat(executionId);
                return;
            }

            // 只处理运行中的任务
            if (execution.getStatus() != ExecutionStatus.RUNNING) {
                log.debug("任务状态不是RUNNING,跳过: {}, 状态: {}", executionId, execution.getStatus());
                heartbeatService.removeHeartbeat(executionId);
                return;
            }

            // 标记为心跳超时
            log.warn("任务心跳超时: {}, 任务名: {}", executionId, execution.getExecutionName());
            domainService.markHeartbeatTimeout(execution);
            executionRepository.update(execution);

            // 移除心跳记录
            heartbeatService.removeHeartbeat(executionId);

        } catch (Exception e) {
            log.error("处理超时任务失败: {}", executionId, e);
        }
    }

    /**
     * 手动触发健康检查(用于测试)
     */
    public void triggerHealthCheck() {
        checkHealthStatus();
    }
}
