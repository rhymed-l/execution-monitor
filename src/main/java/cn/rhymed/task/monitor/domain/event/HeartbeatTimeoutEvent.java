package cn.rhymed.task.monitor.domain.event;

import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.model.TaskName;

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

    private final TaskId taskId;
    private final TaskName taskName;
    private final LocalDateTime lastHeartbeatTime;
    private final int heartbeatIntervalSeconds;
    private final LocalDateTime eventTime;

    public HeartbeatTimeoutEvent(TaskId taskId, TaskName taskName, LocalDateTime lastHeartbeatTime,
                                 int heartbeatIntervalSeconds) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.lastHeartbeatTime = lastHeartbeatTime;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.eventTime = LocalDateTime.now();
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public TaskName getTaskName() {
        return taskName;
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
        return Objects.equals(taskId, that.taskId) &&
                Objects.equals(eventTime, that.eventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, eventTime);
    }

    @Override
    public String toString() {
        return "HeartbeatTimeoutEvent{" +
                "taskId=" + taskId +
                ", taskName=" + taskName +
                ", lastHeartbeatTime=" + lastHeartbeatTime +
                '}';
    }
}
