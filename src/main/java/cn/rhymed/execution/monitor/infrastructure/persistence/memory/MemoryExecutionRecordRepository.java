package cn.rhymed.execution.monitor.infrastructure.persistence.memory;

import cn.hutool.core.collection.CollUtil;
import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存存储的任务执行仓储实现
 * 用于开发和测试环境
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class MemoryExecutionRecordRepository implements ExecutionRecordRepository {

    private final Map<String, ExecutionRecord> storage = new ConcurrentHashMap<>();

    @Override
    public void save(ExecutionRecord ExecutionRecord) {
        if (ExecutionRecord == null || ExecutionRecord.getExecutionId() == null) {
            throw new IllegalArgumentException("ExecutionRecord或ExecutionId不能为空");
        }
        storage.put(ExecutionRecord.getExecutionId().getValue(), ExecutionRecord);
    }

    @Override
    public void saveBatch(List<ExecutionRecord> ExecutionRecords) {
        if (CollUtil.isEmpty(ExecutionRecords)) {
            return;
        }
        ExecutionRecords.forEach(this::save);
    }

    @Override
    public void update(ExecutionRecord ExecutionRecord) {
        if (ExecutionRecord == null || ExecutionRecord.getExecutionId() == null) {
            throw new IllegalArgumentException("ExecutionRecord或ExecutionId不能为空");
        }
        String id = ExecutionRecord.getExecutionId().getValue();
        if (!storage.containsKey(id)) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        storage.put(id, ExecutionRecord);
    }

    @Override
    public Optional<ExecutionRecord> findById(ExecutionId executionId) {
        if (executionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(executionId.getValue()));
    }

    @Override
    public List<ExecutionRecord> findByExecutionName(ExecutionName executionName) {
        if (executionName == null) {
            return CollUtil.newArrayList();
        }
        return storage.values().stream()
                .filter(exec -> exec.getExecutionName().equals(executionName))
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findByStatus(ExecutionStatus status) {
        if (status == null) {
            return CollUtil.newArrayList();
        }
        return storage.values().stream()
                .filter(exec -> exec.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findByExecutionNameAndStatus(ExecutionName executionName, ExecutionStatus status) {
        if (executionName == null || status == null) {
            return CollUtil.newArrayList();
        }
        return storage.values().stream()
                .filter(exec -> exec.getExecutionName().equals(executionName) && exec.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findRecoverableExecutions(List<ExecutionStatus> statuses) {
        if (CollUtil.isEmpty(statuses)) {
            return CollUtil.newArrayList();
        }
        return storage.values().stream()
                .filter(exec -> statuses.contains(exec.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findRunningExecutionsWithHeartbeatBefore(LocalDateTime beforeTime) {
        if (beforeTime == null) {
            return CollUtil.newArrayList();
        }
        return storage.values().stream()
                .filter(exec -> exec.getStatus() == ExecutionStatus.RUNNING)
                .filter(exec -> exec.isHeartbeatEnabled())
                .filter(exec -> exec.getLastHeartbeatTime() != null)
                .filter(exec -> exec.getLastHeartbeatTime().isBefore(beforeTime))
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findExecutionsForRetry() {
        return storage.values().stream()
                .filter(exec -> exec.getStatus() == ExecutionStatus.AWAITING_RETRY)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findReadyForRetry(LocalDateTime currentTime) {
        if (currentTime == null) {
            return CollUtil.newArrayList();
        }
        return storage.values().stream()
                .filter(exec -> exec.getStatus() == ExecutionStatus.AWAITING_RETRY)
                .filter(exec -> exec.getNextRetryTime() == null || !exec.getNextRetryTime().isAfter(currentTime))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(ExecutionId executionId) {
        if (executionId != null) {
            storage.remove(executionId.getValue());
        }
    }

    @Override
    public void cleanupBefore(LocalDateTime beforeTime, List<ExecutionStatus> statuses) {
        if (beforeTime == null) {
            return;
        }

        List<String> toRemove = storage.values().stream()
                .filter(exec -> exec.getEndTime() != null)
                .filter(exec -> exec.getEndTime().isBefore(beforeTime))
                .filter(exec -> CollUtil.isEmpty(statuses) || statuses.contains(exec.getStatus()))
                .map(exec -> exec.getExecutionId().getValue())
                .collect(Collectors.toList());

        toRemove.forEach(storage::remove);
    }

    @Override
    public long countByStatus(ExecutionStatus status) {
        if (status == null) {
            return 0;
        }
        return storage.values().stream()
                .filter(exec -> exec.getStatus() == status)
                .count();
    }

    @Override
    public long countByExecutionName(ExecutionName executionName) {
        if (executionName == null) {
            return 0;
        }
        return storage.values().stream()
                .filter(exec -> exec.getExecutionName().equals(executionName))
                .count();
    }

    /**
     * 清空所有数据(仅用于测试)
     */
    public void clear() {
        storage.clear();
    }

    /**
     * 获取所有数据(仅用于测试)
     */
    public List<ExecutionRecord> findAll() {
        return CollUtil.newArrayList(storage.values());
    }
}
