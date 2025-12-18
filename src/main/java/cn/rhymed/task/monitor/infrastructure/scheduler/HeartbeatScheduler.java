package cn.rhymed.task.monitor.infrastructure.scheduler;

import cn.rhymed.task.monitor.application.service.HeartbeatManagementService;
import cn.rhymed.task.monitor.domain.model.TaskId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

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
     * 此方法由TaskMonitorAspect在任务执行过程中调用
     */
    public void updateTaskHeartbeat(TaskId taskId) {
        try {
            if (heartbeatService.hasHeartbeat(taskId)) {
                heartbeatService.updateHeartbeat(taskId);
                log.debug("更新任务心跳: {}", taskId);
            }
        } catch (Exception e) {
            log.error("更新心跳失败: {}", taskId, e);
        }
    }

    /**
     * 注册任务心跳
     */
    public void registerTaskHeartbeat(TaskId taskId, int intervalSeconds) {
        try {
            heartbeatService.registerHeartbeat(taskId, intervalSeconds);
            log.debug("注册任务心跳: {}, 间隔: {}秒", taskId, intervalSeconds);
        } catch (Exception e) {
            log.error("注册心跳失败: {}", taskId, e);
        }
    }

    /**
     * 移除任务心跳
     */
    public void removeTaskHeartbeat(TaskId taskId) {
        try {
            heartbeatService.removeHeartbeat(taskId);
            log.debug("移除任务心跳: {}", taskId);
        } catch (Exception e) {
            log.error("移除心跳失败: {}", taskId, e);
        }
    }
}
