package cn.rhymed.task.monitor.domain.model;

import cn.hutool.core.collection.CollUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 异常分类值对象
 * 封装可重试和可忽略异常的分类规则
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
@ToString
@EqualsAndHashCode
public class ExceptionClassification implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 可重试的异常列表
     * 包含可以进行重试的异常类名(支持部分匹配)
     * 例如: ["java.net.SocketTimeoutException", "java.sql.SQLException"]
     * 不可变集合
     */
    private final List<String> retryableExceptions;

    /**
     * 可忽略的异常列表
     * 包含应该忽略不处理的异常类名(支持部分匹配)
     * 例如: ["java.lang.IllegalArgumentException"]
     * 不可变集合,与retryableExceptions不能有交集
     */
    private final List<String> ignorableExceptions;

    public ExceptionClassification(List<String> retryableExceptions, List<String> ignorableExceptions) {
        this.retryableExceptions = CollUtil.isEmpty(retryableExceptions)
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(retryableExceptions));
        this.ignorableExceptions = CollUtil.isEmpty(ignorableExceptions)
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(ignorableExceptions));

        // 验证重试和忽略列表不能有交集
        validateNoOverlap();
    }

    public static ExceptionClassification empty() {
        return new ExceptionClassification(Collections.emptyList(), Collections.emptyList());
    }

    public static ExceptionClassification of(List<String> retryableExceptions, List<String> ignorableExceptions) {
        return new ExceptionClassification(retryableExceptions, ignorableExceptions);
    }

    private void validateNoOverlap() {
        if (CollUtil.isEmpty(retryableExceptions) || CollUtil.isEmpty(ignorableExceptions)) {
            return;
        }

        for (String retryable : retryableExceptions) {
            for (String ignorable : ignorableExceptions) {
                if (retryable.equals(ignorable) || retryable.contains(ignorable) || ignorable.contains(retryable)) {
                    throw new IllegalArgumentException(
                            "异常分类不能有重叠: " + retryable + " 与 " + ignorable);
                }
            }
        }
    }

    /**
     * 判断异常是否可重试
     */
    public boolean isRetryable(ErrorInfo errorInfo) {
        if (errorInfo == null) {
            return false;
        }
        return errorInfo.isRetryable(retryableExceptions);
    }

    /**
     * 判断异常是否可忽略
     */
    public boolean isIgnorable(ErrorInfo errorInfo) {
        if (errorInfo == null) {
            return false;
        }
        return errorInfo.isIgnorable(ignorableExceptions);
    }

    /**
     * 判断是否有重试规则
     */
    public boolean hasRetryRules() {
        return CollUtil.isNotEmpty(retryableExceptions);
    }

    /**
     * 判断是否有忽略规则
     */
    public boolean hasIgnoreRules() {
        return CollUtil.isNotEmpty(ignorableExceptions);
    }


}
