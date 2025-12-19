package cn.rhymed.execution.monitor.interfaces.config;

import cn.rhymed.execution.monitor.domain.repository.ExecutionLockRepository;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.domain.repository.HeartbeatStorage;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.DatabaseExecutionLockRepository;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.DatabaseExecutionRecordRepository;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.DatabaseHeartbeatStorage;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.mapper.ExecutionLockMapper;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.mapper.ExecutionLogMapper;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.mapper.HeartbeatMapper;
import cn.rhymed.execution.monitor.infrastructure.persistence.redis.RedisExecutionRecordRepository;
import cn.rhymed.execution.monitor.infrastructure.persistence.redis.RedisHeartbeatStorage;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 存储配置
 * 根据配置选择不同的存储后端
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
@Configuration
public class StorageConfiguration {

    /**
     * 内存存储配置
     */
    @Configuration
    @ConditionalOnProperty(name = "execution.monitor.storage-type", havingValue = "memory", matchIfMissing = true)
    public static class MemoryStorageConfiguration {
        // 内存存储的bean已在MonitorAutoConfiguration中注册
    }

    /**
     * Redis存储配置
     */
    @Configuration
    @ConditionalOnProperty(name = "execution.monitor.storage-type", havingValue = "redis")
    public static class RedisStorageConfiguration {

        /**
         * Redis存储仓储
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(RedisTemplate.class)
        public ExecutionRecordRepository redisExecutionRecordRepository(RedisTemplate<String, String> redisTemplate) {
            log.info("使用Redis存储实现");
            return new RedisExecutionRecordRepository(redisTemplate);
        }

        /**
         * Redis心跳存储
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(RedisTemplate.class)
        public HeartbeatStorage redisHeartbeatStorage(RedisTemplate<String, String> redisTemplate) {
            log.info("使用Redis心跳存储");
            return new RedisHeartbeatStorage(redisTemplate);
        }
    }

    /**
     * 数据库存储配置
     */
    @Configuration
    @ConditionalOnProperty(name = "execution.monitor.storage-type", havingValue = "database")
    @MapperScan("cn.rhymed.execution.monitor.infrastructure.persistence.database.mapper")
    public static class DatabaseStorageConfiguration {

        /**
         * 方法元信息仓储
         */
        @Bean
        @ConditionalOnMissingBean
        public cn.rhymed.execution.monitor.infrastructure.persistence.MethodMetadataRepositoryImpl
        methodMetadataRepository(cn.rhymed.execution.monitor.infrastructure.persistence.database.mapper.MethodMetadataMapper mapper) {
            log.info("注册方法元信息仓储");
            return new cn.rhymed.execution.monitor.infrastructure.persistence.MethodMetadataRepositoryImpl(mapper);
        }

        /**
         * 数据库存储仓储
         */
        @Bean
        @ConditionalOnMissingBean
        public ExecutionRecordRepository databaseExecutionRecordRepository(
                ExecutionLogMapper mapper,
                cn.rhymed.execution.monitor.infrastructure.persistence.MethodMetadataRepositoryImpl metadataRepository) {
            log.info("使用数据库存储实现");
            return new DatabaseExecutionRecordRepository(mapper, metadataRepository);
        }

        /**
         * 数据库心跳存储
         */
        @Bean
        @ConditionalOnMissingBean
        public HeartbeatStorage databaseHeartbeatStorage(HeartbeatMapper mapper) {
            log.info("使用数据库心跳存储");
            return new DatabaseHeartbeatStorage(mapper);
        }

        /**
         * 数据库执行锁仓储
         */
        @Bean
        @ConditionalOnMissingBean
        public ExecutionLockRepository databaseExecutionLockRepository(ExecutionLockMapper mapper) {
            log.info("使用数据库执行锁仓储");
            return new DatabaseExecutionLockRepository(mapper);
        }

        /**
         * 锁清理调度器
         * 定期清理过期的数据库锁
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(name = "execution.monitor.distributed-lock.enabled", havingValue = "true")
        public cn.rhymed.execution.monitor.infrastructure.scheduler.LockCleanupScheduler lockCleanupScheduler(
                ExecutionLockRepository lockRepository) {
            log.info("启用锁清理调度器");
            return new cn.rhymed.execution.monitor.infrastructure.scheduler.LockCleanupScheduler(lockRepository);
        }
    }
}
