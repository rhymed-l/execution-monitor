package cn.rhymed.task.monitor.application.dto;

import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务日志DTO
 * 传递给自定义恢复处理器
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Data
public class TaskLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 业务键,用于关联业务实体
     */
    private String bizKey;

    /**
     * 任务参数JSON字符串
     */
    private String paramsJson;

    /**
     * 任务状态
     */
    private TaskStatus status;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 异常类型全限定类名
     */
    private String exceptionType;

    /**
     * 任务开始时间
     */
    private LocalDateTime startTime;

    /**
     * 任务结束时间
     */
    private LocalDateTime endTime;

    /**
     * 当前重试次数
     */
    private int retryCount;

    /**
     * 最大重试次数
     */
    private int maxRetry;

    public static TaskLogDTO fromTaskExecution(TaskExecution execution) {
        TaskLogDTO dto = new TaskLogDTO();
        dto.setTaskId(execution.getTaskId().getValue());
        dto.setTaskName(execution.getTaskName().getValue());
        dto.setBizKey(execution.getBizKey().isPresent() ? execution.getBizKey().getValue() : null);
        dto.setParamsJson(execution.getParams().getJsonData());
        dto.setStatus(execution.getStatus());

        if (execution.getErrorInfo() != null) {
            dto.setErrorMessage(execution.getErrorInfo().getErrorMessage());
            dto.setExceptionType(execution.getErrorInfo().getExceptionType());
        }

        dto.setStartTime(execution.getStartTime());
        dto.setEndTime(execution.getEndTime());
        dto.setRetryCount(execution.getRetryCount());
        dto.setMaxRetry(execution.getMaxRetry());

        return dto;
    }

}
