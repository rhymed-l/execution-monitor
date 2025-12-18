package cn.rhymed.task.monitor.infrastructure.scheduler;

import cn.rhymed.task.monitor.application.service.HeartbeatManagementService;
import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.domain.service.TaskExecutionDomainService;
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
    private final TaskExecutionRepository taskRepository;
    private final TaskExecutionDomainService domainService;

    public HealthCheckScheduler(HeartbeatManagementService heartbeatService,
                                TaskExecutionRepository taskRepository,
                                TaskExecutionDomainService domainService) {
        this.heartbeatService = heartbeatService;
        this.taskRepository = taskRepository;
        this.domainService = domainService;
    }

    /**
     * 定期检查健康状态
     * 默认每30秒执行一次
     */
    @Scheduled(fixedDelayString = "${task.monitor.heartbeat.check-interval-seconds:30}000")
    public void checkHealthStatus() {
        try {
            List<TaskId> timeoutTasks = heartbeatService.checkForTimeoutTasks();

            if (timeoutTasks.isEmpty()) {
                return;
            }

            log.warn("检测到 {} 个心跳超时任务", timeoutTasks.size());

            for (TaskId taskId : timeoutTasks) {
                handleTimeoutTask(taskId);
            }
        } catch (Exception e) {
            log.error("健康检查失败", e);
        }
    }

    /**
     * 处理超时任务
     */
    private void handleTimeoutTask(TaskId taskId) {
        try {
            TaskExecution execution = taskRepository.findById(taskId).orElse(null);
            if (execution == null) {
                log.warn("超时任务不存在: {}", taskId);
                heartbeatService.removeHeartbeat(taskId);
                return;
            }

            // 只处理运行中的任务
            if (execution.getStatus() != TaskStatus.RUNNING) {
                log.debug("任务状态不是RUNNING,跳过: {}, 状态: {}", taskId, execution.getStatus());
                heartbeatService.removeHeartbeat(taskId);
                return;
            }

            // 标记为心跳超时
            log.warn("任务心跳超时: {}, 任务名: {}", taskId, execution.getTaskName());
            domainService.markHeartbeatTimeout(execution);
            taskRepository.update(execution);

            // 移除心跳记录
            heartbeatService.removeHeartbeat(taskId);

        } catch (Exception e) {
            log.error("处理超时任务失败: {}", taskId, e);
        }
    }

    /**
     * 手动触发健康检查(用于测试)
     */
    public void triggerHealthCheck() {
        checkHealthStatus();
    }
}
