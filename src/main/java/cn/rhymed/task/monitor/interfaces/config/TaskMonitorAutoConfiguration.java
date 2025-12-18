package cn.rhymed.task.monitor.interfaces.config;

import cn.rhymed.task.monitor.application.service.HeartbeatManagementService;
import cn.rhymed.task.monitor.application.service.TaskMonitorService;
import cn.rhymed.task.monitor.domain.repository.HeartbeatStorage;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.domain.service.TaskExecutionDomainService;
import cn.rhymed.task.monitor.infrastructure.alert.AlertService;
import cn.rhymed.task.monitor.infrastructure.alert.dingtalk.DingTalkAlertImpl;
import cn.rhymed.task.monitor.infrastructure.aop.TaskMonitorAspect;
import cn.rhymed.task.monitor.infrastructure.persistence.memory.MemoryHeartbeatStorage;
import cn.rhymed.task.monitor.infrastructure.persistence.memory.MemoryTaskExecutionRepository;
import cn.rhymed.task.monitor.infrastructure.scheduler.HealthCheckScheduler;
import cn.rhymed.task.monitor.infrastructure.scheduler.HeartbeatScheduler;
import cn.rhymed.task.monitor.infrastructure.util.ExceptionClassifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 任务监控自动配置
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "task.monitor.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TaskMonitorProperties.class)
@EnableScheduling
@Import({
        StorageConfiguration.class
})
public class TaskMonitorAutoConfiguration {

    public TaskMonitorAutoConfiguration(TaskMonitorProperties properties) {
        log.info("任务监控启动 - 存储类型: {}", properties.getStorageType());
    }

    /**
     * 领域服务
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskExecutionDomainService taskExecutionDomainService() {
        return new TaskExecutionDomainService();
    }

    /**
     * 序列化决策服务
     */
    @Bean
    @ConditionalOnMissingBean
    public cn.rhymed.task.monitor.domain.service.SerializationDecisionService serializationDecisionService() {
        return new cn.rhymed.task.monitor.domain.service.SerializationDecisionService();
    }

    /**
     * 恢复决策服务
     */
    @Bean
    @ConditionalOnMissingBean
    public cn.rhymed.task.monitor.domain.service.RecoveryDecisionService recoveryDecisionService() {
        return new cn.rhymed.task.monitor.domain.service.RecoveryDecisionService();
    }

    /**
     * 异常分类器
     */
    @Bean
    @ConditionalOnMissingBean
    public ExceptionClassifier exceptionClassifier(TaskMonitorProperties properties) {
        return new ExceptionClassifier(
                properties.getRetry().getRetryableExceptions(),
                properties.getRetry().getIgnorableExceptions()
        );
    }

    /**
     * 内存存储仓储(默认)
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "task.monitor.storage-type", havingValue = "memory", matchIfMissing = true)
    public TaskExecutionRepository memoryTaskExecutionRepository() {
        log.info("使用内存存储实现");
        return new MemoryTaskExecutionRepository();
    }

    /**
     * 应用服务
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskMonitorService taskMonitorService(TaskExecutionDomainService domainService,
                                                 TaskExecutionRepository repository) {
        return new TaskMonitorService(domainService, repository);
    }

    /**
     * AOP切面
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskMonitorAspect taskMonitorAspect(TaskMonitorService taskMonitorService,
                                               TaskMonitorProperties properties,
                                               cn.rhymed.task.monitor.domain.service.SerializationDecisionService serializationService) {
        log.info("注册TaskMonitor切面");
        return new TaskMonitorAspect(taskMonitorService, properties, serializationService);
    }

    /**
     * 内存心跳存储(默认)
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "task.monitor.storage-type", havingValue = "memory", matchIfMissing = true)
    public HeartbeatStorage memoryHeartbeatStorage() {
        return new MemoryHeartbeatStorage();
    }

    /**
     * 心跳管理服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "task.monitor.heartbeat.enabled", havingValue = "true")
    public HeartbeatManagementService heartbeatManagementService(HeartbeatStorage heartbeatStorage) {
        log.info("启用心跳监控");
        return new HeartbeatManagementService(heartbeatStorage);
    }

    /**
     * 心跳调度器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "task.monitor.heartbeat.enabled", havingValue = "true")
    public HeartbeatScheduler heartbeatScheduler(HeartbeatManagementService heartbeatService) {
        return new HeartbeatScheduler(heartbeatService);
    }

    /**
     * 健康检查调度器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "task.monitor.heartbeat.enabled", havingValue = "true")
    public HealthCheckScheduler healthCheckScheduler(HeartbeatManagementService heartbeatService,
                                                     TaskExecutionRepository taskRepository,
                                                     TaskExecutionDomainService domainService) {
        log.info("启用健康检查调度器");
        return new HealthCheckScheduler(heartbeatService, taskRepository, domainService);
    }

    /**
     * 钉钉告警服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "task.monitor.alert.dingtalk.enabled", havingValue = "true")
    public AlertService dingTalkAlertService(TaskMonitorProperties properties) {
        String webhookUrl = System.getProperty("task.monitor.alert.dingtalk.webhook-url", "");
        String secretKey = System.getProperty("task.monitor.alert.dingtalk.secret", "");
        log.info("启用钉钉告警服务");
        return new DingTalkAlertImpl(webhookUrl, secretKey, true);
    }

    /**
     * 任务恢复服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "task.monitor.recovery.enabled", havingValue = "true")
    public cn.rhymed.task.monitor.application.service.TaskRecoveryService taskRecoveryService(
            TaskExecutionRepository repository,
            cn.rhymed.task.monitor.domain.service.RecoveryDecisionService decisionService,
            TaskExecutionDomainService domainService) {
        log.info("启用任务自动恢复");
        return new cn.rhymed.task.monitor.application.service.TaskRecoveryService(
                repository, decisionService, domainService);
    }

    /**
     * 恢复处理器注册表
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "task.monitor.recovery.enabled", havingValue = "true")
    public cn.rhymed.task.monitor.application.service.RecoveryHandlerRegistry recoveryHandlerRegistry() {
        log.info("启用自定义恢复处理器注册表");
        return new cn.rhymed.task.monitor.application.service.RecoveryHandlerRegistry();
    }

    /**
     * 任务重试执行器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "task.monitor.recovery.enabled", havingValue = "true")
    public cn.rhymed.task.monitor.application.service.TaskRetryExecutor taskRetryExecutor(
            TaskExecutionRepository repository,
            cn.rhymed.task.monitor.application.service.RecoveryHandlerRegistry handlerRegistry) {
        return new cn.rhymed.task.monitor.application.service.TaskRetryExecutor(repository, handlerRegistry);
    }

    /**
     * 重试任务调度器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "task.monitor.recovery.enabled", havingValue = "true")
    public cn.rhymed.task.monitor.infrastructure.scheduler.RetryTaskScheduler retryTaskScheduler(
            cn.rhymed.task.monitor.application.service.TaskRetryExecutor retryExecutor) {
        log.info("启用重试任务调度器");
        return new cn.rhymed.task.monitor.infrastructure.scheduler.RetryTaskScheduler(retryExecutor);
    }
}
