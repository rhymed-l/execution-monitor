package cn.rhymed.execution.monitor.application.service;

import cn.rhymed.execution.monitor.interfaces.annotation.RecoveryHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 恢复处理器注册表
 * 扫描并注册所有@RecoveryHandler标注的方法
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Slf4j
public class RecoveryHandlerRegistry implements ApplicationContextAware {

    private final Map<String, List<HandlerMethod>> handlers = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        scanHandlers();
    }

    /**
     * 扫描所有@RecoveryHandler标注的方法
     */
    private void scanHandlers() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> clazz = bean.getClass();

                // 扫描所有方法
                for (Method method : clazz.getDeclaredMethods()) {
                    RecoveryHandler annotation = method.getAnnotation(RecoveryHandler.class);
                    if (annotation != null) {
                        registerHandler(annotation.name(), bean, method, annotation.priority());
                    }
                }
            } catch (Exception e) {
                log.warn("扫描恢复处理器失败: {}", beanName, e);
            }
        }

        log.info("扫描到 {} 个任务的恢复处理器", handlers.size());
    }

    /**
     * 注册处理器
     */
    private void registerHandler(String name, Object bean, Method method, int priority) {
        HandlerMethod handler = new HandlerMethod(bean, method, priority);

        handlers.computeIfAbsent(name, k -> new ArrayList<>()).add(handler);

        // 按优先级排序
        handlers.get(name).sort(Comparator.comparingInt(HandlerMethod::getPriority));

        log.info("注册恢复处理器: {} -> {}.{}", name, bean.getClass().getSimpleName(), method.getName());
    }

    /**
     * 获取指定任务的处理器
     */
    public Optional<HandlerMethod> getHandler(String name) {
        List<HandlerMethod> handlerList = handlers.get(name);
        if (handlerList != null && !handlerList.isEmpty()) {
            // 返回优先级最高的
            return Optional.of(handlerList.get(0));
        }
        return Optional.empty();
    }

    /**
     * 判断是否有处理器
     */
    public boolean hasHandler(String name) {
        return handlers.containsKey(name) && !handlers.get(name).isEmpty();
    }

    /**
     * 处理器方法包装类
     */
    @Getter
    public static class HandlerMethod {
        private final Object bean;
        private final Method method;
        private final int priority;

        public HandlerMethod(Object bean, Method method, int priority) {
            this.bean = bean;
            this.method = method;
            this.priority = priority;
        }

        public Object invoke(Object... args) throws Exception {
            method.setAccessible(true);
            return method.invoke(bean, args);
        }

    }
}
