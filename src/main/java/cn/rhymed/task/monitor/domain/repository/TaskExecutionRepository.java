package cn.rhymed.task.monitor.domain.repository;

import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.model.TaskName;

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
public interface TaskExecutionRepository {

    /**
     * 保存任务执行
     */
    void save(TaskExecution taskExecution);

    /**
     * 批量保存
     */
    void saveBatch(List<TaskExecution> taskExecutions);

    /**
     * 更新任务执行
     */
    void update(TaskExecution taskExecution);

    /**
     * 根据ID查找
     */
    Optional<TaskExecution> findById(TaskId taskId);

    /**
     * 根据任务名称查找所有执行记录
     */
    List<TaskExecution> findByTaskName(TaskName taskName);

    /**
     * 根据状态查找
     */
    List<TaskExecution> findByStatus(TaskStatus status);

    /**
     * 查找指定任务名称和状态的记录
     */
    List<TaskExecution> findByTaskNameAndStatus(TaskName taskName, TaskStatus status);

    /**
     * 查找需要恢复的任务
     * 通常是INTERRUPTED或HEARTBEAT_TIMEOUT状态的任务
     */
    List<TaskExecution> findRecoverableTasks(List<TaskStatus> statuses);

    /**
     * 查找超时未心跳的运行中任务
     *
     * @param beforeTime 心跳时间早于此时间的任务
     */
    List<TaskExecution> findRunningTasksWithHeartbeatBefore(LocalDateTime beforeTime);

    /**
     * 查找需要重试的任务
     */
    List<TaskExecution> findTasksForRetry();

    /**
     * 删除任务执行记录
     */
    void deleteById(TaskId taskId);

    /**
     * 清理过期记录
     *
     * @param beforeTime 清理此时间之前的记录
     * @param statuses   只清理指定状态的记录
     */
    void cleanupBefore(LocalDateTime beforeTime, List<TaskStatus> statuses);

    /**
     * 统计指定状态的任务数量
     */
    long countByStatus(TaskStatus status);

    /**
     * 统计指定任务名称的执行次数
     */
    long countByTaskName(TaskName taskName);
}
