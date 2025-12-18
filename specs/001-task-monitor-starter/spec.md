# Feature Specification: Spring Boot Task Monitor Starter

**Feature Branch**: `001-task-monitor-starter`
**Created**: 2025-12-17
**Status**: Draft
**Input**: User description: "根据task-monitor-design.md设计文档创建Spring
Boot任务监控Starter,包含可插拔心跳机制、智能参数序列化和自动恢复功能"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Basic Task Monitoring with Annotation (Priority: P1)

As a Spring Boot developer, I want to monitor long-running tasks by simply adding an annotation to my methods, so that I
can track task execution status without modifying business logic.

**Why this priority**: This is the core value proposition - enabling task monitoring with zero code changes. Without
this, the entire feature has no value.

**Independent Test**: Can be fully tested by adding `@TaskMonitor` annotation to a method, executing it, and verifying
the task execution record is created in storage with correct status transitions.

**Acceptance Scenarios**:

1. **Given** a Spring Boot application with task monitoring enabled, **When** a method annotated with
   `@TaskMonitor("Daily Settlement")` is executed successfully, **Then** a task execution record is created with RUNNING
   status, updated with heartbeat timestamps during execution, and marked as SUCCESS upon completion
2. **Given** a monitored task is running, **When** the task execution fails with an exception, **Then** the task record
   is updated with FAILED status, error message is captured, and the system can determine if retry is needed based on
   exception type

---

### User Story 2 - Heartbeat-based Task Health Monitoring (Priority: P1)

As a system operator, I want the system to automatically detect when a task has stopped responding (frozen/crashed), so
that I can be alerted even when the process doesn't fail explicitly.

**Why this priority**: This distinguishes the feature from simple logging - it detects silent failures that would
otherwise go unnoticed. Critical for production reliability.

**Independent Test**: Can be tested by starting a monitored task, simulating a freeze (infinite loop without
exceptions), and verifying the system detects heartbeat timeout and marks the task as timed out.

**Acceptance Scenarios**:

1. **Given** a task with 5-minute heartbeat interval and 30-minute timeout, **When** the task runs normally, **Then**
   heartbeat timestamps are updated every 5 minutes in storage
2. **Given** a task has stopped sending heartbeats for 30 minutes, **When** the health check service scans for timeout
   tasks, **Then** the task is marked as HEARTBEAT_TIMEOUT and an alert is sent
3. **Given** multiple storage backends (memory/Redis/database) are available, **When** users configure different storage
   types, **Then** heartbeat tracking works consistently across all storage options

---

### User Story 3 - Intelligent Parameter Serialization (Priority: P2)

As a developer using task monitoring on methods with different parameter types, I want the system to automatically
determine which parameters can be safely serialized and stored, so that I don't have to manually configure serialization
for every method or risk storage issues with large objects.

**Why this priority**: This enables the auto-recovery feature to work automatically in most cases. Without intelligent
serialization, developers would need extensive manual configuration.

**Independent Test**: Can be tested by monitoring methods with various parameter types (no params, basic types, small
objects, large collections) and verifying correct serialization decisions are made for each case.

**Acceptance Scenarios**:

1. **Given** a task with no parameters or only basic types (String, numbers, dates), **When** the task starts, **Then**
   parameters are automatically serialized and stored, and the task is marked as recoverable
2. **Given** a task with a complex object parameter, **When** serialization is attempted and the result is under 10KB, *
   *Then** parameters are stored and task is marked recoverable
3. **Given** a task with parameters that serialize to over 10KB, **When** the serialization size check runs, **Then**
   parameters are not stored, task is marked as non-recoverable, and the reason is logged
4. **Given** a task with `serializeParams = false` explicitly configured, **When** the task starts, **Then** parameters
   are never serialized regardless of size or type

---

### User Story 4 - Automatic Task Recovery After Interruption (Priority: P2)

As a system operator, I want interrupted tasks (due to server restart or crashes) to be automatically detected and
retried on startup, so that I don't lose critical business operations during system maintenance or failures.

**Why this priority**: This provides the self-healing capability that makes the monitoring system valuable for
production environments.

**Independent Test**: Can be tested by starting a monitored task, forcibly stopping the application mid-execution,
restarting the application, and verifying the interrupted task is detected and retried automatically.

**Acceptance Scenarios**:

1. **Given** tasks were running when the application shut down, **When** the application restarts, **Then** all RUNNING
   tasks from the local host are identified as INTERRUPTED
2. **Given** an interrupted task with serialized parameters (recoverable), **When** the recovery service processes it, *
   *Then** the task is scheduled for retry with status RETRY and next retry time set
3. **Given** an interrupted task without serialized parameters (non-recoverable), **When** the recovery service
   processes it, **Then** the task status remains INTERRUPTED and an alert is sent without retry attempt
4. **Given** an interrupted task has already been retried 3 times (max retry count), **When** the recovery service
   evaluates it, **Then** no further retry is scheduled and a final failure alert is sent

---

### User Story 5 - Exception-based Retry Control (Priority: P3)

As a developer, I want to configure which exception types should trigger automatic retries versus which should fail
immediately, so that transient errors are handled automatically while business validation errors are not retried
wastefully.

**Why this priority**: This provides smart retry logic and reduces unnecessary work. It's valuable but the system is
still functional without fine-grained exception control.

**Independent Test**: Can be tested by configuring retryable exceptions (e.g., SQLException) and ignorable exceptions (
e.g., IllegalArgumentException), then triggering tasks that throw these exceptions and verifying correct retry/ignore
behavior.

**Acceptance Scenarios**:

1. **Given** SQLException is configured as retryable, **When** a monitored task fails with SQLException, **Then** the
   task status is set to RETRY and scheduled for retry with exponential backoff
2. **Given** IllegalArgumentException is configured as ignorable, **When** a monitored task fails with this exception, *
   *Then** the task is marked FAILED without sending alerts
3. **Given** a task fails with an unconfigured exception type, **When** the exception classifier evaluates it, **Then**
   the task is marked FAILED, an alert is sent, and no retry is attempted
4. **Given** a retryable exception occurs but max retry count is reached, **When** the retry evaluator checks it, **Then
   ** the task is marked as permanently FAILED and a failure alert is sent

---

### User Story 6 - Custom Recovery Handlers (Priority: P3)

As a developer working with non-serializable parameters (like file streams or large datasets), I want to provide custom
recovery logic that knows how to reconstruct the execution context, so that even complex tasks can be automatically
recovered after interruptions.

**Why this priority**: This handles advanced use cases but isn't needed for 80% of scenarios where parameters are
serializable.

**Independent Test**: Can be tested by creating a task with `recoveryStrategy = CUSTOM`, implementing a
`@TaskRecoveryHandler` method, interrupting the task, and verifying the custom handler is invoked during recovery.

**Acceptance Scenarios**:

1. **Given** a task annotated with `recoveryStrategy = CUSTOM` and a corresponding `@TaskRecoveryHandler`, **When** the
   task is interrupted and recovery is triggered, **Then** the custom recovery handler method is invoked with the task
   log information
2. **Given** a custom recovery handler that reads file data based on a business key, **When** the handler is invoked, *
   *Then** it can reconstruct the task parameters from external sources (files, database) and re-execute the task
3. **Given** a task with custom recovery strategy but no recovery handler defined, **When** recovery is attempted, *
   *Then** the system logs an error indicating missing handler and marks the task as unrecoverable

---

### User Story 7 - Flexible Storage Backend Configuration (Priority: P3)

As a system architect, I want to choose between memory, Redis, or database storage for heartbeat and task data based on
my deployment needs (standalone vs distributed, persistence requirements), so that the monitoring system fits my
infrastructure.

**Why this priority**: This provides deployment flexibility but memory storage can serve as a default for getting
started quickly.

**Independent Test**: Can be tested by configuring each storage type (memory/Redis/database) and verifying task
monitoring and heartbeat tracking works correctly with each backend.

**Acceptance Scenarios**:

1. **Given** storage type is configured as "memory", **When** tasks are monitored, **Then** all task execution data and
   heartbeat information is stored in application memory (lost on restart)
2. **Given** storage type is configured as "redis", **When** tasks are monitored, **Then** task data and heartbeats are
   persisted to Redis and accessible across multiple application instances
3. **Given** storage type is configured as "database", **When** tasks are monitored, **Then** task execution logs are
   persisted to the database table and survive application restarts
4. **Given** the storage backend fails (Redis unavailable, database connection lost), **When** a task tries to update
   heartbeat, **Then** the failure is logged but the task execution continues without throwing exceptions

---

### Edge Cases

- What happens when a task completes successfully but the heartbeat thread fails to stop cleanly?
    - The heartbeat service should have cleanup logic that stops heartbeat threads on task completion even if exceptions
      occur

- How does the system handle concurrent executions of the same task name?
    - Each execution gets a unique task ID (UUID or timestamp-based), so concurrent executions are tracked independently

- What happens when parameter serialization succeeds but the serialized JSON is exactly at the 10KB limit?
    - The task is marked as recoverable since it's within the limit (≤ not <)

- How does the system behave when Spring application context is still initializing during recovery scan?
    - The recovery service should implement ApplicationRunner or CommandLineRunner to ensure it runs after full context
      initialization

- What happens if a custom recovery handler method throws an exception?
    - The exception should be caught, logged, and the task marked as FAILED to prevent infinite retry loops

- How are heartbeat threads cleaned up when the application shuts down gracefully?
    - The heartbeat service should implement DisposableBean or use @PreDestroy to stop all active heartbeat threads
      during shutdown

- What happens when a task is manually marked as completed in storage while still actually running?
    - The heartbeat thread will detect the task no longer exists in RUNNING status and stop sending updates

- How does the system handle clock skew in distributed environments?
    - Heartbeat timeout should use a margin (e.g., timeout + 60 seconds grace period) to account for minor clock
      differences

- What happens when deserializing parameters fails during retry (class definition changed)?
    - The deserialization exception should be caught, logged, task marked as unrecoverable, and alert sent

## Requirements *(mandatory)*

### Functional Requirements

#### Core Monitoring

- **FR-001**: System MUST provide an `@TaskMonitor` annotation that can be applied to any Spring-managed bean methods to
  enable monitoring
- **FR-002**: System MUST automatically capture task start time, end time, execution duration, and final status (
  RUNNING/SUCCESS/FAILED/INTERRUPTED/HEARTBEAT_TIMEOUT/RETRY)
- **FR-003**: System MUST generate unique task identifiers for each execution to distinguish concurrent runs of the same
  task
- **FR-004**: System MUST support extraction of business keys from method parameters using SpEL expressions (e.g.,
  `bizKey = "#orderId"`) for business-level task identification
- **FR-005**: System MUST capture method signature information to enable task re-execution during recovery

#### Heartbeat Mechanism

- **FR-006**: System MUST provide a heartbeat mechanism that periodically updates task liveness timestamps at
  configurable intervals (default 5 minutes)
- **FR-007**: System MUST detect heartbeat timeouts by comparing last heartbeat time against configurable timeout
  threshold (default 30 minutes)
- **FR-008**: System MUST run a background health check service at configurable intervals (default 60 seconds) to scan
  for timeout tasks
- **FR-009**: System MUST mark tasks as HEARTBEAT_TIMEOUT when heartbeat threshold is exceeded and send alerts
- **FR-010**: System MUST automatically stop heartbeat updates when a task completes, fails, or is interrupted
- **FR-011**: System MUST allow per-task configuration of heartbeat interval and timeout via annotation attributes

#### Intelligent Parameter Serialization

- **FR-012**: System MUST automatically determine if method parameters should be serialized based on parameter types and
  size
- **FR-013**: System MUST always attempt to serialize parameters when they are null, empty, or contain only basic
  types (primitives, String, Number, Boolean, Date, LocalDate, LocalDateTime, Enum)
- **FR-014**: System MUST check serialized parameter size against configurable maximum (default 10KB) and reject storage
  if exceeded
- **FR-015**: System MUST mark tasks as "recoverable" when parameters are successfully serialized and stored, and "
  non-recoverable" otherwise
- **FR-016**: System MUST record the reason for non-recoverability (e.g., "parameters exceed 10KB limit", "parameters
  not serializable", "user disabled serialization")
- **FR-017**: System MUST respect explicit `serializeParams` annotation attribute: true (force serialize), false (never
  serialize), null (auto-detect)
- **FR-018**: System MUST serialize parameters to JSON format for storage and deserialization compatibility

#### Task Recovery and Retry

- **FR-019**: System MUST scan for interrupted tasks (status = RUNNING) on application startup for the local host
- **FR-020**: System MUST mark interrupted tasks as INTERRUPTED status with timestamp
- **FR-021**: System MUST automatically schedule retry for interrupted recoverable tasks within configured retry limits
- **FR-022**: System MUST support configurable maximum retry count (default 3) at both global and per-task levels
- **FR-023**: System MUST implement exponential backoff for retry intervals when enabled (default true)
- **FR-024**: System MUST run a scheduled background task (default every 30 seconds) to execute tasks with status RETRY
  whose next retry time has arrived
- **FR-025**: System MUST deserialize stored parameters and re-invoke the original method using reflection during retry
- **FR-026**: System MUST increment retry count on each retry attempt and prevent further retries when max count is
  reached
- **FR-027**: System MUST send alerts for interrupted non-recoverable tasks without attempting retry

#### Exception Handling and Classification

- **FR-028**: System MUST classify exceptions into retryable, ignorable, or standard failure categories
- **FR-029**: System MUST support global configuration of retryable exception types (default: SQLException,
  SocketTimeoutException, DataAccessException)
- **FR-030**: System MUST support global configuration of ignorable exception types (default: IllegalArgumentException)
- **FR-031**: System MUST allow per-task override of retryable exceptions via annotation attribute `retryableExceptions`
- **FR-032**: System MUST allow per-task override of ignorable exceptions via annotation attribute `ignorableExceptions`
- **FR-033**: System MUST automatically retry tasks that fail with retryable exceptions within retry count limits
- **FR-034**: System MUST mark tasks that fail with ignorable exceptions as FAILED without sending alerts
- **FR-035**: System MUST capture full stack trace for non-ignorable exceptions

#### Custom Recovery Handlers

- **FR-036**: System MUST support `@TaskRecoveryHandler` annotation for defining custom recovery logic
- **FR-037**: System MUST match recovery handlers to tasks by task name
- **FR-038**: System MUST invoke custom recovery handler instead of automatic parameter deserialization when
  `recoveryStrategy = CUSTOM`
- **FR-039**: System MUST pass TaskLog object containing task metadata and business key to custom recovery handlers
- **FR-040**: System MUST log error and mark task unrecoverable if custom recovery handler is configured but not found

#### Storage Backends

- **FR-041**: System MUST support pluggable storage backends: memory, Redis, and database
- **FR-042**: System MUST provide memory-based storage for standalone deployments (data lost on restart)
- **FR-043**: System MUST provide Redis-based storage for distributed deployments with persistence
- **FR-044**: System MUST provide database-based storage with full persistence and query capabilities
- **FR-045**: System MUST store task execution logs with all fields: task ID, task name, business key, status,
  timestamps, parameters, result, error details, retry information, host information
- **FR-046**: System MUST store heartbeat information: task ID, last heartbeat time, heartbeat interval, timeout
  threshold, heartbeat count
- **FR-047**: System MUST handle storage backend failures gracefully without stopping task execution

#### Configuration and Integration

- **FR-048**: System MUST be distributed as a Spring Boot Starter with auto-configuration
- **FR-049**: System MUST be enabled via `@EnableTaskMonitor` annotation on the application class
- **FR-050**: System MUST support YAML/properties-based configuration for all features (storage, heartbeat, params,
  recovery, exception, alerts)
- **FR-051**: System MUST have a global enable/disable switch (`task-monitor.enabled`)
- **FR-052**: System MUST default to database storage, enabled heartbeat, auto-serialization, and enabled recovery
- **FR-053**: System MUST capture execution host information (hostname, IP address, thread name) for distributed
  debugging

#### Alerting

- **FR-054**: System MUST send alerts for task failures (except ignorable exceptions)
- **FR-055**: System MUST send alerts for heartbeat timeouts
- **FR-056**: System MUST send alerts for interrupted non-recoverable tasks
- **FR-057**: System MUST track whether an alert has been sent to prevent duplicate alerts
- **FR-058**: System MUST support configurable alert types (defaults to DingTalk, with extensibility for email/WeChat)

### Key Entities

- **TaskLog (Task Execution Record)**: Represents a single execution of a monitored task. Contains: unique task ID, task
  name, business key, execution status, start/end timestamps, duration, method signature, serialized input parameters,
  parameter size, execution result (optional), error message and stack trace, recoverability flag and reason, retry
  count and limits, next retry time, heartbeat information (last heartbeat time, interval, timeout, count), execution
  environment (host name, IP, thread name), alert tracking (alert sent flag, alert time), and audit timestamps (created,
  updated).

- **HeartbeatInfo**: Represents the heartbeat state of a running task. Contains: task ID (reference to TaskLog), last
  heartbeat timestamp, heartbeat update interval in seconds, heartbeat timeout threshold in seconds, heartbeat count (
  number of successful heartbeat updates), and task status for quick filtering.

- **SerializationDecision**: Represents the outcome of attempting to serialize task parameters. Contains: boolean flag
  indicating if serialization should proceed, serialized JSON data (if successful), size of serialized data in bytes,
  and reason for not serializing (if unsuccessful).

- **RecoveryStrategy**: Enumeration defining recovery behavior options. Values: AUTO (automatically determine based on
  parameter serializability), ALWAYS (always attempt recovery, use custom handler if params not serializable), NEVER (
  never recover, only alert), CUSTOM (always use custom recovery handler).

- **TaskStatus**: Enumeration of possible task execution states. Values: RUNNING (currently executing), SUCCESS (
  completed successfully), FAILED (completed with error), INTERRUPTED (stopped due to application shutdown/crash),
  HEARTBEAT_TIMEOUT (no heartbeat updates within timeout threshold), RETRY (scheduled for retry after failure or
  interruption).

### Assumptions

1. **Storage Default**: We assume database storage is the preferred default for most production use cases since it
   provides full persistence and query capabilities. Memory storage is suitable for development/testing only.

2. **Heartbeat Intervals**: Default 5-minute heartbeat interval and 30-minute timeout are assumed to be reasonable for
   typical long-running tasks (reports, batch processing, data synchronization). Users can override per task.

3. **Parameter Size Limit**: 10KB serialized parameter size limit is assumed to be sufficient for most business objects
   while preventing storage bloat from large collections. This is based on typical entity sizes in enterprise
   applications.

4. **Retry Strategy**: Exponential backoff with 60-second base interval is assumed to be appropriate for most transient
   failures (database deadlocks, network timeouts). Maximum 3 retries prevents infinite loops while giving reasonable
   recovery chances.

5. **Exception Classification**: We assume SQLException and network timeouts are typically transient and retryable,
   while IllegalArgumentException indicates business validation failure that shouldn't be retried. Users should
   customize based on their application patterns.

6. **Serialization Format**: JSON serialization is assumed to be sufficient for most parameter types. Complex scenarios
   requiring custom serialization should use custom recovery handlers.

7. **Host Identification**: We assume interrupted tasks should only be recovered by the same host (identified by IP
   address) to prevent duplicate recovery in distributed environments where shared storage is used.

8. **Alerting**: We assume DingTalk is the primary alert channel for Chinese enterprise users, with extensibility points
   for other channels. Alert deduplication (not sending multiple alerts for the same failure) is handled by the
   `alert_sent` flag.

9. **Spring Context**: We assume all monitored methods are on Spring-managed beans so they can be intercepted by AOP and
   re-invoked during recovery using ApplicationContext.

10. **Thread Safety**: We assume the storage backend implementations handle concurrent access (multiple tasks updating
    heartbeats simultaneously) through appropriate synchronization or database transaction isolation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Developers can enable task monitoring by adding a single annotation to a method without any other code
  changes
- **SC-002**: The system successfully detects and reports tasks that have stopped responding (frozen) within the
  configured heartbeat timeout threshold (30 minutes default)
- **SC-003**: Parameters under 10KB are automatically serialized and stored in at least 95% of monitored task executions
- **SC-004**: Interrupted tasks (due to server restart) are automatically detected and retried within 2 minutes of
  application startup for recoverable tasks
- **SC-005**: Task monitoring adds less than 5% overhead to task execution time for typical business operations
- **SC-006**: The system successfully classifies and handles retryable vs ignorable exceptions with at least 90%
  accuracy based on configured rules
- **SC-007**: The heartbeat mechanism correctly identifies timeout tasks with less than 1% false positive rate (tasks
  marked as timeout that were actually running)
- **SC-008**: The system supports at least 1000 concurrent monitored tasks without heartbeat storage bottlenecks
- **SC-009**: Custom recovery handlers successfully restore and execute interrupted tasks in scenarios where automatic
  parameter deserialization is not possible
- **SC-010**: Alert notifications are sent within 1 minute of task failure or timeout detection
- **SC-011**: The system operates correctly across all three storage backends (memory, Redis, database) with identical
  functional behavior
- **SC-012**: Task execution history is retained and queryable for at least 30 days in database storage mode for
  operational analysis
