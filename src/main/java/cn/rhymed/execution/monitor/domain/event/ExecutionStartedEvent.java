package cn.rhymed.execution.monitor.domain.event;

import cn.rhymed.execution.monitor.domain.model.BizKey;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 执行开始事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
public class ExecutionStartedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ExecutionId executionId;
    private final ExecutionName executionName;
    private final BizKey bizKey;
    private final LocalDateTime startTime;
    private final LocalDateTime eventTime;

    public ExecutionStartedEvent(ExecutionId executionId, ExecutionName executionName, BizKey bizKey, LocalDateTime startTime) {
        this.executionId = executionId;
        this.executionName = executionName;
        this.bizKey = bizKey;
        this.startTime = startTime;
        this.eventTime = LocalDateTime.now();
    }

}
