package cn.rhymed.task.monitor.application.service;

import cn.rhymed.task.monitor.domain.entity.HeartbeatRecord;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.repository.HeartbeatStorage;
import cn.rhymed.task.monitor.infrastructure.persistence.memory.MemoryHeartbeatStorage;
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
        TaskId taskId = TaskId.generate();
        int intervalSeconds = 60;

        // When
        service.registerHeartbeat(taskId, intervalSeconds);

        // Then
        Optional<HeartbeatRecord> record = storage.findByTaskId(taskId);
        assertTrue(record.isPresent());
        assertEquals(taskId, record.get().getTaskId());
        assertEquals(intervalSeconds, record.get().getHeartbeatIntervalSeconds());
    }

    @Test
    void should_update_existing_heartbeat() {
        // Given
        TaskId taskId = TaskId.generate();
        service.registerHeartbeat(taskId, 60);
        HeartbeatRecord original = storage.findByTaskId(taskId).get();
        LocalDateTime originalTime = original.getLastHeartbeatTime();

        // When
        service.updateHeartbeat(taskId);

        // Then
        HeartbeatRecord updated = storage.findByTaskId(taskId).get();
        assertTrue(updated.getLastHeartbeatTime().isAfter(originalTime) ||
                updated.getLastHeartbeatTime().isEqual(originalTime));
    }

    @Test
    void should_throw_exception_when_update_non_existing_heartbeat() {
        // Given
        TaskId nonExistingTaskId = TaskId.generate();

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> service.updateHeartbeat(nonExistingTaskId));
    }

    @Test
    void should_check_for_timeout_tasks() {
        // Given
        TaskId taskId1 = TaskId.generate();
        TaskId taskId2 = TaskId.generate();

        service.registerHeartbeat(taskId1, 60);
        service.registerHeartbeat(taskId2, 60);

        // Simulate timeout by modifying last heartbeat time
        HeartbeatRecord record1 = storage.findByTaskId(taskId1).get();
        storage.save(new HeartbeatRecord(
                taskId1,
                LocalDateTime.now().minusSeconds(120), // 2分钟前
                60
        ));

        // When
        List<TaskId> timeoutTasks = service.checkForTimeoutTasks();

        // Then
        assertEquals(1, timeoutTasks.size());
        assertTrue(timeoutTasks.contains(taskId1));
        assertFalse(timeoutTasks.contains(taskId2));
    }

    @Test
    void should_remove_heartbeat() {
        // Given
        TaskId taskId = TaskId.generate();
        service.registerHeartbeat(taskId, 60);

        // When
        service.removeHeartbeat(taskId);

        // Then
        Optional<HeartbeatRecord> record = storage.findByTaskId(taskId);
        assertFalse(record.isPresent());
    }

    @Test
    void should_check_if_heartbeat_exists() {
        // Given
        TaskId existingTaskId = TaskId.generate();
        TaskId nonExistingTaskId = TaskId.generate();
        service.registerHeartbeat(existingTaskId, 60);

        // When & Then
        assertTrue(service.hasHeartbeat(existingTaskId));
        assertFalse(service.hasHeartbeat(nonExistingTaskId));
    }

    @Test
    void should_get_heartbeat_status() {
        // Given
        TaskId taskId = TaskId.generate();
        service.registerHeartbeat(taskId, 60);

        // When
        Optional<HeartbeatRecord> status = service.getHeartbeatStatus(taskId);

        // Then
        assertTrue(status.isPresent());
        assertEquals(taskId, status.get().getTaskId());
    }

    @Test
    void should_handle_concurrent_heartbeat_updates() throws InterruptedException {
        // Given
        TaskId taskId = TaskId.generate();
        service.registerHeartbeat(taskId, 60);

        // When - 并发更新心跳
        Thread t1 = new Thread(() -> service.updateHeartbeat(taskId));
        Thread t2 = new Thread(() -> service.updateHeartbeat(taskId));

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Then - 不应该抛出异常
        Optional<HeartbeatRecord> record = storage.findByTaskId(taskId);
        assertTrue(record.isPresent());
    }
}
