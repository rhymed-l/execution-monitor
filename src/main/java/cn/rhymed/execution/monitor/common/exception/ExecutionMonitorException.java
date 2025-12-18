package cn.rhymed.execution.monitor.common.exception;

/**
 * 任务监控基础异常
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class ExecutionMonitorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExecutionMonitorException(String message) {
        super(message);
    }

    public ExecutionMonitorException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExecutionMonitorException(Throwable cause) {
        super(cause);
    }
}
