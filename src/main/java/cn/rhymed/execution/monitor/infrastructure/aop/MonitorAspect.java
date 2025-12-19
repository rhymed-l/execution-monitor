package cn.rhymed.execution.monitor.infrastructure.aop;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.rhymed.execution.monitor.application.service.MonitorService;
import cn.rhymed.execution.monitor.common.enums.AlertType;
import cn.rhymed.execution.monitor.common.enums.SerializationMode;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.model.MethodMetadata;
import cn.rhymed.execution.monitor.domain.service.SerializationDecisionService;
import cn.rhymed.execution.monitor.infrastructure.context.RetryContext;
import cn.rhymed.execution.monitor.infrastructure.util.BeanResolver;
import cn.rhymed.execution.monitor.infrastructure.util.BizKeyExpressionParser;
import cn.rhymed.execution.monitor.interfaces.annotation.Monitor;
import cn.rhymed.execution.monitor.interfaces.config.MonitorProperties;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 任务监控切面
 * 拦截@Monitor注解的方法,实现任务监控逻辑
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
@Aspect
public class MonitorAspect {

    private final MonitorService MonitorService;
    private final MonitorProperties properties;
    private final SerializationDecisionService serializationService;

    public MonitorAspect(MonitorService monitorService,
                         MonitorProperties properties,
                             SerializationDecisionService serializationService) {
        this.MonitorService = monitorService;
        this.properties = properties;
        this.serializationService = serializationService;
    }

    @Around("@annotation(cn.rhymed.execution.monitor.interfaces.annotation.Monitor)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 如果当前是重试调用，跳过监控，直接执行方法
        if (RetryContext.isRetrying()) {
            log.debug("检测到重试上下文，跳过AOP监控");
            return joinPoint.proceed();
        }

        // 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Monitor annotation = method.getAnnotation(Monitor.class);

        // 提取任务信息
        String name = resolveExecutionName(annotation.name(), method);
        String bizKey = extractBizKey(annotation.bizKey(), method, joinPoint.getArgs());
        String paramsJson = serializeParams(annotation.serializeParams(), joinPoint.getArgs());
        int maxRetry = resolveMaxRetry(annotation.maxRetry());
        Set<AlertType> alertTypes = resolveAlertTypes(annotation.alertTypes());

        // 提取方法元信息（用于自动重试）
        MethodMetadata methodMetadata = BeanResolver.extractMetadata(joinPoint.getTarget(), method);

        // 开始监控
        ExecutionId executionId = MonitorService.startMonitoring(name, bizKey, paramsJson, methodMetadata, maxRetry, alertTypes);

        // 处理心跳（使用全局配置）
        if (properties.getHeartbeat().isEnabled()) {
            int heartbeatInterval = properties.getHeartbeat().getIntervalSeconds();
            MonitorService.enableHeartbeat(executionId, heartbeatInterval);
        }

        try {
            // 执行原方法
            Object result = joinPoint.proceed();

            // 标记成功
            MonitorService.markSuccess(executionId);

            return result;
        } catch (Throwable throwable) {
            // 标记失败
            MonitorService.markFailure(executionId, throwable);

            // 重新抛出异常
            throw throwable;
        }
    }

    /**
     * 解析任务名称
     * 如果注解中未指定executionName,则使用方法名
     */
    private String resolveExecutionName(String annotationExecutionName, Method method) {
        if (StrUtil.isNotBlank(annotationExecutionName)) {
            return annotationExecutionName;
        }
        return method.getName();
    }

    /**
     * 提取业务关键字
     */
    private String extractBizKey(String bizKeyExpression, Method method, Object[] args) {
        if (StrUtil.isBlank(bizKeyExpression)) {
            return null;
        }

        try {
            return BizKeyExpressionParser.parseExpression(bizKeyExpression, method, args);
        } catch (Exception e) {
            log.warn("解析bizKey表达式失败: {}, 方法: {}", bizKeyExpression, method.getName(), e);
            return null;
        }
    }

    /**
     * 序列化参数
     */
    private String serializeParams(SerializationMode mode, Object[] args) {
        // NEVER 模式：从不序列化
        if (mode == SerializationMode.NEVER) {
            return null;
        }

        if (args == null || args.length == 0) {
            return null;
        }

        // AUTO 模式：需要智能判断
        if (mode == SerializationMode.AUTO) {
            // 检查全局序列化开关
            if (!properties.getSerialization().isEnabled()) {
                return null;
            }

            // 智能判断是否可以序列化
            if (!serializationService.canSerialize(args)) {
                log.debug("参数不可序列化,原因: {}", serializationService.getLastDecisionReason());
                return null;
            }
        }

        // ALWAYS 模式或 AUTO 模式通过检查：执行序列化
        try {
            String json = JSONUtil.toJsonStr(args);

            // 检查大小限制
            int maxSize = properties.getSerialization().getMaxSizeBytes();
            if (json.getBytes().length > maxSize) {
                log.warn("参数序列化超过大小限制 {}字节,放弃序列化", maxSize);
                return null;
            }

            return json;
        } catch (Exception e) {
            log.warn("序列化参数失败", e);
            return null;
        }
    }

    /**
     * 解析最大重试次数
     */
    private int resolveMaxRetry(int annotationMaxRetry) {
        if (annotationMaxRetry >= 0) {
            return annotationMaxRetry;
        }
        return properties.getRetry().getMaxRetry();
    }

    /**
     * 解析告警类型
     * 从注解中提取告警类型配置
     */
    private Set<AlertType> resolveAlertTypes(AlertType[] annotationAlertTypes) {
        if (annotationAlertTypes == null || annotationAlertTypes.length == 0) {
            // 默认使用所有启用的告警服务
            return new HashSet<>(Collections.singletonList(AlertType.DEFAULT));
        }
        return new HashSet<>(Arrays.asList(annotationAlertTypes));
    }

}
