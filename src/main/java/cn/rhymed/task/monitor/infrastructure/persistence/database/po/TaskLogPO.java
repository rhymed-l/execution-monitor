package cn.rhymed.task.monitor.infrastructure.persistence.database.po;

import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务日志持久化对象
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Data
public class TaskLogPO {

    /**
     * 主键ID
     * 数据库自增主键
     */
    private Long id;

    /**
     * 任务ID
     * 任务的全局唯一标识,通常为UUID格式
     */
    private String taskId;

    /**
     * 任务名称
     * 标识任务类型,通常对应被监控的方法名
     */
    private String taskName;

    /**
     * 业务键
     * 关联业务实体的标识,如订单号、用户ID等
     * 可为null
     */
    private String bizKey;

    /**
     * 参数JSON
     * 以JSON格式存储的方法调用参数
     * 用于任务重试时恢复方法调用
     */
    private String paramsJson;

    /**
     * 参数大小(字节)
     * JSON参数以UTF-8编码后的字节数
     * 用于判断是否超过存储限制
     */
    private Integer paramsSizeBytes;

    /**
     * 任务状态
     * 枚举值: RUNNING/SUCCESS/FAILED/RETRY/INTERRUPTED/HEARTBEAT_TIMEOUT
     */
    private String status;

    /**
     * 错误消息
     * 任务失败时的异常描述信息
     * 只有失败时才有值
     */
    private String errorMessage;

    /**
     * 异常类型
     * 异常类的全限定名,如: java.lang.NullPointerException
     * 用于异常分类和判断是否可重试
     */
    private String exceptionType;

    /**
     * 堆栈跟踪
     * 异常的堆栈信息,用于问题排查
     * 最大长度2000字符
     */
    private String stackTrace;

    /**
     * 任务开始时间
     * 记录任务首次启动的时间点
     */
    private LocalDateTime startTime;

    /**
     * 任务结束时间
     * 记录任务完成或失败的时间点
     * 只有终态任务才有值
     */
    private LocalDateTime endTime;

    /**
     * 当前重试次数
     * 记录任务已经重试的次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     * 任务失败后允许重试的最大次数
     */
    private Integer maxRetry;

    /**
     * 心跳间隔(秒)
     * 长时间运行任务的心跳检测间隔
     * null表示未启用心跳监控
     */
    private Integer heartbeatIntervalSeconds;

    /**
     * 创建时间
     * 记录首次插入数据库的时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * 记录最后一次更新数据库的时间
     */
    private LocalDateTime updatedAt;

    /**
     * 从领域对象转换为PO
     */
    public static TaskLogPO fromDomain(TaskExecution execution) {
        TaskLogPO po = new TaskLogPO();
        po.setTaskId(execution.getTaskId().getValue());
        po.setTaskName(execution.getTaskName().getValue());
        po.setBizKey(execution.getBizKey().isPresent() ? execution.getBizKey().getValue() : null);
        po.setParamsJson(execution.getParams().getJsonData());
        po.setParamsSizeBytes(execution.getParams().getSizeBytes());
        po.setStatus(execution.getStatus().name());

        if (execution.getErrorInfo() != null) {
            po.setErrorMessage(execution.getErrorInfo().getErrorMessage());
            po.setExceptionType(execution.getErrorInfo().getExceptionType());
            po.setStackTrace(execution.getErrorInfo().getStackTrace());
        }

        po.setStartTime(execution.getStartTime());
        po.setEndTime(execution.getEndTime());
        po.setRetryCount(execution.getRetryCount());
        po.setMaxRetry(execution.getMaxRetry());
        po.setHeartbeatIntervalSeconds(execution.getHeartbeatIntervalSeconds());
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());

        return po;
    }

    /**
     * 转换为领域对象
     */
    public TaskExecution toDomain() {
        TaskId taskId = new TaskId(this.taskId);
        TaskName taskName = new TaskName(this.taskName);
        BizKey bizKey = this.bizKey != null ? new BizKey(this.bizKey) : BizKey.empty();
        SerializedParams params = SerializedParams.of(this.paramsJson);
        TaskStatus status = TaskStatus.valueOf(this.status);

        ErrorInfo errorInfo = null;
        if (this.errorMessage != null) {
            errorInfo = new ErrorInfo(this.errorMessage, this.exceptionType, this.stackTrace);
        }

        return TaskExecution.builder()
                .taskId(taskId)
                .taskName(taskName)
                .bizKey(bizKey)
                .params(params)
                .status(status)
                .errorInfo(errorInfo)
                .startTime(this.startTime)
                .endTime(this.endTime)
                .retryCount(this.retryCount)
                .maxRetry(this.maxRetry)
                .heartbeatIntervalSeconds(this.heartbeatIntervalSeconds)
                .build();
    }
}
