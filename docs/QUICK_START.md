# Quick Start Guide

本指南将帮助你在5分钟内快速上手Task Monitor。

## 第一步：添加依赖

在你的 `pom.xml` 中添加依赖：

```xml

<dependency>
    <groupId>cn.rhymed</groupId>
    <artifactId>task-monitor-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 第二步：添加配置

在 `application.yml` 中添加最小配置：

```yaml
task:
  monitor:
    enabled: true
```

就这么简单！Task Monitor 会使用内存存储和默认配置。

## 第三步：使用注解

在你的Service方法上添加 `@TaskMonitor` 注解：

```java

@Service
public class MyService {

    @TaskMonitor(taskName = "myTask")
    public void doSomething() {
        // 你的业务逻辑
    }
}
```

## 第四步：运行测试

启动应用，调用你的方法，任务执行状态会被自动记录。

```java

@SpringBootTest
class MyServiceTest {

    @Autowired
    private MyService myService;

    @Test
    void testTask() {
        myService.doSomething();
        // 任务执行记录已自动保存
    }
}
```

## 进阶使用

### 添加业务键

```java

@TaskMonitor(
        taskName = "processOrder",
        bizKey = "#orderId"
)
public void processOrder(String orderId) {
    // 业务逻辑
}
```

### 启用重试

```java

@TaskMonitor(
        taskName = "callApi",
        maxRetry = 3
)
public void callExternalApi() {
    // API调用逻辑
}
```

### 启用心跳监控

```java

@TaskMonitor(
        taskName = "longTask",
        enableHeartbeat = true,
        heartbeatIntervalSeconds = 60
)
public void longRunningTask() {
    // 长时间运行的任务
}
```

### 自定义恢复处理器

```java

@Component
public class MyRecoveryHandler {

    @TaskRecoveryHandler(taskName = "myTask")
    public void recoverMyTask(TaskLogDTO taskLog) {
        // 自定义恢复逻辑
    }
}
```

## 切换存储后端

### 使用Redis

1. 添加Redis依赖：

```xml

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

2. 配置Redis：

```yaml
task:
  monitor:
    storage-type: redis

spring:
  redis:
    host: localhost
    port: 6379
```

### 使用数据库

1. 添加MyBatis和数据库驱动依赖：

```xml
<!-- MyBatis -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>2.3.2</version>
</dependency>

        <!-- 数据库驱动（根据实际使用的数据库选择） -->
        <!-- MySQL示例 -->
<dependency>
<groupId>com.mysql</groupId>
<artifactId>mysql-connector-j</artifactId>
</dependency>
```

2. 创建数据表（参考 `db/migration/init_task_monitor.sql`）

3. 配置数据源：

```yaml
task:
  monitor:
    storage-type: database

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/task_monitor
    username: root
    password: your-password
```

## 常见问题

### Q: 任务执行记录保存在哪里？

A: 默认保存在内存中。你可以通过配置 `task.monitor.storage-type` 切换到Redis或数据库。

### Q: 如何查看任务执行记录？

A: 可以通过注入 `TaskExecutionRepository` 查询：

```java

@Autowired
private TaskExecutionRepository repository;

public void checkTaskStatus(String taskId) {
    Optional<TaskExecution> execution = repository.findById(new TaskId(taskId));
    // 处理任务记录
}
```

### Q: 自动恢复什么时候触发？

A: 应用启动时会自动检测未完成的任务（RUNNING、INTERRUPTED、HEARTBEAT_TIMEOUT状态），并根据恢复策略尝试恢复。

### Q: 如何禁用某个功能？

A: 通过配置禁用：

```yaml
task:
  monitor:
    heartbeat:
      enabled: false  # 禁用心跳监控
    recovery:
      enabled: false  # 禁用自动恢复
```

## 下一步

- 查看完整的 [README.md](../README.md) 了解更多功能
- 参考 [examples](../examples) 目录中的示例代码
- 阅读 [架构设计文档](ARCHITECTURE.md) 了解内部实现

## 获取帮助

如果遇到问题，请查看：

- [常见问题文档](FAQ.md)
- [GitHub Issues](https://github.com/your-repo/task-monitor/issues)
