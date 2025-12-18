package cn.rhymed.execution.monitor.domain.model;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * 序列化参数值对象
 * 封装JSON格式的参数和大小信息
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Getter
@ToString
@EqualsAndHashCode
public class SerializedParams implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * JSON格式的参数数据
     * 存储方法调用时的参数序列化结果
     * 用于任务重试时恢复方法调用
     */
    private final String jsonData;

    /**
     * 参数数据的字节大小
     * 以UTF-8编码计算的字节数
     * 用于判断参数是否超过存储限制
     */
    private final int sizeBytes;

    public SerializedParams(String jsonData) {
        this.jsonData = jsonData;
        this.sizeBytes = StrUtil.isNotBlank(jsonData)
                ? jsonData.getBytes(StandardCharsets.UTF_8).length
                : 0;
    }

    public static SerializedParams of(String jsonData) {
        return new SerializedParams(jsonData);
    }

    public static SerializedParams empty() {
        return new SerializedParams(null);
    }

    public boolean isEmpty() {
        return StrUtil.isBlank(jsonData);
    }

    /**
     * 是否超过大小限制
     */
    public boolean exceedsLimit(int maxSizeBytes) {
        return sizeBytes > maxSizeBytes;
    }

    /**
     * 反序列化为对象数组
     */
    public Object[] deserialize() {
        if (isEmpty()) {
            return new Object[0];
        }
        return JSONUtil.toBean(jsonData, Object[].class);
    }
}
