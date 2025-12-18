package cn.rhymed.task.monitor.application.service;

import cn.rhymed.task.monitor.domain.aggregate.RecoveryPolicy;
import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.domain.service.RecoveryDecisionService;
import cn.rhymed.task.monitor.domain.service.TaskExecutionDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.Arrays;
import java.util.List;

/**
 * 任务恢复服务
 * 在应用启动时自动检测并恢复中断的任务
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class TaskRecoveryService implements ApplicationRunner {

    private final TaskExecutionRepository repository;
    private final RecoveryDecisionService decisionService;
    private final TaskExecutionDomainService domainService;

    public TaskRecoveryService(TaskExecutionRepository repository,
                               RecoveryDecisionService decisionService,
                               TaskExecutionDomainService domainService) {
        this.repository = repository;
        this.decisionService = decisionService;
        this.domainService = domainService;
    }

    /**
     * 应用启动时执行恢复检查
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始任务恢复检查...");
        try {
            int recoveredCount = recoverAllTasks();
            log.info("任务恢复检查完成, 恢复任务数: {}", recoveredCount);
        } catch (Exception e) {
            log.error("任务恢复检查失败", e);
        }
    }

    /**
     * 查找可恢复的任务
     */
    public List<TaskExecution> findRecoverableTasks() {
        List<TaskStatus> recoverableStatuses = Arrays.asList(
                TaskStatus.INTERRUPTED,
                TaskStatus.HEARTBEAT_TIMEOUT,
                TaskStatus.FAILED,
                TaskStatus.RETRY
        );

        return repository.findRecoverableTasks(recoverableStatuses);
    }

    /**
     * 恢复所有适用的任务
     */
    public int recoverAllTasks() {
        List<TaskExecution> recoverableTasks = findRecoverableTasks();

        if (recoverableTasks.isEmpty()) {
            log.debug("没有需要恢复的任务");
            return 0;
        }

        log.info("发现 {} 个可恢复任务", recoverableTasks.size());

        int recoveredCount = 0;
        for (TaskExecution execution : recoverableTasks) {
            try {
                // 获取恢复策略(实际应用中应该从配置或数据库读取)
                RecoveryPolicy policy = RecoveryPolicy.defaultPolicy(execution.getTaskName());

                // 判断是否应该恢复
                if (decisionService.shouldRecover(execution, policy)) {
                    if (scheduleForRetry(execution, policy)) {
                        recoveredCount++;
                    }
                }
            } catch (Exception e) {
                log.error("恢复任务失败: {}", execution.getTaskId(), e);
            }
        }

        return recoveredCount;
    }

    /**
     * 将任务安排为重试
     */
    public boolean scheduleForRetry(TaskExecution execution, RecoveryPolicy policy) {
        // 检查是否还能重试
        if (!execution.canRetry()) {
            log.warn("任务已达到最大重试次数: {}", execution.getTaskId());
            return false;
        }

        // 标记为重试状态
        domainService.markForRetry(execution);
        repository.update(execution);

        log.info("任务已安排重试: {}, 重试次数: {}/{}",
                execution.getTaskId(),
                execution.getRetryCount(),
                execution.getMaxRetry());

        return true;
    }
}
