package cn.rhymed.execution.monitor.infrastructure.scheduler;

import cn.rhymed.execution.monitor.application.service.HeartbeatManagementService;
import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.entity.HeartbeatRecord;
import cn.rhymed.execution.monitor.domain.model.BizKey;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import cn.rhymed.execution.monitor.domain.model.SerializedParams;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.domain.service.ExecutionRecordDomainService;
import cn.rhymed.execution.monitor.infrastructure.persistence.memory.MemoryExecutionRecordRepository;
import cn.rhymed.execution.monitor.infrastructure.persistence.memory.MemoryHeartbeatStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    private ExecutionRecordRepository executionRepository;
    private ExecutionRecordDomainService domainService;

    @BeforeEach
    void setUp() {
        MemoryHeartbeatStorage heartbeatStorage = new MemoryHeartbeatStorage();
        heartbeatService = new HeartbeatManagementService(heartbeatStorage);
        executionRepository = new MemoryExecutionRecordRepository();
        domainService = new ExecutionRecordDomainService();

        scheduler = new HealthCheckScheduler(
                heartbeatService,
                executionRepository,
                domainService
        );
    }

    @Test
    void should_detect_timeout_executions() {
        // Given - 创建一个运行中的任务并注册心跳
        ExecutionRecord execution = createRunningExecution();
        executionRepository.save(execution);
        heartbeatService.registerHeartbeat(execution.getExecutionId(), 60);

        // 模拟心跳超时(修改心跳时间为2分钟前)
        MemoryHeartbeatStorage storage = new MemoryHeartbeatStorage();
        storage.save(new HeartbeatRecord(
                execution.getExecutionId(),
                LocalDateTime.now().minusSeconds(120),
                60
        ));
        heartbeatService = new HeartbeatManagementService(storage);
        scheduler = new HealthCheckScheduler(heartbeatService, executionRepository, domainService);

        // When - 执行健康检查
        scheduler.checkHealthStatus();

        // Then - 任务应该被标记为心跳超时
        ExecutionRecord updated = executionRepository.findById(execution.getExecutionId()).get();
        assertEquals(ExecutionStatus.HEARTBEAT_TIMEOUT, updated.getStatus());
    }

    @Test
    void should_not_mark_timeout_when_heartbeat_is_fresh() {
        // Given - 创建运行中的任务并注册心跳
        ExecutionRecord execution = createRunningExecution();
        executionRepository.save(execution);
        heartbeatService.registerHeartbeat(execution.getExecutionId(), 60);

        // When - 立即执行健康检查
        scheduler.checkHealthStatus();

        // Then - 任务应该保持运行状态
        ExecutionRecord updated = executionRepository.findById(execution.getExecutionId()).get();
        assertEquals(ExecutionStatus.RUNNING, updated.getStatus());
    }

    @Test
    void should_handle_multiple_timeout_executions() {
        // Given - 创建多个超时任务
        ExecutionRecord execution1 = createRunningExecution();
        ExecutionRecord execution2 = createRunningExecution();
        ExecutionRecord execution3 = createRunningExecution();

        executionRepository.save(execution1);
        executionRepository.save(execution2);
        executionRepository.save(execution3);

        // 注册心跳并模拟超时
        MemoryHeartbeatStorage storage = new MemoryHeartbeatStorage();
        storage.save(new HeartbeatRecord(
                execution1.getExecutionId(), LocalDateTime.now().minusSeconds(120), 60));
        storage.save(new HeartbeatRecord(
                execution2.getExecutionId(), LocalDateTime.now().minusSeconds(120), 60));
        storage.save(new HeartbeatRecord(
                execution3.getExecutionId(), LocalDateTime.now(), 60)); // 未超时

        heartbeatService = new HeartbeatManagementService(storage);
        scheduler = new HealthCheckScheduler(heartbeatService, executionRepository, domainService);

        // When
        scheduler.checkHealthStatus();

        // Then
        assertEquals(ExecutionStatus.HEARTBEAT_TIMEOUT, executionRepository.findById(execution1.getExecutionId()).get().getStatus());
        assertEquals(ExecutionStatus.HEARTBEAT_TIMEOUT, executionRepository.findById(execution2.getExecutionId()).get().getStatus());
        assertEquals(ExecutionStatus.RUNNING, executionRepository.findById(execution3.getExecutionId()).get().getStatus());
    }

    @Test
    void should_skip_completed_executions() {
        // Given - 创建已完成的任务
        ExecutionRecord execution = createRunningExecution();
        domainService.completeExecution(execution);
        executionRepository.save(execution);

        heartbeatService.registerHeartbeat(execution.getExecutionId(), 60);

        // When
        scheduler.checkHealthStatus();

        // Then - 状态不应该改变
        ExecutionRecord updated = executionRepository.findById(execution.getExecutionId()).get();
        assertEquals(ExecutionStatus.SUCCESS, updated.getStatus());
    }

    @Test
    void should_cleanup_heartbeat_after_timeout() {
        // Given
        ExecutionRecord execution = createRunningExecution();
        executionRepository.save(execution);

        MemoryHeartbeatStorage storage = new MemoryHeartbeatStorage();
        storage.save(new HeartbeatRecord(
                execution.getExecutionId(),
                LocalDateTime.now().minusSeconds(120),
                60
        ));
        heartbeatService = new HeartbeatManagementService(storage);
        scheduler = new HealthCheckScheduler(heartbeatService, executionRepository, domainService);

        // When
        scheduler.checkHealthStatus();

        // Then - 心跳记录应该被清理
        assertFalse(heartbeatService.hasHeartbeat(execution.getExecutionId()));
    }

    private ExecutionRecord createRunningExecution() {
        ExecutionRecord execution = ExecutionRecord.create(
                ExecutionName.of("testExecution"),
                BizKey.empty(),
                SerializedParams.empty(),
                3
        );
        execution.enableHeartbeat(60);
        return execution;
    }
}
