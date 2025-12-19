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
- **多样化告警**: 内置钉钉和飞书告警，支持自定义短信、邮件、电话等多种告警方式
- **分布式锁**: 支持Redis和数据库分布式锁，防止多实例重复执行

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

### 告警通知

#### 配置钉钉告警

```yaml
execution:
  monitor:
    alert:
      dingtalk:
        enabled: true
        webhook-url: https://oapi.dingtalk.com/robot/send?access_token=xxx
        secret: SEC***  # 可选，加签密钥
```

#### 配置飞书告警

```yaml
execution:
  monitor:
    alert:
      feishu:
        enabled: true
        webhook-url: https://open.feishu.cn/open-apis/bot/v2/hook/xxx
        secret: xxx  # 可选，签名密钥
```

#### 指定告警方式

在方法上通过 `alertTypes` 参数指定使用哪些告警方式：

```java
import cn.rhymed.execution.monitor.common.enums.AlertType;

// 默认：使用所有已启用的告警服务（钉钉+飞书）
@Monitor(name = "processOrder")
public void processOrder(String orderId) {
    // 业务逻辑
}

// 仅使用飞书告警
@Monitor(
        name = "syncData",
        alertTypes = {AlertType.FEISHU}
)
public void syncData() {
    // 业务逻辑 - 失败时仅发送飞书告警
}

// 同时使用钉钉和飞书告警
@Monitor(
        name = "importantTask",
        alertTypes = {AlertType.DINGTALK, AlertType.FEISHU}
)
public void importantTask() {
    // 业务逻辑 - 失败时同时发送钉钉和飞书告警
}

// 禁用告警
@Monitor(
        name = "backgroundTask",
        alertTypes = {AlertType.NONE}
)
public void backgroundTask() {
    // 业务逻辑 - 失败时不发送任何告警
}
```

告警类型说明：

- `AlertType.DEFAULT`：使用所有已启用的告警服务（默认）
- `AlertType.DINGTALK`：仅使用钉钉告警
- `AlertType.FEISHU`：仅使用飞书告警
- `AlertType.NONE`：禁用告警

#### 未配置告警的影响

如果没有配置告警服务，任务失败时会在日志中输出 **WARN** 级别的警告：

```
⚠️  【告警】任务已达到最大重试次数，最终失败 - executionId: exec-001, executionName: processOrder,
    retryCount: 3/3, error: SocketTimeoutException: Read timed out.
    建议配置告警服务以接收实时通知！
```

#### 告警触发场景

框架提供4种告警类型，在不同场景下自动触发：

| 告警类型         | 触发时机       | 告警级别  | 是否需要处理     |
|--------------|------------|-------|------------|
| **任务失败告警**   | 所有重试都失败时   | 🔴 最高 | ✅ 需要人工介入   |
| **任务执行失败告警** | 任务失败但还能重试时 | 🟡 中等 | ⚠️  关注     |
| **心跳超时告警**   | 长任务可能挂起时   | 🟠 高  | ⚠️  检查任务状态 |
| **自定义告警**    | 业务代码主动调用时  | ⚪ 自定义 | ❓ 取决于业务    |

**示例：任务失败和重试的告警流程**

```java

@Monitor(name = "syncData", maxRetry = 3)
public void syncData(String dataId) {
    // 首次执行失败 -> 触发"任务执行失败告警"（0/3，1分钟后重试）
    // 第1次重试失败 -> 触发"任务执行失败告警"（1/3，5分钟后重试）
    // 第2次重试失败 -> 触发"任务执行失败告警"（2/3，15分钟后重试）
    // 第3次重试失败 -> 触发"任务失败告警"（3/3，最终失败，需要人工介入）
}
```

**重试策略：指数退避**

- 首次失败：1分钟后重试
- 第1次重试失败：5分钟后重试
- 第2次重试失败：15分钟后重试
- 第3次及以后：30分钟后重试

告警中会显示预计重试时间和距离重试的时间，方便用户了解任务状态。

#### 自定义告警使用

业务代码可以主动调用告警服务发送自定义告警：

```java

@Service
public class OrderService {

    @Autowired
    private AlertService alertService;

    @Monitor(name = "processOrder")
    public void processOrder(String orderId) {
        Order order = orderRepository.findById(orderId);

        // 场景1：订单金额异常大，发送告警
        if (order.getAmount() > 100000) {
            alertService.sendCustomAlert(
                    "订单金额异常",
                    String.format("订单 %s 金额高达 %.2f 元，请人工核实", orderId, order.getAmount())
            );
        }

        // 场景2：库存不足警告
        if (inventory.getStock() < order.getQuantity()) {
            alertService.sendCustomAlert(
                    "库存不足警告",
                    String.format("商品 %s 库存不足，需求：%d，库存：%d",
                            order.getProductId(), order.getQuantity(), inventory.getStock())
            );
        }
    }
}
```

**自定义告警的典型场景**：

- 业务指标异常（交易金额、数据量、耗时等）
- 关键事件通知（订单状态变更、VIP操作等）
- 资源告警（库存不足、配额耗尽等）
- 安全告警（异常登录、权限变更等）

#### 自定义告警服务

支持短信、邮件、电话、企业微信等多种告警方式。实现 `AlertService` 接口即可：

```java

@Component
public class CustomAlertService implements AlertService {

    @Override
    public void sendFailureAlert(ExecutionRecord execution) {
        // 实现任务失败告警逻辑（如发送短信、邮件等）
        String message = String.format("任务失败 - %s: %s",
                execution.getExecutionName().getValue(),
                execution.getErrorInfo() != null ? execution.getErrorInfo().getErrorMessage() : "Unknown");
        sendAlert(message);
    }

    @Override
    public void sendHeartbeatTimeoutAlert(ExecutionRecord execution) {
        // 实现心跳超时告警逻辑
    }

    @Override
    public void sendRetryAlert(ExecutionRecord execution, int retryCount) {
        // 实现重试告警逻辑
    }

    @Override
    public void sendCustomAlert(String title, String content) {
        // 实现自定义告警逻辑
    }

    private void sendAlert(String message) {
        // 调用具体的告警API（阿里云短信、邮件服务等）
    }
}
```

详细的告警配置和自定义指南请参考：[告警服务配置指南](docs/ALERT_GUIDE.md)

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

    # 告警配置
    alert:
      # 钉钉告警
      dingtalk:
        enabled: true
        webhook-url: https://oapi.dingtalk.com/robot/send?access_token=xxx
        secret: SEC***  # 可选，加签密钥
      # 飞书告警
      feishu:
        enabled: true
        webhook-url: https://open.feishu.cn/open-apis/bot/v2/hook/xxx
        secret: xxx  # 可选，签名密钥

    # 分布式锁配置（多实例部署时启用）
    distributed-lock:
      enabled: true
      timeout-seconds: 300  # 锁超时时间

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
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id       VARCHAR(64)  NOT NULL,
    execution_name     VARCHAR(128) NOT NULL,
    biz_key            VARCHAR(256),
    method_metadata_id BIGINT COMMENT '方法元信息ID',
    params_json        TEXT,
    status             VARCHAR(32)  NOT NULL,
    error_message      TEXT,
    exception_type     VARCHAR(256),
    stack_trace        TEXT,
    start_time         DATETIME,
    end_time           DATETIME,
    retry_count        INT DEFAULT 0,
    max_retry          INT DEFAULT 0,
    created_at         DATETIME,
    updated_at         DATETIME,
    INDEX              idx_execution_id (execution_id),
    INDEX              idx_status (status),
    INDEX              idx_execution_name (execution_name)
);

CREATE TABLE execution_heartbeat
(
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(64) NOT NULL UNIQUE,
    interval_seconds INT         NOT NULL,
    last_heartbeat   DATETIME    NOT NULL,
    created_at       DATETIME,
    updated_at       DATETIME,
    INDEX        idx_execution_id (execution_id),
    INDEX        idx_last_heartbeat (last_heartbeat)
);

CREATE TABLE execution_method_metadata
(
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    target_bean_name VARCHAR(256) COMMENT 'Spring Bean名称',
    target_class     VARCHAR(512)  NOT NULL COMMENT '目标类全限定名',
    method_signature VARCHAR(1024) NOT NULL COMMENT '方法签名',
    created_at       DATETIME      NOT NULL,
    UNIQUE KEY uk_class_method (target_class, method_signature)
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
- 执行状态（RUNNING, SUCCESS, FAILED, AWAITING_RETRY等）
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

## 更多文档

- [快速开始指南](docs/QUICK_START.md) - 5分钟快速上手
- [告警服务配置指南](docs/ALERT_GUIDE.md) - 配置钉钉、短信、邮件、电话等多种告警方式
- [架构设计文档](docs/ARCHITECTURE.md) - 了解内部实现细节

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
│   ├── alert（告警实现）
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
