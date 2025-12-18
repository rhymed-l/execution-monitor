-- Execution Log Table
CREATE TABLE IF NOT EXISTS execution_log
(
    -- Primary Key
    id
    BIGINT
    AUTO_INCREMENT
    PRIMARY
    KEY
    COMMENT
    '主键',

    -- 标识
    execution_id
    VARCHAR
(
    64
) NOT NULL UNIQUE COMMENT '执行唯一标识(UUID)',
    execution_name VARCHAR
(
    200
) NOT NULL COMMENT '执行名称',
    biz_key VARCHAR
(
    200
) DEFAULT NULL COMMENT '业务关键字',

    -- 执行信息
    status VARCHAR
(
    20
) NOT NULL COMMENT '状态:RUNNING/SUCCESS/FAILED/INTERRUPTED/HEARTBEAT_TIMEOUT/RETRY',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    duration_millis BIGINT DEFAULT NULL COMMENT '执行时长(毫秒)',

    -- 心跳信息
    last_heartbeat_time DATETIME NOT NULL COMMENT '最后心跳时间',
    heartbeat_interval INT DEFAULT 300 COMMENT '心跳间隔(秒)',
    heartbeat_timeout INT DEFAULT 1800 COMMENT '心跳超时阈值(秒)',
    heartbeat_count INT DEFAULT 0 COMMENT '心跳次数',

    -- 方法和参数
    method_signature VARCHAR
(
    500
) DEFAULT NULL COMMENT '方法签名',
    input_params TEXT DEFAULT NULL COMMENT '入参JSON',
    param_size_bytes INT DEFAULT 0 COMMENT '参数大小(字节)',
    execution_result TEXT DEFAULT NULL COMMENT '执行结果',

    -- 错误信息
    error_message TEXT DEFAULT NULL COMMENT '错误消息',
    exception_type VARCHAR
(
    200
) DEFAULT NULL COMMENT '异常类型',
    stack_trace TEXT DEFAULT NULL COMMENT '异常堆栈',

    -- 恢复信息
    recoverable TINYINT
(
    1
) DEFAULT 0 COMMENT '是否可恢复',
    non_recoverable_reason VARCHAR
(
    500
) DEFAULT NULL COMMENT '不可恢复原因',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    max_retry INT DEFAULT 3 COMMENT '最大重试次数',
    next_retry_time DATETIME DEFAULT NULL COMMENT '下次重试时间',

    -- 执行环境
    host_name VARCHAR
(
    100
) DEFAULT NULL COMMENT '主机名',
    host_ip VARCHAR
(
    50
) DEFAULT NULL COMMENT '主机IP',
    thread_name VARCHAR
(
    100
) DEFAULT NULL COMMENT '执行线程',

    -- 告警信息
    alert_sent TINYINT
(
    1
) DEFAULT 0 COMMENT '是否已发送告警',
    alert_time DATETIME DEFAULT NULL COMMENT '告警时间',

    -- 审计
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    INDEX idx_status_heartbeat
(
    status,
    last_heartbeat_time
) COMMENT '健康检查查询',
    INDEX idx_execution_name
(
    execution_name
) COMMENT '按执行名统计',
    INDEX idx_retry_time
(
    status,
    next_retry_time
) COMMENT '重试扫描',
    INDEX idx_host_status
(
    host_ip,
    status
) COMMENT '按主机恢复'
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行日志表';
