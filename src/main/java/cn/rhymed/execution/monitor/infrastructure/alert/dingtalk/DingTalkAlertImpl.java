package cn.rhymed.execution.monitor.infrastructure.alert.dingtalk;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.rhymed.execution.monitor.common.enums.AlertMessageType;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertService;
import cn.rhymed.execution.monitor.infrastructure.util.HostInfoUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 钉钉告警实现
 * <p>
 * 使用钉钉机器人的 Markdown 消息格式
 * <p>
 * 支持根据配置的消息类型过滤告警，只发送指定类型的告警消息。
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class DingTalkAlertImpl implements AlertService {

    private final String webhookUrl;
    private final String secretKey;
    private final boolean enabled;
    private final Set<AlertMessageType> messageTypes;

    /**
     * 构造函数
     *
     * @param webhookUrl   钉钉机器人 Webhook URL
     * @param secretKey    钉钉机器人加签密钥（可选）
     * @param enabled      是否启用告警
     * @param messageTypes 要发送的消息类型集合
     */
    public DingTalkAlertImpl(String webhookUrl, String secretKey, boolean enabled, Set<AlertMessageType> messageTypes) {
        this.webhookUrl = webhookUrl;
        this.secretKey = secretKey;
        this.enabled = enabled;
        this.messageTypes = messageTypes;
    }

    @Override
    public void sendFailureAlert(ExecutionRecord execution) {
        if (!enabled) {
            return;
        }

        // 检查是否配置了发送此类型的告警
        if (!shouldSendAlert(AlertMessageType.FAILURE)) {
            log.debug("跳过发送钉钉最终失败告警，未在 messageTypes 中配置");
            return;
        }

        String title = "🚨 【任务失败告警】";
        String content = buildFailureContent(execution);
        sendMessage(title, content);
    }

    @Override
    public void sendHeartbeatTimeoutAlert(ExecutionRecord execution) {
        if (!enabled) {
            return;
        }

        // 检查是否配置了发送此类型的告警
        if (!shouldSendAlert(AlertMessageType.HEARTBEAT_TIMEOUT)) {
            log.debug("跳过发送钉钉心跳超时告警，未在 messageTypes 中配置");
            return;
        }

        String title = "💔 【心跳超时告警】";
        String content = buildHeartbeatTimeoutContent(execution);
        sendMessage(title, content);
    }

    @Override
    public void sendRetryAlert(ExecutionRecord execution, int retryCount) {
        if (!enabled) {
            return;
        }

        // 检查是否配置了发送此类型的告警
        if (!shouldSendAlert(AlertMessageType.RETRY)) {
            log.debug("跳过发送钉钉重试告警，未在 messageTypes 中配置");
            return;
        }

        String title = "⚠️  【任务执行失败告警】";
        String content = buildRetryContent(execution, retryCount);
        sendMessage(title, content);
    }

    /**
     * 判断是否应该发送指定类型的告警
     *
     * @param messageType 告警消息类型
     * @return true=应该发送，false=不发送
     */
    private boolean shouldSendAlert(AlertMessageType messageType) {
        return messageTypes != null && messageTypes.contains(messageType);
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

    private String buildFailureContent(ExecutionRecord execution) {
        StringBuilder content = new StringBuilder();
        content.append("**📋 任务名称**: ").append(execution.getExecutionName().getValue()).append("\n\n");
        content.append("**🔑 任务ID**: ").append(execution.getExecutionId().getValue()).append("\n\n");

        if (execution.getBizKey() != null && execution.getBizKey().isPresent()) {
            content.append("**🏷️ 业务标识**: ").append(execution.getBizKey().getValue()).append("\n\n");
        }

        content.append("**📊 状态**: ").append(execution.getStatus()).append("\n\n");
        content.append("**🔄 重试次数**: ").append(execution.getRetryCount())
                .append("/").append(execution.getMaxRetry()).append("\n\n");

        // 添加详细的错误信息
        if (execution.getErrorInfo() != null) {
            content.append("**❌ 错误信息**:\n\n");
            content.append("- **异常类型**: ").append(execution.getErrorInfo().getExceptionType()).append("\n\n");

            if (StrUtil.isNotBlank(execution.getErrorInfo().getErrorMessage())) {
                content.append("- **异常消息**: ").append(execution.getErrorInfo().getErrorMessage()).append("\n\n");
            }

            // 添加堆栈跟踪（截取前800个字符，避免消息过长）
            if (StrUtil.isNotBlank(execution.getErrorInfo().getStackTrace())) {
                String stackTrace = execution.getErrorInfo().getStackTrace();
                if (stackTrace.length() > 800) {
                    stackTrace = stackTrace.substring(0, 800) + "\n... (堆栈信息过长，已截断)";
                }
                content.append("**📝 堆栈跟踪**:\n\n");
                content.append("```\n").append(stackTrace).append("\n```");
            }
        }

        return content.toString();
    }

    private String buildHeartbeatTimeoutContent(ExecutionRecord execution) {
        StringBuilder content = new StringBuilder();
        content.append("**📋 任务名称**: ").append(execution.getExecutionName().getValue()).append("\n\n");
        content.append("**🔑 任务ID**: ").append(execution.getExecutionId().getValue()).append("\n\n");

        if (execution.getBizKey() != null && execution.getBizKey().isPresent()) {
            content.append("**🏷️ 业务标识**: ").append(execution.getBizKey().getValue()).append("\n\n");
        }

        content.append("**💓 心跳间隔**: ").append(execution.getHeartbeatIntervalSeconds()).append("秒\n\n");

        if (execution.getLastHeartbeatTime() != null) {
            content.append("**🕐 最后心跳**: ").append(execution.getLastHeartbeatTime());
        }

        return content.toString();
    }

    private String buildRetryContent(ExecutionRecord execution, int retryCount) {
        StringBuilder content = new StringBuilder();
        content.append("**📋 任务名称**: ").append(execution.getExecutionName().getValue()).append("\n\n");
        content.append("**🔑 任务ID**: ").append(execution.getExecutionId().getValue()).append("\n\n");

        if (execution.getBizKey() != null && execution.getBizKey().isPresent()) {
            content.append("**🏷️ 业务标识**: ").append(execution.getBizKey().getValue()).append("\n\n");
        }

        // retryCount = 0 表示首次执行失败，> 0 表示第N次重试失败
        if (retryCount == 0) {
            content.append("**🔄 失败状态**: 首次执行失败\n\n");
            content.append("**📊 重试计划**: 将进行第 1 次重试（最多 ").append(execution.getMaxRetry()).append(" 次）\n\n");
        } else {
            content.append("**🔄 失败状态**: 第 ").append(retryCount).append(" 次重试失败\n\n");
            content.append("**📊 重试进度**: ").append(retryCount).append("/").append(execution.getMaxRetry()).append(" 次\n\n");
        }

        // 添加预计重试时间
        if (execution.getNextRetryTime() != null) {
            content.append("**📅 预计重试时间**: ").append(execution.getNextRetryTime()).append("\n\n");

            // 计算距离重试的时间
            long minutesUntilRetry = java.time.Duration.between(
                    java.time.LocalDateTime.now(),
                    execution.getNextRetryTime()
            ).toMinutes();

            if (minutesUntilRetry > 0) {
                content.append("**⏱️ 距离重试**: 约 ").append(minutesUntilRetry).append(" 分钟\n\n");
            } else {
                content.append("**⏱️ 距离重试**: 即将重试\n\n");
            }
        }

        // 添加详细的错误信息
        if (execution.getErrorInfo() != null) {
            content.append("**❌ 错误信息**:\n\n");
            content.append("- **异常类型**: ").append(execution.getErrorInfo().getExceptionType()).append("\n\n");

            if (StrUtil.isNotBlank(execution.getErrorInfo().getErrorMessage())) {
                content.append("- **异常消息**: ").append(execution.getErrorInfo().getErrorMessage()).append("\n\n");
            }

            // 添加堆栈跟踪（截取前800个字符，避免消息过长）
            if (StrUtil.isNotBlank(execution.getErrorInfo().getStackTrace())) {
                String stackTrace = execution.getErrorInfo().getStackTrace();
                if (stackTrace.length() > 800) {
                    stackTrace = stackTrace.substring(0, 800) + "\n... (堆栈信息过长，已截断)";
                }
                content.append("**📝 堆栈跟踪**:\n\n");
                content.append("```\n").append(stackTrace).append("\n```");
            }
        }

        return content.toString();
    }
}
