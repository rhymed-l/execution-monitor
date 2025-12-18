package cn.rhymed.execution.monitor.domain.event;

import cn.rhymed.execution.monitor.common.enums.RecoveryStrategy;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务恢复事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class ExecutionRecoveredEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ExecutionId originalExecutionId;
    private final ExecutionId newExecutionId;
    private final ExecutionName executionName;
    private final RecoveryStrategy strategy;
    private final LocalDateTime eventTime;

    public ExecutionRecoveredEvent(ExecutionId originalExecutionId, ExecutionId newExecutionId, ExecutionName executionName, RecoveryStrategy strategy) {
        this.originalExecutionId = originalExecutionId;
        this.newExecutionId = newExecutionId;
        this.executionName = executionName;
        this.strategy = strategy;
        this.eventTime = LocalDateTime.now();
    }

    public ExecutionId getOriginalExecutionId() {
        return originalExecutionId;
    }

    public ExecutionId getNewExecutionId() {
        return newExecutionId;
    }

    public ExecutionName getExecutionName() {
        return executionName;
    }

    public RecoveryStrategy getStrategy() {
        return strategy;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionRecoveredEvent that = (ExecutionRecoveredEvent) o;
        return Objects.equals(originalExecutionId, that.originalExecutionId) &&
                Objects.equals(newExecutionId, that.newExecutionId) &&
                Objects.equals(eventTime, that.eventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalExecutionId, newExecutionId, eventTime);
    }

    @Override
    public String toString() {
        return "ExecutionRecoveredEvent{" +
                "originalExecutionId=" + originalExecutionId +
                ", newExecutionId=" + newExecutionId +
                ", executionName=" + executionName +
                ", strategy=" + strategy +
                '}';
    }
}
