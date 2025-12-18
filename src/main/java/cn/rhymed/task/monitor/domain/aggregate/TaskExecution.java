package cn.rhymed.task.monitor.domain.aggregate;

import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.*;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务执行聚合根
 * 封装任务执行的完整生命周期和业务规则
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
public class TaskExecution implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务唯一标识
     * 作为聚合根的唯一标识,保证任务的全局唯一性
     */
    private final TaskId taskId;

    /**
     * 任务名称
     * 标识任务类型,通常对应被监控的方法名或业务名称
     */
    private final TaskName taskName;

    /**
     * 业务键
     * 关联业务实体的可选标识,如订单号、用户ID等
     * 默认为空,用于快速定位具体业务数据
     */
    @Builder.Default
    private final BizKey bizKey = BizKey.empty();

    /**
     * 序列化的方法参数
     * 以JSON格式存储方法调用时的参数,用于任务恢复和重试
     * 默认为空
     */
    @Builder.Default
    private final SerializedParams params = SerializedParams.empty();
    /**
     * 任务开始时间
     * 记录任务首次启动的时间点,不可变
     */
    private final LocalDateTime startTime;
    /**
     * 最大重试次数
     * 任务失败后允许重试的最大次数
     * 默认为3次
     */
    @Builder.Default
    private final int maxRetry = 3;
    /**
     * 任务执行状态
     * 标识任务当前所处的生命周期阶段
     * 默认为RUNNING状态
     */
    @Builder.Default
    private TaskStatus status = TaskStatus.RUNNING;
    /**
     * 错误信息
     * 任务失败时记录的异常详情,包含错误消息、异常类型和堆栈跟踪
     * 只有在任务失败时才会有值
     */
    private ErrorInfo errorInfo;
    /**
     * 任务结束时间
     * 记录任务完成或失败的时间点
     * 仅在任务进入终态时设置
     */
    private LocalDateTime endTime;
    /**
     * 最后心跳时间
     * 记录任务最近一次心跳更新的时间
     * 用于判断长时间运行任务是否超时
     */
    private LocalDateTime lastHeartbeatTime;
    /**
     * 当前重试次数
     * 记录任务已经重试的次数
     * 默认为0,每次重试递增
     */
    @Builder.Default
    private int retryCount = 0;
    /**
     * 心跳间隔(秒)
     * 长时间运行任务的心跳检测间隔
     * 为null表示未启用心跳监控
     */
    private Integer heartbeatIntervalSeconds;

    /**
     * 创建新的任务执行
     */
    public static TaskExecution create(TaskName taskName, BizKey bizKey, SerializedParams params, int maxRetry) {
        return TaskExecution.builder()
                .taskId(TaskId.generate())
                .taskName(taskName)
                .bizKey(bizKey)
                .params(params)
                .status(TaskStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .retryCount(0)
                .maxRetry(maxRetry)
                .build();
    }

    /**
     * 任务成功完成
     */
    public void complete() {
        validateTransition(TaskStatus.SUCCESS);
        this.status = TaskStatus.SUCCESS;
        this.endTime = LocalDateTime.now();
        this.errorInfo = null;
    }

    /**
     * 任务失败
     */
    public void fail(ErrorInfo errorInfo) {
        if (errorInfo == null) {
            throw new IllegalArgumentException("errorInfo不能为空");
        }
        validateTransition(TaskStatus.FAILED);
        this.status = TaskStatus.FAILED;
        this.endTime = LocalDateTime.now();
        this.errorInfo = errorInfo;
    }

    /**
     * 任务被中断
     */
    public void interrupt() {
        validateTransition(TaskStatus.INTERRUPTED);
        this.status = TaskStatus.INTERRUPTED;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 心跳超时
     */
    public void markHeartbeatTimeout() {
        validateTransition(TaskStatus.HEARTBEAT_TIMEOUT);
        this.status = TaskStatus.HEARTBEAT_TIMEOUT;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 标记为重试状态
     */
    public void markForRetry() {
        validateTransition(TaskStatus.RETRY);
        this.status = TaskStatus.RETRY;
        this.retryCount++;
    }

    /**
     * 重新开始执行(用于重试)
     */
    public TaskExecution restart() {
        if (this.status != TaskStatus.RETRY) {
            throw new IllegalStateException("只有RETRY状态的任务才能重新开始");
        }

        return TaskExecution.builder()
                .taskId(TaskId.generate())
                .taskName(this.taskName)
                .bizKey(this.bizKey)
                .params(this.params)
                .status(TaskStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .retryCount(this.retryCount)
                .maxRetry(this.maxRetry)
                .heartbeatIntervalSeconds(this.heartbeatIntervalSeconds)
                .build();
    }

    /**
     * 更新心跳
     */
    public void updateHeartbeat() {
        if (this.status != TaskStatus.RUNNING) {
            throw new IllegalStateException("只有RUNNING状态的任务才能更新心跳");
        }
        this.lastHeartbeatTime = LocalDateTime.now();
    }

    /**
     * 启用心跳监控
     */
    public void enableHeartbeat(int intervalSeconds) {
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("心跳间隔必须大于0");
        }
        this.heartbeatIntervalSeconds = intervalSeconds;
        this.lastHeartbeatTime = LocalDateTime.now();
    }

    /**
     * 是否启用了心跳
     */
    public boolean isHeartbeatEnabled() {
        return heartbeatIntervalSeconds != null && heartbeatIntervalSeconds > 0;
    }

    /**
     * 判断是否可以重试
     */
    public boolean canRetry() {
        return retryCount < maxRetry;
    }

    /**
     * 判断是否为终态
     */
    public boolean isTerminated() {
        return status.isTerminal();
    }

    /**
     * 计算执行时长(秒)
     */
    public Long getDurationSeconds() {
        if (endTime == null) {
            return null;
        }
        return java.time.Duration.between(startTime, endTime).getSeconds();
    }

    private void validateTransition(TaskStatus newStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("任务状态不能从 %s 转换到 %s", status, newStatus));
        }
    }


}
