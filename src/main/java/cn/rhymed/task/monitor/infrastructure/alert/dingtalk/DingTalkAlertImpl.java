package cn.rhymed.task.monitor.infrastructure.alert.dingtalk;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.infrastructure.alert.AlertService;
import cn.rhymed.task.monitor.infrastructure.util.HostInfoUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 钉钉告警实现
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class DingTalkAlertImpl implements AlertService {

    private final String webhookUrl;
    private final String secretKey;
    private final boolean enabled;

    public DingTalkAlertImpl(String webhookUrl, String secretKey, boolean enabled) {
        this.webhookUrl = webhookUrl;
        this.secretKey = secretKey;
        this.enabled = enabled;
    }

    @Override
    public void sendFailureAlert(TaskExecution execution) {
        if (!enabled) {
            return;
        }

        String title = "【任务失败告警】";
        String content = buildFailureContent(execution);
        sendMessage(title, content);
    }

    @Override
    public void sendHeartbeatTimeoutAlert(TaskExecution execution) {
        if (!enabled) {
            return;
        }

        String title = "【心跳超时告警】";
        String content = buildHeartbeatTimeoutContent(execution);
        sendMessage(title, content);
    }

    @Override
    public void sendRetryAlert(TaskExecution execution, int retryCount) {
        if (!enabled) {
            return;
        }

        String title = "【任务重试告警】";
        String content = buildRetryContent(execution, retryCount);
        sendMessage(title, content);
    }

    @Override
    public void sendCustomAlert(String title, String content) {
        if (!enabled) {
            return;
        }
        sendMessage(title, content);
    }

    private void sendMessage(String title, String content) {
        if (StrUtil.isBlank(webhookUrl)) {
            log.warn("钉钉Webhook URL未配置,跳过告警发送");
            return;
        }

        try {
            JSONObject message = new JSONObject();
            message.set("msgtype", "markdown");

            JSONObject markdown = new JSONObject();
            markdown.set("title", title);
            markdown.set("text", formatMarkdown(title, content));

            message.set("markdown", markdown);

            String response = HttpUtil.post(webhookUrl, message.toString());
            log.debug("钉钉告警发送成功: {}", response);
        } catch (Exception e) {
            log.error("发送钉钉告警失败", e);
        }
    }

    private String formatMarkdown(String title, String content) {
        String markdown = "### " + title + "\n\n" +
                content +
                "\n\n---\n" +
                "**主机**: " + HostInfoUtil.getHostIdentifier() + "\n" +
                "**时间**: " + java.time.LocalDateTime.now();
        return markdown;
    }

    private String buildFailureContent(TaskExecution execution) {
        StringBuilder content = new StringBuilder();
        content.append("**任务名称**: ").append(execution.getTaskName()).append("\n");
        content.append("**任务ID**: ").append(execution.getTaskId()).append("\n");
        if (execution.getBizKey().isPresent()) {
            content.append("**业务标识**: ").append(execution.getBizKey()).append("\n");
        }
        content.append("**状态**: ").append(execution.getStatus()).append("\n");
        if (execution.getErrorInfo() != null) {
            content.append("**异常类型**: ").append(execution.getErrorInfo().getExceptionType()).append("\n");
            content.append("**异常消息**: ").append(execution.getErrorInfo().getErrorMessage()).append("\n");
        }
        content.append("**重试次数**: ").append(execution.getRetryCount()).append("/").append(execution.getMaxRetry());
        return content.toString();
    }

    private String buildHeartbeatTimeoutContent(TaskExecution execution) {
        StringBuilder content = new StringBuilder();
        content.append("**任务名称**: ").append(execution.getTaskName()).append("\n");
        content.append("**任务ID**: ").append(execution.getTaskId()).append("\n");
        if (execution.getBizKey().isPresent()) {
            content.append("**业务标识**: ").append(execution.getBizKey()).append("\n");
        }
        content.append("**心跳间隔**: ").append(execution.getHeartbeatIntervalSeconds()).append("秒\n");
        content.append("**最后心跳**: ").append(execution.getLastHeartbeatTime());
        return content.toString();
    }

    private String buildRetryContent(TaskExecution execution, int retryCount) {
        String content = "**任务名称**: " + execution.getTaskName() + "\n" +
                "**任务ID**: " + execution.getTaskId() + "\n" +
                "**当前重试**: 第" + retryCount + "次\n" +
                "**最大重试**: " + execution.getMaxRetry() + "次";
        return content;
    }
}
