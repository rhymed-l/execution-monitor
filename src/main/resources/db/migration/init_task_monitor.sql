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
    method_metadata_id BIGINT COMMENT '方法元信息ID',
    params_json TEXT COMMENT '参数JSON',
    status VARCHAR
(
    32
) NOT NULL COMMENT '状态: RUNNING/SUCCESS/FAILED/RETRYABLE_FAILED/INTERRUPTED/HEARTBEAT_TIMEOUT/AWAITING_RETRY',
    error_message TEXT COMMENT '错误信息',
    exception_type VARCHAR
(
    256
) COMMENT '异常类型',
    stack_trace TEXT COMMENT '异常堆栈',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    max_retry INT DEFAULT 0 COMMENT '最大重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
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
),
    INDEX idx_status_next_retry_time
(
    status,
    next_retry_time
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

-- 方法元信息表
CREATE TABLE IF NOT EXISTS execution_method_metadata
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT
    COMMENT
    '主键ID',
    target_bean_name
    VARCHAR
(
    256
) COMMENT '目标Bean名称',
    target_class VARCHAR
(
    512
) NOT NULL COMMENT '目标类全限定名',
    method_signature VARCHAR
(
    1024
) NOT NULL COMMENT '方法签名: methodName(paramType1,paramType2)',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_class_method
(
    target_class,
    method_signature
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方法元信息表';

-- 执行锁表（用于分布式环境防止重复执行）
CREATE TABLE IF NOT EXISTS execution_lock
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
) NOT NULL UNIQUE COMMENT '执行ID（唯一约束）',
    locked_by VARCHAR
(
    256
) NOT NULL COMMENT '锁持有者（实例标识）',
    locked_at DATETIME NOT NULL COMMENT '锁定时间',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_expires_at
(
    expires_at
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行锁表';
