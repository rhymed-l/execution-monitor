package cn.rhymed.execution.monitor.application.service;

import cn.rhymed.execution.monitor.application.dto.ExecutionLogDTO;
import cn.rhymed.execution.monitor.interfaces.annotation.RecoveryHandler;
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
        ExecutionLogDTO executionLog = new ExecutionLogDTO();
        executionLog.setExecutionName("fileProcessing");
        executionLog.setBizKey("/path/to/file.txt");

        FileRecoveryHandler handlerBean = applicationContext.getBean(FileRecoveryHandler.class);
        int beforeCount = handlerBean.getInvocationCount();

        // When
        Optional<RecoveryHandlerRegistry.HandlerMethod> handler = registry.getHandler("fileProcessing");
        assertTrue(handler.isPresent());
        handler.get().invoke(executionLog);

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
    void shouldReturnEmptyForNonExistentExecution() {
        // When
        Optional<RecoveryHandlerRegistry.HandlerMethod> handler = registry.getHandler("nonExistentExecution");

        // Then
        assertFalse(handler.isPresent());
    }

    @Test
    void shouldCheckIfHandlerExists() {
        // Then
        assertTrue(registry.hasHandler("fileProcessing"));
        assertTrue(registry.hasHandler("dataSync"));
        assertFalse(registry.hasHandler("unknownExecution"));
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

        @RecoveryHandler(name = "fileProcessing", priority = 0)
        public void recoverFileProcessing(ExecutionLogDTO executionLog) {
            invocationCount.incrementAndGet();
            // Simulate file recovery logic
            System.out.println("Recovering file: " + executionLog.getBizKey());
        }

        public int getInvocationCount() {
            return invocationCount.get();
        }
    }

    static class DataSyncRecoveryHandler {
        @RecoveryHandler(name = "dataSync", priority = 10)
        public void recoverDataSyncHighPriority(ExecutionLogDTO executionLog) {
            // High priority handler
            System.out.println("High priority data sync recovery");
        }

        @RecoveryHandler(name = "dataSync", priority = 20)
        public void recoverDataSyncLowPriority(ExecutionLogDTO executionLog) {
            // Low priority handler
            System.out.println("Low priority data sync recovery");
        }
    }
}
