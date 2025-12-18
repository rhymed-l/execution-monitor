package cn.rhymed.execution.monitor.application.service;

import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.BizKey;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import cn.rhymed.execution.monitor.domain.model.SerializedParams;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.domain.service.ExecutionRecordDomainService;

/**
 * 任务监控应用服务
 * 协调任务监控的用例流程
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class MonitorService {

    private final ExecutionRecordDomainService domainService;
    private final ExecutionRecordRepository repository;

    public MonitorService(ExecutionRecordDomainService domainService,
                                   ExecutionRecordRepository repository) {
        this.domainService = domainService;
        this.repository = repository;
    }

    /**
     * 开始监控任务
     */
    public ExecutionId startMonitoring(String name, String bizKey, String paramsJson, int maxRetry) {
        ExecutionName executionName = ExecutionName.of(name);
        BizKey key = BizKey.of(bizKey);
        SerializedParams params = SerializedParams.of(paramsJson);

        ExecutionRecord execution = domainService.startExecution(executionName, key, params, maxRetry);
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
     */
    public void markFailure(ExecutionId executionId, Throwable throwable) {
        ExecutionRecord execution = repository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + executionId));

        domainService.failExecution(execution, throwable);
        repository.update(execution);
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
