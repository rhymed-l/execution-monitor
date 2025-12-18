package cn.rhymed.execution.monitor.domain.event;

import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 心跳超时事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class HeartbeatTimeoutEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ExecutionId executionId;
    private final ExecutionName executionName;
    private final LocalDateTime lastHeartbeatTime;
    private final int heartbeatIntervalSeconds;
    private final LocalDateTime eventTime;

    public HeartbeatTimeoutEvent(ExecutionId executionId, ExecutionName executionName, LocalDateTime lastHeartbeatTime,
                                 int heartbeatIntervalSeconds) {
        this.executionId = executionId;
        this.executionName = executionName;
        this.lastHeartbeatTime = lastHeartbeatTime;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.eventTime = LocalDateTime.now();
    }

    public ExecutionId getExecutionId() {
        return executionId;
    }

    public ExecutionName getExecutionName() {
        return executionName;
    }

    public LocalDateTime getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HeartbeatTimeoutEvent that = (HeartbeatTimeoutEvent) o;
        return Objects.equals(executionId, that.executionId) &&
                Objects.equals(eventTime, that.eventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionId, eventTime);
    }

    @Override
    public String toString() {
        return "HeartbeatTimeoutEvent{" +
                "executionId=" + executionId +
                ", executionName =" + executionName +
                ", lastHeartbeatTime=" + lastHeartbeatTime +
                '}';
    }
}
