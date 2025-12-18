package cn.rhymed.task.monitor.infrastructure.persistence.database.po;

import cn.rhymed.task.monitor.domain.entity.HeartbeatRecord;
import cn.rhymed.task.monitor.domain.model.TaskId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 心跳记录持久化对象
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Data
public class HeartbeatPO {

    /**
     * 主键ID
     * 数据库自增主键
     */
    private Long id;

    /**
     * 任务ID
     * 关联到task_execution_log表的task_id
     */
    private String taskId;

    /**
     * 心跳间隔(秒)
     * 定义心跳检测的时间间隔
     */
    private Integer intervalSeconds;

    /**
     * 最后心跳时间
     * 记录任务最近一次发送心跳的时间点
     */
    private LocalDateTime lastHeartbeat;

    /**
     * 创建时间
     * 记录首次插入数据库的时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * 记录最后一次更新数据库的时间
     */
    private LocalDateTime updatedAt;

    /**
     * 从领域对象转换为PO
     */
    public static HeartbeatPO fromDomain(HeartbeatRecord record) {
        HeartbeatPO po = new HeartbeatPO();
        po.setTaskId(record.getTaskId().getValue());
        po.setIntervalSeconds(record.getHeartbeatIntervalSeconds());
        po.setLastHeartbeat(record.getLastHeartbeatTime());
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        return po;
    }

    /**
     * 转换为领域对象
     */
    public HeartbeatRecord toDomain() {
        TaskId taskId = new TaskId(this.taskId);
        return new HeartbeatRecord(taskId, this.lastHeartbeat, this.intervalSeconds);
    }
}
