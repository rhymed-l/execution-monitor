package cn.rhymed.execution.monitor.domain.model;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 执行ID值对象
 * 不可变对象,保证唯一性
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
@ToString
@EqualsAndHashCode
public class ExecutionId implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 执行ID的字符串值
     * 全局唯一,通常为UUID格式
     */
    private final String value;

    public ExecutionId(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("ExecutionId不能为空");
        }
        this.value = value;
    }

    /**
     * 生成新的ExecutionId
     */
    public static ExecutionId generate() {
        return new ExecutionId(IdUtil.simpleUUID());
    }

    /**
     * 从字符串创建
     */
    public static ExecutionId of(String value) {
        return new ExecutionId(value);
    }

}
