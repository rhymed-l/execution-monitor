-- 执行日志表
CREATE TABLE IF NOT EXISTS execution_log
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT
    COMMENT
    '主键ID',
    execution_id
    VARCHAR
(
    64
) NOT NULL COMMENT '执行ID',
    execution_name VARCHAR
(
    128
) NOT NULL COMMENT '执行名称',
    biz_key VARCHAR
(
    256
) COMMENT '业务键',
    params_json TEXT COMMENT '参数JSON',
    params_size_bytes INT COMMENT '参数大小（字节）',
    status VARCHAR
(
    32
) NOT NULL COMMENT '状态: RUNNING/SUCCESS/FAILED/INTERRUPTED/HEARTBEAT_TIMEOUT/RETRY',
    error_message TEXT COMMENT '错误信息',
    exception_type VARCHAR
(
    256
) COMMENT '异常类型',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    max_retry INT DEFAULT 0 COMMENT '最大重试次数',
    heartbeat_enabled BOOLEAN DEFAULT FALSE COMMENT '是否启用心跳',
    heartbeat_interval_seconds INT COMMENT '心跳间隔（秒）',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    INDEX idx_execution_id
(
    execution_id
),
    INDEX idx_status
(
    status
),
    INDEX idx_execution_name
(
    execution_name
),
    INDEX idx_created_at
(
    created_at
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行日志表';

-- 执行心跳表
CREATE TABLE IF NOT EXISTS execution_heartbeat
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT
    COMMENT
    '主键ID',
    execution_id
    VARCHAR
(
    64
) NOT NULL UNIQUE COMMENT '执行ID',
    interval_seconds INT NOT NULL COMMENT '心跳间隔（秒）',
    last_heartbeat DATETIME NOT NULL COMMENT '最后心跳时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    INDEX idx_execution_id
(
    execution_id
),
    INDEX idx_last_heartbeat
(
    last_heartbeat
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行心跳表';
