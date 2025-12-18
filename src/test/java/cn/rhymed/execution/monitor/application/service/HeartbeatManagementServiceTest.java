package cn.rhymed.execution.monitor.application.service;

import cn.rhymed.execution.monitor.domain.entity.HeartbeatRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.repository.HeartbeatStorage;
import cn.rhymed.execution.monitor.infrastructure.persistence.memory.MemoryHeartbeatStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HeartbeatManagementService 集成测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class HeartbeatManagementServiceTest {

    private HeartbeatManagementService service;
    private HeartbeatStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MemoryHeartbeatStorage();
        service = new HeartbeatManagementService(storage);
    }

    @Test
    void should_register_heartbeat() {
        // Given
        ExecutionId executionId = ExecutionId.generate();
        int intervalSeconds = 60;

        // When
        service.registerHeartbeat(executionId, intervalSeconds);

        // Then
        Optional<HeartbeatRecord> record = storage.findByExecutionId(executionId);
        assertTrue(record.isPresent());
        assertEquals(executionId, record.get().getExecutionId());
        assertEquals(intervalSeconds, record.get().getHeartbeatIntervalSeconds());
    }

    @Test
    void should_update_existing_heartbeat() {
        // Given
        ExecutionId executionId = ExecutionId.generate();
        service.registerHeartbeat(executionId, 60);
        HeartbeatRecord original = storage.findByExecutionId(executionId).get();
        LocalDateTime originalTime = original.getLastHeartbeatTime();

        // When
        service.updateHeartbeat(executionId);

        // Then
        HeartbeatRecord updated = storage.findByExecutionId(executionId).get();
        assertTrue(updated.getLastHeartbeatTime().isAfter(originalTime) ||
                updated.getLastHeartbeatTime().isEqual(originalTime));
    }

    @Test
    void should_throw_exception_when_update_non_existing_heartbeat() {
        // Given
        ExecutionId nonExistingExecutionId = ExecutionId.generate();

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> service.updateHeartbeat(nonExistingExecutionId));
    }

    @Test
    void should_check_for_timeout_executions() {
        // Given
        ExecutionId executionId1 = ExecutionId.generate();
        ExecutionId executionId2 = ExecutionId.generate();

        service.registerHeartbeat(executionId1, 60);
        service.registerHeartbeat(executionId2, 60);

        // Simulate timeout by modifying last heartbeat time
        HeartbeatRecord record1 = storage.findByExecutionId(executionId1).get();
        storage.save(new HeartbeatRecord(
                executionId1,
                LocalDateTime.now().minusSeconds(120), // 2分钟前
                60
        ));

        // When
        List<ExecutionId> timeoutExecutions = service.checkForTimeoutExecutions();

        // Then
        assertEquals(1, timeoutExecutions.size());
        assertTrue(timeoutExecutions.contains(executionId1));
        assertFalse(timeoutExecutions.contains(executionId2));
    }

    @Test
    void should_remove_heartbeat() {
        // Given
        ExecutionId executionId = ExecutionId.generate();
        service.registerHeartbeat(executionId, 60);

        // When
        service.removeHeartbeat(executionId);

        // Then
        Optional<HeartbeatRecord> record = storage.findByExecutionId(executionId);
        assertFalse(record.isPresent());
    }

    @Test
    void should_check_if_heartbeat_exists() {
        // Given
        ExecutionId existingExecutionId = ExecutionId.generate();
        ExecutionId nonExistingExecutionId = ExecutionId.generate();
        service.registerHeartbeat(existingExecutionId, 60);

        // When & Then
        assertTrue(service.hasHeartbeat(existingExecutionId));
        assertFalse(service.hasHeartbeat(nonExistingExecutionId));
    }

    @Test
    void should_get_heartbeat_status() {
        // Given
        ExecutionId executionId = ExecutionId.generate();
        service.registerHeartbeat(executionId, 60);

        // When
        Optional<HeartbeatRecord> status = service.getHeartbeatStatus(executionId);

        // Then
        assertTrue(status.isPresent());
        assertEquals(executionId, status.get().getExecutionId());
    }

    @Test
    void should_handle_concurrent_heartbeat_updates() throws InterruptedException {
        // Given
        ExecutionId executionId = ExecutionId.generate();
        service.registerHeartbeat(executionId, 60);

        // When - 并发更新心跳
        Thread t1 = new Thread(() -> service.updateHeartbeat(executionId));
        Thread t2 = new Thread(() -> service.updateHeartbeat(executionId));

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Then - 不应该抛出异常
        Optional<HeartbeatRecord> record = storage.findByExecutionId(executionId);
        assertTrue(record.isPresent());
    }
}
