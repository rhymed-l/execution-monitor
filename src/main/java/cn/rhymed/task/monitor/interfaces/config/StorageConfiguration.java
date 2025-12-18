package cn.rhymed.task.monitor.interfaces.config;

import cn.rhymed.task.monitor.domain.repository.HeartbeatStorage;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.infrastructure.persistence.database.DatabaseHeartbeatStorage;
import cn.rhymed.task.monitor.infrastructure.persistence.database.DatabaseTaskExecutionRepository;
import cn.rhymed.task.monitor.infrastructure.persistence.database.mapper.HeartbeatMapper;
import cn.rhymed.task.monitor.infrastructure.persistence.database.mapper.TaskLogMapper;
import cn.rhymed.task.monitor.infrastructure.persistence.redis.RedisHeartbeatStorage;
import cn.rhymed.task.monitor.infrastructure.persistence.redis.RedisTaskExecutionRepository;
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
    @ConditionalOnProperty(name = "task.monitor.storage-type", havingValue = "memory", matchIfMissing = true)
    public static class MemoryStorageConfiguration {
        // 内存存储的bean已在TaskMonitorAutoConfiguration中注册
    }

    /**
     * Redis存储配置
     */
    @Configuration
    @ConditionalOnProperty(name = "task.monitor.storage-type", havingValue = "redis")
    public static class RedisStorageConfiguration {

        /**
         * Redis存储仓储
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(RedisTemplate.class)
        public TaskExecutionRepository redisTaskExecutionRepository(RedisTemplate<String, String> redisTemplate) {
            log.info("使用Redis存储实现");
            return new RedisTaskExecutionRepository(redisTemplate);
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
    @ConditionalOnProperty(name = "task.monitor.storage-type", havingValue = "database")
    public static class DatabaseStorageConfiguration {

        /**
         * 数据库存储仓储
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(TaskLogMapper.class)
        public TaskExecutionRepository databaseTaskExecutionRepository(TaskLogMapper mapper) {
            log.info("使用数据库存储实现");
            return new DatabaseTaskExecutionRepository(mapper);
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
