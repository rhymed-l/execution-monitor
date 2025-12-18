package cn.rhymed.task.monitor.domain.event;

import cn.rhymed.task.monitor.common.enums.RecoveryStrategy;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.model.TaskName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务恢复事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class TaskRecoveredEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TaskId originalTaskId;
    private final TaskId newTaskId;
    private final TaskName taskName;
    private final RecoveryStrategy strategy;
    private final LocalDateTime eventTime;

    public TaskRecoveredEvent(TaskId originalTaskId, TaskId newTaskId, TaskName taskName, RecoveryStrategy strategy) {
        this.originalTaskId = originalTaskId;
        this.newTaskId = newTaskId;
        this.taskName = taskName;
        this.strategy = strategy;
        this.eventTime = LocalDateTime.now();
    }

    public TaskId getOriginalTaskId() {
        return originalTaskId;
    }

    public TaskId getNewTaskId() {
        return newTaskId;
    }

    public TaskName getTaskName() {
        return taskName;
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
        TaskRecoveredEvent that = (TaskRecoveredEvent) o;
        return Objects.equals(originalTaskId, that.originalTaskId) &&
                Objects.equals(newTaskId, that.newTaskId) &&
                Objects.equals(eventTime, that.eventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalTaskId, newTaskId, eventTime);
    }

    @Override
    public String toString() {
        return "TaskRecoveredEvent{" +
                "originalTaskId=" + originalTaskId +
                ", newTaskId=" + newTaskId +
                ", taskName=" + taskName +
                ", strategy=" + strategy +
                '}';
    }
}
