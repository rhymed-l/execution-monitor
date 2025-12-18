# Data Model: Spring Boot Task Monitor Starter

**Feature**: 001-task-monitor-starter
**Date**: 2025-12-17
**Purpose**: 定义领域模型(聚合、实体、值对象)和数据库Schema

---

## DDD领域模型

### 聚合1: 任务执行聚合 (TaskExecution Aggregate)

**聚合根**: `TaskExecution`

**职责**: 代表一次任务的完整执行过程,管理任务生命周期、状态转换、心跳记录

**不变性约束**:

1. 任务ID(TaskId)一旦创建不可变
2. 状态转换必须合法:RUNNING→{SUCCESS|FAILED|INTERRUPTED|HEARTBEAT_TIMEOUT}, INTERRUPTED→RETRY, RETRY→RUNNING
3. 心跳时间戳必须单调递增
4. 重试次数不能超过maxRetry配置
5. 任务开始时间不能晚于结束时间

**属性**:

```java
public class TaskExecution {
    // 标识
    private TaskId taskId;              // 唯一任务ID(值对象)
    private TaskName taskName;          // 任务名称(值对象)
    private BizKey bizKey;              // 业务关键字(值对象,可选)

    // 执行信息
    private TaskStatus status;          // 当前状态(枚举)
    private LocalDateTime startTime;    // 开始时间
    private LocalDateTime endTime;      // 结束时间(可选)
    private Long durationMillis;        // 执行时长(毫秒)

    // 方法信息
    private String methodSignature;     // 方法签名(用于反射调用)
    private SerializedParams inputParams; // 序列化的入参(值对象)
    private String executionResult;     // 执行结果(可选)

    // 错误信息
    private ErrorInfo errorInfo;        // 错误详情(值对象,可选)

    // 恢复信息
    private boolean recoverable;        // 是否可恢复
    private String nonRecoverableReason; // 不可恢复原因

    // 重试信息
    private int retryCount;             // 已重试次数
    private int maxRetry;               // 最大重试次数
    private LocalDateTime nextRetryTime; // 下次重试时间(可选)

    // 心跳信息(组合实体)
    private HeartbeatRecord heartbeatRecord; // 心跳记录实体

    // 环境信息
    private String hostName;            // 主机名
    private String hostIp;              // 主机IP
    private String threadName;          // 线程名

    // 告警信息
    private boolean alertSent;          // 是否已发送告警
    private LocalDateTime alertTime;    // 告警时间(可选)

    // 审计信息
    private LocalDateTime createdTime;  // 创建时间
    private LocalDateTime updatedTime;  // 更新时间
}
```

**领域方法**:

```java
// 状态转换
public void markAsRunning()

public void markAsSuccess(String result)

public void markAsFailed(ErrorInfo errorInfo)

public void markAsInterrupted()

public void markAsHeartbeatTimeout()

public void scheduleRetry(LocalDateTime nextRetryTime)

// 心跳管理
public void updateHeartbeat()

public boolean isHeartbeatTimeout(int timeoutSeconds)

// 恢复判断
public boolean canRecover()

public boolean canRetry()

// 业务查询
public boolean isCompleted()

public boolean isRunning()

public long getRunningDurationSeconds()
```

---

### 实体: `HeartbeatRecord`

**职责**: 记录任务的心跳信息,检测任务是否存活

**属性**:

```java
public class HeartbeatRecord {
    private LocalDateTime lastHeartbeatTime;  // 最后心跳时间
    private int heartbeatInterval;            // 心跳间隔(秒)
    private int heartbeatTimeout;             // 超时阈值(秒)
    private int heartbeatCount;               // 心跳次数

    // 领域方法
    public void beat() {
        this.lastHeartbeatTime = LocalDateTime.now();
        this.heartbeatCount++;
    }

    public boolean isTimeout() {
        LocalDateTime timeoutThreshold = LocalDateTime.now()
                .minusSeconds(heartbeatTimeout);
        return lastHeartbeatTime.isBefore(timeoutThreshold);
    }

    public long getSecondsSinceLastBeat() {
        return ChronoUnit.SECONDS.between(lastHeartbeatTime, LocalDateTime.now());
    }
}
```

---

### 值对象

#### `TaskId`

```java
public class TaskId {
    private final String value;  // UUID格式

    public TaskId(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("TaskId不能为空");
        }
        this.value = value;
    }

    public static TaskId generate() {
        return new TaskId(IdUtil.simpleUUID());
    }

    @Override
    public boolean equals(Object o) { /* value相等即相等 */ }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
```

#### `TaskName`

```java
public class TaskName {
    private final String value;

    public TaskName(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("TaskName不能为空");
        }
        if (value.length() > 200) {
            throw new IllegalArgumentException("TaskName长度不能超过200字符");
        }
        this.value = value;
    }
}
```

#### `BizKey`

```java
public class BizKey {
    private final String value;  // 可选的业务标识,如订单号、文件名

    public BizKey(String value) {
        if (value != null && value.length() > 200) {
            throw new IllegalArgumentException("BizKey长度不能超过200字符");
        }
        this.value = value;
    }

    public static BizKey empty() {
        return new BizKey(null);
    }

    public boolean isPresent() {
        return StrUtil.isNotBlank(value);
    }
}
```

#### `SerializedParams`

```java
public class SerializedParams {
    private final String jsonData;     // JSON格式的参数
    private final int sizeBytes;       // 字节大小

    public SerializedParams(String jsonData) {
        this.jsonData = jsonData;
        this.sizeBytes = StrUtil.isNotBlank(jsonData)
                ? jsonData.getBytes(StandardCharsets.UTF_8).length
                : 0;
    }

    public static SerializedParams empty() {
        return new SerializedParams(null);
    }

    public boolean isEmpty() {
        return StrUtil.isBlank(jsonData);
    }

    public boolean exceedsLimit(int maxSizeBytes) {
        return sizeBytes > maxSizeBytes;
    }

    public Object[] deserialize() {
        if (isEmpty()) {
            return new Object[0];
        }
        return JSONUtil.toBean(jsonData, Object[].class);
    }
}
```

#### `ErrorInfo`

```java
public class ErrorInfo {
    private final String errorMessage;      // 错误消息
    private final String exceptionType;     // 异常类型
    private final String stackTrace;        // 堆栈信息(可选)

    public ErrorInfo(Throwable throwable) {
        this.errorMessage = throwable.getMessage();
        this.exceptionType = throwable.getClass().getName();
        this.stackTrace = getStackTraceAsString(throwable);
    }

    private String getStackTraceAsString(Throwable t) {
        // 使用Hutool的ExceptionUtil
        return ExceptionUtil.stacktraceToString(t, 2000); // 限制2000字符
    }

    public boolean isRetryable(List<String> retryableExceptions) {
        return retryableExceptions.stream()
                .anyMatch(retryableType -> exceptionType.contains(retryableType));
    }

    public boolean isIgnorable(List<String> ignorableExceptions) {
        return ignorableExceptions.stream()
                .anyMatch(ignorableType -> exceptionType.contains(ignorableType));
    }
}
```

---

### 聚合2: 恢复策略聚合 (RecoveryPolicy Aggregate)

**聚合根**: `RecoveryPolicy`

**职责**: 定义任务中断后的恢复策略,判断是否可恢复、如何重试

**属性**:

```java
public class RecoveryPolicy {
    private RecoveryStrategy strategy;        // 恢复策略枚举
    private RetryConfig retryConfig;          // 重试配置(值对象)
    private ExceptionClassification exceptionClassification; // 异常分类(值对象)

    // 领域方法
    public boolean shouldRecover(TaskExecution taskExecution) {
        switch (strategy) {
            case AUTO:
                return taskExecution.canRecover();
            case ALWAYS:
                return true;
            case NEVER:
                return false;
            case CUSTOM:
                return taskExecution.getBizKey().isPresent(); // 自定义恢复需要业务键
            default:
                return false;
        }
    }

    public boolean shouldRetry(TaskExecution taskExecution, ErrorInfo errorInfo) {
        if (taskExecution.getRetryCount() >= retryConfig.getMaxRetry()) {
            return false;
        }
        return exceptionClassification.isRetryable(errorInfo);
    }

    public LocalDateTime calculateNextRetryTime(int currentRetryCount) {
        return retryConfig.calculateNextRetryTime(currentRetryCount);
    }
}
```

---

### 值对象

#### `RetryConfig`

```java
public class RetryConfig {
    private final int maxRetry;              // 最大重试次数
    private final int baseIntervalSeconds;   // 基础间隔(秒)
    private final boolean exponentialBackoff; // 是否指数退避

    public RetryConfig(int maxRetry, int baseIntervalSeconds, boolean exponentialBackoff) {
        if (maxRetry < 0) {
            throw new IllegalArgumentException("maxRetry不能为负数");
        }
        if (baseIntervalSeconds < 1) {
            throw new IllegalArgumentException("baseIntervalSeconds必须≥1");
        }
        this.maxRetry = maxRetry;
        this.baseIntervalSeconds = baseIntervalSeconds;
        this.exponentialBackoff = exponentialBackoff;
    }

    public LocalDateTime calculateNextRetryTime(int currentRetryCount) {
        int intervalSeconds = exponentialBackoff
                ? baseIntervalSeconds * (int) Math.pow(2, currentRetryCount)
                : baseIntervalSeconds;
        return LocalDateTime.now().plusSeconds(intervalSeconds);
    }
}
```

#### `ExceptionClassification`

```java
public class ExceptionClassification {
    private final List<String> retryableExceptions;   // 可重试异常类型
    private final List<String> ignorableExceptions;   // 可忽略异常类型

    public ExceptionClassification(List<String> retryableExceptions, List<String> ignorableExceptions) {
        this.retryableExceptions = retryableExceptions != null ? retryableExceptions : new ArrayList<>();
        this.ignorableExceptions = ignorableExceptions != null ? ignorableExceptions : new ArrayList<>();
    }

    public boolean isRetryable(ErrorInfo errorInfo) {
        return errorInfo.isRetryable(retryableExceptions);
    }

    public boolean isIgnorable(ErrorInfo errorInfo) {
        return errorInfo.isIgnorable(ignorableExceptions);
    }
}
```

---

### 枚举类型

#### `TaskStatus`

```java
public enum TaskStatus {
    RUNNING("执行中"),
    SUCCESS("成功"),
    FAILED("失败"),
    INTERRUPTED("中断"),
    HEARTBEAT_TIMEOUT("心跳超时"),
    RETRY("待重试");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    public boolean isActive() {
        return this == RUNNING;
    }

    public boolean canTransitionTo(TaskStatus newStatus) {
        // 状态转换规则
        switch (this) {
            case RUNNING:
                return newStatus == SUCCESS || newStatus == FAILED
                        || newStatus == INTERRUPTED || newStatus == HEARTBEAT_TIMEOUT;
            case INTERRUPTED:
            case HEARTBEAT_TIMEOUT:
                return newStatus == RETRY || newStatus == FAILED;
            case RETRY:
                return newStatus == RUNNING || newStatus == FAILED;
            case FAILED:
            case SUCCESS:
                return false; // 终态不可转换
            default:
                return false;
        }
    }
}
```

#### `RecoveryStrategy`

```java
public enum RecoveryStrategy {
    AUTO("自动判断"),
    ALWAYS("总是恢复"),
    NEVER("从不恢复"),
    CUSTOM("自定义处理器");

    private final String description;

    RecoveryStrategy(String description) {
        this.description = description;
    }
}
```

---

## 领域服务

### `TaskExecutionDomainService`

**职责**: 协调任务执行聚合的复杂业务逻辑

```java
public class TaskExecutionDomainService {

    /**
     * 开始任务执行
     */
    public TaskExecution startTask(TaskName taskName, BizKey bizKey,
                                   SerializedParams params, String methodSignature,
                                   HeartbeatConfig heartbeatConfig) {
        TaskExecution task = new TaskExecution();
        task.setTaskId(TaskId.generate());
        task.setTaskName(taskName);
        task.setBizKey(bizKey);
        task.setInputParams(params);
        task.setMethodSignature(methodSignature);
        task.setRecoverable(!params.isEmpty());
        task.markAsRunning();

        // 初始化心跳
        HeartbeatRecord heartbeat = new HeartbeatRecord();
        heartbeat.setHeartbeatInterval(heartbeatConfig.getInterval());
        heartbeat.setHeartbeatTimeout(heartbeatConfig.getTimeout());
        heartbeat.beat();
        task.setHeartbeatRecord(heartbeat);

        return task;
    }

    /**
     * 完成任务
     */
    public void completeTask(TaskExecution task, String result) {
        task.markAsSuccess(result);
        task.setDurationMillis(calculateDuration(task));
    }

    /**
     * 任务失败
     */
    public void failTask(TaskExecution task, Throwable throwable) {
        ErrorInfo errorInfo = new ErrorInfo(throwable);
        task.markAsFailed(errorInfo);
        task.setDurationMillis(calculateDuration(task));
    }

    private long calculateDuration(TaskExecution task) {
        if (task.getEndTime() == null) {
            return 0;
        }
        return ChronoUnit.MILLIS.between(task.getStartTime(), task.getEndTime());
    }
}
```

### `SerializationDecisionService`

**职责**: 决策参数是否应该序列化

```java
public class SerializationDecisionService {

    public SerializationDecision decide(Object[] args, Boolean userConfig, int maxSizeBytes) {
        // 1. 用户明确配置
        if (userConfig != null) {
            if (userConfig) {
                return trySerialize(args, maxSizeBytes);
            } else {
                return SerializationDecision.no("用户配置禁止序列化");
            }
        }

        // 2. 无参数
        if (args == null || args.length == 0) {
            return SerializationDecision.yes("", 0);
        }

        // 3. 尝试序列化
        return trySerialize(args, maxSizeBytes);
    }

    private SerializationDecision trySerialize(Object[] args, int maxSizeBytes) {
        try {
            String json = JSONUtil.toJsonStr(args);
            int sizeBytes = json.getBytes(StandardCharsets.UTF_8).length;

            if (sizeBytes > maxSizeBytes) {
                return SerializationDecision.no(
                        StrUtil.format("参数大小{}字节超过限制{}字节", sizeBytes, maxSizeBytes)
                );
            }

            return SerializationDecision.yes(json, sizeBytes);

        } catch (Exception e) {
            return SerializationDecision.no("参数序列化失败: " + e.getMessage());
        }
    }
}

/**
 * 序列化决策结果
 */
public class SerializationDecision {
    private final boolean shouldSerialize;
    private final String serializedData;
    private final int sizeBytes;
    private final String reason;

    public static SerializationDecision yes(String data, int size) {
        return new SerializationDecision(true, data, size, null);
    }

    public static SerializationDecision no(String reason) {
        return new SerializationDecision(false, null, 0, reason);
    }
}
```

### `RecoveryDecisionService`

**职责**: 决策任务是否应该恢复

```java
public class RecoveryDecisionService {

    public boolean shouldRecover(TaskExecution task, RecoveryPolicy policy) {
        // 1. 检查策略
        if (!policy.shouldRecover(task)) {
            return false;
        }

        // 2. 检查是否可恢复
        if (!task.canRecover()) {
            return false;
        }

        // 3. 检查重试次数
        return task.canRetry();
    }

    public LocalDateTime scheduleRetry(TaskExecution task, RecoveryPolicy policy) {
        return policy.calculateNextRetryTime(task.getRetryCount());
    }
}
```

---

## 仓储接口

### `TaskExecutionRepository`

```java
public interface TaskExecutionRepository {

    /**
     * 保存任务执行记录
     */
    void save(TaskExecution taskExecution);

    /**
     * 根据TaskId查询
     */
    Optional<TaskExecution> findByTaskId(TaskId taskId);

    /**
     * 查询指定状态的任务
     */
    List<TaskExecution> findByStatus(TaskStatus status);

    /**
     * 查询指定主机上指定状态的任务
     */
    List<TaskExecution> findByStatusAndHost(TaskStatus status, String hostIp);

    /**
     * 查询需要重试的任务(status=RETRY且nextRetryTime<=now)
     */
    List<TaskExecution> findRetryableTasks();

    /**
     * 查询心跳超时的任务
     */
    List<TaskExecution> findHeartbeatTimeoutTasks(int timeoutSeconds);

    /**
     * 更新任务
     */
    void update(TaskExecution taskExecution);
}
```

### `HeartbeatStorage`

```java
public interface HeartbeatStorage {

    /**
     * 初始化心跳
     */
    void initHeartbeat(TaskId taskId, HeartbeatRecord heartbeatRecord);

    /**
     * 更新心跳时间
     */
    void updateHeartbeat(TaskId taskId, LocalDateTime heartbeatTime);

    /**
     * 获取心跳记录
     */
    Optional<HeartbeatRecord> getHeartbeat(TaskId taskId);

    /**
     * 删除心跳记录(任务结束时)
     */
    void removeHeartbeat(TaskId taskId);

    /**
     * 扫描所有运行中的任务ID
     */
    Set<TaskId> scanRunningTaskIds();

    /**
     * 扫描心跳超时的任务ID
     */
    Set<TaskId> scanTimeoutTaskIds(int timeoutSeconds);
}
```

---

## 数据库Schema

### `task_execution_log` 表

```sql
CREATE TABLE task_execution_log
(
    -- 主键
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',

    -- 标识
    task_id                VARCHAR(64)  NOT NULL UNIQUE COMMENT '任务唯一标识(UUID)',
    task_name              VARCHAR(200) NOT NULL COMMENT '任务名称',
    biz_key                VARCHAR(200) DEFAULT NULL COMMENT '业务关键字',

    -- 执行信息
    status                 VARCHAR(20)  NOT NULL COMMENT '状态:RUNNING/SUCCESS/FAILED/INTERRUPTED/HEARTBEAT_TIMEOUT/RETRY',
    start_time             DATETIME     NOT NULL COMMENT '开始时间',
    end_time               DATETIME     DEFAULT NULL COMMENT '结束时间',
    duration_millis        BIGINT       DEFAULT NULL COMMENT '执行时长(毫秒)',

    -- 心跳信息
    last_heartbeat_time    DATETIME     NOT NULL COMMENT '最后心跳时间',
    heartbeat_interval     INT          DEFAULT 300 COMMENT '心跳间隔(秒)',
    heartbeat_timeout      INT          DEFAULT 1800 COMMENT '心跳超时阈值(秒)',
    heartbeat_count        INT          DEFAULT 0 COMMENT '心跳次数',

    -- 方法和参数
    method_signature       VARCHAR(500) DEFAULT NULL COMMENT '方法签名',
    input_params           TEXT         DEFAULT NULL COMMENT '入参JSON',
    param_size_bytes       INT          DEFAULT 0 COMMENT '参数大小(字节)',
    execution_result       TEXT         DEFAULT NULL COMMENT '执行结果',

    -- 错误信息
    error_message          TEXT         DEFAULT NULL COMMENT '错误消息',
    exception_type         VARCHAR(200) DEFAULT NULL COMMENT '异常类型',
    stack_trace            TEXT         DEFAULT NULL COMMENT '异常堆栈',

    -- 恢复信息
    recoverable            TINYINT(1) DEFAULT 0 COMMENT '是否可恢复',
    non_recoverable_reason VARCHAR(500) DEFAULT NULL COMMENT '不可恢复原因',
    retry_count            INT          DEFAULT 0 COMMENT '已重试次数',
    max_retry              INT          DEFAULT 3 COMMENT '最大重试次数',
    next_retry_time        DATETIME     DEFAULT NULL COMMENT '下次重试时间',

    -- 执行环境
    host_name              VARCHAR(100) DEFAULT NULL COMMENT '主机名',
    host_ip                VARCHAR(50)  DEFAULT NULL COMMENT '主机IP',
    thread_name            VARCHAR(100) DEFAULT NULL COMMENT '执行线程',

    -- 告警信息
    alert_sent             TINYINT(1) DEFAULT 0 COMMENT '是否已发送告警',
    alert_time             DATETIME     DEFAULT NULL COMMENT '告警时间',

    -- 审计
    created_time           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    INDEX                  idx_status_heartbeat (status, last_heartbeat_time) COMMENT '健康检查查询',
    INDEX                  idx_task_name (task_name) COMMENT '按任务名统计',
    INDEX                  idx_retry_time (status, next_retry_time) COMMENT '重试任务扫描',
    INDEX                  idx_host_status (host_ip, status) COMMENT '按主机恢复任务'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志表';
```

### 索引说明

1. **idx_status_heartbeat**: 支持健康检查查询
   ```sql
   WHERE status = 'RUNNING' AND last_heartbeat_time < DATE_SUB(NOW(), INTERVAL 1800 SECOND)
   ```

2. **idx_task_name**: 支持按任务名称分组统计
   ```sql
   SELECT task_name, COUNT(*), AVG(duration_millis) FROM task_execution_log
   GROUP BY task_name
   ```

3. **idx_retry_time**: 支持重试任务扫描
   ```sql
   WHERE status = 'RETRY' AND next_retry_time <= NOW()
   ```

4. **idx_host_status**: 支持启动时恢复本机中断任务
   ```sql
   WHERE host_ip = ? AND status = 'RUNNING'
   ```

---

## 领域事件

### 事件定义

所有领域事件继承基类:

```java
public abstract class DomainEvent {
    private final String eventId = IdUtil.simpleUUID();
    private final LocalDateTime occurredOn = LocalDateTime.now();

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getOccurredOn() {
        return occurredOn;
    }
}
```

#### `TaskStartedEvent`

```java
public class TaskStartedEvent extends DomainEvent {
    private final TaskId taskId;
    private final TaskName taskName;
    private final BizKey bizKey;
}
```

#### `TaskCompletedEvent`

```java
public class TaskCompletedEvent extends DomainEvent {
    private final TaskId taskId;
    private final TaskName taskName;
    private final long durationMillis;
}
```

#### `TaskFailedEvent`

```java
public class TaskFailedEvent extends DomainEvent {
    private final TaskId taskId;
    private final TaskName taskName;
    private final ErrorInfo errorInfo;
}
```

#### `TaskInterruptedEvent`

```java
public class TaskInterruptedEvent extends DomainEvent {
    private final TaskId taskId;
    private final TaskName taskName;
}
```

#### `HeartbeatTimeoutEvent`

```java
public class HeartbeatTimeoutEvent extends DomainEvent {
    private final TaskId taskId;
    private final long secondsSinceLastBeat;
}
```

#### `TaskRetryScheduledEvent`

```java
public class TaskRetryScheduledEvent extends DomainEvent {
    private final TaskId taskId;
    private final int retryCount;
    private final LocalDateTime nextRetryTime;
}
```

#### `TaskRecoveredEvent`

```java
public class TaskRecoveredEvent extends DomainEvent {
    private final TaskId taskId;
    private final TaskName taskName;
    private final int attemptCount;
}
```

---

## 数据转换

### 领域对象 ↔ 持久化对象

**TaskExecution → TaskLogPO** (保存到数据库):

```java
public class TaskLogPO {
    private Long id;
    private String taskId;
    private String taskName;
    private String bizKey;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    // ... 其他字段与表结构一致

    public static TaskLogPO fromDomain(TaskExecution domain) {
        TaskLogPO po = new TaskLogPO();
        po.setTaskId(domain.getTaskId().getValue());
        po.setTaskName(domain.getTaskName().getValue());
        po.setBizKey(domain.getBizKey().getValue());
        po.setStatus(domain.getStatus().name());
        // ... 其他字段映射
        return po;
    }

    public TaskExecution toDomain() {
        TaskExecution domain = new TaskExecution();
        domain.setTaskId(new TaskId(this.taskId));
        domain.setTaskName(new TaskName(this.taskName));
        domain.setBizKey(new BizKey(this.bizKey));
        domain.setStatus(TaskStatus.valueOf(this.status));
        // ... 其他字段映射
        return domain;
    }
}
```

---

## 数据验证规则

### 字段约束

| 字段                 | 规则                        |
|--------------------|---------------------------|
| task_id            | 非空,唯一,UUID格式              |
| task_name          | 非空,≤200字符                 |
| biz_key            | 可空,≤200字符                 |
| status             | 非空,枚举值之一                  |
| start_time         | 非空,不能晚于end_time           |
| input_params       | 可空,TEXT类型,实际限制10KB(应用层控制) |
| retry_count        | ≥0, ≤max_retry            |
| heartbeat_interval | ≥10秒,≤3600秒               |
| heartbeat_timeout  | ≥heartbeat_interval*2     |

### 业务规则验证

1. **状态转换合法性**: 通过`TaskStatus.canTransitionTo()`验证
2. **重试次数限制**: retryCount ≤ maxRetry
3. **心跳超时计算**: timeoutSeconds ≥ interval * 2 (至少丢失2次心跳才超时)
4. **参数大小限制**: 序列化后≤10KB(默认,可配置)

---

## 查询场景

### 常见查询

1. **按状态查询任务**:
   ```java
   repository.findByStatus(TaskStatus.RUNNING)
   ```

2. **查询本机中断任务**(启动恢复):
   ```java
   repository.findByStatusAndHost(TaskStatus.RUNNING, hostIp)
   ```

3. **查询待重试任务**:
   ```java
   repository.findRetryableTasks()
   // WHERE status='RETRY' AND next_retry_time <= NOW()
   ```

4. **查询心跳超时任务**:
   ```java
   repository.findHeartbeatTimeoutTasks(1800)
   // WHERE status='RUNNING' AND last_heartbeat_time < NOW() - INTERVAL 1800 SECOND
   ```

5. **按任务名称统计**:
   ```sql
   SELECT task_name,
          COUNT(*) as total,
          SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) as success_count,
          AVG(duration_millis) as avg_duration
   FROM task_execution_log
   WHERE created_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
   GROUP BY task_name
   ```

---

## 数据迁移

### 初始化脚本

**V1__create_task_log_table.sql**:

```sql
-- 上面的CREATE TABLE语句
```

### 数据清理策略

1. **定期清理**: 保留最近30天的任务记录,删除更早的成功任务
2. **失败任务保留**: 失败任务保留90天,用于问题排查
3. **归档策略**: 超过90天的数据迁移到归档表或对象存储

**清理SQL示例**:

```sql
DELETE
FROM task_execution_log
WHERE status = 'SUCCESS'
  AND created_time < DATE_SUB(NOW(), INTERVAL 30 DAY) LIMIT 10000;
```

---

## 数据模型总结

### 聚合边界

- **TaskExecution聚合**: 管理单个任务的完整生命周期,包含HeartbeatRecord实体
- **RecoveryPolicy聚合**: 管理恢复策略和重试配置,独立于具体任务

### 值对象不变性

所有值对象(TaskId、TaskName、BizKey等)均为不可变对象,创建后不可修改,确保线程安全和数据一致性。

### 仓储职责

- **TaskExecutionRepository**: 负责TaskExecution聚合的持久化和查询
- **HeartbeatStorage**: 负责心跳信息的高性能存储(可选Redis/内存)

### 数据流

1. **任务开始**: TaskExecution创建 → 保存到Repository → 心跳初始化到HeartbeatStorage
2. **心跳更新**: 定时从HeartbeatStorage更新心跳时间
3. **任务完成**: 更新Repository状态 → 删除HeartbeatStorage中的心跳
4. **任务恢复**: 从Repository查询中断任务 → 反序列化参数 → 重新执行

**Phase 1: Data Model Complete** ✅
