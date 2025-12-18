# Research: Spring Boot Task Monitor Starter

**Feature**: 001-task-monitor-starter
**Date**: 2025-12-17
**Purpose**: 解决Technical Context中的未明确项,研究最佳实践,确定技术选型

---

## 研究主题

### 1. Spring Boot Starter最佳实践

**Decision**: 使用Spring Boot 2.7.x作为基线版本,兼容Spring Boot 2.x和3.x

**Rationale**:

- Spring Boot 2.7.x是2.x系列的LTS版本,广泛应用于生产环境
- 通过条件编译可以支持Spring Boot 3.x
- 使用`spring-boot-autoconfigure`和`spring.factories`实现自动配置
- Starter命名遵循规范:`xxx-spring-boot-starter`(第三方)或`spring-boot-starter-xxx`(官方)

**Alternatives Considered**:

- **仅支持Spring Boot 3.x**: 排除大量仍在使用2.x的用户
- **使用Spring Boot 1.x**: 已过时,不再维护

**Best Practices**:

1. 提供`@EnableXxx`注解显式启用(虽然自动配置也行,但显式更清晰)
2. 配置属性使用`@ConfigurationProperties`
3. 条件装配使用`@ConditionalOnProperty`、`@ConditionalOnClass`
4. META-INF/spring.factories注册自动配置类
5. 提供默认配置但允许用户覆盖

**References**:

- [Spring Boot Starters官方文档](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/features.html#features.developing-auto-configuration)
- [Creating Your Own Starter](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/features.html#features.developing-auto-configuration.custom-starter)

---

### 2. AOP拦截与性能优化

**Decision**: 使用Spring AOP的`@Around`切面拦截,采用CGLIB代理模式

**Rationale**:

- `@Around`提供完整的方法执行控制(before、after、异常处理)
- CGLIB代理支持拦截类方法(不要求接口)
- 通过异步心跳减少主线程阻塞
- 参数序列化在任务开始前一次性完成,不重复序列化

**Performance Optimization**:

1. **心跳异步化**: 使用单独的线程池发送心跳,不阻塞业务方法
2. **批量心跳更新**: 如果存储是数据库,可以批量更新多个任务心跳(可选优化)
3. **参数序列化缓存**: 序列化结果在任务开始时计算一次,存储后不再重复
4. **快速失败**: 如果参数明显过大(>10KB),跳过序列化尝试
5. **线程池配置**: 心跳线程池使用`ScheduledThreadPoolExecutor`,核心线程数=max(10, CPU核心数)

**Alternatives Considered**:

- **使用JDK动态代理**: 需要接口,限制太大
- **使用字节码增强(如ByteBuddy)**: 过于复杂,违反简约原则
- **同步心跳**: 会增加5-10ms延迟,不可接受

**Benchmark Target**:

- AOP拦截开销<5ms (P95)
- 参数序列化<10ms (对于<10KB对象,使用Hutool JSONUtil)

---

### 3. Hutool JSON序列化性能与限制

**Decision**: 使用Hutool的`JSONUtil.toJsonStr()`进行序列化,`JSONUtil.parse()`反序列化

**Rationale**:

- Hutool底层使用FastJSON(或Jackson,视版本而定),性能接近原生库
- API更简洁:`JSONUtil.toJsonStr(obj)` vs `objectMapper.writeValueAsString(obj)`
- 自动处理常见Java类型:LocalDateTime、Date、Enum等
- 字节大小计算:`jsonStr.getBytes(StandardCharsets.UTF_8).length`

**Limitations**:

1. **循环引用**: 会导致StackOverflowError,需要在序列化决策时try-catch
2. **自定义类型**: 需要有默认构造函数和getter/setter
3. **大对象性能**: 超过1MB的对象序列化可能超过50ms

**Alternatives Considered**:

- **直接使用Jackson ObjectMapper**: 违反Hutool优先原则
- **使用Java原生序列化**: 序列化结果不可读,体积更大
- **使用Protobuf/Avro**: 过度工程,增加依赖复杂度

**Fallback Strategy**:
如果Hutool序列化失败(异常或超时),标记任务为不可恢复并记录原因。

---

### 4. MyBatis-Plus vs JPA选择

**Decision**: 使用MyBatis-Plus进行数据库持久化

**Rationale**:

- MyBatis-Plus提供CRUD基础方法,减少SQL编写
- 灵活的`QueryWrapper`支持复杂查询(如按状态、时间范围查询任务)
- 性能优于JPA(无动态代理,SQL明确)
- 更符合中国开发者习惯

**Alternatives Considered**:

- **Spring Data JPA**:
    - 优点: Spring生态集成好,支持声明式查询
    - 缺点: 性能较差,N+1查询问题,复杂查询需要写JPQL
    - 排除原因: 性能考虑+MyBatis在中国生态更成熟

- **纯JDBC或JdbcTemplate**:
    - 优点: 性能最高
    - 缺点: 需要手写大量SQL,维护成本高
    - 排除原因: 违反简约原则,MyBatis-Plus已提供足够抽象

**Schema Design Considerations**:

- 使用自增主键`id`(BIGINT)
- `task_id`使用VARCHAR(64)存储UUID,加唯一索引
- `status`字段加索引,支持快速查询RUNNING/RETRY状态的任务
- `last_heartbeat_time`加索引,支持健康检查快速扫描

---

### 5. Redis存储设计

**Decision**: Redis存储使用String类型存储JSON,Hash类型存储心跳信息

**Rationale**:

- **任务日志**: `task:execution:{taskId}` -> JSON字符串(完整TaskLog对象)
- **心跳信息**: `task:heartbeat:{taskId}` -> Hash (lastHeartbeatTime, status, count)
- **活跃任务集合**: `task:running` -> Set (存储所有RUNNING状态的taskId,方便健康检查扫描)

**TTL Strategy**:

- 任务日志: 默认保留7天(可配置)
- 心跳信息: 任务完成后立即删除
- 活跃任务集合: 无TTL,任务完成时从Set中移除

**Advantages**:

- Hash类型减少序列化开销(心跳更新频繁)
- Set集合O(n)扫描所有活跃任务,支持健康检查
- Redis原生支持并发,无需额外锁

**Alternatives Considered**:

- **全部使用String类型**: 心跳更新需要序列化整个对象,浪费CPU
- **使用Redis Streams**: 过度设计,任务监控不需要消息队列特性
- **使用Redisson**: 额外依赖,违反简约原则,Spring Data Redis足够

---

### 6. 内存存储线程安全设计

**Decision**: 使用`ConcurrentHashMap`存储任务日志和心跳,使用`CopyOnWriteArraySet`存储活跃任务ID

**Rationale**:

- `ConcurrentHashMap`: 高并发读写,无需外部同步
- `CopyOnWriteArraySet`: 活跃任务集合读多写少(仅任务开始/结束时修改)
- 所有心跳更新通过`computeIfPresent`保证原子性

**Thread Safety Patterns**:

```java
// 更新心跳示例
heartbeatMap.computeIfPresent(taskId, (k, v) ->{
        v.

setLastHeartbeatTime(LocalDateTime.now());
        v.

incrementCount();
    return v;
});
```

**Alternatives Considered**:

- **使用synchronized**: 性能差,锁粒度大
- **使用ReentrantReadWriteLock**: 代码复杂,ConcurrentHashMap已足够高效

---

### 7. 心跳线程池设计

**Decision**: 使用`ScheduledThreadPoolExecutor`,核心线程数=`max(10, Runtime.getRuntime().availableProcessors())`

**Rationale**:

- `ScheduledThreadPoolExecutor`支持固定延迟调度(每N秒一次心跳)
- 核心线程常驻,避免频繁创建销毁线程
- 线程数上限为核心线程数的2倍,防止任务堆积

**Scheduling Strategy**:

- 每个任务启动时提交一个ScheduledFuture
- 固定延迟调度:`scheduleWithFixedDelay(heartbeatTask, 0, interval, TimeUnit.SECONDS)`
- 任务完成时取消Future

**Graceful Shutdown**:

- 实现`DisposableBean`,在`destroy()`方法中调用`threadPool.shutdown()`
- 等待最多30秒让心跳任务完成
- 超时后强制`shutdownNow()`

**Alternatives Considered**:

- **使用@Scheduled注解**: 无法动态管理任务,不灵活
- **使用Timer**: 单线程,性能差,已废弃
- **使用Quartz**: 过度工程,增加依赖

---

### 8. 异常分类与重试策略

**Decision**: 使用异常类型匹配+配置驱动的分类器

**Rationale**:

- **Retryable异常**: SQLException、SocketTimeoutException、DataAccessException(数据库/网络瞬时故障)
- **Ignorable异常**: IllegalArgumentException、ValidationException(业务校验失败)
- **Fatal异常**: 其他所有异常(需要告警和人工介入)

**Retry Backoff Strategy**:

- 基础间隔: 60秒
- 指数退避: 第1次60s,第2次120s,第3次240s
- 最大重试次数: 3次(全局配置)
- 每次重试在日志中记录重试原因

**Implementation**:

```java
// 使用Hutool的ClassUtil检查异常类型
boolean retryable = retryableExceptions.stream()
                .anyMatch(exClass -> ClassUtil.isAssignable(exClass, throwable.getClass()));
```

**Alternatives Considered**:

- **使用Spring Retry**: 额外依赖,功能过于复杂
- **固定间隔重试**: 可能导致雪崩效应(所有任务同时重试)

---

### 9. SpEL表达式解析

**Decision**: 使用Spring Expression Language解析`bizKey`表达式

**Rationale**:

- SpEL是Spring标准,无需额外依赖
- 支持强大的表达式:`#orderId`、`#order.id`、`#order.items.size()`
- 与Spring生态集成好

**Implementation**:

```java
StandardEvaluationContext context = new StandardEvaluationContext();
context.

setVariable("arg0",args[0]);
context.

setVariable("arg1",args[1]);
// 支持参数名(需要-parameters编译参数)
for(
int i = 0;
i<parameterNames.length;i++){
        context.

setVariable(parameterNames[i], args[i]);
}
Expression expression = parser.parseExpression(spelExpression);
return expression.

getValue(context, String .class);
```

**Fallback**:
如果SpEL解析失败,记录警告日志,bizKey设为null(任务仍可正常监控)。

**Alternatives Considered**:

- **简单字符串替换**: 功能太弱,无法处理嵌套属性
- **使用OGNL**: 非Spring生态,增加依赖

---

### 10. 数据库表设计最佳实践

**Decision**: 单表存储所有任务执行记录,使用索引优化查询

**Schema**:

```sql
CREATE TABLE task_execution_log
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id             VARCHAR(64)  NOT NULL UNIQUE,
    task_name           VARCHAR(200) NOT NULL,
    biz_key             VARCHAR(200),
    status              VARCHAR(20)  NOT NULL,
    start_time          DATETIME     NOT NULL,
    end_time            DATETIME,
    last_heartbeat_time DATETIME     NOT NULL,
    input_params        TEXT,
    error_message       TEXT,
    recoverable         TINYINT(1),
    retry_count         INT DEFAULT 0,
    host_ip             VARCHAR(50),
    INDEX               idx_status_heartbeat (status, last_heartbeat_time),
    INDEX               idx_task_name (task_name),
    INDEX               idx_next_retry_time (status, next_retry_time)
);
```

**Index Justification**:

1. `idx_status_heartbeat`: 健康检查查询`WHERE status='RUNNING' AND last_heartbeat_time < ?`
2. `idx_task_name`: 按任务名称分组统计
3. `idx_next_retry_time`: 重试任务扫描`WHERE status='RETRY' AND next_retry_time <= NOW()`

**Partitioning Consideration**:
对于高吞吐场景(>100万记录/天),考虑按月分区:

```sql
PARTITION
BY RANGE (YEAR(start_time)*100 + MONTH(start_time))
```

**Alternatives Considered**:

- **任务表+心跳表分离**: 增加JOIN复杂度,心跳更新频繁会产生大量记录
- **使用NoSQL(如MongoDB)**: 增加技术栈复杂度,关系型数据库已满足需求

---

### 11. 告警机制设计

**Decision**: 使用钉钉Webhook作为默认告警渠道,提供AlertService接口支持扩展

**Rationale**:

- 钉钉在中国企业广泛使用
- Webhook集成简单,使用Hutool的`HttpUtil.post()`发送JSON即可
- 接口设计支持后续扩展(邮件、企业微信、短信)

**Alert Trigger Scenarios**:

1. 任务失败(非ignorable异常)
2. 心跳超时
3. 任务中断且不可恢复
4. 重试次数达到上限

**Alert Message Format**:

```markdown
## 任务监控告警

- **任务名称**: 每日订单结算
- **业务标识**: order-2023-12-17
- **状态**: 失败
- **错误信息**: SQLException: Connection timeout
- **主机**: 192.168.1.10
- **时间**: 2025-12-17 10:30:00
- **任务ID**: abc-def-123-456
```

**Rate Limiting**:
同一taskId的告警在5分钟内最多发送一次,防止告警轰炸。

**Alternatives Considered**:

- **使用Spring Events**: 告警是外部集成,不适合用领域事件
- **集成第三方监控平台(如Prometheus)**: 增加复杂度,不符合Starter简约定位

---

### 12. 测试策略

**Decision**: 三层测试策略:单元测试(领域层)、集成测试(基础设施层)、契约测试(注解API)

**Test Coverage Target**:

- 领域层: ≥85% (核心业务逻辑)
- 应用层: ≥75%
- 基础设施层: ≥60% (集成测试为主)

**Integration Test Tools**:

- **Testcontainers**: 启动真实MySQL和Redis容器进行集成测试
- **Spring Boot Test**: 启动完整ApplicationContext测试自动配置
- **Mockito**: Mock外部依赖(如AlertService)

**Contract Test**:
验证@TaskMonitor注解的所有属性组合是否正确工作:

- serializeParams=null/true/false
- recoveryStrategy=AUTO/ALWAYS/NEVER/CUSTOM
- retryableExceptions配置
- heartbeatInterval配置

**Alternatives Considered**:

- **手动启动外部依赖**: 维护成本高,CI/CD难集成
- **使用H2内存数据库**: 与生产环境MySQL行为不一致,可能遗漏兼容性问题

---

## 技术决策矩阵

| 技术选型          | 决策                          | 理由              | 风险        |
|---------------|-----------------------------|-----------------|-----------|
| Spring Boot版本 | 2.7.x                       | LTS版本,兼容性好      | 低         |
| JSON库         | Hutool JSONUtil             | 宪章要求,性能足够       | 中(循环引用问题) |
| ORM           | MyBatis-Plus                | 灵活、性能好、生态成熟     | 低         |
| Redis客户端      | Spring Data Redis           | Spring生态,无需额外依赖 | 低         |
| 线程池           | ScheduledThreadPoolExecutor | JDK内置,稳定高效      | 低         |
| 告警渠道          | 钉钉Webhook                   | 中国企业主流          | 低(提供扩展接口) |

---

## 未解决问题与假设

### 假设

1. 用户的Spring Boot应用已启用AOP(`spring-boot-starter-aop`依赖存在)
2. 数据库连接池由用户应用提供(HikariCP等)
3. Redis连接配置由用户通过`spring.redis.*`属性提供
4. 用户方法参数对象有合理的toString()或可序列化为JSON

### 待澄清(如果后续发现需要)

- 是否需要支持分布式锁(防止多实例同时恢复同一任务)?
    - **当前决策**: 通过host_ip字段过滤,仅恢复本机中断任务,无需分布式锁

- 是否需要支持任务优先级?
    - **当前决策**: 不支持,所有任务平等,按FIFO重试

- 是否需要支持任务依赖关系?
    - **当前决策**: 不支持,超出Starter范围,属于工作流引擎功能

---

## 性能预估

基于research结论的性能预估:

| 指标            | 目标           | 预估实际值     |
|---------------|--------------|-----------|
| AOP拦截开销       | <5ms (P95)   | 2-3ms     |
| 参数序列化(<10KB)  | <10ms        | 5-8ms     |
| 心跳更新延迟        | <100ms (P95) | 50-80ms   |
| 数据库批量查询(100条) | <200ms       | 100-150ms |
| Redis心跳更新     | <10ms (P95)  | 3-5ms     |
| 内存心跳更新        | <1ms (P99)   | <1ms      |

---

## 下一步

Phase 1将基于此研究成果创建:

1. **data-model.md**: 详细的领域模型定义(聚合、实体、值对象)
2. **contracts/**: API契约(注解属性、配置属性schema)
3. **quickstart.md**: 5分钟快速开始指南

**Phase 0 Complete** ✅
