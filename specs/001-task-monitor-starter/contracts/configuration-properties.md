# Configuration Properties Contract

**Feature**: 001-task-monitor-starter
**Date**: 2025-12-17
**Purpose**: 定义YAML/properties配置属性的契约规范

---

## 配置前缀

**Prefix**: `task-monitor`

**完整示例配置**:

```yaml
task-monitor:
  enabled: true
  storage:
    type: database
  heartbeat:
    enabled: true
    default-interval: 300
    default-timeout: 1800
    thread-pool-size: 10
    check-interval: 60
  params:
    auto-serialize: true
    max-size-bytes: 10240
    serialize-basic-types: true
  recovery:
    enabled: true
    scan-on-startup: true
    max-retry-count: 3
    retry-interval: 60
    exponential-backoff: true
  exception:
    retryable:
      - "java.sql.SQLException"
      - "java.net.SocketTimeoutException"
      - "org.springframework.dao.DataAccessException"
    ignorable:
      - "java.lang.IllegalArgumentException"
  alert:
    enabled: true
    type: dingtalk
    dingtalk:
      webhook-url: "https://oapi.dingtalk.com/robot/send?access_token=xxx"
      secret: "SECxxx"
```

---

## 配置属性定义

### 根配置

#### `task-monitor.enabled`

**类型**: `boolean`
**默认值**: `true`
**描述**: 全局开关,是否启用任务监控功能

**行为**:

- `true`: 启用任务监控,AOP切面生效
- `false`: 禁用任务监控,所有@TaskMonitor注解不生效

**验证规则**: 无

---

### 存储配置 (`task-monitor.storage`)

#### `task-monitor.storage.type`

**类型**: `String` (枚举: `memory` | `redis` | `database`)
**默认值**: `database`
**描述**: 存储后端类型

**行为**:

- `memory`: 使用内存存储(ConcurrentHashMap),重启丢失数据
- `redis`: 使用Redis存储,需要配置`spring.redis.*`
- `database`: 使用数据库存储,需要配置DataSource

**验证规则**:

- 必须是三个枚举值之一
- 如果选择`redis`但未配置Redis连接,启动时抛异常
- 如果选择`database`但未配置DataSource,启动时抛异常

**配置示例**:

```yaml
task-monitor:
  storage:
    type: database  # 默认数据库存储

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/task_monitor
    username: root
    password: password
```

---

### 心跳配置 (`task-monitor.heartbeat`)

#### `task-monitor.heartbeat.enabled`

**类型**: `boolean`
**默认值**: `true`
**描述**: 全局心跳开关

**行为**:

- `true`: 启用心跳机制
- `false`: 禁用所有心跳(即使注解指定enableHeartbeat=true)

#### `task-monitor.heartbeat.default-interval`

**类型**: `int` (单位:秒)
**默认值**: `300` (5分钟)
**范围**: `[10, 3600]`
**描述**: 默认心跳间隔

**行为**:

- 注解未指定heartbeatInterval时使用此值
- 心跳线程按此间隔更新last_heartbeat_time

**验证规则**:

- 必须≥10秒
- 必须≤3600秒(1小时)
- 违反时启动失败,抛`IllegalArgumentException`

#### `task-monitor.heartbeat.default-timeout`

**类型**: `int` (单位:秒)
**默认值**: `1800` (30分钟)
**范围**: `[default-interval * 2, 86400]`
**描述**: 默认心跳超时阈值

**行为**:

- 注解未指定heartbeatTimeout时使用此值
- 健康检查判断任务是否心跳超时

**验证规则**:

- 必须≥ default-interval * 2
- 必须≤86400秒(24小时)
- 违反时启动失败,抛`IllegalArgumentException`

#### `task-monitor.heartbeat.thread-pool-size`

**类型**: `int`
**默认值**: `max(10, CPU核心数)`
**范围**: `[1, 100]`
**描述**: 心跳线程池核心线程数

**行为**:

- 线程池用于发送心跳更新
- 线程数=max(配置值, CPU核心数)

**验证规则**:

- 必须≥1
- 必须≤100
- 违反时使用默认值并记录WARNING

#### `task-monitor.heartbeat.check-interval`

**类型**: `int` (单位:秒)
**默认值**: `60` (1分钟)
**范围**: `[10, 600]`
**描述**: 健康检查扫描间隔

**行为**:

- 定时扫描所有RUNNING任务,检查心跳超时
- 扫描间隔越短,超时检测越及时,但CPU开销越大

**验证规则**:

- 必须≥10秒
- 必须≤600秒(10分钟)
- 建议: default-interval / 5

---

### 参数序列化配置 (`task-monitor.params`)

#### `task-monitor.params.auto-serialize`

**类型**: `boolean`
**默认值**: `true`
**描述**: 是否启用自动参数序列化判断

**行为**:

- `true`: 自动判断参数是否应该序列化(基于类型和大小)
- `false`: 仅当注解明确指定serializeParams=true时才序列化

#### `task-monitor.params.max-size-bytes`

**类型**: `int` (单位:字节)
**默认值**: `10240` (10KB)
**范围**: `[1024, 1048576]` (1KB ~ 1MB)
**描述**: 参数序列化大小上限

**行为**:

- 序列化后的JSON字节数超过此值,则不存储
- 任务标记为不可恢复

**验证规则**:

- 必须≥1024字节(1KB)
- 必须≤1048576字节(1MB)
- 违反时使用默认值并记录WARNING

#### `task-monitor.params.serialize-basic-types`

**类型**: `boolean`
**默认值**: `true`
**描述**: 基本类型是否总是序列化

**行为**:

- `true`: 基本类型(String, Number, Date等)无论如何都序列化
- `false`: 基本类型也遵循大小限制

**基本类型定义**:

- `String`
- `Number`及其子类(`Integer`, `Long`, `Double`等)
- `Boolean`, `Character`
- `LocalDate`, `LocalDateTime`, `Date`
- 枚举类型

---

### 恢复配置 (`task-monitor.recovery`)

#### `task-monitor.recovery.enabled`

**类型**: `boolean`
**默认值**: `true`
**描述**: 是否启用任务恢复功能

**行为**:

- `true`: 启动时扫描中断任务并恢复
- `false`: 不恢复中断任务(仅记录日志)

#### `task-monitor.recovery.scan-on-startup`

**类型**: `boolean`
**默认值**: `true`
**描述**: 应用启动时是否扫描中断任务

**行为**:

- `true`: ApplicationRunner阶段扫描RUNNING任务
- `false`: 不在启动时扫描(但异常中断后仍会重试)

#### `task-monitor.recovery.max-retry-count`

**类型**: `int`
**默认值**: `3`
**范围**: `[0, 10]`
**描述**: 最大重试次数

**行为**:

- 注解未指定maxRetry时使用此值
- 重试次数达到上限后,任务标记为FAILED并发送告警

**验证规则**:

- 必须≥0 (0表示不重试)
- 必须≤10
- 违反时使用默认值并记录WARNING

#### `task-monitor.recovery.retry-interval`

**类型**: `int` (单位:秒)
**默认值**: `60` (1分钟)
**范围**: `[10, 3600]`
**描述**: 重试基础间隔

**行为**:

- 第1次重试间隔=retry-interval
- 如果启用指数退避,第N次间隔=retry-interval * 2^(N-1)

**验证规则**:

- 必须≥10秒
- 必须≤3600秒(1小时)

#### `task-monitor.recovery.exponential-backoff`

**类型**: `boolean`
**默认值**: `true`
**描述**: 是否启用指数退避

**行为**:

- `true`: 重试间隔指数增长(60s, 120s, 240s, ...)
- `false`: 重试间隔固定(60s, 60s, 60s, ...)

**指数退避公式**:

```
nextRetryTime = now + retry-interval * 2^(retryCount)
```

**示例**:

- retry-interval=60, retryCount=0: 60秒后重试
- retry-interval=60, retryCount=1: 120秒后重试
- retry-interval=60, retryCount=2: 240秒后重试

---

### 异常配置 (`task-monitor.exception`)

#### `task-monitor.exception.retryable`

**类型**: `List<String>`
**默认值**:

```yaml
- "java.sql.SQLException"
- "java.net.SocketTimeoutException"
- "org.springframework.dao.DataAccessException"
```

**描述**: 全局可重试异常类型列表

**行为**:

- 任务抛出这些异常时,自动重试(在重试次数限制内)
- 注解未指定retryableExceptions时使用此配置

**验证规则**:

- 必须是完整的类名(包含包路径)
- 类必须是`Throwable`的子类
- 如果类不存在,记录WARNING但不影响启动

**匹配规则**:

- 支持父类匹配:抛出`SQLException`会匹配`java.sql.SQLException`
- 支持子类匹配:抛出`MySQLTimeoutException`会匹配`java.sql.SQLException`

#### `task-monitor.exception.ignorable`

**类型**: `List<String>`
**默认值**:

```yaml
- "java.lang.IllegalArgumentException"
```

**描述**: 全局可忽略异常类型列表

**行为**:

- 任务抛出这些异常时,标记为FAILED但不发送告警
- 注解未指定ignorableExceptions时使用此配置

**验证规则**:

- 与retryable相同
- retryable和ignorable不能包含相同的异常(违反时,ignorable优先级更高)

---

### 告警配置 (`task-monitor.alert`)

#### `task-monitor.alert.enabled`

**类型**: `boolean`
**默认值**: `true`
**描述**: 是否启用告警功能

**行为**:

- `true`: 发送告警通知
- `false`: 不发送告警(仅记录日志)

#### `task-monitor.alert.type`

**类型**: `String` (枚举: `dingtalk` | `email` | `wechat`)
**默认值**: `dingtalk`
**描述**: 告警渠道类型

**行为**:

- `dingtalk`: 使用钉钉Webhook
- `email`: 使用邮件(需配置SMTP)
- `wechat`: 使用企业微信(需配置企业微信API)

#### 钉钉配置 (`task-monitor.alert.dingtalk`)

##### `task-monitor.alert.dingtalk.webhook-url`

**类型**: `String`
**默认值**: (无,必填)
**描述**: 钉钉机器人Webhook URL

**验证规则**:

- 必须是有效的URL
- 必须以`https://oapi.dingtalk.com/robot/send`开头
- 未配置时,告警功能不可用(记录ERROR)

##### `task-monitor.alert.dingtalk.secret`

**类型**: `String`
**默认值**: (无,可选)
**描述**: 钉钉机器人加签密钥

**行为**:

- 如果配置,使用HMAC-SHA256签名
- 如果不配置,直接发送(不安全,仅测试环境)

**配置示例**:

```yaml
task-monitor:
  alert:
    enabled: true
    type: dingtalk
    dingtalk:
      webhook-url: "https://oapi.dingtalk.com/robot/send?access_token=xxx"
      secret: "SECxxx"
```

---

## 配置优先级

1. **注解配置** (最高优先级)
    - @TaskMonitor注解的属性值
    - 示例: `heartbeatInterval = 600`

2. **全局配置** (次优先级)
    - `task-monitor.*`配置属性
    - 示例: `task-monitor.heartbeat.default-interval = 300`

3. **默认值** (最低优先级)
    - 代码中定义的默认值

**优先级示例**:

```java
// 配置文件
task-monitor.heartbeat .default-interval =300

// 注解
@TaskMonitor(
        value = "测试任务",
        heartbeatInterval = 600  // 优先使用此值
)
```

---

## 配置验证

### 启动时验证

**必须通过的验证**:

- [ ] heartbeat.default-timeout ≥ heartbeat.default-interval * 2
- [ ] params.max-size-bytes在[1KB, 1MB]范围内
- [ ] recovery.max-retry-count在[0, 10]范围内
- [ ] storage.type是有效枚举值
- [ ] alert.type是有效枚举值

**违反时行为**:

- 抛出`IllegalStateException`,启动失败
- 错误日志包含具体的配置键和违反的规则

### 运行时验证

**动态配置更新** (如果支持):

- 配置更新后重新验证
- 验证失败时,保留旧配置并记录ERROR

**配置一致性检查**:

- 每小时检查一次配置一致性
- 发现异常配置记录WARNING

---

## 配置模板

### 开发环境配置

```yaml
task-monitor:
  enabled: true
  storage:
    type: memory  # 开发环境使用内存存储
  heartbeat:
    default-interval: 60   # 1分钟
    default-timeout: 180   # 3分钟
  params:
    max-size-bytes: 10240  # 10KB
  recovery:
    enabled: false  # 开发环境禁用自动恢复
  alert:
    enabled: false  # 开发环境禁用告警
```

### 测试环境配置

```yaml
task-monitor:
  enabled: true
  storage:
    type: database
  heartbeat:
    default-interval: 120  # 2分钟
    default-timeout: 360   # 6分钟
  recovery:
    enabled: true
    max-retry-count: 1  # 测试环境最多重试1次
  alert:
    enabled: true
    type: dingtalk
    dingtalk:
      webhook-url: "${DINGTALK_WEBHOOK_URL}"
```

### 生产环境配置

```yaml
task-monitor:
  enabled: true
  storage:
    type: database
  heartbeat:
    enabled: true
    default-interval: 300   # 5分钟
    default-timeout: 1800   # 30分钟
    thread-pool-size: 20    # 生产环境加大线程池
    check-interval: 60      # 1分钟
  params:
    auto-serialize: true
    max-size-bytes: 10240
  recovery:
    enabled: true
    scan-on-startup: true
    max-retry-count: 3
    retry-interval: 60
    exponential-backoff: true
  exception:
    retryable:
      - "java.sql.SQLException"
      - "java.net.SocketTimeoutException"
      - "org.springframework.dao.DataAccessException"
      - "com.mysql.cj.jdbc.exceptions.CommunicationsException"
    ignorable:
      - "java.lang.IllegalArgumentException"
      - "javax.validation.ValidationException"
  alert:
    enabled: true
    type: dingtalk
    dingtalk:
      webhook-url: "${DINGTALK_WEBHOOK_URL}"
      secret: "${DINGTALK_SECRET}"
```

---

## 配置迁移

### v1.0.0 → v1.1.0

**新增配置**:

- `task-monitor.heartbeat.check-interval` (默认60秒)

**迁移步骤**:

1. 无需任何操作,新配置有默认值

### v1.x → v2.0.0 (假设)

**配置重命名**:

- `task-monitor.storage.type` → `task-monitor.storage.backend`

**迁移步骤**:

1. 更新配置文件中的键名
2. 运行迁移工具:`java -jar task-monitor-migrator.jar --from 1.x --to 2.0`

---

## 配置Schema (JSON Schema)

提供给IDE自动补全的JSON Schema:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "task-monitor": {
      "type": "object",
      "properties": {
        "enabled": {
          "type": "boolean",
          "default": true,
          "description": "全局开关"
        },
        "storage": {
          "type": "object",
          "properties": {
            "type": {
              "type": "string",
              "enum": [
                "memory",
                "redis",
                "database"
              ],
              "default": "database"
            }
          }
        },
        "heartbeat": {
          "type": "object",
          "properties": {
            "enabled": {
              "type": "boolean",
              "default": true
            },
            "default-interval": {
              "type": "integer",
              "minimum": 10,
              "maximum": 3600,
              "default": 300
            },
            "default-timeout": {
              "type": "integer",
              "minimum": 20,
              "maximum": 86400,
              "default": 1800
            }
          }
        }
      }
    }
  }
}
```

**使用方式**:
在`spring-configuration-metadata.json`中引用此Schema,IDEA等IDE会提供自动补全和验证。

---

## 配置最佳实践

1. **生产环境使用数据库存储**: 确保任务历史持久化
2. **根据任务时长调整心跳间隔**: 短任务(<5分钟)禁用心跳,长任务调大间隔
3. **配置合理的重试次数**: 避免无限重试导致资源浪费
4. **使用环境变量管理敏感配置**: 如webhook-url, secret
5. **开发环境禁用告警**: 避免打扰
6. **定期审查exception配置**: 确保异常分类准确

**Phase 1: Configuration Properties Contract Complete** ✅
