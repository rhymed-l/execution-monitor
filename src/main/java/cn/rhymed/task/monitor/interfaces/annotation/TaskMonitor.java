package cn.rhymed.task.monitor.interfaces.annotation;

import java.lang.annotation.*;

/**
 * 任务监控注解
 * 标记在需要监控的方法上,自动记录任务执行状态
 * <p>
 * 使用示例:
 * <pre>
 * // 指定任务名称
 * {@code @TaskMonitor(taskName = "processOrder", bizKey = "#orderId", serializeParams = true, maxRetry = 3)}
 * public void processOrder(String orderId, OrderData data) {
 *     // 业务逻辑
 * }
 *
 * // 不指定任务名称,自动使用方法名 "handlePayment"
 * {@code @TaskMonitor(bizKey = "#paymentId", maxRetry = 5)}
 * public void handlePayment(String paymentId) {
 *     // 业务逻辑
 * }
 *
 * // 最简单的用法,只监控执行状态
 * {@code @TaskMonitor}
 * public void syncData() {
 *     // 业务逻辑
 * }
 * </pre>
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskMonitor {

    /**
     * 任务名称(可选)
     * 用于标识任务类型,相同任务名称的执行记录会被归类管理
     * 如果不填,默认使用方法名
     */
    String taskName() default "";

    /**
     * 业务关键字表达式(可选)
     * 支持SpEL表达式,用于提取业务标识(如订单号、文件名)
     * 示例: "#orderId", "#request.orderId", "#p0"
     */
    String bizKey() default "";

    /**
     * 是否序列化方法参数(可选)
     * 如果为true,将把方法参数序列化为JSON存储
     * 注意:参数对象需要支持JSON序列化
     */
    boolean serializeParams() default false;

    /**
     * 最大重试次数(可选)
     * 默认值取自全局配置 task.monitor.retry.max-retry
     */
    int maxRetry() default -1;

    /**
     * 是否启用心跳监控(可选)
     * 如果为true,将定期检查任务是否存活
     * 默认值取自全局配置 task.monitor.heartbeat.enabled
     */
    boolean enableHeartbeat() default false;

    /**
     * 心跳间隔(秒)(可选)
     * 仅在enableHeartbeat=true时生效
     * 默认值取自全局配置 task.monitor.heartbeat.interval-seconds
     */
    int heartbeatIntervalSeconds() default -1;
}
