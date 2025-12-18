package cn.rhymed.task.monitor.interfaces.annotation;

import java.lang.annotation.*;

/**
 * 任务恢复处理器注解
 * 用于标记自定义恢复处理器方法
 * <p>
 * 使用示例:
 * <pre>
 * {@code @TaskRecoveryHandler(taskName = "processFile")}
 * public void recoverProcessFile(TaskLogDTO taskLog) {
 *     // 自定义恢复逻辑
 *     File file = new File(taskLog.getBizKey());
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
public @interface TaskRecoveryHandler {

    /**
     * 任务名称
     * 对应@TaskMonitor的taskName
     */
    String taskName();

    /**
     * 处理器优先级
     * 值越小优先级越高
     */
    int priority() default 0;
}
