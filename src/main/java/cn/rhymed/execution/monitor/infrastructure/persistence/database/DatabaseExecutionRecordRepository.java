package cn.rhymed.execution.monitor.infrastructure.persistence.database;

import cn.rhymed.execution.monitor.common.enums.ExecutionStatus;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import cn.rhymed.execution.monitor.domain.model.MethodMetadata;
import cn.rhymed.execution.monitor.domain.repository.ExecutionRecordRepository;
import cn.rhymed.execution.monitor.infrastructure.persistence.MethodMetadataRepositoryImpl;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.mapper.ExecutionLogMapper;
import cn.rhymed.execution.monitor.infrastructure.persistence.database.po.ExecutionLogPO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 基于数据库的任务执行仓储实现
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class DatabaseExecutionRecordRepository implements ExecutionRecordRepository {

    private final ExecutionLogMapper mapper;
    private final MethodMetadataRepositoryImpl metadataRepository;

    public DatabaseExecutionRecordRepository(ExecutionLogMapper mapper,
                                             MethodMetadataRepositoryImpl metadataRepository) {
        this.mapper = mapper;
        this.metadataRepository = metadataRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ExecutionRecord execution) {
        ExecutionLogPO po = ExecutionLogPO.fromDomain(execution);

        // 保存方法元信息并获取ID
        MethodMetadata metadata = execution.getMethodMetadata();
        if (metadata != null && !metadata.isEmpty()) {
            Long metadataId = metadataRepository.saveOrGetId(metadata);
            po.setMethodMetadataId(metadataId);
        }

        mapper.insert(po);
        log.debug("保存任务执行记录到数据库: {}", execution.getExecutionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatch(List<ExecutionRecord> ExecutionRecords) {
        if (ExecutionRecords == null || ExecutionRecords.isEmpty()) {
            return;
        }

        List<ExecutionLogPO> pos = ExecutionRecords.stream()
                .map(execution -> {
                    ExecutionLogPO po = ExecutionLogPO.fromDomain(execution);

                    // 保存方法元信息并获取ID
                    MethodMetadata metadata = execution.getMethodMetadata();
                    if (metadata != null && !metadata.isEmpty()) {
                        Long metadataId = metadataRepository.saveOrGetId(metadata);
                        po.setMethodMetadataId(metadataId);
                    }

                    return po;
                })
                .collect(Collectors.toList());

        mapper.insertBatch(pos);
        log.debug("批量保存{}条任务执行记录到数据库", ExecutionRecords.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ExecutionRecord execution) {
        ExecutionLogPO po = ExecutionLogPO.fromDomain(execution);
        po.setUpdatedAt(LocalDateTime.now());

        // 保存方法元信息并获取ID
        MethodMetadata metadata = execution.getMethodMetadata();
        if (metadata != null && !metadata.isEmpty()) {
            Long metadataId = metadataRepository.saveOrGetId(metadata);
            po.setMethodMetadataId(metadataId);
        }

        mapper.updateByExecutionId(po);
        log.debug("更新任务执行记录到数据库: {}", execution.getExecutionId());
    }

    @Override
    public Optional<ExecutionRecord> findById(ExecutionId executionId) {
        ExecutionLogPO po = mapper.selectByExecutionId(executionId.getValue());
        return po != null ? Optional.of(toDomainWithMetadata(po)) : Optional.empty();
    }

    /**
     * 将 PO 转换为领域对象，并加载方法元信息
     */
    private ExecutionRecord toDomainWithMetadata(ExecutionLogPO po) {
        ExecutionRecord record = po.toDomain();

        // 加载方法元信息
        if (po.getMethodMetadataId() != null) {
            MethodMetadata metadata = metadataRepository.findById(po.getMethodMetadataId());
            if (metadata != null) {
                // 使用 toBuilder 重新构建，添加 methodMetadata
                record = record.toBuilder()
                        .methodMetadata(metadata)
                        .build();
            }
        }

        return record;
    }

    @Override
    public List<ExecutionRecord> findByExecutionName(ExecutionName executionName) {
        return mapper.selectByExecutionName(executionName.getValue()).stream()
                .map(this::toDomainWithMetadata)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findByStatus(ExecutionStatus status) {
        return mapper.selectByStatus(status.name()).stream()
                .map(this::toDomainWithMetadata)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findByExecutionNameAndStatus(ExecutionName executionName, ExecutionStatus status) {
        return mapper.selectByExecutionNameAndStatus(executionName.getValue(), status.name()).stream()
                .map(this::toDomainWithMetadata)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findRecoverableExecutions(List<ExecutionStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return mapper.selectRecoverableExecutions(null).stream()
                    .map(this::toDomainWithMetadata)
                    .collect(Collectors.toList());
        }
        List<String> statusNames = statuses.stream()
                .map(ExecutionStatus::name)
                .collect(Collectors.toList());
        return mapper.selectRecoverableExecutions(statusNames).stream()
                .map(this::toDomainWithMetadata)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findRunningExecutionsWithHeartbeatBefore(LocalDateTime beforeTime) {
        return mapper.selectRunningExecutionsWithHeartbeatBefore(beforeTime).stream()
                .map(this::toDomainWithMetadata)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findExecutionsForRetry() {
        return mapper.selectExecutionsForRetry().stream()
                .map(this::toDomainWithMetadata)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionRecord> findReadyForRetry(LocalDateTime currentTime) {
        return mapper.selectReadyForRetry(currentTime).stream()
                .map(this::toDomainWithMetadata)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(ExecutionId executionId) {
        mapper.deleteByExecutionId(executionId.getValue());
        log.debug("删除任务执行记录: {}", executionId);
    }

    @Override
    public void cleanupBefore(LocalDateTime beforeTime, List<ExecutionStatus> statuses) {
        List<String> statusNames = null;
        if (statuses != null && !statuses.isEmpty()) {
            statusNames = statuses.stream()
                    .map(ExecutionStatus::name)
                    .collect(Collectors.toList());
        }
        int deleted = mapper.cleanupBefore(beforeTime, statusNames);
        log.info("清理数据库中{}条过期任务记录", deleted);
    }

    @Override
    public long countByStatus(ExecutionStatus status) {
        return mapper.countByStatus(status.name());
    }

    @Override
    public long countByExecutionName(ExecutionName executionName) {
        return mapper.countByExecutionName(executionName.getValue());
    }
}
