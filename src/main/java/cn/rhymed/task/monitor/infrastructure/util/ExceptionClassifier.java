package cn.rhymed.task.monitor.infrastructure.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 异常分类器
 * 判断异常是可重试还是可忽略
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class ExceptionClassifier {

    private final List<String> retryableExceptions;
    private final List<String> ignorableExceptions;

    public ExceptionClassifier(List<String> retryableExceptions, List<String> ignorableExceptions) {
        this.retryableExceptions = retryableExceptions != null
                ? Collections.unmodifiableList(new ArrayList<>(retryableExceptions))
                : Collections.emptyList();
        this.ignorableExceptions = ignorableExceptions != null
                ? Collections.unmodifiableList(new ArrayList<>(ignorableExceptions))
                : Collections.emptyList();
    }

    /**
     * 判断异常是否可重试
     */
    public boolean isRetryable(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        return matchesAnyPattern(throwable, retryableExceptions);
    }

    /**
     * 判断异常是否可忽略
     */
    public boolean isIgnorable(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        return matchesAnyPattern(throwable, ignorableExceptions);
    }

    /**
     * 对异常进行分类
     */
    public ClassificationResult classify(Throwable throwable) {
        if (throwable == null) {
            return ClassificationResult.NEITHER;
        }

        // 可忽略优先级更高
        if (isIgnorable(throwable)) {
            return ClassificationResult.IGNORABLE;
        }

        if (isRetryable(throwable)) {
            return ClassificationResult.RETRYABLE;
        }

        return ClassificationResult.NEITHER;
    }

    /**
     * 检查异常是否匹配任何模式
     */
    private boolean matchesAnyPattern(Throwable throwable, List<String> patterns) {
        if (patterns.isEmpty()) {
            return false;
        }

        // 检查异常本身
        if (matchesException(throwable, patterns)) {
            return true;
        }

        // 检查cause链
        Throwable cause = throwable.getCause();
        while (cause != null) {
            if (matchesException(cause, patterns)) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }

    /**
     * 检查单个异常是否匹配模式
     */
    private boolean matchesException(Throwable throwable, List<String> patterns) {
        String exceptionClassName = throwable.getClass().getName();
        String simpleClassName = throwable.getClass().getSimpleName();

        for (String pattern : patterns) {
            // 完全匹配类名
            if (exceptionClassName.equals(pattern)) {
                return true;
            }

            // 简单类名匹配
            if (simpleClassName.equals(pattern)) {
                return true;
            }

            // 包含匹配(子串)
            if (exceptionClassName.contains(pattern)) {
                return true;
            }

            // 检查父类
            if (matchesParentClass(throwable.getClass(), pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查父类是否匹配
     */
    private boolean matchesParentClass(Class<?> exceptionClass, String pattern) {
        Class<?> current = exceptionClass.getSuperclass();

        while (current != null && current != Object.class) {
            String className = current.getName();
            String simpleName = current.getSimpleName();

            if (className.equals(pattern) || simpleName.equals(pattern) || className.contains(pattern)) {
                return true;
            }

            current = current.getSuperclass();
        }

        return false;
    }

    /**
     * 获取配置的可重试异常列表
     */
    public List<String> getRetryableExceptions() {
        return retryableExceptions;
    }

    /**
     * 获取配置的可忽略异常列表
     */
    public List<String> getIgnorableExceptions() {
        return ignorableExceptions;
    }

    /**
     * 分类结果枚举
     */
    public enum ClassificationResult {
        RETRYABLE,   // 可重试
        IGNORABLE,   // 可忽略
        NEITHER      // 都不是
    }
}
