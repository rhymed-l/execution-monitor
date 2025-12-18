package cn.rhymed.task.monitor.domain.entity;

import cn.rhymed.task.monitor.domain.model.TaskId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HeartbeatRecord 单元测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class HeartbeatRecordTest {

    @Test
    void should_create_heartbeat_record() {
        // Given
        TaskId taskId = TaskId.generate();
        LocalDateTime heartbeatTime = LocalDateTime.now();
        int intervalSeconds = 60;

        // When
        HeartbeatRecord record = new HeartbeatRecord(taskId, heartbeatTime, intervalSeconds);

        // Then
        assertEquals(taskId, record.getTaskId());
        assertEquals(heartbeatTime, record.getLastHeartbeatTime());
        assertEquals(intervalSeconds, record.getHeartbeatIntervalSeconds());
    }

    @Test
    void should_throw_exception_when_taskId_is_null() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatRecord(null, LocalDateTime.now(), 60));
    }

    @Test
    void should_throw_exception_when_heartbeat_time_is_null() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatRecord(TaskId.generate(), null, 60));
    }

    @Test
    void should_throw_exception_when_interval_is_invalid() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatRecord(TaskId.generate(), LocalDateTime.now(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new HeartbeatRecord(TaskId.generate(), LocalDateTime.now(), -1));
    }

    @Test
    void should_update_heartbeat_time() {
        // Given
        HeartbeatRecord record = createRecord();
        LocalDateTime oldTime = record.getLastHeartbeatTime();
        LocalDateTime newTime = oldTime.plusSeconds(10);

        // When
        record.updateHeartbeat(newTime);

        // Then
        assertEquals(newTime, record.getLastHeartbeatTime());
    }

    @Test
    void should_throw_exception_when_update_with_earlier_time() {
        // Given
        HeartbeatRecord record = createRecord();
        LocalDateTime earlierTime = record.getLastHeartbeatTime().minusSeconds(10);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> record.updateHeartbeat(earlierTime));
    }

    @Test
    void should_return_false_when_not_timeout() {
        // Given
        HeartbeatRecord record = createRecord();
        LocalDateTime currentTime = record.getLastHeartbeatTime().plusSeconds(30);

        // When
        boolean isTimeout = record.isTimeout(currentTime);

        // Then
        assertFalse(isTimeout);
    }

    @Test
    void should_return_true_when_timeout() {
        // Given
        HeartbeatRecord record = createRecord();
        LocalDateTime currentTime = record.getLastHeartbeatTime()
                .plusSeconds(record.getHeartbeatIntervalSeconds() + 1);

        // When
        boolean isTimeout = record.isTimeout(currentTime);

        // Then
        assertTrue(isTimeout);
    }

    @Test
    void should_return_true_when_exactly_at_timeout_threshold() {
        // Given
        HeartbeatRecord record = createRecord();
        LocalDateTime currentTime = record.getLastHeartbeatTime()
                .plusSeconds(record.getHeartbeatIntervalSeconds());

        // When
        boolean isTimeout = record.isTimeout(currentTime);

        // Then
        assertFalse(isTimeout); // 等于阈值时不算超时
    }

    @Test
    void should_calculate_seconds_until_timeout() {
        // Given
        HeartbeatRecord record = createRecord();
        LocalDateTime currentTime = record.getLastHeartbeatTime().plusSeconds(30);

        // When
        long secondsUntilTimeout = record.secondsUntilTimeout(currentTime);

        // Then
        assertEquals(30, secondsUntilTimeout); // 60 - 30 = 30
    }

    @Test
    void should_return_negative_when_already_timeout() {
        // Given
        HeartbeatRecord record = createRecord();
        LocalDateTime currentTime = record.getLastHeartbeatTime().plusSeconds(70);

        // When
        long secondsUntilTimeout = record.secondsUntilTimeout(currentTime);

        // Then
        assertTrue(secondsUntilTimeout < 0);
    }

    @Test
    void should_be_equal_when_same_taskId() {
        // Given
        TaskId taskId = TaskId.generate();
        HeartbeatRecord record1 = new HeartbeatRecord(taskId, LocalDateTime.now(), 60);
        HeartbeatRecord record2 = new HeartbeatRecord(taskId, LocalDateTime.now().plusSeconds(10), 60);

        // Then
        assertEquals(record1, record2);
        assertEquals(record1.hashCode(), record2.hashCode());
    }

    private HeartbeatRecord createRecord() {
        return new HeartbeatRecord(
                TaskId.generate(),
                LocalDateTime.now(),
                60
        );
    }
}
