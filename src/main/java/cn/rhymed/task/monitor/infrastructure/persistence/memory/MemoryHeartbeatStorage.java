package cn.rhymed.task.monitor.infrastructure.persistence.memory;

import cn.hutool.core.collection.CollUtil;
import cn.rhymed.task.monitor.domain.entity.HeartbeatRecord;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.repository.HeartbeatStorage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存存储的心跳仓储实现
 * 用于开发和测试环境
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class MemoryHeartbeatStorage implements HeartbeatStorage {

    private final Map<String, HeartbeatRecord> storage = new ConcurrentHashMap<>();

    @Override
    public void save(HeartbeatRecord heartbeatRecord) {
        if (heartbeatRecord == null || heartbeatRecord.getTaskId() == null) {
            throw new IllegalArgumentException("HeartbeatRecord或TaskId不能为空");
        }
        storage.put(heartbeatRecord.getTaskId().getValue(), heartbeatRecord);
    }

    @Override
    public void update(HeartbeatRecord heartbeatRecord) {
        if (heartbeatRecord == null || heartbeatRecord.getTaskId() == null) {
            throw new IllegalArgumentException("HeartbeatRecord或TaskId不能为空");
        }
        String id = heartbeatRecord.getTaskId().getValue();
        if (!storage.containsKey(id)) {
            throw new IllegalArgumentException("心跳记录不存在: " + id);
        }
        storage.put(id, heartbeatRecord);
    }

    @Override
    public Optional<HeartbeatRecord> findByTaskId(TaskId taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(taskId.getValue()));
    }

    @Override
    public List<HeartbeatRecord> findAllActive() {
        return CollUtil.newArrayList(storage.values());
    }

    @Override
    public List<HeartbeatRecord> findTimeoutRecords(LocalDateTime currentTime) {
        if (currentTime == null) {
            return CollUtil.newArrayList();
        }
        return storage.values().stream()
                .filter(record -> record.isTimeout(currentTime))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByTaskId(TaskId taskId) {
        if (taskId != null) {
            storage.remove(taskId.getValue());
        }
    }

    @Override
    public void cleanupBefore(LocalDateTime beforeTime) {
        if (beforeTime == null) {
            return;
        }
        List<String> toRemove = storage.values().stream()
                .filter(record -> record.getLastHeartbeatTime().isBefore(beforeTime))
                .map(record -> record.getTaskId().getValue())
                .collect(Collectors.toList());

        toRemove.forEach(storage::remove);
    }

    @Override
    public void deleteBatch(List<TaskId> taskIds) {
        if (CollUtil.isEmpty(taskIds)) {
            return;
        }
        taskIds.forEach(this::deleteByTaskId);
    }

    /**
     * 清空所有数据(仅用于测试)
     */
    public void clear() {
        storage.clear();
    }

    /**
     * 获取存储大小(仅用于测试)
     */
    public int size() {
        return storage.size();
    }
}
