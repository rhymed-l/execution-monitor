package cn.rhymed.execution.monitor.domain.event;

import cn.rhymed.execution.monitor.common.enums.RecoveryStrategy;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务恢复事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
public class ExecutionRecoveredEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ExecutionId originalExecutionId;
    private final ExecutionId newExecutionId;
    private final ExecutionName executionName;
    private final RecoveryStrategy strategy;
    private final LocalDateTime eventTime;

    public ExecutionRecoveredEvent(ExecutionId originalExecutionId, ExecutionId newExecutionId, ExecutionName executionName, RecoveryStrategy strategy) {
        this.originalExecutionId = originalExecutionId;
        this.newExecutionId = newExecutionId;
        this.executionName = executionName;
        this.strategy = strategy;
        this.eventTime = LocalDateTime.now();
    }

}
