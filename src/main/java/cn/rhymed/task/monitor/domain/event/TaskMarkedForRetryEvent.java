package cn.rhymed.task.monitor.domain.event;

import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.model.TaskName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务标记为重试事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class TaskMarkedForRetryEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TaskId taskId;
    private final TaskName taskName;
    private final int retryCount;
    private final long nextRetryTimeMillis;
    private final LocalDateTime eventTime;

    public TaskMarkedForRetryEvent(TaskId taskId, TaskName taskName, int retryCount, long nextRetryTimeMillis) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.retryCount = retryCount;
        this.nextRetryTimeMillis = nextRetryTimeMillis;
        this.eventTime = LocalDateTime.now();
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public TaskName getTaskName() {
        return taskName;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getNextRetryTimeMillis() {
        return nextRetryTimeMillis;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskMarkedForRetryEvent that = (TaskMarkedForRetryEvent) o;
        return Objects.equals(taskId, that.taskId) &&
                Objects.equals(eventTime, that.eventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, eventTime);
    }

    @Override
    public String toString() {
        return "TaskMarkedForRetryEvent{" +
                "taskId=" + taskId +
                ", taskName=" + taskName +
                ", retryCount=" + retryCount +
                ", nextRetryTimeMillis=" + nextRetryTimeMillis +
                '}';
    }
}
