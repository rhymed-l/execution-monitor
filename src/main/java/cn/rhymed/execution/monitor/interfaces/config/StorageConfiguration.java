package cn.rhymed.execution.monitor.interfaces.config;

import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.domain.repository.HeartbeatStorage;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.DatabaseExecutionRecordRepository;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.DatabaseHeartbeatStorage;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.mapper.ExecutionLogMapper;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.mapper.HeartbeatMapper;
import cn.rhymed.execution.monitor.infrastructure.persistence.redis.RedisExecutionRecordRepository;
import cn.rhymed.execution.monitor.infrastructure.persistence.redis.RedisHeartbeatStorage;
import lombok.extern.slf4j.Slf4j;
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
        // 内存存储的bean已在ExecutionMonitorAutoConfiguration中注册
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
    public static class DatabaseStorageConfiguration {

        /**
         * 数据库存储仓储
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(ExecutionLogMapper.class)
        public ExecutionRecordRepository databaseExecutionRecordRepository(ExecutionLogMapper mapper) {
            log.info("使用数据库存储实现");
            return new DatabaseExecutionRecordRepository(mapper);
        }

        /**
         * 数据库心跳存储
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(HeartbeatMapper.class)
        public HeartbeatStorage databaseHeartbeatStorage(HeartbeatMapper mapper) {
            log.info("使用数据库心跳存储");
            return new DatabaseHeartbeatStorage(mapper);
        }
    }
}
