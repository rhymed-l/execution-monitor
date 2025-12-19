package cn.rhymed.execution.monitor.interfaces.config;

import cn.rhymed.execution.monitor.application.service.*;
import cn.rhymed.execution.monitor.domain.repository.ExecutionLockRepository;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.domain.repository.HeartbeatStorage;
import cn.rhymed.execution.monitor.domain.service.ExecutionLockService;
import cn.rhymed.execution.monitor.domain.service.ExecutionRecordDomainService;
import cn.rhymed.execution.monitor.domain.service.RecoveryDecisionService;
import cn.rhymed.execution.monitor.domain.service.SerializationDecisionService;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertDispatcher;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertService;
import cn.rhymed.execution.monitor.infrastructure.alert.dingtalk.DingTalkAlertImpl;
import cn.rhymed.execution.monitor.infrastructure.aop.MonitorAspect;
import cn.rhymed.execution.monitor.infrastructure.lock.DatabaseExecutionLockService;
import cn.rhymed.execution.monitor.infrastructure.lock.NoOpExecutionLockService;
import cn.rhymed.execution.monitor.infrastructure.lock.RedisExecutionLockService;
import cn.rhymed.execution.monitor.infrastructure.persistence.memory.MemoryExecutionRecordRepository;
import cn.rhymed.execution.monitor.infrastructure.persistence.memory.MemoryHeartbeatStorage;
import cn.rhymed.execution.monitor.infrastructure.scheduler.HealthCheckScheduler;
import cn.rhymed.execution.monitor.infrastructure.scheduler.HeartbeatScheduler;
import cn.rhymed.execution.monitor.infrastructure.scheduler.RetryExecutionScheduler;
import cn.rhymed.execution.monitor.infrastructure.util.BeanResolver;
import cn.rhymed.execution.monitor.infrastructure.util.ExceptionClassifier;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 执行监控自动配置
 * <p>
 * 该配置类负责自动装配执行监控框架的所有核心组件，包括：
 * <ul>
 *   <li>领域服务：ExecutionRecordDomainService、SerializationDecisionService、RecoveryDecisionService</li>
 *   <li>应用服务：MonitorService、HeartbeatManagementService、ExecutionRecoveryService、ExecutionRetryExecutor</li>
 *   <li>基础设施：MonitorAspect（AOP切面）、ExceptionClassifier（异常分类器）、BeanResolver</li>
 *   <li>存储仓储：根据storage-type配置选择内存/Redis/数据库实现</li>
 *   <li>调度器：HeartbeatScheduler、HealthCheckScheduler、RetryExecutionScheduler</li>
 *   <li>分布式锁：根据storage-type选择数据库/Redis/NoOp实现</li>
 *   <li>告警服务：DingTalk、Lark告警实现和AlertDispatcher分发器</li>
 * </ul>
 * <p>
 * 配置条件：execution.monitor.enabled=true（默认启用）
 * <p>
 * 注意事项：
 * <ul>
 *   <li>所有Bean都使用 @ConditionalOnMissingBean，允许用户自定义覆盖</li>
 *   <li>存储配置由 StorageConfiguration 负责，支持三种存储后端</li>
 *   <li>分布式锁会根据storage-type自动选择合适的实现</li>
 *   <li>告警服务可以同时启用多个渠道</li>
 * </ul>
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "execution.monitor.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MonitorProperties.class)
@EnableAspectJAutoProxy
@EnableScheduling
@EnableAsync
@Import({
        StorageConfiguration.class
})
public class MonitorAutoConfiguration {

    public MonitorAutoConfiguration(MonitorProperties properties) {
        log.info("执行监控启动 - 存储类型: {}", properties.getStorageType());
    }

    /**
     * 监控模块异步任务执行器
     * <p>
     * 用于执行监控模块的异步任务，主要包括：
     * <ul>
     *   <li>启动时的异步恢复扫描</li>
     *   <li>异步告警发送</li>
     *   <li>其他需要异步执行的监控任务</li>
     * </ul>
     * <p>
     * 线程池配置：
     * <ul>
     *   <li>核心线程数：2</li>
     *   <li>最大线程数：5</li>
     *   <li>队列容量：100</li>
     *   <li>优雅关闭：等待60秒完成正在执行的任务</li>
     * </ul>
     *
     * @return 异步任务执行器
     */
    @Bean(name = "monitorAsyncExecutor")
    @ConditionalOnMissingBean(name = "monitorAsyncExecutor")
    public Executor monitorAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("monitor-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("配置监控模块异步任务执行器: corePoolSize=2, maxPoolSize=5");
        return executor;
    }

    /**
     * Bean解析器
     * <p>
     * 用于在运行时解析Spring容器中的Bean实例。
     * 主要用于自定义恢复处理器的动态查找和调用。
     *
     * @return Bean解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public BeanResolver beanResolver() {
        log.info("注册BeanResolver");
        return new BeanResolver();
    }

    /**
     * 执行记录领域服务
     * <p>
     * 核心领域服务，负责执行记录的业务逻辑处理，包括：
     * <ul>
     *   <li>执行记录的创建、更新、查询</li>
     *   <li>执行状态的转换和验证</li>
     *   <li>业务规则的校验</li>
     * </ul>
     *
     * @return 执行记录领域服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ExecutionRecordDomainService ExecutionRecordDomainService() {
        return new ExecutionRecordDomainService();
    }

    /**
     * 序列化决策服务
     * <p>
     * 负责判断任务参数是否需要序列化保存，考虑因素包括：
     * <ul>
     *   <li>参数大小是否超过限制</li>
     *   <li>参数类型是否支持序列化</li>
     *   <li>SerializationMode配置（AUTO/ALWAYS/NEVER）</li>
     * </ul>
     *
     * @return 序列化决策服务
     */
    @Bean
    @ConditionalOnMissingBean
    public SerializationDecisionService serializationDecisionService() {
        return new SerializationDecisionService();
    }

    /**
     * 恢复决策服务
     * <p>
     * 负责判断失败的任务是否应该自动恢复，考虑因素包括：
     * <ul>
     *   <li>任务的当前状态</li>
     *   <li>失败原因和异常类型</li>
     *   <li>恢复策略配置（AUTO/ALWAYS/NEVER/CUSTOM）</li>
     *   <li>是否超过最大重试次数</li>
     * </ul>
     *
     * @return 恢复决策服务
     */
    @Bean
    @ConditionalOnMissingBean
    public RecoveryDecisionService recoveryDecisionService() {
        return new RecoveryDecisionService();
    }

    /**
     * 异常分类器
     * <p>
     * 根据配置的异常类型列表，对任务执行过程中抛出的异常进行分类：
     * <ul>
     *   <li>可重试异常（retryableExceptions）：临时性错误，如网络超时、数据库连接失败</li>
     *   <li>可忽略异常（ignorableExceptions）：业务逻辑错误，如参数错误、空指针异常</li>
     *   <li>其他异常：根据配置的默认策略处理</li>
     * </ul>
     * <p>
     * 异常匹配规则：类名包含匹配（支持模糊匹配）
     *
     * @param properties 监控配置属性
     * @return 异常分类器
     */
    @Bean
    @ConditionalOnMissingBean
    public ExceptionClassifier exceptionClassifier(MonitorProperties properties) {
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
    public MonitorService MonitorService(ExecutionRecordDomainService domainService,
                                         ExecutionRecordRepository repository,
                                         @Autowired(required = false) AlertDispatcher alertDispatcher) {
        return new MonitorService(domainService, repository, alertDispatcher);
    }

    /**
     * AOP切面
     */
    @Bean
    @ConditionalOnMissingBean
    public MonitorAspect MonitorAspect(MonitorService MonitorService,
                                       MonitorProperties properties,
                                                         SerializationDecisionService serializationService) {
        log.info("注册Monitor切面");
        return new MonitorAspect(MonitorService, properties, serializationService);
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
    public AlertService dingTalkAlertService(MonitorProperties properties) {
        String webhookUrl = StrUtil.isNotBlank(properties.getAlert().getDingtalk().getWebhookUrl())
                ? properties.getAlert().getDingtalk().getWebhookUrl()
                : System.getProperty("execution.monitor.alert.dingtalk.webhook-url", "");
        String secretKey = StrUtil.isNotBlank(properties.getAlert().getDingtalk().getSecret())
                ? properties.getAlert().getDingtalk().getSecret()
                : System.getProperty("execution.monitor.alert.dingtalk.secret", "");

        log.info("启用钉钉告警服务, 消息类型: {}", properties.getAlert().getDingtalk().getMessageTypes());
        return new DingTalkAlertImpl(
                webhookUrl,
                secretKey,
                true,
                properties.getAlert().getDingtalk().getMessageTypes()
        );
    }

    /**
     * Lark(飞书)告警服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "execution.monitor.alert.lark.enabled", havingValue = "true")
    public AlertService larkAlertService(MonitorProperties properties) {
        String webhookUrl = StrUtil.isNotBlank(properties.getAlert().getLark().getWebhookUrl())
                ? properties.getAlert().getLark().getWebhookUrl()
                : System.getProperty("execution.monitor.alert.lark.webhook-url", "");
        String secretKey = StrUtil.isNotBlank(properties.getAlert().getLark().getSecret())
                ? properties.getAlert().getLark().getSecret()
                : System.getProperty("execution.monitor.alert.lark.secret", "");

        log.info("启用Lark(飞书)告警服务, 消息类型: {}", properties.getAlert().getLark().getMessageTypes());
        return new cn.rhymed.execution.monitor.infrastructure.alert.lark.LarkAlertImpl(
                webhookUrl,
                secretKey,
                true,
                properties.getAlert().getLark().getMessageTypes()
        );
    }

    /**
     * 告警分发器
     * 根据ExecutionRecord的alertTypes配置选择性发送告警
     */
    @Bean
    @ConditionalOnMissingBean
    public AlertDispatcher alertDispatcher(@Autowired(required = false) List<AlertService> alertServices) {
        log.info("注册告警分发器, 可用告警服务数: {}", alertServices != null ? alertServices.size() : 0);
        return new AlertDispatcher(alertServices);
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
     * 分布式锁服务 - 无操作实现（默认）
     * 用于以下场景:
     * 1. distributed-lock.enabled=false（分布式锁禁用）
     * 2. storage-type=memory（内存存储不支持分布式锁）
     * 3. 其他未匹配到具体实现的场景
     */
    @Bean
    @ConditionalOnMissingBean(ExecutionLockService.class)
    public ExecutionLockService noOpExecutionLockService(MonitorProperties properties) {
        if (properties.getDistributedLock().isEnabled()) {
            if ("memory".equals(properties.getStorageType())) {
                log.warn("内存存储模式不支持分布式锁，使用NoOp实现。多实例部署时请使用database或redis存储。");
            } else {
                log.warn("未找到匹配的分布式锁实现，使用NoOp实现");
            }
        } else {
            log.info("分布式锁已禁用，使用NoOp实现");
        }
        return new NoOpExecutionLockService();
    }

    /**
     * 任务重试执行器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "execution.monitor.recovery.enabled", havingValue = "true")
    public ExecutionRetryExecutor executionRetryExecutor(
            ExecutionRecordRepository repository,
            RecoveryHandlerRegistry handlerRegistry,
            ExecutionLockService lockService,
            @Autowired(required = false) AlertDispatcher alertDispatcher) {
        return new ExecutionRetryExecutor(repository, handlerRegistry, lockService, alertDispatcher);
    }

    /**
     * 应用启动时自动恢复监听器
     * 监听应用启动完成事件，自动扫描并处理待重试的任务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "execution.monitor.recovery.enabled", havingValue = "true")
    public cn.rhymed.execution.monitor.infrastructure.recovery.ApplicationStartupRecoveryListener
    applicationStartupRecoveryListener(ExecutionRetryExecutor retryExecutor) {
        log.info("启用启动时自动恢复");
        return new cn.rhymed.execution.monitor.infrastructure.recovery.ApplicationStartupRecoveryListener(retryExecutor);
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

    /**
     * 分布式锁配置
     */
    @Configuration
    @ConditionalOnProperty(name = "execution.monitor.distributed-lock.enabled", havingValue = "true", matchIfMissing = true)
    public static class DistributedLockConfiguration {

        /**
         * 分布式锁服务 - 数据库实现
         */
        @Bean
        @ConditionalOnMissingBean(ExecutionLockService.class)
        @ConditionalOnProperty(name = "execution.monitor.storage-type", havingValue = "database")
        public ExecutionLockService databaseExecutionLockService(
                ExecutionLockRepository lockRepository,
                MonitorProperties properties) {
            int timeoutSeconds = properties.getDistributedLock().getTimeoutSeconds();
            log.info("启用数据库分布式锁，超时时间: {}秒", timeoutSeconds);
            return new DatabaseExecutionLockService(lockRepository, timeoutSeconds);
        }

        /**
         * 分布式锁服务 - Redis实现
         */
        @Bean
        @ConditionalOnMissingBean(ExecutionLockService.class)
        @ConditionalOnProperty(name = "execution.monitor.storage-type", havingValue = "redis")
        public ExecutionLockService redisExecutionLockService(
                @Autowired(required = false) RedisTemplate<String, String> redisTemplate,
                MonitorProperties properties) {
            if (redisTemplate == null) {
                log.warn("Redis未配置，回退到NoOp锁实现");
                return new NoOpExecutionLockService();
            }
            int timeoutSeconds = properties.getDistributedLock().getTimeoutSeconds();
            log.info("启用Redis分布式锁，超时时间: {}秒", timeoutSeconds);
            return new RedisExecutionLockService(redisTemplate, timeoutSeconds);
        }
    }
}
