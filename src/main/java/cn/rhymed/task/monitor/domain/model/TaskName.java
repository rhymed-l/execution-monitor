package cn.rhymed.task.monitor.domain.model;

import cn.hutool.core.util.StrUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;

/**
 * 任务名称值对象
 * 不可变对象,有长度限制
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
@ToString
@EqualsAndHashCode
public class TaskName implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int MAX_LENGTH = 200;

    /**
     * 任务名称的字符串值
     * 标识任务类型,长度不超过200字符
     * 通常对应被监控的方法名或业务操作名称
     */
    private final String value;

    public TaskName(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("TaskName不能为空");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("TaskName长度不能超过" + MAX_LENGTH + "字符");
        }
        this.value = value;
    }

    public static TaskName of(String value) {
        return new TaskName(value);
    }


}
