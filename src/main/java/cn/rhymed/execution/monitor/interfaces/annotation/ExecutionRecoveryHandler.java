package cn.rhymed.execution.monitor.interfaces.annotation;

import java.lang.annotation.*;

/**
 * 执行恢复处理器注解
 * 用于标记自定义恢复处理器方法
 * <p>
 * 使用示例:
 * <pre>
 * {@code @ExecutionRecoveryHandler(executionName = "processFile")}
 * public void recoverProcessFile(ExecutionLogDTO executionLog) {
 *     // 自定义恢复逻辑
 *     File file = new File(executionLog.getBizKey());
 *     processFile(file);
 * }
 * </pre>
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExecutionRecoveryHandler {

    /**
     * 执行名称
     * 对应@ExecutionMonitor的executionName
     */
    String executionName();

    /**
     * 处理器优先级
     * 值越小优先级越高
     */
    int priority() default 0;
}
