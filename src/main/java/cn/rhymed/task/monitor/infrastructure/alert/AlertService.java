package cn.rhymed.task.monitor.infrastructure.alert;

import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;

/**
 * 告警服务接口
 * 定义任务异常时的告警行为
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public interface AlertService {

    /**
     * 发送任务失败告警
     */
    void sendFailureAlert(TaskExecution execution);

    /**
     * 发送心跳超时告警
     */
    void sendHeartbeatTimeoutAlert(TaskExecution execution);

    /**
     * 发送任务重试告警
     */
    void sendRetryAlert(TaskExecution execution, int retryCount);

    /**
     * 发送自定义告警
     */
    void sendCustomAlert(String title, String content);
}
