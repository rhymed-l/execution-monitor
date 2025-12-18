package cn.rhymed.task.monitor.infrastructure.util;

import cn.hutool.core.util.ReflectUtil;
import cn.rhymed.task.monitor.domain.model.SerializedParams;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * 反射调用工具
 * 用于重试时重新调用原方法
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class ReflectionInvoker {

    /**
     * 调用方法
     *
     * @param targetBean 目标bean
     * @param methodName 方法名
     * @param params     序列化的参数
     * @return 方法返回值
     */
    public static Object invoke(Object targetBean, String methodName, SerializedParams params) {
        if (targetBean == null) {
            throw new IllegalArgumentException("目标bean不能为空");
        }

        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("方法名不能为空");
        }

        try {
            // 反序列化参数
            Object[] args = params != null && !params.isEmpty()
                    ? params.deserialize()
                    : new Object[0];

            // 查找方法
            Method method = findMethod(targetBean.getClass(), methodName, args);

            if (method == null) {
                throw new NoSuchMethodException(
                        String.format("未找到方法: %s.%s", targetBean.getClass().getName(), methodName));
            }

            // 调用方法
            method.setAccessible(true);
            Object result = method.invoke(targetBean, args);

            log.debug("方法调用成功: {}.{}", targetBean.getClass().getSimpleName(), methodName);
            return result;

        } catch (Exception e) {
            log.error("反射调用方法失败: {}.{}", targetBean.getClass().getName(), methodName, e);
            throw new RuntimeException("方法调用失败", e);
        }
    }

    /**
     * 根据方法名和参数查找方法
     */
    private static Method findMethod(Class<?> clazz, String methodName, Object[] args) {
        // 首先尝试精确匹配
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                Class<?>[] paramTypes = method.getParameterTypes();

                // 检查参数数量
                if (paramTypes.length != args.length) {
                    continue;
                }

                // 检查参数类型
                boolean matches = true;
                for (int i = 0; i < paramTypes.length; i++) {
                    if (args[i] != null && !isAssignable(paramTypes[i], args[i].getClass())) {
                        matches = false;
                        break;
                    }
                }

                if (matches) {
                    return method;
                }
            }
        }

        // 如果没找到,尝试查找父类
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            return findMethod(superclass, methodName, args);
        }

        return null;
    }

    /**
     * 检查类型是否可赋值
     */
    private static boolean isAssignable(Class<?> paramType, Class<?> argType) {
        // 基本类型处理
        if (paramType.isPrimitive()) {
            return isPrimitiveMatch(paramType, argType);
        }

        return paramType.isAssignableFrom(argType);
    }

    /**
     * 基本类型匹配
     */
    private static boolean isPrimitiveMatch(Class<?> primitiveType, Class<?> wrapperType) {
        if (primitiveType == int.class) {
            return wrapperType == Integer.class;
        } else if (primitiveType == long.class) {
            return wrapperType == Long.class;
        } else if (primitiveType == boolean.class) {
            return wrapperType == Boolean.class;
        } else if (primitiveType == double.class) {
            return wrapperType == Double.class;
        } else if (primitiveType == float.class) {
            return wrapperType == Float.class;
        } else if (primitiveType == short.class) {
            return wrapperType == Short.class;
        } else if (primitiveType == byte.class) {
            return wrapperType == Byte.class;
        } else if (primitiveType == char.class) {
            return wrapperType == Character.class;
        }
        return false;
    }

    /**
     * 使用Hutool的ReflectUtil进行简单调用
     */
    public static Object invokeSimple(Object targetBean, String methodName, Object... args) {
        try {
            return ReflectUtil.invoke(targetBean, methodName, args);
        } catch (Exception e) {
            log.error("Hutool反射调用失败: {}.{}", targetBean.getClass().getName(), methodName, e);
            throw new RuntimeException("方法调用失败", e);
        }
    }
}
