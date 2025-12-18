package cn.rhymed.execution.monitor.infrastructure.persistence.database;

import cn.rhymed.execution.monitor.domain.entity.HeartbeatRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.repository.HeartbeatStorage;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.mapper.HeartbeatMapper;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.po.HeartbeatPO;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 基于数据库的心跳存储实现
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class DatabaseHeartbeatStorage implements HeartbeatStorage {

    private final HeartbeatMapper mapper;

    public DatabaseHeartbeatStorage(HeartbeatMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(HeartbeatRecord heartbeatRecord) {
        HeartbeatPO po = HeartbeatPO.fromDomain(heartbeatRecord);
        mapper.insert(po);
        log.debug("保存心跳记录到数据库: {}", heartbeatRecord.getExecutionId());
    }

    @Override
    public void update(HeartbeatRecord heartbeatRecord) {
        HeartbeatPO po = HeartbeatPO.fromDomain(heartbeatRecord);
        int rows = mapper.updateByExecutionId(po);
        if (rows > 0) {
            log.debug("更新心跳记录: {}", heartbeatRecord.getExecutionId());
        } else {
            log.warn("更新心跳记录失败,记录不存在: {}", heartbeatRecord.getExecutionId());
        }
    }

    @Override
    public Optional<HeartbeatRecord> findByExecutionId(ExecutionId executionId) {
        HeartbeatPO po = mapper.selectByExecutionId(executionId.getValue());
        if (po != null) {
            return Optional.of(po.toDomain());
        }
        return Optional.empty();
    }

    @Override
    public List<HeartbeatRecord> findAllActive() {
        List<HeartbeatPO> pos = mapper.selectAll();
        return pos.stream()
                .map(HeartbeatPO::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<HeartbeatRecord> findTimeoutRecords(LocalDateTime currentTime) {
        List<HeartbeatPO> pos = mapper.selectTimeoutRecords(currentTime);
        return pos.stream()
                .map(HeartbeatPO::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByExecutionId(ExecutionId executionId) {
        mapper.deleteByExecutionId(executionId.getValue());
        log.debug("删除心跳记录: {}", executionId);
    }

    @Override
    public void cleanupBefore(LocalDateTime beforeTime) {
        int deleted = mapper.deleteBeforeTime(beforeTime);
        log.info("清理{}之前的心跳记录,删除{}条", beforeTime, deleted);
    }

    @Override
    public void deleteBatch(List<ExecutionId> executionIds) {
        if (executionIds == null || executionIds.isEmpty()) {
            return;
        }
        List<String> executionIdStrings = executionIds.stream()
                .map(ExecutionId::getValue)
                .collect(Collectors.toList());
        int deleted = mapper.deleteBatch(executionIdStrings);
        log.debug("批量删除心跳记录,删除{}条", deleted);
    }
}
