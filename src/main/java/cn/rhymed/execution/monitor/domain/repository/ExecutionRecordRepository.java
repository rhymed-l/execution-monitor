package cn.rhymed.execution.monitor.domain.repository;

import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 任务执行仓储接口
 * 定义任务执行聚合的持久化契约
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public interface ExecutionRecordRepository {

    /**
     * 保存任务执行
     */
    void save(ExecutionRecord ExecutionRecord);

    /**
     * 批量保存
     */
    void saveBatch(List<ExecutionRecord> ExecutionRecords);

    /**
     * 更新任务执行
     */
    void update(ExecutionRecord ExecutionRecord);

    /**
     * 根据ID查找
     */
    Optional<ExecutionRecord> findById(ExecutionId executionId);

    /**
     * 根据任务名称查找所有执行记录
     */
    List<ExecutionRecord> findByExecutionName(ExecutionName executionName);

    /**
     * 根据状态查找
     */
    List<ExecutionRecord> findByStatus(ExecutionStatus status);

    /**
     * 查找指定任务名称和状态的记录
     */
    List<ExecutionRecord> findByExecutionNameAndStatus(ExecutionName executionName, ExecutionStatus status);

    /**
     * 查找需要恢复的任务
     * 通常是INTERRUPTED或HEARTBEAT_TIMEOUT状态的任务
     */
    List<ExecutionRecord> findRecoverableExecutions(List<ExecutionStatus> statuses);

    /**
     * 查找超时未心跳的运行中任务
     *
     * @param beforeTime 心跳时间早于此时间的任务
     */
    List<ExecutionRecord> findRunningExecutionsWithHeartbeatBefore(LocalDateTime beforeTime);

    /**
     * 查找需要重试的任务
     */
    List<ExecutionRecord> findExecutionsForRetry();

    /**
     * 查找已到达重试时间的任务
     * 状态为 AWAITING_RETRY 且 nextRetryTime <= currentTime
     *
     * @param currentTime 当前时间
     * @return 准备重试的任务列表
     */
    List<ExecutionRecord> findReadyForRetry(LocalDateTime currentTime);

    /**
     * 删除任务执行记录
     */
    void deleteById(ExecutionId executionId);

    /**
     * 清理过期记录
     *
     * @param beforeTime 清理此时间之前的记录
     * @param statuses   只清理指定状态的记录
     */
    void cleanupBefore(LocalDateTime beforeTime, List<ExecutionStatus> statuses);

    /**
     * 统计指定状态的任务数量
     */
    long countByStatus(ExecutionStatus status);

    /**
     * 统计指定任务名称的执行次数
     */
    long countByExecutionName(ExecutionName executionName);
}
