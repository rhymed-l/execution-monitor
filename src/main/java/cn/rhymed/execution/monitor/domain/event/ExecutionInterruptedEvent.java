package cn.rhymed.execution.monitor.domain.event;

import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务中断事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class ExecutionInterruptedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ExecutionId executionId;
    private final ExecutionName executionName;
    private final String reason;
    private final LocalDateTime eventTime;

    public ExecutionInterruptedEvent(ExecutionId executionId, ExecutionName executionName, String reason) {
        this.executionId = executionId;
        this.executionName = executionName;
        this.reason = reason;
        this.eventTime = LocalDateTime.now();
    }

    public ExecutionId getExecutionId() {
        return executionId;
    }

    public ExecutionName getExecutionName() {
        return executionName;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionInterruptedEvent that = (ExecutionInterruptedEvent) o;
        return Objects.equals(executionId, that.executionId) &&
                Objects.equals(eventTime, that.eventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionId, eventTime);
    }

    @Override
    public String toString() {
        return "ExecutionInterruptedEvent{" +
                "executionId=" + executionId +
                ", executionName=" + executionName +
                ", reason='" + reason + '\'' +
                '}';
    }
}
