package cn.rhymed.task.monitor.domain.event;

import cn.rhymed.task.monitor.domain.model.ErrorInfo;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.model.TaskName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务失败事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class TaskFailedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TaskId taskId;
    private final TaskName taskName;
    private final ErrorInfo errorInfo;
    private final int retryCount;
    private final LocalDateTime eventTime;

    public TaskFailedEvent(TaskId taskId, TaskName taskName, ErrorInfo errorInfo, int retryCount) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.errorInfo = errorInfo;
        this.retryCount = retryCount;
        this.eventTime = LocalDateTime.now();
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public TaskName getTaskName() {
        return taskName;
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
        TaskFailedEvent that = (TaskFailedEvent) o;
        return Objects.equals(taskId, that.taskId) &&
                Objects.equals(eventTime, that.eventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, eventTime);
    }

    @Override
    public String toString() {
        return "TaskFailedEvent{" +
                "taskId=" + taskId +
                ", taskName=" + taskName +
                ", errorInfo=" + errorInfo +
                ", retryCount=" + retryCount +
                '}';
    }
}
