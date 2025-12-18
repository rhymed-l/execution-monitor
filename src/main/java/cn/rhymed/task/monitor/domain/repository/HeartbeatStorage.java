package cn.rhymed.task.monitor.domain.repository;

import cn.rhymed.task.monitor.domain.entity.HeartbeatRecord;
import cn.rhymed.task.monitor.domain.model.TaskId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 心跳存储接口
 * 定义心跳记录的持久化契约
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public interface HeartbeatStorage {

    /**
     * 保存心跳记录
     */
    void save(HeartbeatRecord heartbeatRecord);

    /**
     * 更新心跳记录
     */
    void update(HeartbeatRecord heartbeatRecord);

    /**
     * 根据任务ID查找心跳记录
     */
    Optional<HeartbeatRecord> findByTaskId(TaskId taskId);

    /**
     * 查找所有活跃的心跳记录
     */
    List<HeartbeatRecord> findAllActive();

    /**
     * 查找超时的心跳记录
     *
     * @param currentTime 当前时间
     */
    List<HeartbeatRecord> findTimeoutRecords(LocalDateTime currentTime);

    /**
     * 删除心跳记录
     */
    void deleteByTaskId(TaskId taskId);

    /**
     * 清理过期的心跳记录
     *
     * @param beforeTime 清理此时间之前的记录
     */
    void cleanupBefore(LocalDateTime beforeTime);

    /**
     * 批量删除
     */
    void deleteBatch(List<TaskId> taskIds);
}
