package cn.rhymed.execution.monitor.common.exception;

/**
 * 任务监控基础异常
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class MonitorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MonitorException(String message) {
        super(message);
    }

    public MonitorException(String message, Throwable cause) {
        super(message, cause);
    }

    public MonitorException(Throwable cause) {
        super(cause);
    }
}
