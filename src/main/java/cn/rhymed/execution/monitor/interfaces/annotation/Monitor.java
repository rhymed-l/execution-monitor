package cn.rhymed.execution.monitor.interfaces.annotation;

import cn.rhymed.execution.monitor.common.enums.AlertType;
import cn.rhymed.execution.monitor.common.enums.SerializationMode;

import java.lang.annotation.*;

/**
 * 监控注解
 * 标记在需要监控的方法上,自动记录执行状态
 * <p>
 * 使用示例:
 * <pre>
 * // 指定执行名称
 * {@code @Monitor(name = "processOrder", bizKey = "#orderId", serializeParams = true, maxRetry = 3)}
 * public void processOrder(String orderId, OrderData data) {
 *     // 业务逻辑
 * }
 *
 * // 不指定执行名称,自动使用方法名 "handlePayment"
 * {@code @Monitor(bizKey = "#paymentId", maxRetry = 5)}
 * public void handlePayment(String paymentId) {
 *     // 业务逻辑
 * }
 *
 * // 最简单的用法,只监控执行状态
 * {@code @Monitor}
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
public @interface Monitor {

    /**
     * 执行名称(可选)
     * 用于标识执行类型,相同名称的执行记录会被归类管理
     * 如果不填,默认使用方法名
     */
    String name() default "";

    /**
     * 业务关键字表达式(可选)
     * 支持SpEL表达式,用于提取业务标识(如订单号、文件名)
     * 示例: "#orderId", "#request.orderId", "#p0"
     */
    String bizKey() default "";

    /**
     * 参数序列化模式(可选)
     * AUTO: 自动检测是否需要序列化(默认)
     * ALWAYS: 总是序列化参数
     * NEVER: 从不序列化参数
     * 注意:参数对象需要支持JSON序列化
     */
    SerializationMode serializeParams() default SerializationMode.AUTO;

    /**
     * 最大重试次数(可选)
     * 默认值取自全局配置 execution.monitor.retry.max-retry
     */
    int maxRetry() default -1;

    /**
     * 告警类型(可选)
     * 指定使用哪些告警服务，支持多选
     * DEFAULT: 使用所有已启用的告警服务(默认)
     * DINGTALK: 仅使用钉钉告警
     * FEISHU: 仅使用飞书告警
     * NONE: 禁用告警
     * <p>
     * 示例:
     * - alertTypes = {AlertType.DEFAULT} : 使用所有已启用的告警
     * - alertTypes = {AlertType.FEISHU} : 仅使用飞书告警
     * - alertTypes = {AlertType.DINGTALK, AlertType.FEISHU} : 同时使用钉钉和飞书告警
     * - alertTypes = {AlertType.NONE} : 禁用告警
     */
    AlertType[] alertTypes() default {AlertType.DEFAULT};
}
