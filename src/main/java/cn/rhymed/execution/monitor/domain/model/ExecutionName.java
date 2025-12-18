package cn.rhymed.execution.monitor.domain.model;

import cn.hutool.core.util.StrUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 执行名称值对象
 * 不可变对象,有长度限制
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
@ToString
@EqualsAndHashCode
public class ExecutionName implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int MAX_LENGTH = 200;

    /**
     * 执行名称的字符串值
     * 标识执行类型,长度不超过200字符
     * 通常对应被监控的方法名或业务操作名称
     */
    private final String value;

    public ExecutionName(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("ExecutionName不能为空");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("ExecutionName长度不能超过" + MAX_LENGTH + "字符");
        }
        this.value = value;
    }

    public static ExecutionName of(String value) {
        return new ExecutionName(value);
    }


}
