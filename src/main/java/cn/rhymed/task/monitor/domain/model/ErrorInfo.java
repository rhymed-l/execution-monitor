package cn.rhymed.task.monitor.domain.model;

import cn.hutool.core.exceptions.ExceptionUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 错误信息值对象
 * 封装异常的详细信息
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
@ToString
@EqualsAndHashCode
public class ErrorInfo implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int MAX_STACK_TRACE_LENGTH = 2000;

    /**
     * 错误消息
     * 异常的描述信息,通常来自 Throwable.getMessage()
     */
    private final String errorMessage;

    /**
     * 异常类型
     * 异常类的全限定名,如: java.lang.NullPointerException
     * 用于异常分类和判断是否可重试
     */
    private final String exceptionType;

    /**
     * 堆栈跟踪
     * 异常的堆栈信息,最大长度2000字符
     * 用于问题排查和调试
     */
    private final String stackTrace;

    public ErrorInfo(String errorMessage, String exceptionType, String stackTrace) {
        this.errorMessage = errorMessage;
        this.exceptionType = exceptionType;
        this.stackTrace = stackTrace;
    }

    /**
     * 从Throwable创建
     */
    public static ErrorInfo fromThrowable(Throwable throwable) {
        String message = throwable.getMessage();
        String type = throwable.getClass().getName();
        String trace = ExceptionUtil.stacktraceToString(throwable, MAX_STACK_TRACE_LENGTH);
        return new ErrorInfo(message, type, trace);
    }

    /**
     * 判断是否为可重试异常
     */
    public boolean isRetryable(List<String> retryableExceptions) {
        if (retryableExceptions == null || retryableExceptions.isEmpty()) {
            return false;
        }
        return retryableExceptions.stream()
                .anyMatch(exceptionType::contains);
    }

    /**
     * 判断是否为可忽略异常
     */
    public boolean isIgnorable(List<String> ignorableExceptions) {
        if (ignorableExceptions == null || ignorableExceptions.isEmpty()) {
            return false;
        }
        return ignorableExceptions.stream()
                .anyMatch(exceptionType::contains);
    }

}
