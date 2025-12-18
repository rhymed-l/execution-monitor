package cn.rhymed.task.monitor.infrastructure.persistence.memory;

import cn.hutool.core.collection.CollUtil;
import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.model.TaskName;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;

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
public class MemoryTaskExecutionRepository implements TaskExecutionRepository {

    private final Map<String, TaskExecution> storage = new ConcurrentHashMap<>();

    @Override
    public void save(TaskExecution taskExecution) {
        if (taskExecution == null || taskExecution.getTaskId() == null) {
            throw new IllegalArgumentException("TaskExecution或TaskId不能为空");
        }
        storage.put(taskExecution.getTaskId().getValue(), taskExecution);
    }

    @Override
    public void saveBatch(List<TaskExecution> taskExecutions) {
        if (CollUtil.isEmpty(taskExecutions)) {
            return;
        }
        taskExecutions.forEach(this::save);
    }

    @Override
    public void update(TaskExecution taskExecution) {
        if (taskExecution == null || taskExecution.getTaskId() == null) {
            throw new IllegalArgumentException("TaskExecution或TaskId不能为空");
        }
        String id = taskExecution.getTaskId().getValue();
        if (!storage.containsKey(id)) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        storage.put(id, taskExecution);
    }

    @Override
    public Optional<TaskExecution> findById(TaskId taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(taskId.getValue()));
    }

    @Override
    public List<TaskExecution> findByTaskName(TaskName taskName) {
        if (taskName == null) {
            return CollUtil.newArrayList();
        }
        return storage.values().stream()
                .filter(exec -> exec.getTaskName().equals(taskName))
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecution> findByStatus(TaskStatus status) {
        if (status == null) {
            return CollUtil.newArrayList();
        }
        return storage.values().stream()
                .filter(exec -> exec.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecution> findByTaskNameAndStatus(TaskName taskName, TaskStatus status) {
        if (taskName == null || status == null) {
            return CollUtil.newArrayList();
        }
        return storage.values().stream()
                .filter(exec -> exec.getTaskName().equals(taskName) && exec.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecution> findRecoverableTasks(List<TaskStatus> statuses) {
        if (CollUtil.isEmpty(statuses)) {
            return CollUtil.newArrayList();
        }
        return storage.values().stream()
                .filter(exec -> statuses.contains(exec.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecution> findRunningTasksWithHeartbeatBefore(LocalDateTime beforeTime) {
        if (beforeTime == null) {
            return CollUtil.newArrayList();
        }
        return storage.values().stream()
                .filter(exec -> exec.getStatus() == TaskStatus.RUNNING)
                .filter(exec -> exec.isHeartbeatEnabled())
                .filter(exec -> exec.getLastHeartbeatTime() != null)
                .filter(exec -> exec.getLastHeartbeatTime().isBefore(beforeTime))
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecution> findTasksForRetry() {
        return storage.values().stream()
                .filter(exec -> exec.getStatus() == TaskStatus.RETRY)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(TaskId taskId) {
        if (taskId != null) {
            storage.remove(taskId.getValue());
        }
    }

    @Override
    public void cleanupBefore(LocalDateTime beforeTime, List<TaskStatus> statuses) {
        if (beforeTime == null) {
            return;
        }

        List<String> toRemove = storage.values().stream()
                .filter(exec -> exec.getEndTime() != null)
                .filter(exec -> exec.getEndTime().isBefore(beforeTime))
                .filter(exec -> CollUtil.isEmpty(statuses) || statuses.contains(exec.getStatus()))
                .map(exec -> exec.getTaskId().getValue())
                .collect(Collectors.toList());

        toRemove.forEach(storage::remove);
    }

    @Override
    public long countByStatus(TaskStatus status) {
        if (status == null) {
            return 0;
        }
        return storage.values().stream()
                .filter(exec -> exec.getStatus() == status)
                .count();
    }

    @Override
    public long countByTaskName(TaskName taskName) {
        if (taskName == null) {
            return 0;
        }
        return storage.values().stream()
                .filter(exec -> exec.getTaskName().equals(taskName))
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
    public List<TaskExecution> findAll() {
        return CollUtil.newArrayList(storage.values());
    }
}
