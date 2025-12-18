package cn.rhymed.execution.monitor.common.exception;

/**
 * 恢复异常
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class RecoveryException extends MonitorException {

    private static final long serialVersionUID = 1L;

    public RecoveryException(String message) {
        super(message);
    }

    public RecoveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public RecoveryException(Throwable cause) {
        super(cause);
    }
}
