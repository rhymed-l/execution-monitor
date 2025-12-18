# Annotation API Contract

**Feature**: 001-task-monitor-starter
**Date**: 2025-12-17
**Purpose**: 定义公开注解API的契约规范

---

## @TaskMonitor 注解

**用途**: 标记需要监控的方法

**目标**: `@Target(ElementType.METHOD)`
**保留**: `@Retention(RetentionPolicy.RUNTIME)`

### 属性契约

```java

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskMonitor {

    /**
     * 任务名称(必填)
     * 用于标识任务类型,支持中文
     */
    String value();

    /**
     * 业务关键字SpEL表达式(可选)
     * 示例: "#orderId", "#order.id", "#fileName"
     * 用于在日志中标识具体的业务实例
     */
    String bizKey() default "";

    /**
     * 是否序列化参数(可选)
     * - null(默认): 自动判断(基于参数类型和大小)
     * - true: 强制序列化(如果超过大小限制仍会失败)
     * - false: 强制不序列化
     */
    Boolean serializeParams() default null;

    /**
     * 是否记录返回值(可选)
     * 默认false,如果启用会将返回值序列化存储
     * 注意: 大对象会影响性能
     */
    boolean recordResult() default false;

    /**
     * 是否启用心跳机制(可选)
     * 默认true,对于短任务(<30秒)建议禁用
     */
    boolean enableHeartbeat() default true;

    /**
     * 心跳间隔(秒)(可选)
     * 默认300秒(5分钟)
     * 建议: 任务预期时长的1/10
     */
    int heartbeatInterval() default 300;

    /**
     * 心跳超时阈值(秒)(可选)
     * 默认1800秒(30分钟)
     * 必须≥ heartbeatInterval * 2
     */
    int heartbeatTimeout() default 1800;

    /**
     * 恢复策略(可选)
     * 默认AUTO:自动判断是否可恢复
     */
    RecoveryStrategy recoveryStrategy() default RecoveryStrategy.AUTO;

    /**
     * 失败时是否自动重试(可选)
     * 默认true,会根据异常类型判断是否重试
     */
    boolean autoRetry() default true;

    /**
     * 最大重试次数(可选)
     * 0表示使用全局配置(默认3次)
     */
    int maxRetry() default 0;

    /**
     * 可重试的异常类型(可选)
     * 为空使用全局配置
     * 示例: {SQLException.class, SocketTimeoutException.class}
     */
    Class<? extends Throwable>[] retryableExceptions() default {};

    /**
     * 忽略的异常类型(可选)
     * 遇到这些异常不告警,直接标记失败
     * 示例: {IllegalArgumentException.class}
     */
    Class<? extends Throwable>[] ignorableExceptions() default {};
}
```

### 使用示例

#### 基本使用

```java

@TaskMonitor("每日订单结算")
public void dailySettlement() {
    // 无参数,自动可恢复
}
```

#### 带业务关键字

```java

@TaskMonitor(value = "生成报表", bizKey = "#reportType")
public void generateReport(String reportType, LocalDate date) {
    // bizKey用于标识具体的报表类型
}
```

#### 自定义心跳配置

```java

@TaskMonitor(
        value = "大数据处理",
        heartbeatInterval = 600,   // 10分钟
        heartbeatTimeout = 3600    // 1小时
)
public void processLargeData(String dataPath) {
    // 长时间运行任务,自定义心跳参数
}
```

#### 禁止参数序列化

```java

@TaskMonitor(
        value = "批量导入订单",
        bizKey = "#fileName",
        serializeParams = false  // 明确不序列化大参数
)
public void batchImport(String fileName, List<Order> orders) {
    // orders可能有上万条,禁止序列化
}
```

#### 自定义异常处理

```java

@TaskMonitor(
        value = "验证订单",
        retryableExceptions = {SQLException.class},
        ignorableExceptions = {IllegalArgumentException.class}
)
public void validateOrder(Order order) {
    // SQLException会重试,IllegalArgumentException不告警
}
```

### 验证规则

**编译时验证**:

- `value`不能为空字符串
- `heartbeatTimeout` ≥ `heartbeatInterval * 2` (运行时验证,违反时记录警告)

**运行时验证**:

- SpEL表达式`bizKey`解析失败时,记录警告但不中断任务
- 参数序列化失败时,标记任务为不可恢复并记录原因

---

## @TaskRecoveryHandler 注解

**用途**: 标记自定义恢复处理器方法

**目标**: `@Target(ElementType.METHOD)`
**保留**: `@Retention(RetentionPolicy.RUNTIME)`

### 属性契约

```java

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskRecoveryHandler {

    /**
     * 对应的任务名称(必填)
     * 必须与@TaskMonitor的value值完全匹配
     */
    String value();
}
```

### 方法签名要求

**标准签名**:

```java

@TaskRecoveryHandler("任务名称")
public void recoverTask(TaskLog taskLog) {
    // TaskLog包含taskId、taskName、bizKey等信息
}
```

**签名验证规则**:

- 方法必须是public
- 方法参数必须是单个`TaskLog`对象
- 方法返回值可以是void或任意类型(返回值会被忽略)
- 方法不能是static

### 使用示例

```java

@Service
public class OrderService {

    @TaskMonitor(
            value = "批量导入订单",
            bizKey = "#fileName",
            recoveryStrategy = RecoveryStrategy.CUSTOM
    )
    public void batchImport(String fileName, List<Order> orders) {
        // 批量导入逻辑
    }

    @TaskRecoveryHandler("批量导入订单")
    public void recoverBatchImport(TaskLog taskLog) {
        // 根据bizKey(fileName)重新读取文件
        String fileName = taskLog.getBizKey();
        List<Order> orders = orderFileReader.read(fileName);
        batchImport(fileName, orders);
    }
}
```

### 验证规则

**运行时验证**:

- 如果`recoveryStrategy = CUSTOM`但找不到对应的RecoveryHandler,记录ERROR日志并标记任务为不可恢复
- 如果RecoveryHandler方法执行抛异常,捕获异常并标记任务为失败

---

## @EnableTaskMonitor 注解

**用途**: 启用任务监控功能

**目标**: `@Target(ElementType.TYPE)`
**保留**: `@Retention(RetentionPolicy.RUNTIME)`

### 属性契约

```java

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(TaskMonitorAutoConfiguration.class)
public @interface EnableTaskMonitor {
    // 无属性,纯开关注解
}
```

### 使用示例

```java

@SpringBootApplication
@EnableTaskMonitor  // 启用任务监控
public class Application {
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 功能说明

添加此注解后,会自动:

1. 注册`TaskMonitorAspect` AOP切面
2. 注册`TaskMonitorAutoConfiguration`自动配置类
3. 启动心跳调度器、健康检查调度器、重试调度器
4. 启动时扫描中断任务并调度恢复

---

## RecoveryStrategy 枚举

```java
public enum RecoveryStrategy {
    /**
     * 自动判断(默认)
     * - 无参数或参数可序列化 → 自动恢复
     * - 参数无法序列化 → 仅告警,不恢复
     */
    AUTO,

    /**
     * 总是尝试恢复
     * 如果参数无法序列化,会尝试调用自定义恢复处理器
     */
    ALWAYS,

    /**
     * 从不恢复,仅告警
     */
    NEVER,

    /**
     * 使用自定义恢复处理器
     * 必须提供对应的@TaskRecoveryHandler方法
     */
    CUSTOM
}
```

---

## TaskLog DTO

**用途**: 传递给自定义恢复处理器的任务信息

```java
public class TaskLog {
    private String taskId;          // 任务唯一ID
    private String taskName;        // 任务名称
    private String bizKey;          // 业务关键字
    private String status;          // 当前状态
    private LocalDateTime startTime; // 开始时间
    private String methodSignature; // 方法签名
    private String inputParams;     // 序列化的参数JSON
    private String errorMessage;    // 错误消息(如果有)
    private int retryCount;         // 已重试次数
    private String hostIp;          // 执行主机IP

    // Getter/Setter省略
}
```

---

## API契约验证清单

### 编译时契约

- [ ] @TaskMonitor.value()不能为空
- [ ] @TaskRecoveryHandler.value()不能为空
- [ ] retryableExceptions和ignorableExceptions不能同时包含相同异常类型

### 运行时契约

- [ ] heartbeatTimeout ≥ heartbeatInterval * 2 (违反时记录WARNING)
- [ ] SpEL表达式解析失败时,记录WARNING但不中断任务
- [ ] 参数序列化失败时,标记任务为不可恢复并记录INFO
- [ ] 找不到对应的RecoveryHandler时,记录ERROR并标记任务为不可恢复
- [ ] RecoveryHandler方法签名不正确时,记录ERROR并忽略该处理器
- [ ] maxRetry=0时,使用全局配置的最大重试次数

### 行为契约

- [ ] 添加@TaskMonitor后,方法执行前创建任务记录,状态为RUNNING
- [ ] 方法成功执行后,任务状态更新为SUCCESS
- [ ] 方法抛异常后,任务状态更新为FAILED,并记录异常信息
- [ ] 心跳定时更新last_heartbeat_time字段
- [ ] 心跳超时后,任务状态更新为HEARTBEAT_TIMEOUT并发送告警
- [ ] 应用重启后,本机RUNNING状态任务标记为INTERRUPTED
- [ ] 可恢复的INTERRUPTED任务自动调度为RETRY
- [ ] RETRY任务到达next_retry_time后自动重新执行
- [ ] 重试次数达到maxRetry后,任务状态更新为FAILED并发送告警

---

## 向后兼容性

### 版本1.0.0 → 1.1.0 (MINOR)

**新增属性**(向后兼容):

- 新增注解属性需提供默认值
- 新增枚举值不影响现有值的语义

**示例**:

```java
// 1.0.0
@TaskMonitor("任务名")

// 1.1.0新增customTag属性
@TaskMonitor(value = "任务名", customTag = "默认值")  // 1.0.0代码仍可运行
```

### 版本1.0.0 → 2.0.0 (MAJOR)

**破坏性变更**(不兼容):

- 删除注解属性
- 修改注解属性名称
- 修改注解属性类型
- 修改枚举值语义

**迁移指南**:

- 提供自动迁移工具(如ArchUnit规则)
- 文档详细说明迁移步骤
- 提供过渡期的兼容性支持

---

## 测试契约

### 契约测试用例

**TC-001: 基本注解使用**

- Given: 方法标注@TaskMonitor("测试任务")
- When: 执行方法成功
- Then: 任务记录创建,状态为RUNNING→SUCCESS

**TC-002: 参数自动序列化**

- Given: 方法参数为String和int
- When: 执行方法
- Then: 参数被序列化为JSON并存储

**TC-003: 参数序列化大小限制**

- Given: 方法参数序列化后>10KB
- When: 执行方法
- Then: 参数不被存储,任务标记为不可恢复

**TC-004: SpEL解析**

- Given: bizKey = "#order.id", 参数order.id = "12345"
- When: 执行方法
- Then: 任务bizKey字段值为"12345"

**TC-005: 心跳更新**

- Given: heartbeatInterval = 5秒
- When: 方法执行10秒
- Then: 心跳至少更新2次

**TC-006: 心跳超时检测**

- Given: heartbeatTimeout = 10秒,任务卡死
- When: 超过10秒未收到心跳
- Then: 任务状态更新为HEARTBEAT_TIMEOUT

**TC-007: 任务恢复**

- Given: 任务RUNNING时应用重启
- When: 应用启动完成
- Then: 任务状态更新为INTERRUPTED→RETRY

**TC-008: 自定义恢复处理器**

- Given: recoveryStrategy = CUSTOM, 存在对应的RecoveryHandler
- When: 任务中断并恢复
- Then: 调用自定义RecoveryHandler方法

**TC-009: 异常分类**

- Given: retryableExceptions = {SQLException.class}
- When: 方法抛出SQLException
- Then: 任务状态更新为RETRY

**TC-010: 忽略异常**

- Given: ignorableExceptions = {IllegalArgumentException.class}
- When: 方法抛出IllegalArgumentException
- Then: 任务状态更新为FAILED,但不发送告警

---

## API稳定性承诺

**Stable API** (稳定,不会在MINOR版本变更):

- @TaskMonitor核心属性: value, bizKey, serializeParams, recordResult
- @TaskRecoveryHandler核心属性: value
- @EnableTaskMonitor注解

**Evolving API** (演进中,可能在MINOR版本新增属性):

- @TaskMonitor扩展属性: enableHeartbeat, heartbeatInterval等

**Experimental API** (实验性,可能在MINOR版本变更):

- (当前无实验性API)

**Phase 1: Annotation API Contract Complete** ✅
