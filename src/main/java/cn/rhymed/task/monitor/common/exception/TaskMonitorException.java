package cn.rhymed.task.monitor.common.exception;

/**
 * 任务监控基础异常
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class TaskMonitorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TaskMonitorException(String message) {
        super(message);
    }

    public TaskMonitorException(String message, Throwable cause) {
        super(message, cause);
    }

    public TaskMonitorException(Throwable cause) {
        super(cause);
    }
}
