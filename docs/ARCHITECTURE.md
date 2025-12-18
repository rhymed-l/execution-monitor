# 架构设计文档

## 概述

Task Monitor 采用领域驱动设计（DDD）方法，将系统划分为清晰的四层架构，确保代码的可维护性、可扩展性和可测试性。

## 架构分层

```
┌─────────────────────────────────────────────────────────┐
│                   Interfaces Layer                       │
│              (接口层 - 对外API与配置)                      │
│  - @TaskMonitor 注解                                     │
│  - @TaskRecoveryHandler 注解                             │
│  - TaskMonitorAutoConfiguration                          │
│  - TaskMonitorProperties                                 │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                  Application Layer                       │
│              (应用层 - 业务流程编排)                       │
│  - TaskMonitorService                                    │
│  - TaskRecoveryService                                   │
│  - TaskRetryExecutor                                     │
│  - RecoveryHandlerRegistry                               │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                    Domain Layer                          │
│              (领域层 - 核心业务逻辑)                       │
│  - TaskExecution (聚合根)                                │
│  - RecoveryPolicy (聚合根)                               │
│  - Value Objects (值对象)                                │
│  - Domain Services (领域服务)                            │
│  - Domain Events (领域事件)                              │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                Infrastructure Layer                      │
│              (基础设施层 - 技术实现)                       │
│  - TaskMonitorAspect (AOP切面)                           │
│  - Memory/Redis/Database Repositories                    │
│  - Schedulers (调度器)                                   │
│  - ReflectionInvoker (反射工具)                          │
└─────────────────────────────────────────────────────────┘
```

## 领域模型

### 聚合根

#### TaskExecution（任务执行）

任务执行是核心聚合根，封装任务的完整生命周期：

```java
public class TaskExecution {
    private TaskId taskId;              // 任务ID（标识符）
    private TaskName taskName;          // 任务名称
    private BizKey bizKey;              // 业务键
    private SerializedParams params;    // 序列化参数
    private TaskStatus status;          // 任务状态
    private ErrorInfo errorInfo;        // 错误信息
    private LocalDateTime startTime;    // 开始时间
    private LocalDateTime endTime;      // 结束时间
    private int retryCount;             // 重试次数
    private int maxRetry;               // 最大重试次数

    // 聚合根方法
    public void complete() {
    }          // 完成任务

    public void fail(ErrorInfo) {
    }     // 标记失败

    public void interrupt() {
    }         // 中断任务

    public TaskExecution restart() {
    }  // 重启任务
}
```

**设计要点**：

- 状态转换通过聚合根方法实现，保证业务规则一致性
- 使用值对象封装复杂属性，确保不变性
- restart() 方法创建新的执行实例，保持历史记录

#### RecoveryPolicy（恢复策略）

恢复策略聚合根定义任务恢复规则：

```java
public class RecoveryPolicy {
    private TaskName taskName;          // 任务名称
    private RecoveryStrategy strategy;  // 恢复策略
    private RetryConfig retryConfig;    // 重试配置
    private ExceptionClassification exceptionClassification;
    private String customRecoveryHandler;
}
```

**设计要点**：

- 封装恢复决策逻辑
- 支持多种恢复策略（AUTO/ALWAYS/NEVER/CUSTOM）
- 可扩展的异常分类机制

### 值对象

值对象是不可变的领域概念封装：

- **TaskId**: 任务唯一标识符
- **TaskName**: 任务名称
- **BizKey**: 业务键（关联业务数据）
- **SerializedParams**: 序列化参数（包含JSON和大小）
- **ErrorInfo**: 错误信息（错误消息和异常类型）
- **RetryConfig**: 重试配置（最大次数和间隔计算）

**值对象特点**：

- 不可变性：一旦创建不可修改
- 相等性：基于属性值比较，不是引用比较
- 无副作用：方法不会改变对象状态

### 领域服务

领域服务封装跨聚合的业务逻辑：

#### TaskExecutionDomainService

```java
public class TaskExecutionDomainService {
    // 标记任务超时
    public void markTaskTimeout(TaskExecution execution);

    // 恢复任务
    public void recoverTask(TaskExecution execution, RecoveryPolicy policy);
}
```

#### SerializationDecisionService

```java
public class SerializationDecisionService {
    // 决定是否序列化参数
    public boolean canSerialize(Object[] args);
}
```

#### RecoveryDecisionService

```java
public class RecoveryDecisionService {
    // 决定是否恢复任务
    public boolean shouldRecover(TaskExecution execution, RecoveryPolicy policy);
}
```

### 领域事件

领域事件用于聚合间通信和解耦：

1. **TaskStartedEvent**: 任务开始事件
2. **TaskCompletedEvent**: 任务完成事件
3. **TaskFailedEvent**: 任务失败事件
4. **TaskInterruptedEvent**: 任务中断事件
5. **HeartbeatTimeoutEvent**: 心跳超时事件
6. **RetryScheduledEvent**: 重试调度事件
7. **RecoveryAttemptedEvent**: 恢复尝试事件

**事件驱动优势**：

- 解耦聚合间依赖
- 便于扩展和审计
- 支持异步处理

## 应用层设计

### TaskMonitorService

应用服务协调领域对象完成业务流程：

```java
public class TaskMonitorService {
    public void startTask(String taskName, String bizKey, Object[] params);

    public void completeTask(String taskId);

    public void failTask(String taskId, Throwable error);
}
```

### TaskRecoveryService

实现 ApplicationRunner，在应用启动时自动恢复任务：

```java
public class TaskRecoveryService implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        int recoveredCount = recoverAllTasks();
        log.info("恢复任务数: {}", recoveredCount);
    }
}
```

### RecoveryHandlerRegistry

扫描和注册自定义恢复处理器：

```java
public class RecoveryHandlerRegistry implements ApplicationContextAware {
    public Optional<HandlerMethod> getHandler(String taskName);
}
```

## 基础设施层设计

### 持久化策略

#### 仓储模式

所有存储实现遵循 Repository 接口：

```java
public interface TaskExecutionRepository {
    void save(TaskExecution execution);

    void update(TaskExecution execution);

    Optional<TaskExecution> findById(TaskId taskId);

    List<TaskExecution> findByStatus(TaskStatus status);
}
```

三种实现：

1. **MemoryTaskExecutionRepository**:
    - 使用 ConcurrentHashMap
    - 性能最高
    - 数据不持久化

2. **RedisTaskExecutionRepository**:
    - 使用 RedisTemplate
    - 支持分布式
    - 自动TTL过期

3. **DatabaseTaskExecutionRepository**:
    - 使用 MyBatis-Plus
    - 数据持久化
    - 支持复杂查询

### AOP切面

TaskMonitorAspect 拦截 @TaskMonitor 注解：

```java

@Around("@annotation(TaskMonitor)")
public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
    // 1. 提取注解信息
    // 2. 开始任务监控
    // 3. 执行目标方法
    // 4. 标记成功或失败
    // 5. 处理心跳
}
```

### 调度器

#### HeartbeatScheduler

定期发送心跳：

```java

@Scheduled(fixedDelayString = "${task.monitor.heartbeat.interval-seconds:60}000")
public void sendHeartbeat() {
    // 更新所有运行中任务的心跳
}
```

#### HealthCheckScheduler

定期检查超时任务：

```java

@Scheduled(fixedDelayString = "${task.monitor.heartbeat.check-interval-seconds:30}000")
public void checkHealthStatus() {
    List<TaskId> timeoutTasks = heartbeatService.checkForTimeoutTasks();
    // 处理超时任务
}
```

#### RetryTaskScheduler

定期执行重试任务：

```java

@Scheduled(fixedDelayString = "${task.monitor.retry.check-interval-seconds:60}000")
public void executeRetryTasks() {
    int executed = retryExecutor.executeBatchRetry(batchSize);
}
```

## 设计模式

### 1. 聚合根模式

TaskExecution 和 RecoveryPolicy 作为聚合根，维护一致性边界。

### 2. 值对象模式

TaskId、TaskName、BizKey等作为值对象，确保不变性。

### 3. 仓储模式

Repository 接口抽象数据访问，支持多种存储实现。

### 4. 策略模式

RecoveryStrategy 枚举定义多种恢复策略。

### 5. 观察者模式

领域事件实现发布-订阅模式。

### 6. 工厂模式

TaskExecution.create() 工厂方法创建对象。

### 7. 构建者模式

TaskExecution.builder() 构建复杂对象。

## 核心流程

### 任务执行流程

```
User Code
   ↓
@TaskMonitor Method
   ↓
TaskMonitorAspect
   ↓
TaskMonitorService.startTask()
   ↓
TaskExecution.create()
   ↓
Repository.save()
   ↓
Execute Method
   ↓
Success → complete() / Failure → fail()
   ↓
Repository.update()
```

### 任务恢复流程

```
Application Startup
   ↓
TaskRecoveryService.run()
   ↓
Repository.findRecoverableTasks()
   ↓
For Each Task:
   ↓
RecoveryDecisionService.shouldRecover()
   ↓
Yes → TaskRetryExecutor.executeRetry()
   ↓
Check RecoveryHandlerRegistry
   ↓
Custom Handler → Invoke
OR
Default → Restart Task
```

### 心跳检测流程

```
Task with Heartbeat Enabled
   ↓
HeartbeatScheduler (Every N seconds)
   ↓
HeartbeatManagementService.updateHeartbeat()
   ↓
HeartbeatStorage.updateHeartbeat()
   ↓
HealthCheckScheduler (Periodic)
   ↓
Check Timeout Tasks
   ↓
Timeout Detected → markHeartbeatTimeout()
```

## 扩展点

### 1. 自定义存储

实现 TaskExecutionRepository 接口：

```java
public class CustomRepository implements TaskExecutionRepository {
    // 实现所有方法
}
```

### 2. 自定义恢复处理器

使用 @TaskRecoveryHandler 注解：

```java

@TaskRecoveryHandler(taskName = "myTask")
public void recover(TaskLogDTO taskLog) {
    // 自定义恢复逻辑
}
```

### 3. 自定义序列化决策

覆盖 SerializationDecisionService Bean：

```java

@Bean
public SerializationDecisionService customSerializationService() {
    return new MySerializationDecisionService();
}
```

### 4. 自定义异常分类

配置异常列表：

```yaml
task:
  monitor:
    retry:
      retryable-exceptions:
        - com.example.MyRetryableException
```

## 性能考虑

### 1. 并发安全

- 使用 ConcurrentHashMap 实现内存存储
- 数据库和Redis本身提供并发控制

### 2. 批量操作

- 批量重试：`executeBatchRetry(batchSize)`
- 批量清理：定期清理过期记录

### 3. 索引优化

数据库表添加索引：

- task_id (主查询)
- status (状态查询)
- created_at (时间范围查询)

### 4. 异步处理

- 调度器使用独立线程池
- 领域事件可配置异步发布

## 测试策略

### 1. 单元测试

每个领域对象、值对象都有对应单元测试：

- TaskExecutionTest
- RecoveryPolicyTest
- ValueObjectsTest

### 2. 集成测试

测试组件协作：

- TaskMonitorServiceIntegrationTest
- RecoveryHandlerRegistryIntegrationTest

### 3. 契约测试

测试注解契约：

- TaskMonitorAnnotationTest
- TaskRecoveryHandlerTest

## 总结

Task Monitor 的架构设计遵循以下原则：

1. **领域驱动**: 以业务领域为核心，而非技术实现
2. **分层清晰**: 四层架构，职责明确
3. **高内聚低耦合**: 聚合根封装业务规则，领域事件解耦
4. **可扩展性**: 策略模式、仓储模式支持扩展
5. **可测试性**: 依赖注入、接口抽象便于测试

这种设计使系统易于理解、维护和扩展，同时保持了高质量的代码标准。
