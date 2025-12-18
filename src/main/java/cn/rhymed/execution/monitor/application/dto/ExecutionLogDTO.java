package cn.rhymed.execution.monitor.application.dto;

import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 执行日志DTO
 * 传递给自定义恢复处理器
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Data
public class ExecutionLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 执行ID
     */
    private String executionId;

    /**
     * 执行名称
     */
    private String executionName;

    /**
     * 业务键,用于关联业务实体
     */
    private String bizKey;

    /**
     * 参数JSON字符串
     */
    private String paramsJson;

    /**
     * 执行状态
     */
    private ExecutionStatus status;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 异常类型全限定类名
     */
    private String exceptionType;

    /**
     * 执行开始时间
     */
    private LocalDateTime startTime;

    /**
     * 执行结束时间
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

    public static ExecutionLogDTO fromExecutionRecord(ExecutionRecord execution) {
        ExecutionLogDTO dto = new ExecutionLogDTO();
        dto.setExecutionId(execution.getExecutionId().getValue());
        dto.setExecutionName(execution.getExecutionName().getValue());
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
