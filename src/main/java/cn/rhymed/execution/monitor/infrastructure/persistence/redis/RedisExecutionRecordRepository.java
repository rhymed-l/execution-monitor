package cn.rhymed.execution.monitor.infrastructure.persistence.redis;

import cn.hutool.json.JSONUtil;
import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
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
public class RedisExecutionRecordRepository implements ExecutionRecordRepository {

    private static final String KEY_PREFIX = "execution:execution:";
    private static final String INDEX_STATUS_PREFIX = "execution:index:status:";
    private static final String INDEX_TASK_NAME_PREFIX = "execution:index:name:";
    private static final long DEFAULT_TTL_DAYS = 7;

    private final RedisTemplate<String, String> redisTemplate;

    public RedisExecutionRecordRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(ExecutionRecord execution) {
        String key = buildKey(execution.getExecutionId());
        String json = JSONUtil.toJsonStr(execution);

        redisTemplate.opsForValue().set(key, json, DEFAULT_TTL_DAYS, TimeUnit.DAYS);

        // 添加到状态索引
        String statusIndexKey = INDEX_STATUS_PREFIX + execution.getStatus().name();
        redisTemplate.opsForSet().add(statusIndexKey, execution.getExecutionId().getValue());

        // 添加到任务名称索引
        String nameIndexKey = INDEX_TASK_NAME_PREFIX + execution.getExecutionName().getValue();
        redisTemplate.opsForSet().add(nameIndexKey, execution.getExecutionId().getValue());

        log.debug("保存任务执行记录到Redis: {}", execution.getExecutionId());
    }

    @Override
    public void saveBatch(List<ExecutionRecord> executionRecords) {
        if (executionRecords == null || executionRecords.isEmpty()) {
            return;
        }
        for (ExecutionRecord execution : executionRecords) {
            save(execution);
        }
        log.debug("批量保存{}条任务执行记录到Redis", executionRecords.size());
    }

    @Override
    public void update(ExecutionRecord execution) {
        // 先删除旧的状态索引
        Optional<ExecutionRecord> oldExecution = findById(execution.getExecutionId());
        if (oldExecution.isPresent()) {
            String oldStatusIndexKey = INDEX_STATUS_PREFIX + oldExecution.get().getStatus().name();
            redisTemplate.opsForSet().remove(oldStatusIndexKey, execution.getExecutionId().getValue());
        }

        // 保存新数据
        save(execution);
    }

    @Override
    public Optional<ExecutionRecord> findById(ExecutionId executionId) {
        String key = buildKey(executionId);
        String json = redisTemplate.opsForValue().get(key);

        if (json != null) {
            ExecutionRecord execution = JSONUtil.toBean(json, ExecutionRecord.class);
            return Optional.of(execution);
        }
        return Optional.empty();
    }

    @Override
    public List<ExecutionRecord> findByExecutionName(ExecutionName executionName) {
        String nameIndexKey = INDEX_TASK_NAME_PREFIX + executionName.getValue();
        Set<String> executionIds = redisTemplate.opsForSet().members(nameIndexKey);

        List<ExecutionRecord> result = new ArrayList<>();
        if (executionIds != null) {
            for (String executionIdValue : executionIds) {
                Optional<ExecutionRecord> execution = findById(new ExecutionId(executionIdValue));
                execution.ifPresent(result::add);
            }
        }
        return result;
    }

    @Override
    public List<ExecutionRecord> findByStatus(ExecutionStatus status) {
        String statusIndexKey = INDEX_STATUS_PREFIX + status.name();
        Set<String> executionIds = redisTemplate.opsForSet().members(statusIndexKey);

        List<ExecutionRecord> result = new ArrayList<>();
        if (executionIds != null) {
            for (String executionIdValue : executionIds) {
                Optional<ExecutionRecord> execution = findById(new ExecutionId(executionIdValue));
                execution.ifPresent(result::add);
            }
        }
        return result;
    }

    @Override
    public List<ExecutionRecord> findByExecutionNameAndStatus(ExecutionName executionName, ExecutionStatus status) {
        List<ExecutionRecord> byExecutionName = findByExecutionName(executionName);
        return byExecutionName.stream()
                .filter(execution -> execution.getStatus().equals(status))
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findRecoverableExecutions(List<ExecutionStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            // 默认恢复RUNNING, INTERRUPTED, HEARTBEAT_TIMEOUT状态
            List<ExecutionRecord> result = new ArrayList<>();
            result.addAll(findByStatus(ExecutionStatus.RUNNING));
            result.addAll(findByStatus(ExecutionStatus.INTERRUPTED));
            result.addAll(findByStatus(ExecutionStatus.HEARTBEAT_TIMEOUT));
            return result;
        }

        List<ExecutionRecord> result = new ArrayList<>();
        for (ExecutionStatus status : statuses) {
            result.addAll(findByStatus(status));
        }
        return result;
    }

    @Override
    public List<ExecutionRecord> findRunningExecutionsWithHeartbeatBefore(LocalDateTime beforeTime) {
        List<ExecutionRecord> runningExecutions = findByStatus(ExecutionStatus.RUNNING);
        return runningExecutions.stream()
                .filter(execution -> execution.isHeartbeatEnabled() &&
                        execution.getStartTime().isBefore(beforeTime))
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findExecutionsForRetry() {
        List<ExecutionRecord> failedExecutions = findByStatus(ExecutionStatus.FAILED);
        return failedExecutions.stream()
                .filter(execution -> execution.getRetryCount() < execution.getMaxRetry())
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(ExecutionId executionId) {
        // 先获取任务信息用于清理索引
        Optional<ExecutionRecord> execution = findById(executionId);

        // 删除主数据
        String key = buildKey(executionId);
        redisTemplate.delete(key);

        // 从索引中删除
        if (execution.isPresent()) {
            ExecutionRecord exec = execution.get();

            // 从状态索引删除
            String statusIndexKey = INDEX_STATUS_PREFIX + exec.getStatus().name();
            redisTemplate.opsForSet().remove(statusIndexKey, executionId.getValue());

            // 从任务名称索引删除
            String nameIndexKey = INDEX_TASK_NAME_PREFIX + exec.getExecutionName().getValue();
            redisTemplate.opsForSet().remove(nameIndexKey, executionId.getValue());
        }

        log.debug("删除任务执行记录: {}", executionId);
    }

    @Override
    public void cleanupBefore(LocalDateTime beforeTime, List<ExecutionStatus> statuses) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        int deleted = 0;
        for (String key : keys) {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                ExecutionRecord execution = JSONUtil.toBean(json, ExecutionRecord.class);
                boolean shouldDelete = execution.getStartTime().isBefore(beforeTime);

                // 如果指定了状态列表,只删除匹配状态的
                if (statuses != null && !statuses.isEmpty()) {
                    shouldDelete = shouldDelete && statuses.contains(execution.getStatus());
                }

                if (shouldDelete) {
                    deleteById(execution.getExecutionId());
                    deleted++;
                }
            }
        }
        log.info("清理Redis中{}条过期任务记录", deleted);
    }

    @Override
    public long countByStatus(ExecutionStatus status) {
        String statusIndexKey = INDEX_STATUS_PREFIX + status.name();
        Long count = redisTemplate.opsForSet().size(statusIndexKey);
        return count != null ? count : 0L;
    }

    @Override
    public long countByExecutionName(ExecutionName executionName) {
        String nameIndexKey = INDEX_TASK_NAME_PREFIX + executionName.getValue();
        Long count = redisTemplate.opsForSet().size(nameIndexKey);
        return count != null ? count : 0L;
    }

    private String buildKey(ExecutionId executionId) {
        return KEY_PREFIX + executionId.getValue();
    }
}
