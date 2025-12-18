package cn.rhymed.execution.monitor.infrastructure.persistence.database.mapper;

import cn.rhymed.execution.monitor.infrastructure.persistence.database.po.ExecutionLogPO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行日志Mapper
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Mapper
public interface ExecutionLogMapper {

    /**
     * 插入执行记录
     */
    @Insert("INSERT INTO execution_log (execution_id, execution_name, biz_key, params_json, params_size_bytes, " +
            "status, error_message, exception_type, stack_trace, start_time, end_time, retry_count, max_retry, " +
            "heartbeat_interval_seconds, created_at, updated_at) " +
            "VALUES (#{executionId}, #{executionName}, #{bizKey}, #{paramsJson}, #{paramsSizeBytes}, " +
            "#{status}, #{errorMessage}, #{exceptionType}, #{stackTrace}, #{startTime}, #{endTime}, #{retryCount}, #{maxRetry}, " +
            "#{heartbeatIntervalSeconds}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ExecutionLogPO executionLog);

    /**
     * 根据执行ID更新
     */
    @Update("UPDATE execution_log SET execution_name=#{executionName}, biz_key=#{bizKey}, " +
            "params_json=#{paramsJson}, params_size_bytes=#{paramsSizeBytes}, status=#{status}, " +
            "error_message=#{errorMessage}, exception_type=#{exceptionType}, stack_trace=#{stackTrace}, start_time=#{startTime}, " +
            "end_time=#{endTime}, retry_count=#{retryCount}, max_retry=#{maxRetry}, " +
            "heartbeat_interval_seconds=#{heartbeatIntervalSeconds}, " +
            "updated_at=#{updatedAt} WHERE execution_id=#{executionId}")
    int updateByExecutionId(ExecutionLogPO executionLog);

    /**
     * 根据执行ID查询
     */
    @Select("SELECT * FROM execution_log WHERE execution_id = #{executionId}")
    ExecutionLogPO selectByExecutionId(@Param("executionId") String executionId);

    /**
     * 根据状态查询
     */
    @Select("SELECT * FROM execution_log WHERE status = #{status}")
    List<ExecutionLogPO> selectByStatus(@Param("status") String status);

    /**
     * 查询所有执行记录
     */
    @Select("SELECT * FROM execution_log")
    List<ExecutionLogPO> selectAll();

    /**
     * 根据执行ID删除
     */
    @Delete("DELETE FROM execution_log WHERE execution_id = #{executionId}")
    int deleteByExecutionId(@Param("executionId") String executionId);

    /**
     * 删除所有记录
     */
    @Delete("DELETE FROM execution_log")
    int deleteAll();

    /**
     * 批量插入执行记录
     */
    @Insert("<script>" +
            "INSERT INTO execution_log (execution_id, execution_name, biz_key, params_json, params_size_bytes, " +
            "status, error_message, exception_type, stack_trace, start_time, end_time, retry_count, max_retry, " +
            "heartbeat_interval_seconds, created_at, updated_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.executionId}, #{item.executionName}, #{item.bizKey}, #{item.paramsJson}, #{item.paramsSizeBytes}, " +
            "#{item.status}, #{item.errorMessage}, #{item.exceptionType}, #{item.stackTrace}, #{item.startTime}, " +
            "#{item.endTime}, #{item.retryCount}, #{item.maxRetry}, #{item.heartbeatIntervalSeconds}, " +
            "#{item.createdAt}, #{item.updatedAt})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<ExecutionLogPO> executionLogs);

    /**
     * 根据执行名称查询
     */
    @Select("SELECT * FROM execution_log WHERE execution_name = #{executionName} ORDER BY created_at DESC")
    List<ExecutionLogPO> selectByExecutionName(@Param("executionName") String name);

    /**
     * 根据执行名称和状态查询
     */
    @Select("SELECT * FROM execution_log WHERE execution_name = #{executionName} AND status = #{status} ORDER BY created_at DESC")
    List<ExecutionLogPO> selectByExecutionNameAndStatus(@Param("executionName") String name, @Param("status") String status);

    /**
     * 查询可恢复的执行记录
     */
    @Select("<script>" +
            "SELECT * FROM execution_log WHERE status IN " +
            "<foreach collection='statuses' item='status' open='(' separator=',' close=')'>" +
            "#{status}" +
            "</foreach>" +
            " ORDER BY created_at DESC" +
            "</script>")
    List<ExecutionLogPO> selectRecoverableExecutions(@Param("statuses") List<String> statuses);

    /**
     * 查询需要重试的执行记录
     * FAILED状态且重试次数未达到最大值
     */
    @Select("SELECT * FROM execution_log " +
            "WHERE status = 'FAILED' " +
            "AND retry_count < max_retry " +
            "ORDER BY created_at DESC")
    List<ExecutionLogPO> selectExecutionsForRetry();

    /**
     * 查询心跳超时的运行中执行记录
     * 运行中且最后更新时间超过心跳间隔
     */
    @Select("SELECT * FROM execution_log " +
            "WHERE status = 'RUNNING' " +
            "AND heartbeat_interval_seconds IS NOT NULL " +
            "AND TIMESTAMPDIFF(SECOND, updated_at, #{currentTime}) > heartbeat_interval_seconds " +
            "ORDER BY start_time ASC")
    List<ExecutionLogPO> selectRunningExecutionsWithHeartbeatBefore(@Param("currentTime") LocalDateTime currentTime);

    /**
     * 统计指定状态的执行记录数量
     */
    @Select("SELECT COUNT(*) FROM execution_log WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /**
     * 统计指定执行名称的执行次数
     */
    @Select("SELECT COUNT(*) FROM execution_log WHERE execution_name = #{executionName}")
    long countByExecutionName(@Param("executionName") String name);

    /**
     * 清理指定时间和状态的执行记录
     */
    @Delete("<script>" +
            "DELETE FROM execution_log WHERE created_at &lt; #{beforeTime} " +
            "<if test='statuses != null and statuses.size() > 0'>" +
            "AND status IN " +
            "<foreach collection='statuses' item='status' open='(' separator=',' close=')'>" +
            "#{status}" +
            "</foreach>" +
            "</if>" +
            "</script>")
    int cleanupBefore(@Param("beforeTime") LocalDateTime beforeTime, @Param("statuses") List<String> statuses);
}
