package cn.rhymed.execution.monitor.application.service;

import cn.rhymed.execution.monitor.domain.entity.HeartbeatRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.repository.HeartbeatStorage;

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
    public void registerHeartbeat(ExecutionId executionId, int intervalSeconds) {
        HeartbeatRecord record = new HeartbeatRecord(
                executionId,
                LocalDateTime.now(),
                intervalSeconds
        );
        storage.save(record);
    }

    /**
     * 更新心跳时间
     */
    public void updateHeartbeat(ExecutionId executionId) {
        HeartbeatRecord record = storage.findByExecutionId(executionId)
                .orElseThrow(() -> new IllegalArgumentException("心跳记录不存在: " + executionId));

        record.updateHeartbeat(LocalDateTime.now());
        storage.update(record);
    }

    /**
     * 移除心跳
     */
    public void removeHeartbeat(ExecutionId executionId) {
        storage.deleteByExecutionId(executionId);
    }

    /**
     * 检查是否有心跳记录
     */
    public boolean hasHeartbeat(ExecutionId executionId) {
        return storage.findByExecutionId(executionId).isPresent();
    }

    /**
     * 获取心跳状态
     */
    public Optional<HeartbeatRecord> getHeartbeatStatus(ExecutionId executionId) {
        return storage.findByExecutionId(executionId);
    }

    /**
     * 检查超时的任务
     *
     * @return 超时的任务ID列表
     */
    public List<ExecutionId> checkForTimeoutExecutions() {
        LocalDateTime currentTime = LocalDateTime.now();
        return storage.findTimeoutRecords(currentTime).stream()
                .map(HeartbeatRecord::getExecutionId)
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
    public void removeHeartbeats(List<ExecutionId> executionIds) {
        storage.deleteBatch(executionIds);
    }
}
