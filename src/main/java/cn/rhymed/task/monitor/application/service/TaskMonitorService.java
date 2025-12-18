package cn.rhymed.task.monitor.application.service;

import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.domain.model.BizKey;
import cn.rhymed.task.monitor.domain.model.SerializedParams;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.model.TaskName;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.domain.service.TaskExecutionDomainService;

/**
 * 任务监控应用服务
 * 协调任务监控的用例流程
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class TaskMonitorService {

    private final TaskExecutionDomainService domainService;
    private final TaskExecutionRepository repository;

    public TaskMonitorService(TaskExecutionDomainService domainService,
                              TaskExecutionRepository repository) {
        this.domainService = domainService;
        this.repository = repository;
    }

    /**
     * 开始监控任务
     */
    public TaskId startMonitoring(String taskName, String bizKey, String paramsJson, int maxRetry) {
        TaskName name = TaskName.of(taskName);
        BizKey key = BizKey.of(bizKey);
        SerializedParams params = SerializedParams.of(paramsJson);

        TaskExecution execution = domainService.startTask(name, key, params, maxRetry);
        repository.save(execution);

        return execution.getTaskId();
    }

    /**
     * 标记任务成功
     */
    public void markSuccess(TaskId taskId) {
        TaskExecution execution = repository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        domainService.completeTask(execution);
        repository.update(execution);
    }

    /**
     * 标记任务失败
     */
    public void markFailure(TaskId taskId, Throwable throwable) {
        TaskExecution execution = repository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        domainService.failTask(execution, throwable);
        repository.update(execution);
    }

    /**
     * 更新心跳
     */
    public void updateHeartbeat(TaskId taskId) {
        TaskExecution execution = repository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        domainService.updateHeartbeat(execution);
        repository.update(execution);
    }

    /**
     * 启用心跳监控
     */
    public void enableHeartbeat(TaskId taskId, int intervalSeconds) {
        TaskExecution execution = repository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        domainService.enableHeartbeat(execution, intervalSeconds);
        repository.update(execution);
    }
}
