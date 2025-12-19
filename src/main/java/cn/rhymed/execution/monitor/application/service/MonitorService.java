package cn.rhymed.execution.monitor.application.service;

import cn.rhymed.execution.monitor.common.enums.AlertType;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.*;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.domain.service.ExecutionRecordDomainService;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertDispatcher;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 任务监控应用服务
 * 协调任务监控的用例流程
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class MonitorService {

    private final ExecutionRecordDomainService domainService;
    private final ExecutionRecordRepository repository;
    private final AlertDispatcher alertDispatcher; // 可选的告警分发器

    public MonitorService(ExecutionRecordDomainService domainService,
                          ExecutionRecordRepository repository,
                          AlertDispatcher alertDispatcher) {
        this.domainService = domainService;
        this.repository = repository;
        this.alertDispatcher = alertDispatcher;

        if (alertDispatcher == null) {
            log.warn("⚠️  告警服务未配置！任务失败时将只记录日志，不会发送告警通知。");
        }
    }

    /**
     * 开始监控任务
     */
    public ExecutionId startMonitoring(String name, String bizKey, String paramsJson,
                                       MethodMetadata methodMetadata, int maxRetry, Set<AlertType> alertTypes) {
        ExecutionName executionName = ExecutionName.of(name);
        BizKey key = BizKey.of(bizKey);
        SerializedParams params = SerializedParams.of(paramsJson);

        ExecutionRecord execution = domainService.startExecution(executionName, key, params, methodMetadata, maxRetry, alertTypes);
        repository.save(execution);

        return execution.getExecutionId();
    }

    /**
     * 标记任务成功
     */
    public void markSuccess(ExecutionId executionId) {
        ExecutionRecord execution = repository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + executionId));

        domainService.completeExecution(execution);
        repository.update(execution);
    }

    /**
     * 标记任务失败
     * 会判断是否需要重试：
     * - 如果还有重试机会，标记为 AWAITING_RETRY 状态，发送重试告警
     * - 如果没有重试机会，标记为 FAILED 状态，发送失败告警
     */
    public void markFailure(ExecutionId executionId, Throwable throwable) {
        ExecutionRecord execution = repository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + executionId));

        // 记录异常信息
        ErrorInfo errorInfo = ErrorInfo.fromThrowable(throwable);

        // 判断是否还有重试机会
        if (execution.canRetry()) {
            // 还有重试机会，标记为可重试失败，然后标记为待重试
            int currentRetryCount = execution.getRetryCount();  // 0=首次执行，1=第1次重试，2=第2次重试
            execution.retryableFail(errorInfo);
            execution.markForRetry();  // 标记为RETRY状态

            log.info("任务失败，安排重试: executionId={}, retryCount={}, 下次将是第{}次重试（最多{}次）, 异常: {}",
                    execution.getExecutionId(),
                    currentRetryCount,
                    currentRetryCount + 1,
                    execution.getMaxRetry(),
                    errorInfo.getExceptionType());

            // 发送重试告警
            sendRetryAlert(execution, currentRetryCount);
        } else {
            // 没有重试机会（maxRetry = 0），直接标记为最终失败
            execution.fail(errorInfo);

            log.warn("任务首次失败且不支持重试，标记为最终失败: executionId={}, maxRetry={}, 异常: {}",
                    execution.getExecutionId(),
                    execution.getMaxRetry(),
                    errorInfo.getExceptionType());

            // 发送最终失败告警
            sendFailureAlert(execution);
        }

        repository.update(execution);
    }

    /**
     * 发送重试告警
     *
     * @param execution         执行记录
     * @param currentRetryCount 当前已完成的重试次数（首次执行失败为0，第1次重试失败为1，以此类推）
     */
    private void sendRetryAlert(ExecutionRecord execution, int currentRetryCount) {
        try {
            if (alertDispatcher != null) {
                alertDispatcher.sendRetryAlert(execution, currentRetryCount);
            } else {
                log.warn("⚠️  【告警】任务失败，将进行重试 - executionId: {}, executionName: {}, retryCount: {}/{}",
                        execution.getExecutionId().getValue(),
                        execution.getExecutionName().getValue(),
                        currentRetryCount,
                        execution.getMaxRetry());
            }
        } catch (Exception e) {
            log.error("发送重试告警失败", e);
        }
    }

    /**
     * 发送最终失败告警
     */
    private void sendFailureAlert(ExecutionRecord execution) {
        try {
            if (alertDispatcher != null) {
                alertDispatcher.sendFailureAlert(execution);
            } else {
                log.error("⚠️  【告警】任务最终失败 - executionId: {}, executionName: {}, maxRetry: {}, error: {}: {}",
                        execution.getExecutionId().getValue(),
                        execution.getExecutionName().getValue(),
                        execution.getMaxRetry(),
                        execution.getErrorInfo() != null ? execution.getErrorInfo().getExceptionType() : "Unknown",
                        execution.getErrorInfo() != null ? execution.getErrorInfo().getErrorMessage() : "No error message");
            }
        } catch (Exception e) {
            log.error("发送失败告警失败", e);
        }
    }

    /**
     * 更新心跳
     */
    public void updateHeartbeat(ExecutionId executionId) {
        ExecutionRecord execution = repository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + executionId));

        domainService.updateHeartbeat(execution);
        repository.update(execution);
    }

    /**
     * 启用心跳监控
     */
    public void enableHeartbeat(ExecutionId executionId, int intervalSeconds) {
        ExecutionRecord execution = repository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + executionId));

        domainService.enableHeartbeat(execution, intervalSeconds);
        repository.update(execution);
    }
}
