package cn.rhymed.task.monitor.infrastructure.persistence.database;

import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.model.TaskName;
import cn.rhymed.task.monitor.infrastructure.persistence.database.mapper.TaskLogMapper;
import cn.rhymed.task.monitor.infrastructure.persistence.database.po.TaskLogPO;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 基于数据库的任务执行仓储实现
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class DatabaseTaskExecutionRepository implements TaskExecutionRepository {

    private final TaskLogMapper mapper;

    public DatabaseTaskExecutionRepository(TaskLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(TaskExecution execution) {
        TaskLogPO po = TaskLogPO.fromDomain(execution);
        mapper.insert(po);
        log.debug("保存任务执行记录到数据库: {}", execution.getTaskId());
    }

    @Override
    public void saveBatch(List<TaskExecution> taskExecutions) {
        if (taskExecutions == null || taskExecutions.isEmpty()) {
            return;
        }
        List<TaskLogPO> pos = taskExecutions.stream()
                .map(TaskLogPO::fromDomain)
                .collect(Collectors.toList());
        mapper.insertBatch(pos);
        log.debug("批量保存{}条任务执行记录到数据库", taskExecutions.size());
    }

    @Override
    public void update(TaskExecution execution) {
        TaskLogPO po = TaskLogPO.fromDomain(execution);
        po.setUpdatedAt(LocalDateTime.now());
        mapper.updateByTaskId(po);
        log.debug("更新任务执行记录到数据库: {}", execution.getTaskId());
    }

    @Override
    public Optional<TaskExecution> findById(TaskId taskId) {
        TaskLogPO po = mapper.selectByTaskId(taskId.getValue());
        return po != null ? Optional.of(po.toDomain()) : Optional.empty();
    }

    @Override
    public List<TaskExecution> findByTaskName(TaskName taskName) {
        return mapper.selectByTaskName(taskName.getValue()).stream()
                .map(TaskLogPO::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecution> findByStatus(TaskStatus status) {
        return mapper.selectByStatus(status.name()).stream()
                .map(TaskLogPO::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecution> findByTaskNameAndStatus(TaskName taskName, TaskStatus status) {
        return mapper.selectByTaskNameAndStatus(taskName.getValue(), status.name()).stream()
                .map(TaskLogPO::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecution> findRecoverableTasks(List<TaskStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return mapper.selectRecoverableTasks(null).stream()
                    .map(TaskLogPO::toDomain)
                    .collect(Collectors.toList());
        }
        List<String> statusNames = statuses.stream()
                .map(TaskStatus::name)
                .collect(Collectors.toList());
        return mapper.selectRecoverableTasks(statusNames).stream()
                .map(TaskLogPO::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecution> findRunningTasksWithHeartbeatBefore(LocalDateTime beforeTime) {
        return mapper.selectRunningTasksWithHeartbeatBefore(beforeTime).stream()
                .map(TaskLogPO::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecution> findTasksForRetry() {
        return mapper.selectTasksForRetry().stream()
                .map(TaskLogPO::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(TaskId taskId) {
        mapper.deleteByTaskId(taskId.getValue());
        log.debug("删除任务执行记录: {}", taskId);
    }

    @Override
    public void cleanupBefore(LocalDateTime beforeTime, List<TaskStatus> statuses) {
        List<String> statusNames = null;
        if (statuses != null && !statuses.isEmpty()) {
            statusNames = statuses.stream()
                    .map(TaskStatus::name)
                    .collect(Collectors.toList());
        }
        int deleted = mapper.cleanupBefore(beforeTime, statusNames);
        log.info("清理数据库中{}条过期任务记录", deleted);
    }

    @Override
    public long countByStatus(TaskStatus status) {
        return mapper.countByStatus(status.name());
    }

    @Override
    public long countByTaskName(TaskName taskName) {
        return mapper.countByTaskName(taskName.getValue());
    }
}
