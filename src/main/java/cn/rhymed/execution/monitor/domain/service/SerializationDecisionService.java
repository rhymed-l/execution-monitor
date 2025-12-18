package cn.rhymed.execution.monitor.domain.service;

import cn.hutool.core.util.ClassUtil;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 序列化决策服务
 * 智能判断参数是否可以序列化
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class SerializationDecisionService {

    /**
     * 不应序列化的类型黑名单
     */
    private static final Set<Class<?>> BLACKLIST_TYPES = new HashSet<>(Arrays.asList(
            InputStream.class,
            OutputStream.class,
            Reader.class,
            Writer.class,
            File.class,
            Thread.class,
            ThreadLocal.class,
            ClassLoader.class
    ));
    private String lastDecisionReason = "";

    /**
     * 判断参数数组是否可以序列化
     */
    public boolean canSerialize(Object[] args) {
        lastDecisionReason = "";

        // 空参数不序列化
        if (args == null || args.length == 0) {
            lastDecisionReason = "参数为空";
            return false;
        }

        // 检查每个参数
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];

            // null参数可以序列化
            if (arg == null) {
                continue;
            }

            // 检查是否在黑名单中
            if (isBlacklistType(arg)) {
                lastDecisionReason = String.format("参数[%d]类型在黑名单中: %s", i, arg.getClass().getName());
                return false;
            }

            // 检查是否可序列化
            if (!isSerializableType(arg)) {
                lastDecisionReason = String.format("参数[%d]不可序列化: %s", i, arg.getClass().getName());
                return false;
            }

            // 检查循环引用
            if (hasCircularReference(arg)) {
                lastDecisionReason = String.format("参数[%d]存在循环引用", i);
                return false;
            }
        }

        lastDecisionReason = "所有参数可以序列化";
        return true;
    }

    /**
     * 获取最后一次决策的原因
     */
    public String getLastDecisionReason() {
        return lastDecisionReason;
    }

    /**
     * 检查是否为黑名单类型
     */
    private boolean isBlacklistType(Object obj) {
        Class<?> clazz = obj.getClass();

        // 检查是否为黑名单类型或其子类
        for (Class<?> blacklistType : BLACKLIST_TYPES) {
            if (blacklistType.isAssignableFrom(clazz)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查对象是否可序列化
     */
    private boolean isSerializableType(Object obj) {
        Class<?> clazz = obj.getClass();

        // 基本类型和包装类
        if (ClassUtil.isBasicType(clazz)) {
            return true;
        }

        // 字符串
        if (clazz == String.class) {
            return true;
        }

        // 数组
        if (clazz.isArray()) {
            Class<?> componentType = clazz.getComponentType();
            // 基本类型数组
            if (componentType.isPrimitive()) {
                return true;
            }
            // 对象数组需要检查元素
            Object[] array = (Object[]) obj;
            for (Object element : array) {
                if (element != null && !isSerializableType(element)) {
                    return false;
                }
            }
            return true;
        }

        // 集合类型
        if (obj instanceof Collection) {
            Collection<?> collection = (Collection<?>) obj;
            for (Object element : collection) {
                if (element != null && !isSerializableType(element)) {
                    return false;
                }
            }
            return true;
        }

        // Map类型
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && !isSerializableType(entry.getKey())) {
                    return false;
                }
                if (entry.getValue() != null && !isSerializableType(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }

        // 检查是否实现了Serializable接口
        return Serializable.class.isAssignableFrom(clazz);
    }

    /**
     * 检查是否存在循环引用
     * 使用简单的深度检查,避免栈溢出
     */
    private boolean hasCircularReference(Object obj) {
        Set<Object> visited = new HashSet<>();
        return hasCircularReferenceInternal(obj, visited, 0);
    }

    private boolean hasCircularReferenceInternal(Object obj, Set<Object> visited, int depth) {
        // 限制检查深度,避免性能问题
        if (depth > 10) {
            return false;
        }

        if (obj == null) {
            return false;
        }

        Class<?> clazz = obj.getClass();

        // 跳过基本类型和字符串
        if (ClassUtil.isBasicType(clazz) || clazz == String.class) {
            return false;
        }

        // 检查是否已访问过(循环引用)
        if (visited.contains(obj)) {
            return true;
        }

        visited.add(obj);

        // 检查集合
        if (obj instanceof Collection) {
            Collection<?> collection = (Collection<?>) obj;
            for (Object element : collection) {
                if (hasCircularReferenceInternal(element, visited, depth + 1)) {
                    return true;
                }
            }
            return false;
        }

        // 检查Map
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Object value : map.values()) {
                if (hasCircularReferenceInternal(value, visited, depth + 1)) {
                    return true;
                }
            }
            return false;
        }

        // 检查对象字段
        try {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                // 跳过静态字段
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                Object fieldValue = field.get(obj);
                if (hasCircularReferenceInternal(fieldValue, visited, depth + 1)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 如果无法访问字段,保守地认为可能有循环引用
            return false;
        }

        return false;
    }
}
