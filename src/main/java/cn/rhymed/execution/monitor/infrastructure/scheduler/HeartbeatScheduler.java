package cn.rhymed.execution.monitor.infrastructure.scheduler;

import cn.rhymed.execution.monitor.application.service.HeartbeatManagementService;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import lombok.extern.slf4j.Slf4j;

/**
 * 心跳调度器
 * 定期更新正在运行的任务的心跳
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class HeartbeatScheduler {

    private final HeartbeatManagementService heartbeatService;

    public HeartbeatScheduler(HeartbeatManagementService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    /**
     * 更新指定任务的心跳
     * 此方法由MonitorAspect在任务执行过程中调用
     */
    public void updateExecutionHeartbeat(ExecutionId executionId) {
        try {
            if (heartbeatService.hasHeartbeat(executionId)) {
                heartbeatService.updateHeartbeat(executionId);
                log.debug("更新任务心跳: {}", executionId);
            }
        } catch (Exception e) {
            log.error("更新心跳失败: {}", executionId, e);
        }
    }

    /**
     * 注册任务心跳
     */
    public void registerExecutionHeartbeat(ExecutionId executionId, int intervalSeconds) {
        try {
            heartbeatService.registerHeartbeat(executionId, intervalSeconds);
            log.debug("注册任务心跳: {}, 间隔: {}秒", executionId, intervalSeconds);
        } catch (Exception e) {
            log.error("注册心跳失败: {}", executionId, e);
        }
    }

    /**
     * 移除任务心跳
     */
    public void removeExecutionHeartbeat(ExecutionId executionId) {
        try {
            heartbeatService.removeHeartbeat(executionId);
            log.debug("移除任务心跳: {}", executionId);
        } catch (Exception e) {
            log.error("移除心跳失败: {}", executionId, e);
        }
    }
}
