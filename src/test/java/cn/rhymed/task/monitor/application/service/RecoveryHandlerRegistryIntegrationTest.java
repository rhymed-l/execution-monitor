package cn.rhymed.task.monitor.application.service;

import cn.rhymed.task.monitor.application.dto.TaskLogDTO;
import cn.rhymed.task.monitor.interfaces.annotation.TaskRecoveryHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecoveryHandlerRegistry集成测试
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class RecoveryHandlerRegistryIntegrationTest {

    private RecoveryHandlerRegistry registry;
    private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext(TestConfig.class);
        registry = new RecoveryHandlerRegistry();
        registry.setApplicationContext(applicationContext);
    }

    @Test
    void shouldScanAndRegisterRecoveryHandlers() {
        // When
        Optional<RecoveryHandlerRegistry.HandlerMethod> handler = registry.getHandler("fileProcessing");

        // Then
        assertTrue(handler.isPresent());
        assertEquals(FileRecoveryHandler.class, handler.get().getBean().getClass());
    }

    @Test
    void shouldInvokeRegisteredHandler() throws Exception {
        // Given
        TaskLogDTO taskLog = new TaskLogDTO();
        taskLog.setTaskName("fileProcessing");
        taskLog.setBizKey("/path/to/file.txt");

        FileRecoveryHandler handlerBean = applicationContext.getBean(FileRecoveryHandler.class);
        int beforeCount = handlerBean.getInvocationCount();

        // When
        Optional<RecoveryHandlerRegistry.HandlerMethod> handler = registry.getHandler("fileProcessing");
        assertTrue(handler.isPresent());
        handler.get().invoke(taskLog);

        // Then
        assertEquals(beforeCount + 1, handlerBean.getInvocationCount());
    }

    @Test
    void shouldReturnHighestPriorityHandler() {
        // When - 两个处理器,优先级10的应该被选中
        Optional<RecoveryHandlerRegistry.HandlerMethod> handler = registry.getHandler("dataSync");

        // Then
        assertTrue(handler.isPresent());
        assertEquals("recoverDataSyncHighPriority", handler.get().getMethod().getName());
    }

    @Test
    void shouldReturnEmptyForNonExistentTask() {
        // When
        Optional<RecoveryHandlerRegistry.HandlerMethod> handler = registry.getHandler("nonExistentTask");

        // Then
        assertFalse(handler.isPresent());
    }

    @Test
    void shouldCheckIfHandlerExists() {
        // Then
        assertTrue(registry.hasHandler("fileProcessing"));
        assertTrue(registry.hasHandler("dataSync"));
        assertFalse(registry.hasHandler("unknownTask"));
    }

    @Configuration
    static class TestConfig {
        @Bean
        public FileRecoveryHandler fileRecoveryHandler() {
            return new FileRecoveryHandler();
        }

        @Bean
        public DataSyncRecoveryHandler dataSyncRecoveryHandler() {
            return new DataSyncRecoveryHandler();
        }
    }

    static class FileRecoveryHandler {
        private final AtomicInteger invocationCount = new AtomicInteger(0);

        @TaskRecoveryHandler(taskName = "fileProcessing", priority = 0)
        public void recoverFileProcessing(TaskLogDTO taskLog) {
            invocationCount.incrementAndGet();
            // Simulate file recovery logic
            System.out.println("Recovering file: " + taskLog.getBizKey());
        }

        public int getInvocationCount() {
            return invocationCount.get();
        }
    }

    static class DataSyncRecoveryHandler {
        @TaskRecoveryHandler(taskName = "dataSync", priority = 10)
        public void recoverDataSyncHighPriority(TaskLogDTO taskLog) {
            // High priority handler
            System.out.println("High priority data sync recovery");
        }

        @TaskRecoveryHandler(taskName = "dataSync", priority = 20)
        public void recoverDataSyncLowPriority(TaskLogDTO taskLog) {
            // Low priority handler
            System.out.println("Low priority data sync recovery");
        }
    }
}
