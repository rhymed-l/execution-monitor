# Implementation Plan: Spring Boot Task Monitor Starter

**Branch**: `001-task-monitor-starter` | **Date**: 2025-12-17 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-task-monitor-starter/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the
execution workflow.

## Summary

创建一个Spring Boot
Starter,通过注解实现任务监控功能,包含可插拔心跳机制、智能参数序列化和自动恢复能力。核心价值是零侵入式监控长时间运行任务,自动检测任务卡死(
心跳超时),并在系统重启后自动恢复中断的可恢复任务。

技术方案采用DDD分层架构,使用AOP切面拦截@TaskMonitor注解的方法,通过Hutool工具库处理JSON序列化和工具操作,支持内存/Redis/数据库三种可插拔存储后端。

## Technical Context

**Language/Version**: Java 8+ (兼容Java 8-17,以确保广泛兼容性)
**Primary Dependencies**:

- Spring Boot 2.x (自动配置框架)
- Spring AOP (方法拦截)
- Hutool 5.8.x (工具库:JSON、字符串、集合、反射等)
- MyBatis-Plus 3.x (数据库持久化,可选)
- Spring Data Redis (Redis存储支持,可选)

**Storage**:

- 主存储:MySQL 5.7+ / PostgreSQL 10+ (task_execution_log表)
- 可选存储:Redis 5.0+ (心跳和任务状态缓存)
- 内存存储:ConcurrentHashMap (开发/测试环境)

**Testing**: JUnit 5 + Mockito + Spring Boot Test + Testcontainers(集成测试)

**Target Platform**: JVM环境,支持Linux/Windows/macOS服务器部署

**Project Type**: 单一Spring Boot Starter库项目

**Performance Goals**:

- 支持1000+并发监控任务
- AOP拦截开销<5ms (P95)
- 心跳更新延迟<100ms (P95)
- 参数序列化<10ms (对于<10KB对象)

**Constraints**:

- 序列化参数大小限制10KB(可配置)
- 单个任务方法执行时间建议>30秒(否则心跳机制价值有限)
- 内存存储模式下重启会丢失所有任务历史
- 分布式环境下需使用Redis或数据库存储

**Scale/Scope**:

- 单实例支持10,000个任务执行记录查询
- 支持100+并发长任务监控
- 心跳存储支持1000+活跃任务

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### 一、领域驱动设计(DDD)优先 ✅ 通过

**检查项**:

- ✅ 项目结构采用DDD分层:`domain/`(领域层)、`application/`(应用层)、`infrastructure/`(基础设施层)、`interfaces/`(接口层)
- ✅ 领域对象无Spring依赖:TaskExecution、RecoveryPolicy等聚合根不依赖@Component
- ✅ 仓储接口在领域层定义,实现在infrastructure层
- ✅ 定义领域事件:TaskStartedEvent、TaskCompletedEvent等7个事件
- ✅ 聚合根维护不变性:状态转换合法性、心跳时间单调性等

### 二、Hutool工具库优先 ✅ 通过

**检查项**:

- ✅ JSON序列化使用`JSONUtil`(不使用Jackson ObjectMapper直接)
- ✅ 字符串操作使用`StrUtil`
- ✅ 集合操作使用`CollUtil`
- ✅ 日期时间使用`DateUtil`、`LocalDateTimeUtil`
- ✅ 反射操作使用`ReflectUtil`、`ClassUtil`
- ✅ UUID生成使用`IdUtil`

**例外说明**: Spring AOP和@Scheduled必须使用,因为是Spring Boot Starter的核心集成点

### 三、测试优先(TDD方法论) ✅ 通过

**检查项**:

- ✅ 计划包含测试阶段:单元测试、集成测试、契约测试
- ✅ 领域逻辑测试覆盖目标≥80%
- ✅ 关键场景集成测试:心跳超时检测、任务恢复、参数序列化

### 四、可插拔架构 ✅ 通过

**检查项**:

- ✅ 存储后端接口抽象:`TaskExecutionRepository`、`HeartbeatStorage`
- ✅ 多种实现:MemoryStorage、RedisStorage、DatabaseStorage
- ✅ 策略模式:`SerializationStrategy`、`RecoveryStrategy`
- ✅ 配置驱动切换:通过`task-monitor.storage.type`配置

### 五、可观测性优先 ✅ 通过

**检查项**:

- ✅ 结构化日志:包含taskId、taskName、bizKey、hostIp
- ✅ 日志级别明确:DEBUG(参数)、INFO(状态)、WARN(可恢复异常)、ERROR(失败)
- ✅ 关键指标:任务执行次数、成功率、心跳超时数、恢复成功率

### 六、版本化与破坏性变更管理 ✅ 通过

**检查项**:

- ✅ 初始版本1.0.0
- ✅ 公开API清晰:@TaskMonitor、@TaskRecoveryHandler、配置属性
- ✅ 版本变更规则明确

### 七、简约设计(YAGNI原则) ✅ 通过

**检查项**:

- ✅ Maven依赖<15个(预估:Spring Boot、AOP、Hutool、MyBatis-Plus、Redis,共约8-10个)
- ✅ 单类<500行、单方法<50行目标设定
- ✅ 无过度设计:只实现spec.md中定义的7个用户故事

**Gate Result**: ✅ **PASS** - 所有宪章原则检查通过,可继续Phase 0研究

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
task-monitor-spring-boot-starter/
├── src/main/java/com/taskmonitor/
│   ├── domain/                          # 领域层(DDD)
│   │   ├── model/                       # 聚合根、实体、值对象
│   │   │   ├── TaskExecution.java       # 任务执行聚合根
│   │   │   ├── HeartbeatRecord.java     # 心跳记录实体
│   │   │   ├── RecoveryPolicy.java      # 恢复策略聚合根
│   │   │   ├── TaskId.java              # 任务ID值对象
│   │   │   ├── TaskName.java            # 任务名称值对象
│   │   │   ├── SerializedParams.java    # 序列化参数值对象
│   │   │   ├── RetryConfig.java         # 重试配置值对象
│   │   │   └── ExceptionClassification.java  # 异常分类值对象
│   │   ├── service/                     # 领域服务
│   │   │   ├── TaskExecutionDomainService.java    # 任务执行领域服务
│   │   │   ├── SerializationDecisionService.java  # 序列化决策服务
│   │   │   └── RecoveryDecisionService.java       # 恢复决策服务
│   │   ├── repository/                  # 仓储接口(仅接口)
│   │   │   ├── TaskExecutionRepository.java
│   │   │   └── HeartbeatStorage.java
│   │   ├── event/                       # 领域事件
│   │   │   ├── TaskStartedEvent.java
│   │   │   ├── TaskCompletedEvent.java
│   │   │   ├── TaskFailedEvent.java
│   │   │   ├── TaskInterruptedEvent.java
│   │   │   ├── HeartbeatTimeoutEvent.java
│   │   │   ├── TaskRetryScheduledEvent.java
│   │   │   └── TaskRecoveredEvent.java
│   │   └── enums/                       # 枚举
│   │       ├── TaskStatus.java          # 任务状态枚举
│   │       └── RecoveryStrategy.java    # 恢复策略枚举
│   │
│   ├── application/                     # 应用层
│   │   ├── service/                     # 应用服务
│   │   │   ├── TaskMonitorService.java        # 任务监控应用服务
│   │   │   ├── HeartbeatManagementService.java # 心跳管理服务
│   │   │   ├── TaskRecoveryService.java       # 任务恢复服务
│   │   │   └── TaskRetryExecutor.java         # 任务重试执行器
│   │   └── dto/                         # 数据传输对象
│   │       ├── TaskLogDTO.java
│   │       └── HeartbeatInfoDTO.java
│   │
│   ├── infrastructure/                  # 基础设施层
│   │   ├── persistence/                 # 持久化实现
│   │   │   ├── memory/
│   │   │   │   ├── MemoryTaskExecutionRepository.java
│   │   │   │   └── MemoryHeartbeatStorage.java
│   │   │   ├── redis/
│   │   │   │   ├── RedisTaskExecutionRepository.java
│   │   │   │   └── RedisHeartbeatStorage.java
│   │   │   └── database/
│   │   │       ├── DatabaseTaskExecutionRepository.java
│   │   │       ├── DatabaseHeartbeatStorage.java
│   │   │       ├── mapper/
│   │   │       │   └── TaskLogMapper.java     # MyBatis-Plus Mapper
│   │   │       └── entity/
│   │   │           └── TaskLogPO.java         # 数据库PO对象
│   │   ├── aop/                         # AOP切面
│   │   │   └── TaskMonitorAspect.java
│   │   ├── scheduler/                   # 调度任务
│   │   │   ├── HeartbeatScheduler.java        # 心跳定时任务
│   │   │   ├── HealthCheckScheduler.java      # 健康检查定时任务
│   │   │   └── RetryTaskScheduler.java        # 重试任务定时任务
│   │   ├── alert/                       # 告警实现
│   │   │   ├── AlertService.java
│   │   │   └── dingtalk/
│   │   │       └── DingTalkAlertImpl.java
│   │   └── util/                        # 基础设施工具
│   │       ├── SpelExpressionParser.java
│   │       ├── HostInfoUtil.java
│   │       └── ReflectionInvoker.java
│   │
│   ├── interfaces/                      # 接口层
│   │   ├── annotation/                  # 注解
│   │   │   ├── TaskMonitor.java
│   │   │   ├── TaskRecoveryHandler.java
│   │   │   └── EnableTaskMonitor.java
│   │   ├── config/                      # 配置
│   │   │   ├── TaskMonitorAutoConfiguration.java
│   │   │   ├── TaskMonitorProperties.java
│   │   │   └── StorageConfiguration.java
│   │   └── controller/                  # REST控制器(可选)
│   │       └── TaskMonitorController.java     # 任务查询接口
│   │
│   └── common/                          # 通用组件
│       ├── exception/                   # 异常定义
│       │   ├── TaskMonitorException.java
│       │   └── SerializationException.java
│       └── constant/                    # 常量
│           └── TaskMonitorConstants.java
│
├── src/main/resources/
│   ├── META-INF/
│   │   └── spring.factories             # Spring Boot自动配置
│   └── db/
│       └── migration/
│           └── V1__create_task_log_table.sql
│
├── src/test/java/com/taskmonitor/
│   ├── domain/                          # 领域层单元测试
│   │   ├── TaskExecutionTest.java
│   │   └── RecoveryPolicyTest.java
│   ├── application/                     # 应用层单元测试
│   │   └── TaskMonitorServiceTest.java
│   ├── infrastructure/                  # 基础设施集成测试
│   │   ├── persistence/
│   │   │   ├── MemoryRepositoryTest.java
│   │   │   ├── RedisRepositoryTest.java (Testcontainers)
│   │   │   └── DatabaseRepositoryTest.java (Testcontainers)
│   │   └── aop/
│   │       └── TaskMonitorAspectIntegrationTest.java
│   └── contract/                        # 契约测试
│       └── TaskMonitorAnnotationContractTest.java
│
└── pom.xml
```

**Structure Decision**:

选择单一库项目结构,采用严格的DDD四层架构:

1. **Domain层(领域层)**: 包含业务核心逻辑,无任何技术依赖(除Java标准库)
    - 定义TaskExecution和RecoveryPolicy两个聚合
    - 值对象确保类型安全和不变性
    - 仓储接口定义但不实现

2. **Application层(应用层)**: 编排领域对象完成用户故事
    - TaskMonitorService协调AOP拦截后的任务生命周期
    - 任务恢复和重试逻辑编排

3. **Infrastructure层(基础设施层)**: 技术实现
    - 三种存储实现:内存、Redis、数据库
    - AOP切面实现@TaskMonitor拦截
    - 定时调度器实现心跳和健康检查

4. **Interfaces层(接口层)**: 对外暴露
    - 注解API:@TaskMonitor、@EnableTaskMonitor
    - 自动配置:TaskMonitorAutoConfiguration
    - 配置属性:TaskMonitorProperties

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无宪章违规,无需复杂度豁免。
