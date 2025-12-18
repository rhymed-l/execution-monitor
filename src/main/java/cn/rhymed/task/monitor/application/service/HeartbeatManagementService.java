package cn.rhymed.task.monitor.application.service;

import cn.rhymed.task.monitor.domain.entity.HeartbeatRecord;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.repository.HeartbeatStorage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 心跳管理应用服务
 * 负责管理任务的心跳状态
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class HeartbeatManagementService {

    private final HeartbeatStorage storage;

    public HeartbeatManagementService(HeartbeatStorage storage) {
        this.storage = storage;
    }

    /**
     * 注册心跳
     */
    public void registerHeartbeat(TaskId taskId, int intervalSeconds) {
        HeartbeatRecord record = new HeartbeatRecord(
                taskId,
                LocalDateTime.now(),
                intervalSeconds
        );
        storage.save(record);
    }

    /**
     * 更新心跳时间
     */
    public void updateHeartbeat(TaskId taskId) {
        HeartbeatRecord record = storage.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("心跳记录不存在: " + taskId));

        record.updateHeartbeat(LocalDateTime.now());
        storage.update(record);
    }

    /**
     * 移除心跳
     */
    public void removeHeartbeat(TaskId taskId) {
        storage.deleteByTaskId(taskId);
    }

    /**
     * 检查是否有心跳记录
     */
    public boolean hasHeartbeat(TaskId taskId) {
        return storage.findByTaskId(taskId).isPresent();
    }

    /**
     * 获取心跳状态
     */
    public Optional<HeartbeatRecord> getHeartbeatStatus(TaskId taskId) {
        return storage.findByTaskId(taskId);
    }

    /**
     * 检查超时的任务
     *
     * @return 超时的任务ID列表
     */
    public List<TaskId> checkForTimeoutTasks() {
        LocalDateTime currentTime = LocalDateTime.now();
        return storage.findTimeoutRecords(currentTime).stream()
                .map(HeartbeatRecord::getTaskId)
                .collect(Collectors.toList());
    }

    /**
     * 清理过期的心跳记录
     */
    public void cleanupExpiredHeartbeats(LocalDateTime beforeTime) {
        storage.cleanupBefore(beforeTime);
    }

    /**
     * 批量移除心跳
     */
    public void removeHeartbeats(List<TaskId> taskIds) {
        storage.deleteBatch(taskIds);
    }
}
