package cn.rhymed.task.monitor.infrastructure.persistence.database.mapper;

import cn.rhymed.task.monitor.infrastructure.persistence.database.po.HeartbeatPO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 心跳记录Mapper
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Mapper
public interface HeartbeatMapper {

    /**
     * 插入心跳记录
     */
    @Insert("INSERT INTO task_heartbeat (task_id, interval_seconds, last_heartbeat, created_at, updated_at) " +
            "VALUES (#{taskId}, #{intervalSeconds}, #{lastHeartbeat}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HeartbeatPO heartbeat);

    /**
     * 根据ID更新
     */
    @Update("UPDATE task_heartbeat SET interval_seconds=#{intervalSeconds}, " +
            "last_heartbeat=#{lastHeartbeat}, updated_at=#{updatedAt} WHERE id=#{id}")
    int updateById(HeartbeatPO heartbeat);

    /**
     * 根据任务ID更新
     */
    @Update("UPDATE task_heartbeat SET interval_seconds=#{intervalSeconds}, " +
            "last_heartbeat=#{lastHeartbeat}, updated_at=#{updatedAt} WHERE task_id=#{taskId}")
    int updateByTaskId(HeartbeatPO heartbeat);

    /**
     * 根据任务ID查询
     */
    @Select("SELECT * FROM task_heartbeat WHERE task_id = #{taskId}")
    HeartbeatPO selectByTaskId(@Param("taskId") String taskId);

    /**
     * 查询所有心跳记录
     */
    @Select("SELECT * FROM task_heartbeat")
    List<HeartbeatPO> selectAll();

    /**
     * 查询超时的心跳记录
     * 超时判断: last_heartbeat + interval_seconds < currentTime
     */
    @Select("SELECT * FROM task_heartbeat WHERE " +
            "TIMESTAMPDIFF(SECOND, last_heartbeat, #{currentTime}) > interval_seconds")
    List<HeartbeatPO> selectTimeoutRecords(@Param("currentTime") LocalDateTime currentTime);

    /**
     * 根据任务ID删除
     */
    @Delete("DELETE FROM task_heartbeat WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") String taskId);

    /**
     * 批量删除
     */
    @Delete("<script>" +
            "DELETE FROM task_heartbeat WHERE task_id IN " +
            "<foreach collection='taskIds' item='taskId' open='(' separator=',' close=')'>" +
            "#{taskId}" +
            "</foreach>" +
            "</script>")
    int deleteBatch(@Param("taskIds") List<String> taskIds);

    /**
     * 清理指定时间之前的记录
     */
    @Delete("DELETE FROM task_heartbeat WHERE last_heartbeat < #{beforeTime}")
    int deleteBeforeTime(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 删除所有心跳记录
     */
    @Delete("DELETE FROM task_heartbeat")
    int deleteAll();
}
