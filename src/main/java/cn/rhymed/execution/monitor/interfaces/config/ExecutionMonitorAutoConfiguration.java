package cn.rhymed.execution.monitor.interfaces.config;

import cn.rhymed.execution.monitor.application.service.*;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.domain.repository.HeartbeatStorage;
import cn.rhymed.execution.monitor.domain.service.ExecutionRecordDomainService;
import cn.rhymed.execution.monitor.domain.service.RecoveryDecisionService;
import cn.rhymed.execution.monitor.domain.service.SerializationDecisionService;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertService;
import cn.rhymed.execution.monitor.infrastructure.alert.dingtalk.DingTalkAlertImpl;
import cn.rhymed.execution.monitor.infrastructure.aop.ExecutionMonitorAspect;
import cn.rhymed.execution.monitor.infrastructure.persistence.memory.MemoryExecutionRecordRepository;
import cn.rhymed.execution.monitor.infrastructure.persistence.memory.MemoryHeartbeatStorage;
import cn.rhymed.execution.monitor.infrastructure.scheduler.HealthCheckScheduler;
import cn.rhymed.execution.monitor.infrastructure.scheduler.HeartbeatScheduler;
import cn.rhymed.execution.monitor.infrastructure.scheduler.RetryExecutionScheduler;
import cn.rhymed.execution.monitor.infrastructure.util.ExceptionClassifier;
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
@ConditionalOnProperty(name = "execution.monitor.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ExecutionMonitorProperties.class)
@EnableScheduling
@Import({
        StorageConfiguration.class
})
public class ExecutionMonitorAutoConfiguration {

    public ExecutionMonitorAutoConfiguration(ExecutionMonitorProperties properties) {
        log.info("任务监控启动 - 存储类型: {}", properties.getStorageType());
    }

    /**
     * 领域服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ExecutionRecordDomainService ExecutionRecordDomainService() {
        return new ExecutionRecordDomainService();
    }

    /**
     * 序列化决策服务
     */
    @Bean
    @ConditionalOnMissingBean
    public SerializationDecisionService serializationDecisionService() {
        return new SerializationDecisionService();
    }

    /**
     * 恢复决策服务
     */
    @Bean
    @ConditionalOnMissingBean
    public RecoveryDecisionService recoveryDecisionService() {
        return new RecoveryDecisionService();
    }

    /**
     * 异常分类器
     */
    @Bean
    @ConditionalOnMissingBean
    public ExceptionClassifier exceptionClassifier(ExecutionMonitorProperties properties) {
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
    @ConditionalOnProperty(name = "execution.monitor.storage-type", havingValue = "memory", matchIfMissing = true)
    public ExecutionRecordRepository memoryExecutionRecordRepository() {
        log.info("使用内存存储实现");
        return new MemoryExecutionRecordRepository();
    }

    /**
     * 应用服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ExecutionMonitorService ExecutionMonitorService(ExecutionRecordDomainService domainService,
                                                           ExecutionRecordRepository repository) {
        return new ExecutionMonitorService(domainService, repository);
    }

    /**
     * AOP切面
     */
    @Bean
    @ConditionalOnMissingBean
    public ExecutionMonitorAspect ExecutionMonitorAspect(ExecutionMonitorService ExecutionMonitorService,
                                                         ExecutionMonitorProperties properties,
                                                         SerializationDecisionService serializationService) {
        log.info("注册ExecutionMonitor切面");
        return new ExecutionMonitorAspect(ExecutionMonitorService, properties, serializationService);
    }

    /**
     * 内存心跳存储(默认)
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "execution.monitor.storage-type", havingValue = "memory", matchIfMissing = true)
    public HeartbeatStorage memoryHeartbeatStorage() {
        return new MemoryHeartbeatStorage();
    }

    /**
     * 心跳管理服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "execution.monitor.heartbeat.enabled", havingValue = "true")
    public HeartbeatManagementService heartbeatManagementService(HeartbeatStorage heartbeatStorage) {
        log.info("启用心跳监控");
        return new HeartbeatManagementService(heartbeatStorage);
    }

    /**
     * 心跳调度器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "execution.monitor.heartbeat.enabled", havingValue = "true")
    public HeartbeatScheduler heartbeatScheduler(HeartbeatManagementService heartbeatService) {
        return new HeartbeatScheduler(heartbeatService);
    }

    /**
     * 健康检查调度器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "execution.monitor.heartbeat.enabled", havingValue = "true")
    public HealthCheckScheduler healthCheckScheduler(HeartbeatManagementService heartbeatService,
                                                     ExecutionRecordRepository executionRepository,
                                                     ExecutionRecordDomainService domainService) {
        log.info("启用健康检查调度器");
        return new HealthCheckScheduler(heartbeatService, executionRepository, domainService);
    }

    /**
     * 钉钉告警服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "execution.monitor.alert.dingtalk.enabled", havingValue = "true")
    public AlertService dingTalkAlertService(ExecutionMonitorProperties properties) {
        String webhookUrl = System.getProperty("execution.monitor.alert.dingtalk.webhook-url", "");
        String secretKey = System.getProperty("execution.monitor.alert.dingtalk.secret", "");
        log.info("启用钉钉告警服务");
        return new DingTalkAlertImpl(webhookUrl, secretKey, true);
    }

    /**
     * 任务恢复服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "execution.monitor.recovery.enabled", havingValue = "true")
    public ExecutionRecoveryService executionRecoveryService(
            ExecutionRecordRepository repository,
            RecoveryDecisionService decisionService,
            ExecutionRecordDomainService domainService) {
        log.info("启用任务自动恢复");
        return new ExecutionRecoveryService(
                repository, decisionService, domainService);
    }

    /**
     * 恢复处理器注册表
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "execution.monitor.recovery.enabled", havingValue = "true")
    public RecoveryHandlerRegistry recoveryHandlerRegistry() {
        log.info("启用自定义恢复处理器注册表");
        return new RecoveryHandlerRegistry();
    }

    /**
     * 任务重试执行器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "execution.monitor.recovery.enabled", havingValue = "true")
    public ExecutionRetryExecutor executionRetryExecutor(
            ExecutionRecordRepository repository,
            RecoveryHandlerRegistry handlerRegistry) {
        return new ExecutionRetryExecutor(repository, handlerRegistry);
    }

    /**
     * 重试任务调度器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "execution.monitor.recovery.enabled", havingValue = "true")
    public RetryExecutionScheduler retryExecutionScheduler(
            ExecutionRetryExecutor retryExecutor) {
        log.info("启用重试任务调度器");
        return new RetryExecutionScheduler(retryExecutor);
    }
}
