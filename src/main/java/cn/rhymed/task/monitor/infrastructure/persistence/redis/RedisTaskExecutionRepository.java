package cn.rhymed.task.monitor.infrastructure.persistence.redis;

import cn.hutool.json.JSONUtil;
import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.model.TaskName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于Redis的任务执行仓储实现
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class RedisTaskExecutionRepository implements TaskExecutionRepository {

    private static final String KEY_PREFIX = "task:execution:";
    private static final String INDEX_STATUS_PREFIX = "task:index:status:";
    private static final String INDEX_TASK_NAME_PREFIX = "task:index:name:";
    private static final long DEFAULT_TTL_DAYS = 7;

    private final RedisTemplate<String, String> redisTemplate;

    public RedisTaskExecutionRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(TaskExecution execution) {
        String key = buildKey(execution.getTaskId());
        String json = JSONUtil.toJsonStr(execution);

        redisTemplate.opsForValue().set(key, json, DEFAULT_TTL_DAYS, TimeUnit.DAYS);

        // 添加到状态索引
        String statusIndexKey = INDEX_STATUS_PREFIX + execution.getStatus().name();
        redisTemplate.opsForSet().add(statusIndexKey, execution.getTaskId().getValue());

        // 添加到任务名称索引
        String taskNameIndexKey = INDEX_TASK_NAME_PREFIX + execution.getTaskName().getValue();
        redisTemplate.opsForSet().add(taskNameIndexKey, execution.getTaskId().getValue());

        log.debug("保存任务执行记录到Redis: {}", execution.getTaskId());
    }

    @Override
    public void saveBatch(List<TaskExecution> taskExecutions) {
        if (taskExecutions == null || taskExecutions.isEmpty()) {
            return;
        }
        for (TaskExecution execution : taskExecutions) {
            save(execution);
        }
        log.debug("批量保存{}条任务执行记录到Redis", taskExecutions.size());
    }

    @Override
    public void update(TaskExecution execution) {
        // 先删除旧的状态索引
        Optional<TaskExecution> oldExecution = findById(execution.getTaskId());
        if (oldExecution.isPresent()) {
            String oldStatusIndexKey = INDEX_STATUS_PREFIX + oldExecution.get().getStatus().name();
            redisTemplate.opsForSet().remove(oldStatusIndexKey, execution.getTaskId().getValue());
        }

        // 保存新数据
        save(execution);
    }

    @Override
    public Optional<TaskExecution> findById(TaskId taskId) {
        String key = buildKey(taskId);
        String json = redisTemplate.opsForValue().get(key);

        if (json != null) {
            TaskExecution execution = JSONUtil.toBean(json, TaskExecution.class);
            return Optional.of(execution);
        }
        return Optional.empty();
    }

    @Override
    public List<TaskExecution> findByTaskName(TaskName taskName) {
        String taskNameIndexKey = INDEX_TASK_NAME_PREFIX + taskName.getValue();
        Set<String> taskIds = redisTemplate.opsForSet().members(taskNameIndexKey);

        List<TaskExecution> result = new ArrayList<>();
        if (taskIds != null) {
            for (String taskIdValue : taskIds) {
                Optional<TaskExecution> execution = findById(new TaskId(taskIdValue));
                execution.ifPresent(result::add);
            }
        }
        return result;
    }

    @Override
    public List<TaskExecution> findByStatus(TaskStatus status) {
        String statusIndexKey = INDEX_STATUS_PREFIX + status.name();
        Set<String> taskIds = redisTemplate.opsForSet().members(statusIndexKey);

        List<TaskExecution> result = new ArrayList<>();
        if (taskIds != null) {
            for (String taskIdValue : taskIds) {
                Optional<TaskExecution> execution = findById(new TaskId(taskIdValue));
                execution.ifPresent(result::add);
            }
        }
        return result;
    }

    @Override
    public List<TaskExecution> findByTaskNameAndStatus(TaskName taskName, TaskStatus status) {
        List<TaskExecution> byTaskName = findByTaskName(taskName);
        return byTaskName.stream()
                .filter(execution -> execution.getStatus().equals(status))
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecution> findRecoverableTasks(List<TaskStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            // 默认恢复RUNNING, INTERRUPTED, HEARTBEAT_TIMEOUT状态
            List<TaskExecution> result = new ArrayList<>();
            result.addAll(findByStatus(TaskStatus.RUNNING));
            result.addAll(findByStatus(TaskStatus.INTERRUPTED));
            result.addAll(findByStatus(TaskStatus.HEARTBEAT_TIMEOUT));
            return result;
        }

        List<TaskExecution> result = new ArrayList<>();
        for (TaskStatus status : statuses) {
            result.addAll(findByStatus(status));
        }
        return result;
    }

    @Override
    public List<TaskExecution> findRunningTasksWithHeartbeatBefore(LocalDateTime beforeTime) {
        List<TaskExecution> runningTasks = findByStatus(TaskStatus.RUNNING);
        return runningTasks.stream()
                .filter(task -> task.isHeartbeatEnabled() &&
                        task.getStartTime().isBefore(beforeTime))
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecution> findTasksForRetry() {
        List<TaskExecution> failedTasks = findByStatus(TaskStatus.FAILED);
        return failedTasks.stream()
                .filter(task -> task.getRetryCount() < task.getMaxRetry())
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(TaskId taskId) {
        // 先获取任务信息用于清理索引
        Optional<TaskExecution> execution = findById(taskId);

        // 删除主数据
        String key = buildKey(taskId);
        redisTemplate.delete(key);

        // 从索引中删除
        if (execution.isPresent()) {
            TaskExecution exec = execution.get();

            // 从状态索引删除
            String statusIndexKey = INDEX_STATUS_PREFIX + exec.getStatus().name();
            redisTemplate.opsForSet().remove(statusIndexKey, taskId.getValue());

            // 从任务名称索引删除
            String taskNameIndexKey = INDEX_TASK_NAME_PREFIX + exec.getTaskName().getValue();
            redisTemplate.opsForSet().remove(taskNameIndexKey, taskId.getValue());
        }

        log.debug("删除任务执行记录: {}", taskId);
    }

    @Override
    public void cleanupBefore(LocalDateTime beforeTime, List<TaskStatus> statuses) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        int deleted = 0;
        for (String key : keys) {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                TaskExecution execution = JSONUtil.toBean(json, TaskExecution.class);
                boolean shouldDelete = execution.getStartTime().isBefore(beforeTime);

                // 如果指定了状态列表,只删除匹配状态的
                if (statuses != null && !statuses.isEmpty()) {
                    shouldDelete = shouldDelete && statuses.contains(execution.getStatus());
                }

                if (shouldDelete) {
                    deleteById(execution.getTaskId());
                    deleted++;
                }
            }
        }
        log.info("清理Redis中{}条过期任务记录", deleted);
    }

    @Override
    public long countByStatus(TaskStatus status) {
        String statusIndexKey = INDEX_STATUS_PREFIX + status.name();
        Long count = redisTemplate.opsForSet().size(statusIndexKey);
        return count != null ? count : 0L;
    }

    @Override
    public long countByTaskName(TaskName taskName) {
        String taskNameIndexKey = INDEX_TASK_NAME_PREFIX + taskName.getValue();
        Long count = redisTemplate.opsForSet().size(taskNameIndexKey);
        return count != null ? count : 0L;
    }

    private String buildKey(TaskId taskId) {
        return KEY_PREFIX + taskId.getValue();
    }
}
