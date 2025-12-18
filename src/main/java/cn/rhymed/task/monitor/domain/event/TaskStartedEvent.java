package cn.rhymed.task.monitor.domain.event;

import cn.rhymed.task.monitor.domain.model.BizKey;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.model.TaskName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务开始事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class TaskStartedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TaskId taskId;
    private final TaskName taskName;
    private final BizKey bizKey;
    private final LocalDateTime startTime;
    private final LocalDateTime eventTime;

    public TaskStartedEvent(TaskId taskId, TaskName taskName, BizKey bizKey, LocalDateTime startTime) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.bizKey = bizKey;
        this.startTime = startTime;
        this.eventTime = LocalDateTime.now();
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public TaskName getTaskName() {
        return taskName;
    }

    public BizKey getBizKey() {
        return bizKey;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskStartedEvent that = (TaskStartedEvent) o;
        return Objects.equals(taskId, that.taskId) &&
                Objects.equals(eventTime, that.eventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, eventTime);
    }

    @Override
    public String toString() {
        return "TaskStartedEvent{" +
                "taskId=" + taskId +
                ", taskName=" + taskName +
                ", startTime=" + startTime +
                '}';
    }
}
