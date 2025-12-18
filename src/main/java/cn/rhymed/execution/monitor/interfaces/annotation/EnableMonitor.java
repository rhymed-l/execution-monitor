package cn.rhymed.execution.monitor.interfaces.annotation;

import cn.rhymed.execution.monitor.interfaces.config.MonitorAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用监控
 * 在Spring Boot应用的配置类上添加此注解以启用监控功能
 * <p>
 * 使用示例:
 * <pre>
 * {@code @SpringBootApplication}
 * {@code @EnableMonitor}
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
@Import(MonitorAutoConfiguration.class)
public @interface EnableMonitor {
}
