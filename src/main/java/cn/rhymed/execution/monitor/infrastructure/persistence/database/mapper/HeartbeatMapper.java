package cn.rhymed.execution.monitor.infrastructure.persistence.database.mapper;

import cn.rhymed.execution.monitor.infrastructure.persistence.database.po.HeartbeatPO;
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
    @Insert("INSERT INTO execution_heartbeat (execution_id, interval_seconds, last_heartbeat, created_at, updated_at) " +
            "VALUES (#{executionId}, #{intervalSeconds}, #{lastHeartbeat}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HeartbeatPO heartbeat);

    /**
     * 根据ID更新
     */
    @Update("UPDATE execution_heartbeat SET interval_seconds=#{intervalSeconds}, " +
            "last_heartbeat=#{lastHeartbeat}, updated_at=#{updatedAt} WHERE id=#{id}")
    int updateById(HeartbeatPO heartbeat);

    /**
     * 根据执行ID更新
     */
    @Update("UPDATE execution_heartbeat SET interval_seconds=#{intervalSeconds}, " +
            "last_heartbeat=#{lastHeartbeat}, updated_at=#{updatedAt} WHERE execution_id=#{executionId}")
    int updateByExecutionId(HeartbeatPO heartbeat);

    /**
     * 根据执行ID查询
     */
    @Select("SELECT * FROM execution_heartbeat WHERE execution_id = #{executionId}")
    HeartbeatPO selectByExecutionId(@Param("executionId") String executionId);

    /**
     * 查询所有心跳记录
     */
    @Select("SELECT * FROM execution_heartbeat")
    List<HeartbeatPO> selectAll();

    /**
     * 查询超时的心跳记录
     * 超时判断: last_heartbeat + interval_seconds < currentTime
     */
    @Select("SELECT * FROM execution_heartbeat WHERE " +
            "TIMESTAMPDIFF(SECOND, last_heartbeat, #{currentTime}) > interval_seconds")
    List<HeartbeatPO> selectTimeoutRecords(@Param("currentTime") LocalDateTime currentTime);

    /**
     * 根据执行ID删除
     */
    @Delete("DELETE FROM execution_heartbeat WHERE execution_id = #{executionId}")
    int deleteByExecutionId(@Param("executionId") String executionId);

    /**
     * 批量删除
     */
    @Delete("<script>" +
            "DELETE FROM execution_heartbeat WHERE execution_id IN " +
            "<foreach collection='executionIds' item='executionId' open='(' separator=',' close=')'>" +
            "#{executionId}" +
            "</foreach>" +
            "</script>")
    int deleteBatch(@Param("executionIds") List<String> executionIds);

    /**
     * 清理指定时间之前的记录
     */
    @Delete("DELETE FROM execution_heartbeat WHERE last_heartbeat < #{beforeTime}")
    int deleteBeforeTime(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 删除所有心跳记录
     */
    @Delete("DELETE FROM execution_heartbeat")
    int deleteAll();
}
