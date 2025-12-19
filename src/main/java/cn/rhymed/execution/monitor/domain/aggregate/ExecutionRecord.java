package cn.rhymed.execution.monitor.domain.aggregate;

import cn.rhymed.execution.monitor.common.enums.AlertType;
import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.model.*;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 执行记录聚合根
 * 封装执行过程的完整生命周期和业务规则
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
public class ExecutionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 执行唯一标识
     * 作为聚合根的唯一标识,保证执行的全局唯一性
     */
    private final ExecutionId executionId;

    /**
     * 执行名称
     * 标识执行类型,通常对应被监控的方法名或业务名称
     */
    private final ExecutionName executionName;

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
     * 方法元信息
     * 包含方法调用所需的完整信息：Bean名称、类名、方法签名
     * 用于重试时自动恢复方法调用
     */
    @Builder.Default
    private final MethodMetadata methodMetadata = MethodMetadata.empty();

    /**
     * 执行开始时间
     * 记录执行首次启动的时间点,不可变
     */
    private final LocalDateTime startTime;
    /**
     * 最大重试次数
     * 执行失败后允许重试的最大次数
     * 默认为3次
     */
    @Builder.Default
    private final int maxRetry = 3;
    /**
     * 执行状态
     * 标识执行当前所处的生命周期阶段
     * 默认为RUNNING状态
     */
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.RUNNING;
    /**
     * 错误信息
     * 执行失败时记录的异常详情,包含错误消息、异常类型和堆栈跟踪
     * 只有在执行失败时才会有值
     */
    private ErrorInfo errorInfo;
    /**
     * 执行结束时间
     * 记录执行完成或失败的时间点
     * 仅在执行进入终态时设置
     */
    private LocalDateTime endTime;
    /**
     * 最后心跳时间
     * 记录执行最近一次心跳更新的时间
     * 用于判断长时间运行执行是否超时
     */
    private LocalDateTime lastHeartbeatTime;
    /**
     * 当前重试次数
     * 记录执行已经重试的次数
     * 默认为0,每次重试递增
     */
    @Builder.Default
    private int retryCount = 0;
    /**
     * 心跳间隔(秒)
     * 长时间运行执行的心跳检测间隔
     * 为null表示未启用心跳监控
     */
    private Integer heartbeatIntervalSeconds;
    /**
     * 告警类型集合
     * 指定该执行记录使用哪些告警服务
     * DEFAULT: 使用所有已启用的告警服务
     * 具体告警类型: 仅使用指定的告警服务
     * NONE: 禁用告警
     * 默认为 DEFAULT
     */
    @Builder.Default
    private final Set<AlertType> alertTypes = new HashSet<>(Collections.singletonList(AlertType.DEFAULT));
    /**
     * 下次重试时间
     * 任务标记为 AWAITING_RETRY 状态时计算，用于告知用户预计重试时间
     * 调度器只会处理到达或超过此时间的任务
     */
    private LocalDateTime nextRetryTime;

    /**
     * 创建新的执行记录（带告警类型）
     */
    public static ExecutionRecord create(ExecutionName executionName, BizKey bizKey, SerializedParams params,
                                         MethodMetadata methodMetadata, int maxRetry, Set<AlertType> alertTypes) {
        return ExecutionRecord.builder()
                .executionId(ExecutionId.generate())
                .executionName(executionName)
                .bizKey(bizKey)
                .params(params)
                .methodMetadata(methodMetadata != null ? methodMetadata : MethodMetadata.empty())
                .status(ExecutionStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .retryCount(0)
                .maxRetry(maxRetry)
                .alertTypes(alertTypes != null && !alertTypes.isEmpty() ? alertTypes : new HashSet<>(Collections.singletonList(AlertType.DEFAULT)))
                .build();
    }

    /**
     * 创建新的执行记录（使用默认告警类型）
     * 向后兼容的重载方法
     */
    public static ExecutionRecord create(ExecutionName executionName, BizKey bizKey, SerializedParams params,
                                         MethodMetadata methodMetadata, int maxRetry) {
        return create(executionName, bizKey, params, methodMetadata, maxRetry,
                new HashSet<>(Collections.singletonList(AlertType.DEFAULT)));
    }

    /**
     * 执行成功完成
     */
    public void complete() {
        validateTransition(ExecutionStatus.SUCCESS);
        this.status = ExecutionStatus.SUCCESS;
        this.endTime = LocalDateTime.now();
        this.errorInfo = null;
    }

    /**
     * 执行失败（最终失败，不可重试）
     */
    public void fail(ErrorInfo errorInfo) {
        if (errorInfo == null) {
            throw new IllegalArgumentException("errorInfo不能为空");
        }
        validateTransition(ExecutionStatus.FAILED);
        this.status = ExecutionStatus.FAILED;
        this.endTime = LocalDateTime.now();
        this.errorInfo = errorInfo;
    }

    /**
     * 可重试的失败（还有重试机会）
     * 与fail()的区别：
     * - fail() 表示最终失败，不再重试
     * - retryableFail() 表示暂时失败，还有重试机会
     */
    public void retryableFail(ErrorInfo errorInfo) {
        if (errorInfo == null) {
            throw new IllegalArgumentException("errorInfo不能为空");
        }
        validateTransition(ExecutionStatus.RETRYABLE_FAILED);
        this.status = ExecutionStatus.RETRYABLE_FAILED;
        // 注意：可重试失败不设置 endTime，因为任务还没有真正结束
        this.errorInfo = errorInfo;
    }

    /**
     * 执行被中断
     */
    public void interrupt() {
        validateTransition(ExecutionStatus.INTERRUPTED);
        this.status = ExecutionStatus.INTERRUPTED;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 心跳超时
     */
    public void markHeartbeatTimeout() {
        validateTransition(ExecutionStatus.HEARTBEAT_TIMEOUT);
        this.status = ExecutionStatus.HEARTBEAT_TIMEOUT;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 标记为等待重试状态，并计算下次重试时间
     * 使用指数退避策略：1分钟 → 5分钟 → 15分钟 → 30分钟
     */
    public void markForRetry() {
        validateTransition(ExecutionStatus.AWAITING_RETRY);
        this.status = ExecutionStatus.AWAITING_RETRY;
        this.nextRetryTime = calculateNextRetryTime();
    }

    /**
     * 计算下次重试时间
     * 指数退避策略：
     * - retryCount=0 (首次失败): 1分钟后
     * - retryCount=1 (第1次重试失败): 5分钟后
     * - retryCount=2 (第2次重试失败): 15分钟后
     * - retryCount=3+ (第3次及以后): 30分钟后
     */
    private LocalDateTime calculateNextRetryTime() {
        long delayMinutes;
        switch (this.retryCount) {
            case 0:
                delayMinutes = 1;  // 首次失败，1分钟后重试
                break;
            case 1:
                delayMinutes = 5;  // 第1次重试失败，5分钟后重试
                break;
            case 2:
                delayMinutes = 15;  // 第2次重试失败，15分钟后重试
                break;
            default:
                delayMinutes = 30;  // 第3次及以后，30分钟后重试
                break;
        }
        return LocalDateTime.now().plusMinutes(delayMinutes);
    }

    /**
     * 递增重试次数
     * 在开始执行重试前调用
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * 重新开始执行(用于重试)
     * 保留原有的告警类型配置，但不继承 errorInfo（每次重试记录新的异常）
     */
    public ExecutionRecord restart() {
        if (this.status != ExecutionStatus.AWAITING_RETRY) {
            throw new IllegalStateException("只有AWAITING_RETRY状态的执行才能重新开始");
        }

        return ExecutionRecord.builder()
                .executionId(ExecutionId.generate())
                .executionName(this.executionName)
                .bizKey(this.bizKey)
                .params(this.params)
                .methodMetadata(this.methodMetadata)
                .status(ExecutionStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .retryCount(this.retryCount)
                .maxRetry(this.maxRetry)
                .heartbeatIntervalSeconds(this.heartbeatIntervalSeconds)
                .alertTypes(this.alertTypes)  // 继承告警类型配置
                .build();
    }

    /**
     * 更新心跳
     */
    public void updateHeartbeat() {
        if (this.status != ExecutionStatus.RUNNING) {
            throw new IllegalStateException("只有RUNNING状态的执行才能更新心跳");
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

    private void validateTransition(ExecutionStatus newStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("执行状态不能从 %s 转换到 %s", status, newStatus));
        }
    }


}
