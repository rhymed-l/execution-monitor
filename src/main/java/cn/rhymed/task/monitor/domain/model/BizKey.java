package cn.rhymed.task.monitor.domain.model;

import cn.hutool.core.util.StrUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;

/**
 * 业务关键字值对象
 * 可选的业务标识,如订单号、文件名
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
@ToString
@EqualsAndHashCode
public class BizKey implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int MAX_LENGTH = 200;

    /**
     * 业务键的字符串值
     * 用于关联业务实体,如订单号、用户ID、文件路径等
     * 可选字段,长度不超过200字符
     * null值表示未设置业务键
     */
    private final String value;

    public BizKey(String value) {
        if (value != null && value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("BizKey长度不能超过" + MAX_LENGTH + "字符");
        }
        this.value = value;
    }

    public static BizKey of(String value) {
        return new BizKey(value);
    }

    public static BizKey empty() {
        return new BizKey(null);
    }

    public boolean isPresent() {
        return StrUtil.isNotBlank(value);
    }

}
