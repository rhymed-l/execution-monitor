package cn.rhymed.task.monitor.domain.entity;

import cn.rhymed.task.monitor.domain.model.TaskId;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 心跳记录实体
 * 跟踪任务的心跳状态
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class HeartbeatRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     * 实体的唯一标识,关联到具体的任务执行实例
     */
    private final TaskId taskId;
    /**
     * 心跳间隔(秒)
     * 定义心跳的检测间隔,超过此时间未收到心跳视为超时
     * 不可变
     */
    private final int heartbeatIntervalSeconds;
    /**
     * 最后心跳时间
     * 记录任务最近一次发送心跳的时间点
     * 可更新
     */
    private LocalDateTime lastHeartbeatTime;

    public HeartbeatRecord(TaskId taskId, LocalDateTime lastHeartbeatTime, int heartbeatIntervalSeconds) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId不能为空");
        }
        if (lastHeartbeatTime == null) {
            throw new IllegalArgumentException("lastHeartbeatTime不能为空");
        }
        if (heartbeatIntervalSeconds <= 0) {
            throw new IllegalArgumentException("heartbeatIntervalSeconds必须大于0");
        }

        this.taskId = taskId;
        this.lastHeartbeatTime = lastHeartbeatTime;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    /**
     * 更新心跳时间
     */
    public void updateHeartbeat(LocalDateTime newHeartbeatTime) {
        if (newHeartbeatTime == null) {
            throw new IllegalArgumentException("newHeartbeatTime不能为空");
        }
        if (newHeartbeatTime.isBefore(this.lastHeartbeatTime)) {
            throw new IllegalArgumentException("新心跳时间不能早于上次心跳时间");
        }
        this.lastHeartbeatTime = newHeartbeatTime;
    }

    /**
     * 判断心跳是否超时
     */
    public boolean isTimeout(LocalDateTime currentTime) {
        if (currentTime == null) {
            throw new IllegalArgumentException("currentTime不能为空");
        }

        LocalDateTime timeoutThreshold = lastHeartbeatTime.plusSeconds(heartbeatIntervalSeconds);
        return currentTime.isAfter(timeoutThreshold);
    }

    /**
     * 计算距离超时还有多少秒
     * 返回负数表示已超时
     */
    public long secondsUntilTimeout(LocalDateTime currentTime) {
        if (currentTime == null) {
            throw new IllegalArgumentException("currentTime不能为空");
        }

        LocalDateTime timeoutThreshold = lastHeartbeatTime.plusSeconds(heartbeatIntervalSeconds);
        return java.time.Duration.between(currentTime, timeoutThreshold).getSeconds();
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public LocalDateTime getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HeartbeatRecord that = (HeartbeatRecord) o;
        return Objects.equals(taskId, that.taskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId);
    }

    @Override
    public String toString() {
        return "HeartbeatRecord{" +
                "taskId=" + taskId +
                ", lastHeartbeatTime=" + lastHeartbeatTime +
                ", heartbeatIntervalSeconds=" + heartbeatIntervalSeconds +
                '}';
    }
}
