package cn.rhymed.task.monitor.infrastructure.scheduler;

import cn.rhymed.task.monitor.application.service.HeartbeatManagementService;
import cn.rhymed.task.monitor.domain.aggregate.TaskExecution;
import cn.rhymed.task.monitor.common.enums.TaskStatus;
import cn.rhymed.task.monitor.domain.model.BizKey;
import cn.rhymed.task.monitor.domain.model.SerializedParams;
import cn.rhymed.task.monitor.domain.model.TaskName;
import cn.rhymed.task.monitor.domain.repository.TaskExecutionRepository;
import cn.rhymed.task.monitor.domain.service.TaskExecutionDomainService;
import cn.rhymed.task.monitor.infrastructure.persistence.memory.MemoryHeartbeatStorage;
import cn.rhymed.task.monitor.infrastructure.persistence.memory.MemoryTaskExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HealthCheckScheduler 集成测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class HealthCheckSchedulerTest {

    private HealthCheckScheduler scheduler;
    private HeartbeatManagementService heartbeatService;
    private TaskExecutionRepository taskRepository;
    private TaskExecutionDomainService domainService;

    @BeforeEach
    void setUp() {
        MemoryHeartbeatStorage heartbeatStorage = new MemoryHeartbeatStorage();
        heartbeatService = new HeartbeatManagementService(heartbeatStorage);
        taskRepository = new MemoryTaskExecutionRepository();
        domainService = new TaskExecutionDomainService();

        scheduler = new HealthCheckScheduler(
                heartbeatService,
                taskRepository,
                domainService
        );
    }

    @Test
    void should_detect_timeout_tasks() {
        // Given - 创建一个运行中的任务并注册心跳
        TaskExecution execution = createRunningTask();
        taskRepository.save(execution);
        heartbeatService.registerHeartbeat(execution.getTaskId(), 60);

        // 模拟心跳超时(修改心跳时间为2分钟前)
        MemoryHeartbeatStorage storage = new MemoryHeartbeatStorage();
        storage.save(new cn.rhymed.task.monitor.domain.entity.HeartbeatRecord(
                execution.getTaskId(),
                LocalDateTime.now().minusSeconds(120),
                60
        ));
        heartbeatService = new HeartbeatManagementService(storage);
        scheduler = new HealthCheckScheduler(heartbeatService, taskRepository, domainService);

        // When - 执行健康检查
        scheduler.checkHealthStatus();

        // Then - 任务应该被标记为心跳超时
        TaskExecution updated = taskRepository.findById(execution.getTaskId()).get();
        assertEquals(TaskStatus.HEARTBEAT_TIMEOUT, updated.getStatus());
    }

    @Test
    void should_not_mark_timeout_when_heartbeat_is_fresh() {
        // Given - 创建运行中的任务并注册心跳
        TaskExecution execution = createRunningTask();
        taskRepository.save(execution);
        heartbeatService.registerHeartbeat(execution.getTaskId(), 60);

        // When - 立即执行健康检查
        scheduler.checkHealthStatus();

        // Then - 任务应该保持运行状态
        TaskExecution updated = taskRepository.findById(execution.getTaskId()).get();
        assertEquals(TaskStatus.RUNNING, updated.getStatus());
    }

    @Test
    void should_handle_multiple_timeout_tasks() {
        // Given - 创建多个超时任务
        TaskExecution task1 = createRunningTask();
        TaskExecution task2 = createRunningTask();
        TaskExecution task3 = createRunningTask();

        taskRepository.save(task1);
        taskRepository.save(task2);
        taskRepository.save(task3);

        // 注册心跳并模拟超时
        MemoryHeartbeatStorage storage = new MemoryHeartbeatStorage();
        storage.save(new cn.rhymed.task.monitor.domain.entity.HeartbeatRecord(
                task1.getTaskId(), LocalDateTime.now().minusSeconds(120), 60));
        storage.save(new cn.rhymed.task.monitor.domain.entity.HeartbeatRecord(
                task2.getTaskId(), LocalDateTime.now().minusSeconds(120), 60));
        storage.save(new cn.rhymed.task.monitor.domain.entity.HeartbeatRecord(
                task3.getTaskId(), LocalDateTime.now(), 60)); // 未超时

        heartbeatService = new HeartbeatManagementService(storage);
        scheduler = new HealthCheckScheduler(heartbeatService, taskRepository, domainService);

        // When
        scheduler.checkHealthStatus();

        // Then
        assertEquals(TaskStatus.HEARTBEAT_TIMEOUT, taskRepository.findById(task1.getTaskId()).get().getStatus());
        assertEquals(TaskStatus.HEARTBEAT_TIMEOUT, taskRepository.findById(task2.getTaskId()).get().getStatus());
        assertEquals(TaskStatus.RUNNING, taskRepository.findById(task3.getTaskId()).get().getStatus());
    }

    @Test
    void should_skip_completed_tasks() {
        // Given - 创建已完成的任务
        TaskExecution execution = createRunningTask();
        domainService.completeTask(execution);
        taskRepository.save(execution);

        heartbeatService.registerHeartbeat(execution.getTaskId(), 60);

        // When
        scheduler.checkHealthStatus();

        // Then - 状态不应该改变
        TaskExecution updated = taskRepository.findById(execution.getTaskId()).get();
        assertEquals(TaskStatus.SUCCESS, updated.getStatus());
    }

    @Test
    void should_cleanup_heartbeat_after_timeout() {
        // Given
        TaskExecution execution = createRunningTask();
        taskRepository.save(execution);

        MemoryHeartbeatStorage storage = new MemoryHeartbeatStorage();
        storage.save(new cn.rhymed.task.monitor.domain.entity.HeartbeatRecord(
                execution.getTaskId(),
                LocalDateTime.now().minusSeconds(120),
                60
        ));
        heartbeatService = new HeartbeatManagementService(storage);
        scheduler = new HealthCheckScheduler(heartbeatService, taskRepository, domainService);

        // When
        scheduler.checkHealthStatus();

        // Then - 心跳记录应该被清理
        assertFalse(heartbeatService.hasHeartbeat(execution.getTaskId()));
    }

    private TaskExecution createRunningTask() {
        TaskExecution execution = TaskExecution.create(
                TaskName.of("testTask"),
                BizKey.empty(),
                SerializedParams.empty(),
                3
        );
        execution.enableHeartbeat(60);
        return execution;
    }
}
