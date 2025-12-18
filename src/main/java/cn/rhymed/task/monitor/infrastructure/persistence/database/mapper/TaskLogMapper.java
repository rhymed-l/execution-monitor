package cn.rhymed.task.monitor.infrastructure.persistence.database.mapper;

import cn.rhymed.task.monitor.infrastructure.persistence.database.po.TaskLogPO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务日志Mapper
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Mapper
public interface TaskLogMapper {

    /**
     * 插入任务记录
     */
    @Insert("INSERT INTO task_execution_log (task_id, task_name, biz_key, params_json, params_size_bytes, " +
            "status, error_message, exception_type, stack_trace, start_time, end_time, retry_count, max_retry, " +
            "heartbeat_interval_seconds, created_at, updated_at) " +
            "VALUES (#{taskId}, #{taskName}, #{bizKey}, #{paramsJson}, #{paramsSizeBytes}, " +
            "#{status}, #{errorMessage}, #{exceptionType}, #{stackTrace}, #{startTime}, #{endTime}, #{retryCount}, #{maxRetry}, " +
            "#{heartbeatIntervalSeconds}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TaskLogPO taskLog);

    /**
     * 根据任务ID更新
     */
    @Update("UPDATE task_execution_log SET task_name=#{taskName}, biz_key=#{bizKey}, " +
            "params_json=#{paramsJson}, params_size_bytes=#{paramsSizeBytes}, status=#{status}, " +
            "error_message=#{errorMessage}, exception_type=#{exceptionType}, stack_trace=#{stackTrace}, start_time=#{startTime}, " +
            "end_time=#{endTime}, retry_count=#{retryCount}, max_retry=#{maxRetry}, " +
            "heartbeat_interval_seconds=#{heartbeatIntervalSeconds}, " +
            "updated_at=#{updatedAt} WHERE task_id=#{taskId}")
    int updateByTaskId(TaskLogPO taskLog);

    /**
     * 根据任务ID查询
     */
    @Select("SELECT * FROM task_execution_log WHERE task_id = #{taskId}")
    TaskLogPO selectByTaskId(@Param("taskId") String taskId);

    /**
     * 根据状态查询
     */
    @Select("SELECT * FROM task_execution_log WHERE status = #{status}")
    List<TaskLogPO> selectByStatus(@Param("status") String status);

    /**
     * 查询所有任务
     */
    @Select("SELECT * FROM task_execution_log")
    List<TaskLogPO> selectAll();

    /**
     * 根据任务ID删除
     */
    @Delete("DELETE FROM task_execution_log WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") String taskId);

    /**
     * 删除所有任务
     */
    @Delete("DELETE FROM task_execution_log")
    int deleteAll();

    /**
     * 批量插入任务记录
     */
    @Insert("<script>" +
            "INSERT INTO task_execution_log (task_id, task_name, biz_key, params_json, params_size_bytes, " +
            "status, error_message, exception_type, stack_trace, start_time, end_time, retry_count, max_retry, " +
            "heartbeat_interval_seconds, created_at, updated_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.taskId}, #{item.taskName}, #{item.bizKey}, #{item.paramsJson}, #{item.paramsSizeBytes}, " +
            "#{item.status}, #{item.errorMessage}, #{item.exceptionType}, #{item.stackTrace}, #{item.startTime}, " +
            "#{item.endTime}, #{item.retryCount}, #{item.maxRetry}, #{item.heartbeatIntervalSeconds}, " +
            "#{item.createdAt}, #{item.updatedAt})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<TaskLogPO> taskLogs);

    /**
     * 根据任务名称查询
     */
    @Select("SELECT * FROM task_execution_log WHERE task_name = #{taskName} ORDER BY created_at DESC")
    List<TaskLogPO> selectByTaskName(@Param("taskName") String taskName);

    /**
     * 根据任务名称和状态查询
     */
    @Select("SELECT * FROM task_execution_log WHERE task_name = #{taskName} AND status = #{status} ORDER BY created_at DESC")
    List<TaskLogPO> selectByTaskNameAndStatus(@Param("taskName") String taskName, @Param("status") String status);

    /**
     * 查询可恢复的任务
     */
    @Select("<script>" +
            "SELECT * FROM task_execution_log WHERE status IN " +
            "<foreach collection='statuses' item='status' open='(' separator=',' close=')'>" +
            "#{status}" +
            "</foreach>" +
            " ORDER BY created_at DESC" +
            "</script>")
    List<TaskLogPO> selectRecoverableTasks(@Param("statuses") List<String> statuses);

    /**
     * 查询需要重试的任务
     * FAILED状态且重试次数未达到最大值
     */
    @Select("SELECT * FROM task_execution_log " +
            "WHERE status = 'FAILED' " +
            "AND retry_count < max_retry " +
            "ORDER BY created_at DESC")
    List<TaskLogPO> selectTasksForRetry();

    /**
     * 查询心跳超时的运行中任务
     * 运行中且最后更新时间超过心跳间隔
     */
    @Select("SELECT * FROM task_execution_log " +
            "WHERE status = 'RUNNING' " +
            "AND heartbeat_interval_seconds IS NOT NULL " +
            "AND TIMESTAMPDIFF(SECOND, updated_at, #{currentTime}) > heartbeat_interval_seconds " +
            "ORDER BY start_time ASC")
    List<TaskLogPO> selectRunningTasksWithHeartbeatBefore(@Param("currentTime") LocalDateTime currentTime);

    /**
     * 统计指定状态的任务数量
     */
    @Select("SELECT COUNT(*) FROM task_execution_log WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /**
     * 统计指定任务名称的执行次数
     */
    @Select("SELECT COUNT(*) FROM task_execution_log WHERE task_name = #{taskName}")
    long countByTaskName(@Param("taskName") String taskName);

    /**
     * 清理指定时间和状态的任务记录
     */
    @Delete("<script>" +
            "DELETE FROM task_execution_log WHERE created_at &lt; #{beforeTime} " +
            "<if test='statuses != null and statuses.size() > 0'>" +
            "AND status IN " +
            "<foreach collection='statuses' item='status' open='(' separator=',' close=')'>" +
            "#{status}" +
            "</foreach>" +
            "</if>" +
            "</script>")
    int cleanupBefore(@Param("beforeTime") LocalDateTime beforeTime, @Param("statuses") List<String> statuses);
}
