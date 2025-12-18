package cn.rhymed.execution.monitor.domain.event;

import cn.rhymed.execution.monitor.domain.model.ErrorInfo;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务失败事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class ExecutionFailedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ExecutionId executionId;
    private final ExecutionName executionName;
    private final ErrorInfo errorInfo;
    private final int retryCount;
    private final LocalDateTime eventTime;

    public ExecutionFailedEvent(ExecutionId executionId, ExecutionName executionName, ErrorInfo errorInfo, int retryCount) {
        this.executionId = executionId;
        this.executionName = executionName;
        this.errorInfo = errorInfo;
        this.retryCount = retryCount;
        this.eventTime = LocalDateTime.now();
    }

    public ExecutionId getExecutionId() {
        return executionId;
    }

    public ExecutionName getExecutionName() {
        return executionName;
    }

    public ErrorInfo getErrorInfo() {
        return errorInfo;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionFailedEvent that = (ExecutionFailedEvent) o;
        return Objects.equals(executionId, that.executionId) &&
                Objects.equals(eventTime, that.eventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionId, eventTime);
    }

    @Override
    public String toString() {
        return "ExecutionFailedEvent{" +
                "executionId=" + executionId +
                ", executionName=" + executionName +
                ", errorInfo=" + errorInfo +
                ", retryCount=" + retryCount +
                '}';
    }
}
