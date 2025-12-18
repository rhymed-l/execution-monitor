package cn.rhymed.task.monitor.infrastructure.persistence.redis;

import cn.hutool.json.JSONUtil;
import cn.rhymed.task.monitor.domain.entity.HeartbeatRecord;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.repository.HeartbeatStorage;
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
 * 基于Redis的心跳存储实现
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class RedisHeartbeatStorage implements HeartbeatStorage {

    private static final String KEY_PREFIX = "task:heartbeat:";
    private static final long DEFAULT_TTL_DAYS = 7;

    private final RedisTemplate<String, String> redisTemplate;

    public RedisHeartbeatStorage(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(HeartbeatRecord heartbeatRecord) {
        String key = buildKey(heartbeatRecord.getTaskId());
        String json = JSONUtil.toJsonStr(heartbeatRecord);
        redisTemplate.opsForValue().set(key, json, DEFAULT_TTL_DAYS, TimeUnit.DAYS);
        log.debug("保存心跳记录到Redis: {}", heartbeatRecord.getTaskId());
    }

    @Override
    public void update(HeartbeatRecord heartbeatRecord) {
        save(heartbeatRecord);
        log.debug("更新心跳记录: {}", heartbeatRecord.getTaskId());
    }

    @Override
    public Optional<HeartbeatRecord> findByTaskId(TaskId taskId) {
        String key = buildKey(taskId);
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            HeartbeatRecord record = JSONUtil.toBean(json, HeartbeatRecord.class);
            return Optional.of(record);
        }
        return Optional.empty();
    }

    @Override
    public List<HeartbeatRecord> findAllActive() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        List<HeartbeatRecord> records = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                String json = redisTemplate.opsForValue().get(key);
                if (json != null) {
                    records.add(JSONUtil.toBean(json, HeartbeatRecord.class));
                }
            }
        }
        return records;
    }

    @Override
    public List<HeartbeatRecord> findTimeoutRecords(LocalDateTime currentTime) {
        List<HeartbeatRecord> allRecords = findAllActive();
        return allRecords.stream()
                .filter(record -> record.isTimeout(currentTime))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByTaskId(TaskId taskId) {
        String key = buildKey(taskId);
        redisTemplate.delete(key);
        log.debug("删除心跳记录: {}", taskId);
    }

    @Override
    public void cleanupBefore(LocalDateTime beforeTime) {
        // Redis使用TTL自动过期,这里实现按时间清理
        List<HeartbeatRecord> allRecords = findAllActive();
        for (HeartbeatRecord record : allRecords) {
            if (record.getLastHeartbeatTime().isBefore(beforeTime)) {
                deleteByTaskId(record.getTaskId());
            }
        }
        log.info("清理Redis中{}之前的心跳记录", beforeTime);
    }

    @Override
    public void deleteBatch(List<TaskId> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        List<String> keys = taskIds.stream()
                .map(this::buildKey)
                .collect(Collectors.toList());
        redisTemplate.delete(keys);
        log.debug("批量删除{}条心跳记录", taskIds.size());
    }

    private String buildKey(TaskId taskId) {
        return KEY_PREFIX + taskId.getValue();
    }
}
