package cn.rhymed.task.monitor.domain.event;

import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.model.TaskName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务中断事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class TaskInterruptedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TaskId taskId;
    private final TaskName taskName;
    private final String reason;
    private final LocalDateTime eventTime;

    public TaskInterruptedEvent(TaskId taskId, TaskName taskName, String reason) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.reason = reason;
        this.eventTime = LocalDateTime.now();
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public TaskName getTaskName() {
        return taskName;
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
        TaskInterruptedEvent that = (TaskInterruptedEvent) o;
        return Objects.equals(taskId, that.taskId) &&
                Objects.equals(eventTime, that.eventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, eventTime);
    }

    @Override
    public String toString() {
        return "TaskInterruptedEvent{" +
                "taskId=" + taskId +
                ", taskName=" + taskName +
                ", reason='" + reason + '\'' +
                '}';
    }
}
