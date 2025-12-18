package cn.rhymed.task.monitor.domain.model;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;

/**
 * 任务ID值对象
 * 不可变对象,保证唯一性
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
@ToString
@EqualsAndHashCode
public class TaskId implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID的字符串值
     * 全局唯一,通常为UUID格式
     */
    private final String value;

    public TaskId(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("TaskId不能为空");
        }
        this.value = value;
    }

    /**
     * 生成新的TaskId
     */
    public static TaskId generate() {
        return new TaskId(IdUtil.simpleUUID());
    }

    /**
     * 从字符串创建
     */
    public static TaskId of(String value) {
        return new TaskId(value);
    }

}
