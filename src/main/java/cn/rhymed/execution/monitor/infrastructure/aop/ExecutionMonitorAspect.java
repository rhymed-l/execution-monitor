package cn.rhymed.execution.monitor.infrastructure.aop;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.rhymed.execution.monitor.application.service.ExecutionMonitorService;
import cn.rhymed.execution.monitor.domain.model.ExecutionId;
import cn.rhymed.execution.monitor.domain.service.SerializationDecisionService;
import cn.rhymed.execution.monitor.infrastructure.util.BizKeyExpressionParser;
import cn.rhymed.execution.monitor.interfaces.annotation.ExecutionMonitor;
import cn.rhymed.execution.monitor.interfaces.config.ExecutionMonitorProperties;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * 任务监控切面
 * 拦截@ExecutionMonitor注解的方法,实现任务监控逻辑
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
@Aspect
public class ExecutionMonitorAspect {

    private final ExecutionMonitorService ExecutionMonitorService;
    private final ExecutionMonitorProperties properties;
    private final SerializationDecisionService serializationService;

    public ExecutionMonitorAspect(ExecutionMonitorService ExecutionMonitorService,
                                  ExecutionMonitorProperties properties,
                             SerializationDecisionService serializationService) {
        this.ExecutionMonitorService = ExecutionMonitorService;
        this.properties = properties;
        this.serializationService = serializationService;
    }

    @Around("@annotation(cn.rhymed.execution.monitor.interfaces.annotation.ExecutionMonitor)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        ExecutionMonitor annotation = method.getAnnotation(ExecutionMonitor.class);

        // 提取任务信息
        String executionName = resolveExecutionName(annotation.executionName(), method);
        String bizKey = extractBizKey(annotation.bizKey(), method, joinPoint.getArgs());
        String paramsJson = serializeParams(annotation.serializeParams(), joinPoint.getArgs());
        int maxRetry = resolveMaxRetry(annotation.maxRetry());

        // 开始监控
        ExecutionId executionId = ExecutionMonitorService.startMonitoring(executionName, bizKey, paramsJson, maxRetry);

        // 处理心跳
        if (shouldEnableHeartbeat(annotation)) {
            int heartbeatInterval = resolveHeartbeatInterval(annotation.heartbeatIntervalSeconds());
            ExecutionMonitorService.enableHeartbeat(executionId, heartbeatInterval);
        }

        try {
            // 执行原方法
            Object result = joinPoint.proceed();

            // 标记成功
            ExecutionMonitorService.markSuccess(executionId);

            return result;
        } catch (Throwable throwable) {
            // 标记失败
            ExecutionMonitorService.markFailure(executionId, throwable);

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
    private String serializeParams(boolean shouldSerialize, Object[] args) {
        if (!shouldSerialize) {
            return null;
        }

        if (!properties.getSerialization().isEnabled()) {
            return null;
        }

        if (args == null || args.length == 0) {
            return null;
        }

        // 智能判断是否可以序列化
        if (!serializationService.canSerialize(args)) {
            log.debug("参数不可序列化,原因: {}", serializationService.getLastDecisionReason());
            return null;
        }

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
     * 判断是否应该启用心跳
     */
    private boolean shouldEnableHeartbeat(ExecutionMonitor annotation) {
        if (annotation.enableHeartbeat()) {
            return true;
        }
        return properties.getHeartbeat().isEnabled();
    }

    /**
     * 解析心跳间隔
     */
    private int resolveHeartbeatInterval(int annotationInterval) {
        if (annotationInterval > 0) {
            return annotationInterval;
        }
        return properties.getHeartbeat().getIntervalSeconds();
    }
}
