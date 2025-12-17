# Spring Boot 任务监控 Starter 设计方案

## 可插拔心跳机制 + 智能参数序列化 + 自动恢复

---

## 一、项目定位

### 1.1 项目名称
**task-monitor-spring-boot-starter**

### 1.2 核心特性
- ✅ **低侵入**：通过注解即可使用，无需修改业务代码
- ✅ **即插即用**：Maven依赖 + 配置即可
- ✅ **智能参数处理**：自动判断参数是否可序列化，避免存储过大对象
- ✅ **心跳机制**：准确识别任务卡死（不是简单timeout）
- ✅ **可插拔存储**：内存/Redis/数据库，按需选择
- ✅ **智能恢复**：自动判断是否可恢复，支持重试次数控制
- ✅ **异常过滤**：支持配置哪些异常可以忽略或重试
- ✅ **分布式友好**：支持分布式部署

---

## 二、使用方式

### 2.1 Maven依赖
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>task-monitor-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2.2 快速开始

#### Step 1: 配置文件
```yaml
task-monitor:
  enabled: true

  # 存储策略选择（memory/redis/database）
  storage:
    type: database  # 默认数据库

  # 心跳配置
  heartbeat:
    enabled: true
    default-interval: 300      # 5分钟
    default-timeout: 1800      # 30分钟

  # 参数序列化配置（智能判断）
  params:
    auto-serialize: true       # 自动判断是否序列化
    max-size-bytes: 10240      # 最大存储10KB，超过不存储
    serialize-basic-types: true # 基本类型总是序列化

  # 恢复配置
  recovery:
    enabled: true
    max-retry-count: 3         # 默认最大重试3次
    retry-interval: 60         # 重试间隔（秒）
    exponential-backoff: true  # 指数退避

  # 异常过滤配置
  exception:
    # 可重试异常（遇到这些异常会重试）
    retryable:
      - "java.sql.SQLException"
      - "java.net.SocketTimeoutException"
      - "org.springframework.dao.DataAccessException"
    # 忽略异常（遇到这些异常不告警，直接标记失败）
    ignorable:
      - "java.lang.IllegalArgumentException"
```

#### Step 2: 启用监控
```java
@SpringBootApplication
@EnableTaskMonitor  // 启用任务监控
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

#### Step 3: 使用注解
```java
@Service
public class OrderService {

    // 示例1：无参数 - 自动可恢复
    @TaskMonitor("每日订单结算")
    public void dailySettlement() {
        // 无参数，系统会自动判断为可恢复
    }

    // 示例2：基本类型参数 - 自动序列化
    @TaskMonitor(value = "生成报表", bizKey = "#reportType")
    public void generateReport(String reportType, LocalDate date) {
        // 参数是基本类型，自动序列化存储
    }

    // 示例3：复杂参数但小 - 自动序列化
    @TaskMonitor(value = "处理订单", bizKey = "#order.id")
    public void processOrder(Order order) {
        // 如果 order 序列化后 < 10KB，会自动存储
        // 如果 > 10KB，不存储，无法自动恢复
    }

    // 示例4：超大参数 - 强制不序列化
    @TaskMonitor(
        value = "批量导入订单",
        bizKey = "#fileName",
        serializeParams = false  // 明确指定不序列化
    )
    public void batchImport(String fileName, List<Order> orders) {
        // orders可能有上万条，明确指定不序列化
        // 中断后仅告警，不自动恢复
    }

    // 示例5：自定义恢复逻辑
    @TaskMonitor(
        value = "批量导入订单V2",
        bizKey = "#fileName"
    )
    public void batchImportV2(String fileName, List<Order> orders) {
        // 实现自定义恢复逻辑
    }

    // 自定义恢复处理器
    @TaskRecoveryHandler("批量导入订单V2")
    public void recoverBatchImport(TaskLog taskLog) {
        String fileName = taskLog.getBizKey();
        List<Order> orders = orderFileReader.read(fileName);
        batchImportV2(fileName, orders);
    }
}
```

---

## 三、核心注解设计

### 3.1 @TaskMonitor 注解
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskMonitor {

    /**
     * 任务名称（必填）
     */
    String value();

    /**
     * 业务关键字SpEL表达式（如：#orderId, #fileName）
     */
    String bizKey() default "";

    /**
     * 是否序列化参数
     * - null（默认）：自动判断
     * - true：强制序列化（如果超过大小限制仍会失败）
     * - false：强制不序列化
     */
    Boolean serializeParams() default null;

    /**
     * 是否记录返回值
     */
    boolean recordResult() default false;

    /**
     * 是否启用心跳机制
     */
    boolean enableHeartbeat() default true;

    /**
     * 心跳间隔（秒），默认300秒（5分钟）
     */
    int heartbeatInterval() default 300;

    /**
     * 心跳超时阈值（秒），默认1800秒（30分钟）
     */
    int heartbeatTimeout() default 1800;

    /**
     * 恢复策略
     * - AUTO：自动判断（默认）
     * - ALWAYS：总是尝试恢复
     * - NEVER：从不恢复，仅告警
     * - CUSTOM：使用自定义恢复处理器
     */
    RecoveryStrategy recoveryStrategy() default RecoveryStrategy.AUTO;

    /**
     * 失败时是否自动重试
     */
    boolean autoRetry() default true;

    /**
     * 最大重试次数（0表示使用全局配置）
     */
    int maxRetry() default 0;

    /**
     * 可重试的异常类型（为空使用全局配置）
     */
    Class<? extends Throwable>[] retryableExceptions() default {};

    /**
     * 忽略的异常类型（遇到这些异常不告警）
     */
    Class<? extends Throwable>[] ignorableExceptions() default {};
}

/**
 * 恢复策略
 */
public enum RecoveryStrategy {
    /**
     * 自动判断（默认）
     * - 无参数或参数可序列化 → 自动恢复
     * - 参数无法序列化 → 仅告警
     */
    AUTO,

    /**
     * 总是尝试恢复
     * 如果参数无法序列化，会调用自定义恢复处理器（如果有）
     */
    ALWAYS,

    /**
     * 从不恢复，仅告警
     */
    NEVER,

    /**
     * 使用自定义恢复处理器
     */
    CUSTOM
}
```

### 3.2 @TaskRecoveryHandler 注解
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskRecoveryHandler {
    /**
     * 对应的任务名称
     */
    String value();
}
```

---

## 四、智能参数序列化

### 4.1 自动判断逻辑
```java
@Component
public class ParamSerializationStrategy {

    @Autowired
    private TaskMonitorProperties properties;

    /**
     * 判断是否应该序列化参数
     */
    public SerializationDecision shouldSerialize(
            Object[] args,
            Boolean userConfig) {

        // 1. 用户明确指定
        if (userConfig != null) {
            if (userConfig) {
                return trySerialize(args); // 尝试序列化，检查大小
            } else {
                return SerializationDecision.no("用户配置不序列化");
            }
        }

        // 2. 无参数 - 可序列化
        if (args == null || args.length == 0) {
            return SerializationDecision.yes(null, 0);
        }

        // 3. 检查参数类型
        if (allBasicTypes(args)) {
            return trySerialize(args); // 基本类型，尝试序列化
        }

        // 4. 包含复杂对象，尝试序列化并检查大小
        return trySerialize(args);
    }

    /**
     * 尝试序列化并检查大小
     */
    private SerializationDecision trySerialize(Object[] args) {
        try {
            String json = objectMapper.writeValueAsString(args);
            int sizeBytes = json.getBytes(StandardCharsets.UTF_8).length;

            // 检查是否超过大小限制
            int maxSize = properties.getParams().getMaxSizeBytes();
            if (sizeBytes > maxSize) {
                return SerializationDecision.no(
                    String.format("参数大小 %d bytes 超过限制 %d bytes",
                                  sizeBytes, maxSize)
                );
            }

            return SerializationDecision.yes(json, sizeBytes);

        } catch (JsonProcessingException e) {
            return SerializationDecision.no("参数无法序列化: " + e.getMessage());
        }
    }

    /**
     * 判断是否都是基本类型
     */
    private boolean allBasicTypes(Object[] args) {
        for (Object arg : args) {
            if (arg == null) continue;

            Class<?> clazz = arg.getClass();
            if (!isBasicType(clazz)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断是否是基本类型
     */
    private boolean isBasicType(Class<?> clazz) {
        return clazz.isPrimitive()
            || clazz == String.class
            || Number.class.isAssignableFrom(clazz)
            || clazz == Boolean.class
            || clazz == Character.class
            || clazz == LocalDate.class
            || clazz == LocalDateTime.class
            || clazz == Date.class
            || clazz.isEnum();
    }
}

/**
 * 序列化决策结果
 */
@Data
public class SerializationDecision {
    private boolean shouldSerialize;
    private String serializedData;
    private int sizeBytes;
    private String reason;

    public static SerializationDecision yes(String data, int size) {
        SerializationDecision decision = new SerializationDecision();
        decision.shouldSerialize = true;
        decision.serializedData = data;
        decision.sizeBytes = size;
        return decision;
    }

    public static SerializationDecision no(String reason) {
        SerializationDecision decision = new SerializationDecision();
        decision.shouldSerialize = false;
        decision.reason = reason;
        return decision;
    }
}
```

### 4.2 AOP切面实现
```java
@Aspect
@Component
public class TaskMonitorAspect {

    @Autowired
    private ParamSerializationStrategy serializationStrategy;

    @Around("@annotation(taskMonitor)")
    public Object around(ProceedingJoinPoint joinPoint, TaskMonitor taskMonitor) {
        String taskId = generateTaskId();
        TaskLog taskLog = new TaskLog();

        try {
            // 1. 智能参数序列化
            Object[] args = joinPoint.getArgs();
            SerializationDecision decision = serializationStrategy.shouldSerialize(
                args,
                taskMonitor.serializeParams()
            );

            if (decision.isShouldSerialize()) {
                taskLog.setInputParams(decision.getSerializedData());
                taskLog.setParamSize(decision.getSizeBytes());
                taskLog.setRecoverable(true);
                log.debug("参数已序列化: taskId={}, size={}bytes", taskId, decision.getSizeBytes());
            } else {
                taskLog.setRecoverable(false);
                taskLog.setNonRecoverableReason(decision.getReason());
                log.debug("参数不可序列化: taskId={}, reason={}", taskId, decision.getReason());
            }

            // 2. 提取业务关键字
            if (StringUtils.hasText(taskMonitor.bizKey())) {
                String bizKey = SpelParser.parse(taskMonitor.bizKey(), joinPoint);
                taskLog.setBizKey(bizKey);
            }

            // 3. 创建任务记录
            taskLog.setTaskId(taskId);
            taskLog.setTaskName(taskMonitor.value());
            taskLog.setStatus(TaskStatus.RUNNING);
            taskLog.setStartTime(LocalDateTime.now());
            taskLog.setLastHeartbeatTime(LocalDateTime.now());
            taskLog.setMethodSignature(joinPoint.getSignature().toLongString());
            taskLogMapper.insert(taskLog);

            // 4. 启动心跳
            if (taskMonitor.enableHeartbeat()) {
                heartbeatService.startHeartbeat(taskId, taskMonitor.heartbeatInterval());
            }

            // 5. 执行业务方法
            Object result = joinPoint.proceed();

            // 6. 更新任务状态为成功
            updateTaskSuccess(taskId, result, taskMonitor);

            return result;

        } catch (Throwable e) {
            // 7. 处理异常
            handleException(taskId, e, taskMonitor);
            throw new RuntimeException(e);

        } finally {
            // 8. 停止心跳
            if (taskMonitor.enableHeartbeat()) {
                heartbeatService.stopHeartbeat(taskId);
            }
        }
    }
}
```

---

## 五、异常处理和重试机制

### 5.1 异常分类
```java
@Component
public class ExceptionClassifier {

    @Autowired
    private TaskMonitorProperties properties;

    /**
     * 判断异常是否可重试
     */
    public boolean isRetryable(Throwable throwable,
                               Class<? extends Throwable>[] annotationConfig) {

        // 1. 优先使用注解配置
        if (annotationConfig != null && annotationConfig.length > 0) {
            return matchesAnyException(throwable, annotationConfig);
        }

        // 2. 使用全局配置
        List<String> retryableExceptions = properties.getException().getRetryable();
        return matchesAnyExceptionByName(throwable, retryableExceptions);
    }

    /**
     * 判断异常是否可忽略
     */
    public boolean isIgnorable(Throwable throwable,
                               Class<? extends Throwable>[] annotationConfig) {

        // 1. 优先使用注解配置
        if (annotationConfig != null && annotationConfig.length > 0) {
            return matchesAnyException(throwable, annotationConfig);
        }

        // 2. 使用全局配置
        List<String> ignorableExceptions = properties.getException().getIgnorable();
        return matchesAnyExceptionByName(throwable, ignorableExceptions);
    }

    private boolean matchesAnyException(Throwable throwable,
                                       Class<? extends Throwable>[] exceptionTypes) {
        for (Class<? extends Throwable> exType : exceptionTypes) {
            if (exType.isAssignableFrom(throwable.getClass())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAnyExceptionByName(Throwable throwable,
                                              List<String> exceptionNames) {
        if (exceptionNames == null || exceptionNames.isEmpty()) {
            return false;
        }

        String exceptionClassName = throwable.getClass().getName();
        for (String name : exceptionNames) {
            try {
                Class<?> exClass = Class.forName(name);
                if (exClass.isAssignableFrom(throwable.getClass())) {
                    return true;
                }
            } catch (ClassNotFoundException e) {
                // 忽略
            }
        }
        return false;
    }
}
```

### 5.2 重试控制
```java
@Component
public class TaskMonitorAspect {

    @Autowired
    private ExceptionClassifier exceptionClassifier;

    private void handleException(String taskId, Throwable e, TaskMonitor taskMonitor) {
        TaskLog taskLog = taskLogMapper.selectByTaskId(taskId);

        // 1. 判断是否是可忽略异常
        if (exceptionClassifier.isIgnorable(e, taskMonitor.ignorableExceptions())) {
            taskLog.setStatus(TaskStatus.FAILED);
            taskLog.setErrorMessage("可忽略异常: " + e.getMessage());
            taskLog.setAlertSent(false); // 不发送告警
            taskLogMapper.updateById(taskLog);
            log.info("任务失败（可忽略异常）: taskId={}, exception={}",
                     taskId, e.getClass().getSimpleName());
            return;
        }

        // 2. 判断是否可重试
        boolean retryable = taskMonitor.autoRetry()
            && exceptionClassifier.isRetryable(e, taskMonitor.retryableExceptions());

        // 3. 检查重试次数
        int maxRetry = taskMonitor.maxRetry() > 0
            ? taskMonitor.maxRetry()
            : properties.getRecovery().getMaxRetryCount();

        if (retryable && taskLog.getRetryCount() < maxRetry) {
            // 调度重试
            scheduleRetry(taskLog, taskMonitor);
        } else {
            // 标记失败，发送告警
            taskLog.setStatus(TaskStatus.FAILED);
            taskLog.setErrorMessage(e.getMessage());
            taskLog.setStackTrace(getStackTrace(e));
            taskLog.setAlertSent(false);
            taskLogMapper.updateById(taskLog);

            // 发送告警
            alertService.sendFailureAlert(taskLog);

            log.error("任务失败: taskId={}, retryCount={}, maxRetry={}",
                      taskId, taskLog.getRetryCount(), maxRetry, e);
        }
    }

    private void scheduleRetry(TaskLog taskLog, TaskMonitor taskMonitor) {
        // 计算下次重试时间（指数退避）
        int baseInterval = properties.getRecovery().getRetryInterval();
        boolean exponentialBackoff = properties.getRecovery().isExponentialBackoff();

        int retryInterval = exponentialBackoff
            ? baseInterval * (int) Math.pow(2, taskLog.getRetryCount())
            : baseInterval;

        LocalDateTime nextRetryTime = LocalDateTime.now().plusSeconds(retryInterval);

        taskLog.setStatus(TaskStatus.RETRY);
        taskLog.setRetryCount(taskLog.getRetryCount() + 1);
        taskLog.setNextRetryTime(nextRetryTime);
        taskLogMapper.updateById(taskLog);

        log.info("任务已调度重试: taskId={}, retryCount={}, nextRetryTime={}",
                 taskLog.getTaskId(), taskLog.getRetryCount(), nextRetryTime);
    }
}
```

### 5.3 重试执行服务
```java
@Service
public class TaskRetryExecutor {

    @Autowired
    private TaskLogMapper taskLogMapper;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 定期扫描待重试任务（每30秒）
     */
    @Scheduled(fixedRate = 30000)
    public void executeRetryTasks() {
        List<TaskLog> retryTasks = taskLogMapper.selectList(
            new QueryWrapper<TaskLog>()
                .eq("status", TaskStatus.RETRY)
                .le("next_retry_time", LocalDateTime.now())
        );

        if (retryTasks.isEmpty()) {
            return;
        }

        log.info("发现 {} 个待重试任务", retryTasks.size());

        for (TaskLog task : retryTasks) {
            try {
                retryTask(task);
            } catch (Exception e) {
                log.error("重试任务失败: taskId={}", task.getTaskId(), e);
            }
        }
    }

    private void retryTask(TaskLog task) {
        // 1. 检查是否可恢复
        if (!task.isRecoverable()) {
            log.warn("任务不可恢复（参数不可序列化）: taskId={}", task.getTaskId());
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage("无法自动恢复: " + task.getNonRecoverableReason());
            taskLogMapper.updateById(task);
            alertService.sendAlert(task);
            return;
        }

        // 2. 反序列化参数
        Object[] params = deserializeParams(task.getInputParams());

        // 3. 通过反射调用方法
        Method method = findMethod(task.getMethodSignature());
        Object bean = applicationContext.getBean(method.getDeclaringClass());

        // 4. 重新执行
        method.invoke(bean, params);

        log.info("任务重试执行成功: taskId={}, retryCount={}",
                 task.getTaskId(), task.getRetryCount());
    }

    private Object[] deserializeParams(String json) {
        try {
            return objectMapper.readValue(json, Object[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("参数反序列化失败", e);
        }
    }

    private Method findMethod(String methodSignature) {
        // 解析方法签名，查找对应的 Method
        // 实现略...
        return null;
    }
}
```

---

## 六、启动恢复机制

### 6.1 启动时恢复服务
```java
@Component
public class TaskRecoveryService implements ApplicationRunner {

    @Autowired
    private TaskLogMapper taskLogMapper;

    @Autowired
    private TaskRetryExecutor retryExecutor;

    @Autowired
    private TaskMonitorProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getRecovery().isEnabled()) {
            log.info("任务恢复功能已禁用");
            return;
        }

        log.info("开始扫描中断任务...");

        // 1. 查询本机上次RUNNING状态的任务
        List<TaskLog> runningTasks = taskLogMapper.selectList(
            new QueryWrapper<TaskLog>()
                .eq("status", TaskStatus.RUNNING)
                .eq("host_ip", getLocalIp())
        );

        log.info("发现 {} 个中断任务", runningTasks.size());

        // 2. 逐个处理
        for (TaskLog task : runningTasks) {
            handleInterruptedTask(task);
        }
    }

    private void handleInterruptedTask(TaskLog task) {
        // 1. 标记为中断
        task.setStatus(TaskStatus.INTERRUPTED);
        task.setErrorMessage("系统重启导致任务中断");
        task.setEndTime(LocalDateTime.now());

        // 2. 判断是否可恢复
        if (!task.isRecoverable()) {
            // 不可恢复，仅告警
            taskLogMapper.updateById(task);
            alertService.sendInterruptAlert(task);
            log.warn("任务中断且不可恢复: taskId={}, reason={}",
                     task.getTaskId(), task.getNonRecoverableReason());
            return;
        }

        // 3. 检查重试次数
        int maxRetry = properties.getRecovery().getMaxRetryCount();
        if (task.getRetryCount() >= maxRetry) {
            // 超过重试次数
            taskLogMapper.updateById(task);
            alertService.sendAlert(task);
            log.warn("任务中断且超过最大重试次数: taskId={}, retryCount={}",
                     task.getTaskId(), task.getRetryCount());
            return;
        }

        // 4. 调度重试
        task.setStatus(TaskStatus.RETRY);
        task.setRetryCount(task.getRetryCount() + 1);
        task.setNextRetryTime(LocalDateTime.now().plusSeconds(60)); // 1分钟后重试
        taskLogMapper.updateById(task);

        log.info("中断任务已调度恢复: taskId={}, retryCount={}",
                 task.getTaskId(), task.getRetryCount());
    }
}
```

---

## 七、数据库设计

### 7.1 任务执行记录表
```sql
CREATE TABLE `task_execution_log` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` VARCHAR(64) NOT NULL COMMENT '任务唯一标识',
  `task_name` VARCHAR(200) NOT NULL COMMENT '任务名称',
  `biz_key` VARCHAR(200) DEFAULT NULL COMMENT '业务关键字',

  -- 执行信息
  `status` VARCHAR(20) NOT NULL COMMENT '状态：RUNNING/SUCCESS/FAILED/INTERRUPTED/HEARTBEAT_TIMEOUT/RETRY',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `duration` BIGINT DEFAULT NULL COMMENT '执行时长（毫秒）',

  -- 心跳机制
  `last_heartbeat_time` DATETIME NOT NULL COMMENT '最后心跳时间',
  `heartbeat_interval` INT DEFAULT 300 COMMENT '心跳间隔（秒）',
  `heartbeat_timeout` INT DEFAULT 1800 COMMENT '心跳超时阈值（秒）',
  `heartbeat_count` INT DEFAULT 0 COMMENT '心跳次数',

  -- 参数和结果
  `method_signature` VARCHAR(500) DEFAULT NULL COMMENT '方法签名',
  `input_params` TEXT DEFAULT NULL COMMENT '入参（JSON格式）',
  `param_size` INT DEFAULT 0 COMMENT '参数大小（字节）',
  `result` TEXT DEFAULT NULL COMMENT '执行结果',
  `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
  `stack_trace` TEXT DEFAULT NULL COMMENT '异常堆栈',

  -- 恢复机制
  `recoverable` TINYINT(1) DEFAULT 0 COMMENT '是否可恢复',
  `non_recoverable_reason` VARCHAR(500) DEFAULT NULL COMMENT '不可恢复原因',
  `retry_count` INT DEFAULT 0 COMMENT '已重试次数',
  `max_retry` INT DEFAULT 3 COMMENT '最大重试次数',
  `next_retry_time` DATETIME DEFAULT NULL COMMENT '下次重试时间',

  -- 执行环境
  `host_name` VARCHAR(100) DEFAULT NULL COMMENT '执行机器',
  `host_ip` VARCHAR(50) DEFAULT NULL COMMENT '机器IP',
  `thread_name` VARCHAR(100) DEFAULT NULL COMMENT '执行线程',

  -- 告警信息
  `alert_sent` TINYINT(1) DEFAULT 0 COMMENT '是否已发送告警',
  `alert_time` DATETIME DEFAULT NULL COMMENT '告警时间',

  -- 元数据
  `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_status_heartbeat` (`status`, `last_heartbeat_time`),
  KEY `idx_task_name` (`task_name`),
  KEY `idx_biz_key` (`biz_key`),
  KEY `idx_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志表';
```

---

## 八、心跳存储策略（可插拔）

### 8.1 存储策略接口
```java
/**
 * 心跳存储策略接口
 */
public interface HeartbeatStorage {

    /**
     * 初始化任务心跳
     */
    void initHeartbeat(String taskId, HeartbeatInfo info);

    /**
     * 更新心跳时间
     */
    void updateHeartbeat(String taskId, LocalDateTime heartbeatTime);

    /**
     * 获取心跳信息
     */
    HeartbeatInfo getHeartbeat(String taskId);

    /**
     * 删除心跳记录（任务结束时）
     */
    void removeHeartbeat(String taskId);

    /**
     * 扫描所有运行中的任务
     */
    List<HeartbeatInfo> scanRunningTasks();

    /**
     * 扫描心跳超时的任务
     */
    List<HeartbeatInfo> scanTimeoutTasks();
}
```

### 8.2 三种实现
详见之前的设计（MemoryHeartbeatStorage / RedisHeartbeatStorage / DatabaseHeartbeatStorage）

---

## 九、配置属性完整定义

```java
@ConfigurationProperties(prefix = "task-monitor")
@Data
public class TaskMonitorProperties {

    /**
     * 是否启用任务监控
     */
    private boolean enabled = true;

    /**
     * 存储配置
     */
    private StorageProperties storage = new StorageProperties();

    /**
     * 心跳配置
     */
    private HeartbeatProperties heartbeat = new HeartbeatProperties();

    /**
     * 参数序列化配置
     */
    private ParamsProperties params = new ParamsProperties();

    /**
     * 恢复配置
     */
    private RecoveryProperties recovery = new RecoveryProperties();

    /**
     * 异常配置
     */
    private ExceptionProperties exception = new ExceptionProperties();

    /**
     * 告警配置
     */
    private AlertProperties alert = new AlertProperties();

    @Data
    public static class StorageProperties {
        /**
         * 存储类型：memory/redis/database
         */
        private StorageType type = StorageType.DATABASE;

        public enum StorageType {
            MEMORY, REDIS, DATABASE
        }
    }

    @Data
    public static class HeartbeatProperties {
        /**
         * 是否启用心跳
         */
        private boolean enabled = true;

        /**
         * 默认心跳间隔（秒）
         */
        private int defaultInterval = 300;

        /**
         * 默认心跳超时（秒）
         */
        private int defaultTimeout = 1800;

        /**
         * 心跳线程池大小
         */
        private int threadPoolSize = 10;

        /**
         * 健康检查间隔（秒）
         */
        private int checkInterval = 60;
    }

    @Data
    public static class ParamsProperties {
        /**
         * 是否自动判断参数序列化
         */
        private boolean autoSerialize = true;

        /**
         * 最大存储大小（字节）
         */
        private int maxSizeBytes = 10240; // 10KB

        /**
         * 基本类型总是序列化
         */
        private boolean serializeBasicTypes = true;
    }

    @Data
    public static class RecoveryProperties {
        /**
         * 是否启用恢复
         */
        private boolean enabled = true;

        /**
         * 启动时扫描中断任务
         */
        private boolean scanOnStartup = true;

        /**
         * 最大重试次数
         */
        private int maxRetryCount = 3;

        /**
         * 重试间隔（秒）
         */
        private int retryInterval = 60;

        /**
         * 指数退避
         */
        private boolean exponentialBackoff = true;
    }

    @Data
    public static class ExceptionProperties {
        /**
         * 可重试异常（全局配置）
         */
        private List<String> retryable = Arrays.asList(
            "java.sql.SQLException",
            "java.net.SocketTimeoutException",
            "org.springframework.dao.DataAccessException"
        );

        /**
         * 可忽略异常（全局配置）
         */
        private List<String> ignorable = Arrays.asList(
            "java.lang.IllegalArgumentException"
        );
    }

    @Data
    public static class AlertProperties {
        private boolean enabled = true;
        private String type = "dingtalk"; // dingtalk/email/wechat
        // ... 其他告警配置
    }
}
```

---

## 十、使用场景示例

### 场景1：定时任务（无参数）
```java
@Component
public class ScheduledTasks {

    @Scheduled(cron = "0 0 2 * * ?")
    @TaskMonitor("每日订单结算")
    public void dailySettlement() {
        // 无参数，自动判断为可恢复
        // 系统重启后会自动重试
    }
}
```
**结果**：参数可序列化，中断后自动恢复

### 场景2：简单参数
```java
@Service
public class ReportService {

    @TaskMonitor(value = "生成报表", bizKey = "#reportType")
    public void generateReport(String reportType, LocalDate date) {
        // 参数是基本类型，自动序列化
    }
}
```
**结果**：参数序列化成功（约50字节），中断后自动恢复

### 场景3：小对象参数
```java
@Service
public class OrderService {

    @TaskMonitor(value = "处理订单", bizKey = "#order.id")
    public void processOrder(Order order) {
        // order 对象序列化后 < 10KB
    }
}
```
**结果**：参数序列化成功（假设5KB），中断后自动恢复

### 场景4：超大参数
```java
@Service
public class OrderService {

    @TaskMonitor(value = "批量导入订单", bizKey = "#fileName")
    public void batchImport(String fileName, List<Order> orders) {
        // orders 包含10000条记录，序列化后 > 10KB
    }
}
```
**结果**：参数超过大小限制，不序列化，标记为不可恢复，中断后仅告警

### 场景5：强制不序列化
```java
@Service
public class OrderService {

    @TaskMonitor(
        value = "批量导入订单V2",
        bizKey = "#fileName",
        serializeParams = false  // 明确指定
    )
    public void batchImportV2(String fileName, List<Order> orders) {
        // 强制不序列化
    }
}
```
**结果**：不序列化，不可恢复，中断后仅告警

### 场景6：自定义恢复
```java
@Service
public class OrderService {

    @TaskMonitor(
        value = "批量导入订单V3",
        bizKey = "#fileName",
        recoveryStrategy = RecoveryStrategy.CUSTOM
    )
    public void batchImportV3(String fileName, List<Order> orders) {
        // 业务逻辑
    }

    @TaskRecoveryHandler("批量导入订单V3")
    public void recoverBatchImport(TaskLog taskLog) {
        // 根据 fileName 重新读取数据
        String fileName = taskLog.getBizKey();
        List<Order> orders = fileReader.read(fileName);
        batchImportV3(fileName, orders);
    }
}
```
**结果**：使用自定义恢复逻辑

### 场景7：异常过滤
```java
@Service
public class OrderService {

    @TaskMonitor(
        value = "验证订单",
        ignorableExceptions = {IllegalArgumentException.class},
        retryableExceptions = {SQLException.class}
    )
    public void validateOrder(Order order) {
        if (order.getAmount() < 0) {
            // 参数错误，不告警
            throw new IllegalArgumentException("金额不能为负数");
        }
        // 数据库异常会重试
    }
}
```
**结果**：IllegalArgumentException 不告警，SQLException 会重试

---

## 十一、项目结构

```
task-monitor-spring-boot-starter
├── src/main/java/com/example/taskmonitor
│   ├── annotation
│   │   ├── TaskMonitor.java              // 核心注解
│   │   ├── TaskRecoveryHandler.java      // 恢复处理器注解
│   │   └── EnableTaskMonitor.java        // 启用注解
│   │
│   ├── config
│   │   ├── TaskMonitorAutoConfiguration.java  // 自动配置
│   │   └── TaskMonitorProperties.java         // 配置属性
│   │
│   ├── aspect
│   │   └── TaskMonitorAspect.java        // AOP切面
│   │
│   ├── storage
│   │   ├── HeartbeatStorage.java         // 存储接口
│   │   ├── HeartbeatInfo.java            // 心跳信息
│   │   ├── MemoryHeartbeatStorage.java   // 内存实现
│   │   ├── RedisHeartbeatStorage.java    // Redis实现
│   │   └── DatabaseHeartbeatStorage.java // 数据库实现
│   │
│   ├── service
│   │   ├── TaskHeartbeatService.java     // 心跳服务
│   │   ├── TaskHealthCheckService.java   // 健康检查
│   │   ├── TaskExecutionService.java     // 任务执行
│   │   ├── TaskRecoveryService.java      // 启动恢复
│   │   ├── TaskRetryExecutor.java        // 重试执行
│   │   ├── ParamSerializationStrategy.java // 参数序列化策略（核心）
│   │   ├── ExceptionClassifier.java      // 异常分类器（核心）
│   │   └── AlertService.java             // 告警服务
│   │
│   ├── domain
│   │   ├── TaskLog.java                  // 任务日志实体
│   │   ├── TaskStatus.java               // 状态枚举
│   │   ├── RecoveryStrategy.java         // 恢复策略枚举
│   │   └── SerializationDecision.java    // 序列化决策
│   │
│   ├── mapper
│   │   └── TaskLogMapper.java            // MyBatis Mapper
│   │
│   ├── controller
│   │   └── TaskMonitorController.java    // 管理接口
│   │
│   └── util
│       ├── TaskIdGenerator.java          // ID生成器
│       └── SpelParser.java               // SpEL解析器
│
├── src/main/resources
│   ├── META-INF
│   │   └── spring.factories              // 自动配置
│   ├── mapper
│   │   └── TaskLogMapper.xml
│   └── db
│       └── schema-mysql.sql              // 建表SQL
│
└── pom.xml
```

---

## 十二、总结

### 核心亮点
1. ✅ **智能参数序列化**：自动判断参数是否可序列化，避免存储过大对象
2. ✅ **灵活的恢复策略**：自动判断、强制恢复、自定义恢复
3. ✅ **重试控制**：最大重试次数、指数退避
4. ✅ **异常过滤**：可重试异常、可忽略异常（全局+注解配置）
5. ✅ **可插拔存储**：内存/Redis/数据库
6. ✅ **心跳机制**：准确识别任务卡死
7. ✅ **即插即用**：零侵入，注解即用

### 设计原则
1. **默认安全**：参数超过限制不存储，避免浪费空间
2. **用户可控**：提供注解和全局配置，灵活控制
3. **智能决策**：自动判断参数类型和大小
4. **容错处理**：用户配置错误时（如大对象强制序列化）也能正常工作

---

**准备好开始编码了吗？** 我建议从核心功能开始：
1. 注解定义
2. AOP切面
3. 智能参数序列化
4. 心跳机制（内存存储）
5. 异常处理和重试

你觉得如何？
