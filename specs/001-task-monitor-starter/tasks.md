# Tasks: Spring Boot Task Monitor Starter

**Input**: Design documents from `/specs/001-task-monitor-starter/`
**Prerequisites**: plan.md, spec.md, data-model.md, contracts/, research.md, quickstart.md

**Tests**: TDD方法论,测试先行 - 所有领域逻辑和关键集成点必须先编写测试

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

**DDD分层结构**:

- **Domain层**: `src/main/java/com/taskmonitor/domain/`
- **Application层**: `src/main/java/com/taskmonitor/application/`
- **Infrastructure层**: `src/main/java/com/taskmonitor/infrastructure/`
- **Interfaces层**: `src/main/java/com/taskmonitor/interfaces/`
- **测试**: `src/test/java/com/taskmonitor/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create Maven project with pom.xml (groupId: com.taskmonitor, artifactId: task-monitor-spring-boot-starter)
- [ ] T002 [P] Add Spring Boot dependencies to pom.xml (spring-boot-starter 2.7.x, spring-boot-starter-aop)
- [ ] T003 [P] Add Hutool dependency to pom.xml (hutool-all 5.8.x)
- [ ] T004 [P] Add MyBatis-Plus dependency to pom.xml (mybatis-plus-boot-starter 3.5.x)
- [ ] T005 [P] Add Spring Data Redis dependency to pom.xml (spring-boot-starter-data-redis, optional)
- [ ] T006 [P] Add test dependencies to pom.xml (junit-jupiter, mockito, spring-boot-starter-test, testcontainers)
- [ ] T007 Create DDD package structure: domain/, application/, infrastructure/, interfaces/, common/
- [ ] T008 [P] Create src/main/resources/META-INF/spring.factories for auto-configuration
- [ ] T009 [P] Create database migration script src/main/resources/db/migration/V1__create_task_log_table.sql

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Domain Layer - Enums and Value Objects

- [ ] T010 [P] Create TaskStatus enum in src/main/java/com/taskmonitor/domain/enums/TaskStatus.java
- [ ] T011 [P] Create RecoveryStrategy enum in src/main/java/com/taskmonitor/domain/enums/RecoveryStrategy.java
- [ ] T012 [P] Create TaskId value object in src/main/java/com/taskmonitor/domain/model/TaskId.java
- [ ] T013 [P] Create TaskName value object in src/main/java/com/taskmonitor/domain/model/TaskName.java
- [ ] T014 [P] Create BizKey value object in src/main/java/com/taskmonitor/domain/model/BizKey.java
- [ ] T015 [P] Create SerializedParams value object in src/main/java/com/taskmonitor/domain/model/SerializedParams.java
- [ ] T016 [P] Create ErrorInfo value object in src/main/java/com/taskmonitor/domain/model/ErrorInfo.java
- [ ] T017 [P] Create RetryConfig value object in src/main/java/com/taskmonitor/domain/model/RetryConfig.java
- [ ] T018 [P] Create ExceptionClassification value object in
  src/main/java/com/taskmonitor/domain/model/ExceptionClassification.java

### Domain Layer - Entities and Aggregates

- [ ] T019 Create HeartbeatRecord entity in src/main/java/com/taskmonitor/domain/model/HeartbeatRecord.java
- [ ] T020 Create TaskExecution aggregate root in src/main/java/com/taskmonitor/domain/model/TaskExecution.java
- [ ] T021 Create RecoveryPolicy aggregate root in src/main/java/com/taskmonitor/domain/model/RecoveryPolicy.java

### Domain Layer - Repository Interfaces

- [ ] T022 [P] Create TaskExecutionRepository interface in
  src/main/java/com/taskmonitor/domain/repository/TaskExecutionRepository.java
- [ ] T023 [P] Create HeartbeatStorage interface in
  src/main/java/com/taskmonitor/domain/repository/HeartbeatStorage.java

### Domain Layer - Domain Events

- [ ] T024 [P] Create DomainEvent base class in src/main/java/com/taskmonitor/domain/event/DomainEvent.java
- [ ] T025 [P] Create TaskStartedEvent in src/main/java/com/taskmonitor/domain/event/TaskStartedEvent.java
- [ ] T026 [P] Create TaskCompletedEvent in src/main/java/com/taskmonitor/domain/event/TaskCompletedEvent.java
- [ ] T027 [P] Create TaskFailedEvent in src/main/java/com/taskmonitor/domain/event/TaskFailedEvent.java
- [ ] T028 [P] Create TaskInterruptedEvent in src/main/java/com/taskmonitor/domain/event/TaskInterruptedEvent.java
- [ ] T029 [P] Create HeartbeatTimeoutEvent in src/main/java/com/taskmonitor/domain/event/HeartbeatTimeoutEvent.java
- [ ] T030 [P] Create TaskRetryScheduledEvent in src/main/java/com/taskmonitor/domain/event/TaskRetryScheduledEvent.java
- [ ] T031 [P] Create TaskRecoveredEvent in src/main/java/com/taskmonitor/domain/event/TaskRecoveredEvent.java

### Interfaces Layer - Configuration

- [ ] T032 Create TaskMonitorProperties in src/main/java/com/taskmonitor/interfaces/config/TaskMonitorProperties.java
- [ ] T033 Create StorageConfiguration in src/main/java/com/taskmonitor/interfaces/config/StorageConfiguration.java
- [ ] T034 Create TaskMonitorAutoConfiguration in
  src/main/java/com/taskmonitor/interfaces/config/TaskMonitorAutoConfiguration.java

### Common Layer

- [ ] T035 [P] Create TaskMonitorException in src/main/java/com/taskmonitor/common/exception/TaskMonitorException.java
- [ ] T036 [P] Create SerializationException in
  src/main/java/com/taskmonitor/common/exception/SerializationException.java
- [ ] T037 [P] Create TaskMonitorConstants in src/main/java/com/taskmonitor/common/constant/TaskMonitorConstants.java

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Basic Task Monitoring with Annotation (Priority: P1) 🎯 MVP

**Goal**: Enable developers to monitor task execution by adding @TaskMonitor annotation

**Independent Test**: Add @TaskMonitor to a method, execute it, verify task record is created with RUNNING→SUCCESS
status transitions

### Tests for User Story 1 (TDD - Write FIRST, ensure FAIL)

- [ ] T038 [P] [US1] Unit test for TaskExecution state transitions in
  src/test/java/com/taskmonitor/domain/TaskExecutionTest.java
- [ ] T039 [P] [US1] Unit test for TaskExecutionDomainService in
  src/test/java/com/taskmonitor/domain/TaskExecutionDomainServiceTest.java
- [ ] T040 [P] [US1] Integration test for TaskMonitorAspect in
  src/test/java/com/taskmonitor/infrastructure/TaskMonitorAspectIntegrationTest.java
- [ ] T041 [P] [US1] Contract test for @TaskMonitor annotation basic usage in
  src/test/java/com/taskmonitor/contract/TaskMonitorAnnotationContractTest.java

### Implementation for User Story 1

- [ ] T042 [P] [US1] Create @TaskMonitor annotation in
  src/main/java/com/taskmonitor/interfaces/annotation/TaskMonitor.java
- [ ] T043 [P] [US1] Create @EnableTaskMonitor annotation in
  src/main/java/com/taskmonitor/interfaces/annotation/EnableTaskMonitor.java
- [ ] T044 [US1] Implement TaskExecutionDomainService in
  src/main/java/com/taskmonitor/domain/service/TaskExecutionDomainService.java (depends on T020)
- [ ] T045 [US1] Implement TaskMonitorService (application service) in
  src/main/java/com/taskmonitor/application/service/TaskMonitorService.java
- [ ] T046 [US1] Implement TaskMonitorAspect (AOP interceptor) in
  src/main/java/com/taskmonitor/infrastructure/aop/TaskMonitorAspect.java
- [ ] T047 [US1] Implement MemoryTaskExecutionRepository in
  src/main/java/com/taskmonitor/infrastructure/persistence/memory/MemoryTaskExecutionRepository.java
- [ ] T048 [US1] Implement SpelExpressionParser in
  src/main/java/com/taskmonitor/infrastructure/util/SpelExpressionParser.java
- [ ] T049 [US1] Implement HostInfoUtil in src/main/java/com/taskmonitor/infrastructure/util/HostInfoUtil.java
- [ ] T050 [US1] Wire components in TaskMonitorAutoConfiguration

**Checkpoint**: User Story 1完成 - 可以通过注解监控任务,记录状态转换

---

## Phase 4: User Story 2 - Heartbeat-based Task Health Monitoring (Priority: P1)

**Goal**: Automatically detect frozen/crashed tasks through heartbeat timeout mechanism

**Independent Test**: Start a monitored task, simulate freeze (infinite loop), verify system detects heartbeat timeout

### Tests for User Story 2 (TDD - Write FIRST)

- [ ] T051 [P] [US2] Unit test for HeartbeatRecord in src/test/java/com/taskmonitor/domain/HeartbeatRecordTest.java
- [ ] T052 [P] [US2] Integration test for HeartbeatManagementService in
  src/test/java/com/taskmonitor/application/HeartbeatManagementServiceTest.java
- [ ] T053 [P] [US2] Integration test for HealthCheckScheduler in
  src/test/java/com/taskmonitor/infrastructure/HealthCheckSchedulerTest.java

### Implementation for User Story 2

- [ ] T054 [P] [US2] Implement MemoryHeartbeatStorage in
  src/main/java/com/taskmonitor/infrastructure/persistence/memory/MemoryHeartbeatStorage.java
- [ ] T055 [US2] Implement HeartbeatManagementService in
  src/main/java/com/taskmonitor/application/service/HeartbeatManagementService.java
- [ ] T056 [US2] Implement HeartbeatScheduler in
  src/main/java/com/taskmonitor/infrastructure/scheduler/HeartbeatScheduler.java
- [ ] T057 [US2] Implement HealthCheckScheduler in
  src/main/java/com/taskmonitor/infrastructure/scheduler/HealthCheckScheduler.java
- [ ] T058 [US2] Integrate heartbeat logic into TaskMonitorAspect (update T046)
- [ ] T059 [US2] Implement AlertService interface in
  src/main/java/com/taskmonitor/infrastructure/alert/AlertService.java
- [ ] T060 [US2] Implement DingTalkAlertImpl in
  src/main/java/com/taskmonitor/infrastructure/alert/dingtalk/DingTalkAlertImpl.java

**Checkpoint**: User Story 2完成 - 心跳机制检测任务卡死

---

## Phase 5: User Story 3 - Intelligent Parameter Serialization (Priority: P2)

**Goal**: Automatically determine which parameters can be safely serialized and stored

**Independent Test**: Monitor methods with various parameter types, verify correct serialization decisions

### Tests for User Story 3 (TDD - Write FIRST)

- [ ] T061 [P] [US3] Unit test for SerializationDecisionService in
  src/test/java/com/taskmonitor/domain/SerializationDecisionServiceTest.java
- [ ] T062 [P] [US3] Integration test for parameter serialization scenarios in
  src/test/java/com/taskmonitor/infrastructure/SerializationIntegrationTest.java

### Implementation for User Story 3

- [ ] T063 [US3] Implement SerializationDecisionService in
  src/main/java/com/taskmonitor/domain/service/SerializationDecisionService.java
- [ ] T064 [US3] Integrate serialization logic into TaskMonitorAspect (update T046)
- [ ] T065 [US3] Add parameter size validation in SerializedParams (update T015)

**Checkpoint**: User Story 3完成 - 智能参数序列化判断

---

## Phase 6: User Story 4 - Automatic Task Recovery After Interruption (Priority: P2)

**Goal**: Automatically detect and retry interrupted tasks on application startup

**Independent Test**: Start task, forcibly stop app mid-execution, restart app, verify interrupted task is detected and
retried

### Tests for User Story 4 (TDD - Write FIRST)

- [ ] T066 [P] [US4] Unit test for RecoveryDecisionService in
  src/test/java/com/taskmonitor/domain/RecoveryDecisionServiceTest.java
- [ ] T067 [P] [US4] Integration test for TaskRecoveryService in
  src/test/java/com/taskmonitor/application/TaskRecoveryServiceTest.java
- [ ] T068 [P] [US4] Integration test for TaskRetryExecutor in
  src/test/java/com/taskmonitor/application/TaskRetryExecutorTest.java

### Implementation for User Story 4

- [ ] T069 [US4] Implement RecoveryDecisionService in
  src/main/java/com/taskmonitor/domain/service/RecoveryDecisionService.java
- [ ] T070 [US4] Implement TaskRecoveryService (ApplicationRunner) in
  src/main/java/com/taskmonitor/application/service/TaskRecoveryService.java
- [ ] T071 [US4] Implement TaskRetryExecutor in src/main/java/com/taskmonitor/application/service/TaskRetryExecutor.java
- [ ] T072 [US4] Implement RetryTaskScheduler in
  src/main/java/com/taskmonitor/infrastructure/scheduler/RetryTaskScheduler.java
- [ ] T073 [US4] Implement ReflectionInvoker for method re-invocation in
  src/main/java/com/taskmonitor/infrastructure/util/ReflectionInvoker.java

**Checkpoint**: User Story 4完成 - 任务中断后自动恢复

---

## Phase 7: User Story 5 - Exception-based Retry Control (Priority: P3)

**Goal**: Configure which exception types trigger retries vs immediate failure

**Independent Test**: Configure retryable/ignorable exceptions, trigger tasks with these exceptions, verify correct
behavior

### Tests for User Story 5 (TDD - Write FIRST)

- [ ] T074 [P] [US5] Unit test for ExceptionClassifier in
  src/test/java/com/taskmonitor/infrastructure/ExceptionClassifierTest.java
- [ ] T075 [P] [US5] Integration test for exception-based retry scenarios in
  src/test/java/com/taskmonitor/infrastructure/RetryControlIntegrationTest.java

### Implementation for User Story 5

- [ ] T076 [US5] Implement ExceptionClassifier in
  src/main/java/com/taskmonitor/infrastructure/util/ExceptionClassifier.java
- [ ] T077 [US5] Integrate exception classification into TaskMonitorAspect exception handling (update T046)
- [ ] T078 [US5] Add exponential backoff logic to RetryConfig (update T017)

**Checkpoint**: User Story 5完成 - 异常分类和智能重试

---

## Phase 8: User Story 6 - Custom Recovery Handlers (Priority: P3)

**Goal**: Support custom recovery logic for non-serializable parameters

**Independent Test**: Create task with recoveryStrategy=CUSTOM, implement @TaskRecoveryHandler, interrupt task, verify
custom handler invoked

### Tests for User Story 6 (TDD - Write FIRST)

- [ ] T079 [P] [US6] Contract test for @TaskRecoveryHandler annotation in
  src/test/java/com/taskmonitor/contract/TaskRecoveryHandlerContractTest.java
- [ ] T080 [P] [US6] Integration test for custom recovery handler invocation in
  src/test/java/com/taskmonitor/application/CustomRecoveryHandlerTest.java

### Implementation for User Story 6

- [ ] T081 [US6] Create @TaskRecoveryHandler annotation in
  src/main/java/com/taskmonitor/interfaces/annotation/TaskRecoveryHandler.java
- [ ] T082 [US6] Implement RecoveryHandlerRegistry in
  src/main/java/com/taskmonitor/application/service/RecoveryHandlerRegistry.java
- [ ] T083 [US6] Integrate custom recovery handler logic into TaskRetryExecutor (update T071)
- [ ] T084 [US6] Create TaskLogDTO in src/main/java/com/taskmonitor/application/dto/TaskLogDTO.java

**Checkpoint**: User Story 6完成 - 自定义恢复处理器

---

## Phase 9: User Story 7 - Flexible Storage Backend Configuration (Priority: P3)

**Goal**: Support memory, Redis, and database storage backends

**Independent Test**: Configure each storage type, verify task monitoring works correctly with each backend

### Tests for User Story 7 (TDD - Write FIRST)

- [ ] T085 [P] [US7] Integration test for RedisTaskExecutionRepository (Testcontainers) in
  src/test/java/com/taskmonitor/infrastructure/RedisRepositoryTest.java
- [ ] T086 [P] [US7] Integration test for DatabaseTaskExecutionRepository (Testcontainers) in
  src/test/java/com/taskmonitor/infrastructure/DatabaseRepositoryTest.java
- [ ] T087 [P] [US7] Integration test for storage backend switching in
  src/test/java/com/taskmonitor/infrastructure/StorageBackendTest.java

### Implementation for User Story 7

- [ ] T088 [P] [US7] Create TaskLogPO (persistence object) in
  src/main/java/com/taskmonitor/infrastructure/persistence/database/entity/TaskLogPO.java
- [ ] T089 [P] [US7] Create TaskLogMapper (MyBatis-Plus) in
  src/main/java/com/taskmonitor/infrastructure/persistence/database/mapper/TaskLogMapper.java
- [ ] T090 [US7] Implement DatabaseTaskExecutionRepository in
  src/main/java/com/taskmonitor/infrastructure/persistence/database/DatabaseTaskExecutionRepository.java
- [ ] T091 [US7] Implement DatabaseHeartbeatStorage in
  src/main/java/com/taskmonitor/infrastructure/persistence/database/DatabaseHeartbeatStorage.java
- [ ] T092 [P] [US7] Implement RedisTaskExecutionRepository in
  src/main/java/com/taskmonitor/infrastructure/persistence/redis/RedisTaskExecutionRepository.java
- [ ] T093 [P] [US7] Implement RedisHeartbeatStorage in
  src/main/java/com/taskmonitor/infrastructure/persistence/redis/RedisHeartbeatStorage.java
- [ ] T094 [US7] Update StorageConfiguration to support conditional bean creation (update T033)

**Checkpoint**: User Story 7完成 - 可插拔存储后端

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T095 [P] Add structured logging with SLF4J + Logback configuration in src/main/resources/logback-spring.xml
- [ ] T096 [P] Add JavaDoc for all public APIs (annotations, configuration properties)
- [ ] T097 [P] Create README.md with usage examples and configuration guide
- [ ] T098 [P] Add spring-configuration-metadata.json for IDE autocomplete in
  src/main/resources/META-INF/spring-configuration-metadata.json
- [ ] T099 Code cleanup: Remove unused imports, format code with Google Java Style
- [ ] T100 Performance optimization: Profile AOP overhead, optimize serialization
- [ ] T101 [P] Add additional unit tests to reach ≥80% coverage target in src/test/java/com/taskmonitor/domain/
- [ ] T102 Security hardening: Validate SpEL expressions, sanitize log output
- [ ] T103 Run quickstart.md validation: Follow guide and verify all steps work
- [ ] T104 [P] Create example application in src/test/resources/example-app/ for integration testing

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-9)**: All depend on Foundational phase completion
    - US1 (P1): Can start after Foundational - No dependencies on other stories
    - US2 (P1): Can start after Foundational - Integrates with US1 but independently testable
    - US3 (P2): Can start after Foundational - Enhances US1 but independently testable
    - US4 (P2): Can start after Foundational - Depends on US3 (serialization) for full recovery
    - US5 (P3): Can start after Foundational - Enhances US4 but independently testable
    - US6 (P3): Can start after Foundational - Alternative to US3/US4, independently testable
    - US7 (P3): Can start after Foundational - Infrastructure enhancement, independently testable
- **Polish (Phase 10)**: Depends on all desired user stories being complete

### Story Dependency Graph

```
Foundational Phase (P2)
        ↓
    ┌───┴───┬───────┬───────┬───────┬───────┬───────┐
    ↓       ↓       ↓       ↓       ↓       ↓       ↓
   US1     US2     US3     US4     US5     US6     US7
   (P1)    (P1)    (P2)    (P2)    (P3)    (P3)    (P3)
    ↑       ↑       ↑       ↑
    └───────┴───────┴───────┘ (Minimal integration, mostly independent)
```

### Within Each User Story

- Tests MUST be written and FAIL before implementation (TDD)
- Domain services before application services
- Application services before infrastructure
- Core implementation before integration with other stories

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel (T002-T006)
- All Foundational domain layer tasks marked [P] can run in parallel (T010-T018, T022-T031, T035-T037)
- Once Foundational phase completes, **all 7 user stories can start in parallel** (if team capacity allows)
- Within each story, tests marked [P] can run in parallel
- Within each story, independent implementation tasks marked [P] can run in parallel

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together (TDD):
Task T038: "Unit test for TaskExecution state transitions"
Task T039: "Unit test for TaskExecutionDomainService"
Task T040: "Integration test for TaskMonitorAspect"
Task T041: "Contract test for @TaskMonitor annotation"

# After tests fail, launch independent implementation tasks together:
Task T042: "Create @TaskMonitor annotation"
Task T043: "Create @EnableTaskMonitor annotation"
Task T047: "Implement MemoryTaskExecutionRepository"
Task T048: "Implement SpelExpressionParser"
Task T049: "Implement HostInfoUtil"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2 Only)

1. Complete Phase 1: Setup (T001-T009)
2. Complete Phase 2: Foundational (T010-T037) - CRITICAL BLOCKER
3. Complete Phase 3: User Story 1 (T038-T050)
4. Complete Phase 4: User Story 2 (T051-T060)
5. **STOP and VALIDATE**: Test US1+US2 independently
6. Deploy/demo if ready

**MVP Delivers**: Basic task monitoring with annotation + heartbeat timeout detection

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (Basic monitoring!)
3. Add User Story 2 → Test independently → Deploy/Demo (+ Heartbeat detection!)
4. Add User Story 3 → Test independently → Deploy/Demo (+ Smart serialization!)
5. Add User Story 4 → Test independently → Deploy/Demo (+ Auto-recovery!)
6. Add User Story 5 → Test independently → Deploy/Demo (+ Smart retry!)
7. Add User Story 6 → Test independently → Deploy/Demo (+ Custom handlers!)
8. Add User Story 7 → Test independently → Deploy/Demo (+ Storage flexibility!)

Each story adds value without breaking previous stories.

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together (T001-T037)
2. Once Foundational is done:
    - **Developer A**: User Story 1 + User Story 2 (T038-T060) - Core MVP
    - **Developer B**: User Story 3 + User Story 4 (T061-T073) - Recovery features
    - **Developer C**: User Story 5 + User Story 6 + User Story 7 (T074-T094) - Advanced features
3. Stories complete and integrate independently
4. Team reconvenes for Phase 10: Polish (T095-T104)

---

## Task Count Summary

- **Phase 1 (Setup)**: 9 tasks
- **Phase 2 (Foundational)**: 28 tasks (BLOCKING)
- **Phase 3 (US1)**: 13 tasks (4 tests + 9 implementation)
- **Phase 4 (US2)**: 10 tasks (3 tests + 7 implementation)
- **Phase 5 (US3)**: 5 tasks (2 tests + 3 implementation)
- **Phase 6 (US4)**: 8 tasks (3 tests + 5 implementation)
- **Phase 7 (US5)**: 5 tasks (2 tests + 3 implementation)
- **Phase 8 (US6)**: 6 tasks (2 tests + 4 implementation)
- **Phase 9 (US7)**: 10 tasks (3 tests + 7 implementation)
- **Phase 10 (Polish)**: 10 tasks
- **TOTAL**: **104 tasks**

### Test Task Count: 19 tests (18% of total, targeting ≥80% domain coverage)

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- **TDD强制**: 先写测试,确保RED(失败),再实现,确保GREEN(通过)
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence

---

## Format Validation ✅

All 104 tasks follow strict checklist format:

- ✅ Checkbox `- [ ]`
- ✅ Task ID (T001-T104)
- ✅ [P] marker where applicable (36 parallel tasks)
- ✅ [Story] label for user story tasks (US1-US7, 57 story tasks)
- ✅ Description with file paths

**Ready for execution** 🚀
