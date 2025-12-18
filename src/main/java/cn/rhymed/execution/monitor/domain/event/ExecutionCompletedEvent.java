package cn.rhymed.execution.monitor.domain.event;

import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务完成事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class ExecutionCompletedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ExecutionId executionId;
    private final ExecutionName executionName;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Long durationSeconds;
    private final LocalDateTime eventTime;

    public ExecutionCompletedEvent(ExecutionId executionId, ExecutionName executionName, LocalDateTime startTime,
                              LocalDateTime endTime, Long durationSeconds) {
        this.executionId = executionId;
        this.executionName = executionName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = durationSeconds;
        this.eventTime = LocalDateTime.now();
    }

    public ExecutionId getExecutionId() {
        return executionId;
    }

    public ExecutionName getExecutionName() {
        return executionName;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionCompletedEvent that = (ExecutionCompletedEvent) o;
        return Objects.equals(executionId, that.executionId) &&
                Objects.equals(eventTime, that.eventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionId, eventTime);
    }

    @Override
    public String toString() {
        return "ExecutionCompletedEvent{" +
                "executionId=" + executionId +
                ", executionName=" + executionName +
                ", durationSeconds=" + durationSeconds +
                '}';
    }
}
