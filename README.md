# Execution Monitor Spring Boot Starter

一个基于DDD（领域驱动设计）的执行监控 Spring Boot Starter，提供方法执行监控、心跳检测、智能序列化、自动恢复、异常重试等功能。

## 特性

### 核心功能

- **执行监控**: 通过 `@Monitor` 注解自动记录方法执行状态、耗时、成功/失败信息
- **心跳检测**: 监控长时间运行方法的健康状态，自动检测超时
- **智能序列化**: 自动判断方法参数是否可序列化，避免不必要的序列化开销
- **自动恢复**: 应用启动时自动恢复中断的执行，支持多种恢复策略
- **异常重试**: 基于异常类型的智能重试机制，支持指数退避策略
- **自定义恢复处理器**: 通过 `@RecoveryHandler` 注解实现业务自定义恢复逻辑
- **灵活存储后端**: 支持内存、Redis、数据库三种存储方式

### 技术亮点

- **DDD架构**: 严格遵循领域驱动设计，清晰的4层架构（domain/application/infrastructure/interfaces）
- **值对象**: 使用不可变值对象保证领域模型完整性
- **聚合根**: ExecutionRecord和RecoveryPolicy作为聚合根管理业务规则
- **领域事件**: 7种领域事件实现跨聚合通信
- **Hutool优先**: 优先使用Hutool工具包，性能更优
- **Lombok简化**: 使用Lombok注解简化PO和DTO代码
- **最小依赖**: 使用原生MyBatis，避免重依赖；数据库依赖设置为provided

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>cn.rhymed</groupId>
    <artifactId>execution-monitor-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

在 `application.yml` 中添加配置：

```yaml
execution:
  monitor:
    enabled: true
    storage-type: memory  # 可选: memory, redis, database
```

### 3. 使用注解

```java
@Service
public class FileProcessService {

    @Monitor(
            name = "processFile",
            bizKey = "#file.path",
            serializeParams = SerializationMode.ALWAYS,
            maxRetry = 3
    )
    public void processLargeFile(File file) {
        // 文件处理逻辑
        // 自动记录执行状态、失败自动重试
    }
}
```

## 详细使用

### 基础监控

最简单的使用方式，只需添加注解：

```java

@Monitor(name = "sendEmail")
public void sendEmail(String to, String subject, String body) {
    // 邮件发送逻辑
}
```

### 业务键关联

使用 `bizKey` 参数关联业务标识，支持SpEL表达式：

```java

@Monitor(
        name = "processOrder",
        bizKey = "#orderId"  // SpEL表达式，自动获取参数值
)
public void processOrder(String orderId, OrderData data) {
    // 订单处理逻辑
}
```

### 参数序列化

对于需要恢复的执行，配置参数序列化：

```java

@Monitor(
        name = "importData",
        serializeParams = SerializationMode.ALWAYS  // 强制序列化参数以便恢复
)
public void importData(List<Record> records) {
    // 数据导入逻辑
}
```

序列化模式说明：

- `AUTO`（默认）：系统智能判断是否需要序列化
- `ALWAYS`：总是序列化参数
- `NEVER`：从不序列化参数

### 心跳监控

心跳监控默认启用，通过全局配置控制：

```yaml
execution:
  monitor:
    heartbeat:
      enabled: true  # 默认启用
      interval-seconds: 300  # 默认300秒
```

### 异常重试

配置可重试和可忽略的异常：

```yaml
execution:
  monitor:
    retry:
      max-retry: 3
      retryable-exceptions:
        - TimeoutException
        - IOException
        - SQLException
      ignorable-exceptions:
        - IllegalArgumentException
        - NullPointerException
```

```java

@Monitor(
        name = "callExternalApi",
        maxRetry = 5  // 覆盖全局配置
)
public ApiResponse callApi(String endpoint) {
    // API调用逻辑
    // TimeoutException会自动重试，最多5次
}
```

### 自定义恢复处理器

当默认重试逻辑无法满足需求时，实现自定义恢复处理器：

```java
@Component
public class FileRecoveryHandler {

    @RecoveryHandler(
            name = "processFile",
            priority = 0  // 优先级，值越小优先级越高
    )
    public void recoverFileProcessing(ExecutionLogDTO executionLog) {
        // 自定义恢复逻辑
        String filePath = executionLog.getBizKey();
        File file = new File(filePath);

        if (file.exists()) {
            // 重新处理文件
            processFile(file);
        } else {
            log.warn("文件不存在，无法恢复: {}", filePath);
        }
    }
}
```

## 配置说明

### 完整配置示例

```yaml
execution:
  monitor:
    # 是否启用执行监控
    enabled: true

    # 存储类型: memory, redis, database
    storage-type: memory

    # 心跳监控配置
    heartbeat:
      enabled: true  # 默认启用
      interval-seconds: 300  # 默认300秒
      check-interval-seconds: 30

    # 重试配置
    retry:
      max-retry: 3
      base-interval-seconds: 60
      exponential-backoff: true
      retryable-exceptions:
        - TimeoutException
        - IOException
      ignorable-exceptions:
        - IllegalArgumentException

    # 恢复配置
    recovery:
      enabled: false
      strategy: AUTO  # AUTO, ALWAYS, NEVER, CUSTOM
      check-on-startup: true

    # 序列化配置
    serialization:
      enabled: true
      max-size-bytes: 10240  # 10KB

    # 清理配置
    cleanup:
      enabled: false
      retention-days: 7
      interval-seconds: 86400
```

### 存储后端配置

#### 内存存储（默认）

```yaml
execution:
  monitor:
    storage-type: memory
```

适用于开发环境和单机应用。

#### Redis存储

```yaml
execution:
  monitor:
    storage-type: redis

spring:
  redis:
    host: localhost
    port: 6379
    password: your-password
```

适用于分布式环境，需要添加Redis依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

#### 数据库存储

```yaml
execution:
  monitor:
    storage-type: database

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/execution_monitor
    username: root
    password: your-password
```

数据库存储需要添加MyBatis和数据库驱动依赖：

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

> **注意**：
> - Execution Monitor使用原生MyBatis，保持最小依赖
> - **Starter不包含数据库驱动**，您可以自由选择MySQL、PostgreSQL、Oracle等任何数据库
> - MyBatis和数据库驱动需要在您的项目中显式添加

需要创建数据表：

```sql
CREATE TABLE execution_log
(
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id      VARCHAR(64)  NOT NULL,
    execution_name    VARCHAR(128) NOT NULL,
    biz_key           VARCHAR(256),
    params_json       TEXT,
    params_size_bytes INT,
    status            VARCHAR(32)  NOT NULL,
    error_message     TEXT,
    exception_type    VARCHAR(256),
    start_time        DATETIME,
    end_time          DATETIME,
    retry_count       INT DEFAULT 0,
    max_retry         INT DEFAULT 0,
    created_at        DATETIME,
    updated_at        DATETIME,
    INDEX             idx_execution_id (execution_id),
    INDEX             idx_status (status),
    INDEX             idx_execution_name (execution_name)
);

CREATE TABLE execution_heartbeat
(
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(64) NOT NULL UNIQUE,
    interval_seconds INT         NOT NULL,
    last_heartbeat   DATETIME    NOT NULL,
    created_at       DATETIME,
    updated_at       DATETIME,
    INDEX        idx_execution_id (execution_id)
);
```

## 恢复策略

### AUTO（自动恢复）

根据异常类型和重试次数自动决定是否恢复：

- 可重试异常且未超过最大重试次数：恢复
- 可忽略异常：不恢复
- 其他情况：不恢复

### ALWAYS（总是恢复）

总是尝试恢复执行，适用于关键业务。

### NEVER（从不恢复）

从不自动恢复，需要人工介入。

### CUSTOM（自定义恢复）

使用 `@RecoveryHandler` 注解实现自定义恢复逻辑。

## 监控指标

框架提供以下监控数据：

- 执行ID和名称
- 业务键（bizKey）
- 执行状态（RUNNING, SUCCESS, FAILED, RETRY等）
- 开始时间和结束时间
- 错误信息和异常类型
- 重试次数
- 心跳信息

## 最佳实践

### 1. 合理使用bizKey

bizKey用于关联业务数据，便于问题排查：

```java
// 好的做法
@Monitor(name = "processOrder", bizKey = "#orderId")
public void processOrder(String orderId, OrderData data) {
}

// 不好的做法（没有业务关联）
@Monitor(name = "processOrder")
public void processOrder(String orderId, OrderData data) {
}
```

### 2. 谨慎使用参数序列化

对于确实需要恢复的执行，使用 `ALWAYS` 模式：

```java
// 好的做法（关键任务需要恢复）
@Monitor(name = "importData", serializeParams = SerializationMode.ALWAYS)
public void importData(List<Record> records) {
}

// 推荐做法（使用默认的AUTO模式，系统智能判断）
@Monitor(name = "sendNotification")
public void sendNotification(String message) {
}
```

### 3. 设置合理的重试次数

根据执行特性设置重试次数：

```java
// API调用：较多重试
@Monitor(name = "callApi", maxRetry = 5)
public void callApi() {
}

// 数据库操作：较少重试
@Monitor(name = "updateDb", maxRetry = 2)
public void updateDb() {
}
```

### 4. 心跳监控

心跳监控默认启用（间隔300秒），适用于长时间运行的方法。如需关闭，配置：

```yaml
execution:
  monitor:
    heartbeat:
      enabled: false
```

## 架构设计

### 四层架构

```
├── domain（领域层）
│   ├── aggregate（聚合根）
│   ├── model（值对象）
│   ├── enums（枚举）
│   ├── repository（仓储接口）
│   ├── event（领域事件）
│   └── service（领域服务）
├── application（应用层）
│   ├── service（应用服务）
│   └── dto（数据传输对象）
├── infrastructure（基础设施层）
│   ├── persistence（持久化实现）
│   ├── aop（AOP实现）
│   ├── scheduler（调度器）
│   └── util（工具类）
└── interfaces（接口层）
    ├── annotation（注解）
    └── config（配置）
```

### 核心概念

#### 聚合根

- **ExecutionRecord**: 执行记录聚合根，封装执行生命周期
- **RecoveryPolicy**: 恢复策略聚合根，定义恢复规则

#### 值对象

- ExecutionId, ExecutionName, BizKey
- SerializedParams, ErrorInfo
- RetryConfig, ExceptionClassification

#### 领域事件

- ExecutionStartedEvent, ExecutionCompletedEvent
- ExecutionFailedEvent, ExecutionInterruptedEvent
- HeartbeatTimeoutEvent, ExecutionMarkedForRetryEvent
- ExecutionRecoveredEvent

## 性能说明

- **内存存储**: 适用于开发环境，性能最好，但数据不持久化
- **Redis存储**: 适用于生产环境，性能好，支持分布式
- **数据库存储**: 适用于需要持久化的场景，性能一般

建议：

- 开发环境使用内存存储
- 生产环境使用Redis存储
- 需要长期保存日志时使用数据库存储

## 许可证

MIT License

## 作者

execution-monitor team
