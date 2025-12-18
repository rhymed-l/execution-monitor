package cn.rhymed.task.monitor.interfaces.annotation;

import cn.rhymed.task.monitor.interfaces.config.TaskMonitorAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用任务监控
 * 在Spring Boot应用的配置类上添加此注解以启用任务监控功能
 * <p>
 * 使用示例:
 * <pre>
 * {@code @SpringBootApplication}
 * {@code @EnableTaskMonitor}
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * </pre>
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(TaskMonitorAutoConfiguration.class)
public @interface EnableTaskMonitor {
}
