package cn.rhymed.execution.monitor.infrastructure.persistence.memory;

import cn.hutool.core.collection.CollUtil;
import cn.rhymed.execution.monitor.domain.entity.HeartbeatRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.repository.HeartbeatStorage;

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
        if (heartbeatRecord == null || heartbeatRecord.getExecutionId() == null) {
            throw new IllegalArgumentException("HeartbeatRecord或ExecutionId不能为空");
        }
        storage.put(heartbeatRecord.getExecutionId().getValue(), heartbeatRecord);
    }

    @Override
    public void update(HeartbeatRecord heartbeatRecord) {
        if (heartbeatRecord == null || heartbeatRecord.getExecutionId() == null) {
            throw new IllegalArgumentException("HeartbeatRecord或ExecutionId不能为空");
        }
        String id = heartbeatRecord.getExecutionId().getValue();
        if (!storage.containsKey(id)) {
            throw new IllegalArgumentException("心跳记录不存在: " + id);
        }
        storage.put(id, heartbeatRecord);
    }

    @Override
    public Optional<HeartbeatRecord> findByExecutionId(ExecutionId executionId) {
        if (executionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(executionId.getValue()));
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
    public void deleteByExecutionId(ExecutionId executionId) {
        if (executionId != null) {
            storage.remove(executionId.getValue());
        }
    }

    @Override
    public void cleanupBefore(LocalDateTime beforeTime) {
        if (beforeTime == null) {
            return;
        }
        List<String> toRemove = storage.values().stream()
                .filter(record -> record.getLastHeartbeatTime().isBefore(beforeTime))
                .map(record -> record.getExecutionId().getValue())
                .collect(Collectors.toList());

        toRemove.forEach(storage::remove);
    }

    @Override
    public void deleteBatch(List<ExecutionId> executionIds) {
        if (CollUtil.isEmpty(executionIds)) {
            return;
        }
        executionIds.forEach(this::deleteByExecutionId);
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
