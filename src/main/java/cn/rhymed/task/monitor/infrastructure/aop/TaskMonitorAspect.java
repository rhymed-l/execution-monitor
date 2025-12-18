package cn.rhymed.task.monitor.infrastructure.aop;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.rhymed.task.monitor.application.service.TaskMonitorService;
import cn.rhymed.task.monitor.domain.model.TaskId;
import cn.rhymed.task.monitor.domain.service.SerializationDecisionService;
import cn.rhymed.task.monitor.infrastructure.util.BizKeyExpressionParser;
import cn.rhymed.task.monitor.interfaces.annotation.TaskMonitor;
import cn.rhymed.task.monitor.interfaces.config.TaskMonitorProperties;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * 任务监控切面
 * 拦截@TaskMonitor注解的方法,实现任务监控逻辑
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
@Aspect
public class TaskMonitorAspect {

    private final TaskMonitorService taskMonitorService;
    private final TaskMonitorProperties properties;
    private final SerializationDecisionService serializationService;

    public TaskMonitorAspect(TaskMonitorService taskMonitorService,
                             TaskMonitorProperties properties,
                             SerializationDecisionService serializationService) {
        this.taskMonitorService = taskMonitorService;
        this.properties = properties;
        this.serializationService = serializationService;
    }

    @Around("@annotation(cn.rhymed.task.monitor.interfaces.annotation.TaskMonitor)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        TaskMonitor annotation = method.getAnnotation(TaskMonitor.class);

        // 提取任务信息
        String taskName = resolveTaskName(annotation.taskName(), method);
        String bizKey = extractBizKey(annotation.bizKey(), method, joinPoint.getArgs());
        String paramsJson = serializeParams(annotation.serializeParams(), joinPoint.getArgs());
        int maxRetry = resolveMaxRetry(annotation.maxRetry());

        // 开始监控
        TaskId taskId = taskMonitorService.startMonitoring(taskName, bizKey, paramsJson, maxRetry);

        // 处理心跳
        if (shouldEnableHeartbeat(annotation)) {
            int heartbeatInterval = resolveHeartbeatInterval(annotation.heartbeatIntervalSeconds());
            taskMonitorService.enableHeartbeat(taskId, heartbeatInterval);
        }

        try {
            // 执行原方法
            Object result = joinPoint.proceed();

            // 标记成功
            taskMonitorService.markSuccess(taskId);

            return result;
        } catch (Throwable throwable) {
            // 标记失败
            taskMonitorService.markFailure(taskId, throwable);

            // 重新抛出异常
            throw throwable;
        }
    }

    /**
     * 解析任务名称
     * 如果注解中未指定taskName,则使用方法名
     */
    private String resolveTaskName(String annotationTaskName, Method method) {
        if (StrUtil.isNotBlank(annotationTaskName)) {
            return annotationTaskName;
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
    private boolean shouldEnableHeartbeat(TaskMonitor annotation) {
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
