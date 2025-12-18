# Quick Start: 5分钟上手任务监控

**Feature**: 001-task-monitor-starter
**Date**: 2025-12-17
**Time**: 5分钟

---

## Step 1: 添加Maven依赖 (30秒)

```xml

<dependency>
    <groupId>com.taskmonitor</groupId>
    <artifactId>task-monitor-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Step 2: 启用任务监控 (30秒)

在Spring Boot启动类添加`@EnableTaskMonitor`注解:

```java

@SpringBootApplication
@EnableTaskMonitor  // 启用任务监控
public class Application {
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

---

## Step 3: 配置存储 (1分钟)

在`application.yml`中配置:

```yaml
task-monitor:
  enabled: true
  storage:
    type: database  # 或memory/redis

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_db
    username: root
    password: your_password
```

**创建数据库表**:

```sql
-- 执行resources/db/migration/V1__create_task_log_table.sql
CREATE TABLE task_execution_log
(
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id   VARCHAR(64)  NOT NULL UNIQUE,
    task_name VARCHAR(200) NOT NULL,
    status    VARCHAR(20)  NOT NULL,
    -- ... 其他字段见data-model.md
);
```

---

## Step 4: 使用@TaskMonitor注解 (2分钟)

### 示例1: 监控定时任务

```java

@Component
public class ScheduledTasks {

    @Scheduled(cron = "0 0 2 * * ?")
    @TaskMonitor("每日订单结算")
    public void dailySettlement() {
        // 你的业务逻辑
        processOrders();
    }
}
```

### 示例2: 监控长时间运行任务

```java

@Service
public class ReportService {

    @TaskMonitor(
            value = "生成月度报表",
            bizKey = "#reportType",  // 业务标识
            heartbeatInterval = 600   // 10分钟心跳
    )
    public void generateMonthlyReport(String reportType) {
        // 生成报表逻辑
    }
}
```

### 示例3: 大参数任务(不序列化)

```java

@Service
public class ImportService {

    @TaskMonitor(
            value = "批量导入数据",
            bizKey = "#fileName",
            serializeParams = false  // 不序列化大参数
    )
    public void batchImport(String fileName, List<Data> dataList) {
        // 导入逻辑
    }
}
```

---

## Step 5: 运行并查看效果 (1分钟)

### 启动应用

```bash
mvn spring-boot:run
```

### 执行任务

触发你的任务(手动调用或等待定时任务),然后查询数据库:

```sql
SELECT task_name, status, start_time, end_time, duration_millis
FROM task_execution_log
ORDER BY start_time DESC LIMIT 10;
```

**结果示例**:

```
| task_name      | status  | start_time          | end_time            | duration_millis |
|----------------|---------|---------------------|---------------------|-----------------|
| 每日订单结算   | SUCCESS | 2025-12-17 02:00:00 | 2025-12-17 02:05:30 | 330000          |
| 生成月度报表   | RUNNING | 2025-12-17 10:00:00 | NULL                | NULL            |
```

---

## 进阶功能

### 配置告警(钉钉)

```yaml
task-monitor:
  alert:
    enabled: true
    type: dingtalk
    dingtalk:
      webhook-url: "https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN"
```

### 配置异常重试

```yaml
task-monitor:
  recovery:
    enabled: true
    max-retry-count: 3
  exception:
    retryable:
      - "java.sql.SQLException"
      - "java.net.SocketTimeoutException"
```

### 自定义恢复处理器

```java

@TaskMonitor(
        value = "批量导入数据",
        bizKey = "#fileName",
        recoveryStrategy = RecoveryStrategy.CUSTOM
)
public void batchImport(String fileName, List<Data> dataList) {
    // 导入逻辑
}

@TaskRecoveryHandler("批量导入数据")
public void recoverImport(TaskLog taskLog) {
    String fileName = taskLog.getBizKey();
    List<Data> dataList = readFromFile(fileName);
    batchImport(fileName, dataList);
}
```

---

## 常见问题

### Q1: 任务没有被监控到?

**检查清单**:

- [ ] 是否添加了`@EnableTaskMonitor`注解?
- [ ] 是否配置了`task-monitor.enabled=true`?
- [ ] 方法是否在Spring管理的Bean中?
- [ ] 方法是否是public的?

### Q2: 心跳不更新?

**可能原因**:

- 任务执行时间太短(<心跳间隔),心跳未来得及更新
- 心跳线程池满了,调大`heartbeat.thread-pool-size`

### Q3: 任务恢复不生效?

**检查清单**:

- [ ] 参数是否可序列化?(查看`non_recoverable_reason`字段)
- [ ] 是否配置了`recovery.enabled=true`?
- [ ] 重试次数是否已达上限?

### Q4: 告警没有发送?

**检查清单**:

- [ ] 是否配置了`alert.enabled=true`?
- [ ] Webhook URL是否正确?
- [ ] 异常是否属于`ignorable`类型?

---

## 下一步

- 查看[data-model.md](./data-model.md)了解完整的领域模型
- 查看[contracts/](./contracts/)了解完整的API契约
- 查看[task-monitor-design.md](../../../task-monitor-design.md)了解详细的设计方案

**恭喜!你已经完成了任务监控的快速上手** 🎉
