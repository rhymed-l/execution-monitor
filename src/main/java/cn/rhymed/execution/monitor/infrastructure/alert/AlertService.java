package cn.rhymed.execution.monitor.infrastructure.alert;

import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;

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
    void sendFailureAlert(ExecutionRecord execution);

    /**
     * 发送心跳超时告警
     */
    void sendHeartbeatTimeoutAlert(ExecutionRecord execution);

    /**
     * 发送任务重试告警
     */
    void sendRetryAlert(ExecutionRecord execution, int retryCount);

    /**
     * 发送自定义告警
     */
    void sendCustomAlert(String title, String content);
}
