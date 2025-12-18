package cn.rhymed.execution.monitor.domain.event;

import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.ExecutionName;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务标记为重试事件
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
public class ExecutionMarkedForRetryEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ExecutionId executionId;
    private final ExecutionName executionName;
    private final int retryCount;
    private final long nextRetryTimeMillis;
    private final LocalDateTime eventTime;

    public ExecutionMarkedForRetryEvent(ExecutionId executionId, ExecutionName executionName, int retryCount, long nextRetryTimeMillis) {
        this.executionId = executionId;
        this.executionName = executionName;
        this.retryCount = retryCount;
        this.nextRetryTimeMillis = nextRetryTimeMillis;
        this.eventTime = LocalDateTime.now();
    }

}
