# 分布式锁配置指南

## 核心概念

### 全局批处理锁

系统使用**全局批处理锁**确保同一时刻只有一个实例在扫描和处理重试任务。

```
时间轴示例（轮询间隔=5分钟）：
00:00 - 实例A获取全局锁，开始处理100个任务
00:05 - 实例B尝试获取锁 → 失败，跳过本次扫描 ✅
00:05 - 实例C尝试获取锁 → 失败，跳过本次扫描 ✅
00:10 - 实例A处理完成，释放锁
00:10 - 实例B/C下次轮询时可以获取锁
```

## 重要配置参数

### 1. 任务重试轮询间隔

```yaml
execution:
  monitor:
    retry:
      # 轮询间隔（毫秒）
      scheduled-interval-ms: 300000    # 默认5分钟

      # 启动延迟（毫秒）
      scheduled-initial-delay-ms: 60000  # 默认1分钟
```

### 2. 分布式锁超时时间

```yaml
execution:
  monitor:
    distributed-lock:
      enabled: true
      # 锁超时时间（秒）
      timeout-seconds: 300  # 默认5分钟
```

## ⚠️ 关键配置原则

### 原则1：锁超时时间 >= 批量任务总处理时间

**公式**：

```
锁超时时间 >= (单个任务平均耗时 × 批量处理数 100) + 缓冲时间
```

**示例**：

- 单个任务平均耗时：3秒
- 批量处理数：100个
- 预估总耗时：300秒（5分钟）
- **建议锁超时**：600秒（10分钟，留有2倍缓冲）

```yaml
distributed-lock:
  timeout-seconds: 600  # 10分钟
```

### 原则2：锁超时时间 >= 轮询间隔

如果锁超时 < 轮询间隔，可能导致：

- 实例A处理到一半，锁过期
- 实例B获取锁，重复扫描相同任务
- **可能导致重复执行** ❌

**正确配置**：

```yaml
retry:
  scheduled-interval-ms: 300000  # 5分钟轮询

distributed-lock:
  timeout-seconds: 600           # 10分钟锁超时（>= 轮询间隔）
```

### 原则3：长时间任务场景

如果单个任务可能运行很长时间（如30分钟）：

**方案A：增加锁超时和轮询间隔**

```yaml
retry:
  scheduled-interval-ms: 1800000  # 30分钟轮询

distributed-lock:
  timeout-seconds: 3600           # 60分钟锁超时
```

**方案B：拆分长任务为多个短任务**

```java
// 不推荐：一个30分钟的大任务
@Monitor(name = "longTask")
public void processAll() {
    // 30分钟的处理...
}

// 推荐：拆分为多个小任务
@Monitor(name = "processChunk")
public void processChunk(int chunkId) {
    // 3分钟的处理...
}
```

## 配置示例

### 场景1：快速任务（默认）

```yaml
execution:
  monitor:
    retry:
      scheduled-interval-ms: 300000  # 5分钟

    distributed-lock:
      enabled: true
      timeout-seconds: 600           # 10分钟
```

适用于：单个任务耗时 < 3秒

### 场景2：中等任务

```yaml
execution:
  monitor:
    retry:
      scheduled-interval-ms: 600000  # 10分钟

    distributed-lock:
      enabled: true
      timeout-seconds: 1200          # 20分钟
```

适用于：单个任务耗时 5-10秒

### 场景3：长时间任务

```yaml
execution:
  monitor:
    retry:
      scheduled-interval-ms: 1800000  # 30分钟

    distributed-lock:
      enabled: true
      timeout-seconds: 3600           # 60分钟
```

适用于：单个任务耗时 > 10秒

## 监控指标

### 日志关键字

**正常情况**：

```
成功获取全局批处理锁，开始处理任务
批量重试完成 - 成功: 95, 失败: 3, 跳过: 2, 总计: 100
释放全局批处理锁
```

**锁竞争情况**：

```
全局批处理锁已被其他实例持有，跳过本次扫描
```

这是正常的，说明多实例协调工作正常 ✅

**锁超时警告**：
如果频繁看到实例A和实例B交替处理任务，可能是锁超时过短：

```
00:00 实例A开始处理...
00:05 实例A锁过期 ⚠️
00:05 实例B获取锁开始处理（可能重复）⚠️
```

**解决方案**：增加 `timeout-seconds`

## 故障排查

### 问题1：任务被重复执行

**可能原因**：锁超时过短
**检查**：

```sql
-- 查看execution_lock表中的锁记录
SELECT * FROM execution_lock
WHERE execution_id = 'BATCH_RETRY_PROCESSING';

-- 检查expires_at是否过早
```

**解决**：增加 `timeout-seconds`

### 问题2：所有实例都不处理任务

**可能原因**：死锁（某个实例宕机后没有释放锁）
**检查**：

```sql
-- 查看是否有过期但未清理的锁
SELECT * FROM execution_lock
WHERE expires_at < NOW();
```

**解决**：

1. 系统会每分钟自动清理过期锁
2. 或手动删除：`DELETE FROM execution_lock WHERE execution_id = 'BATCH_RETRY_PROCESSING';`

### 问题3：锁清理不及时

**检查**：确认 LockCleanupScheduler 是否启用

```
启用锁清理调度器
清理了 X 个过期的执行锁
```

## 最佳实践

1. ✅ **锁超时 = 轮询间隔 × 2-3 倍**
2. ✅ **确保任务幂等性**（重复执行不会造成问题）
3. ✅ **监控锁竞争日志**
4. ✅ **使用database或redis存储**（memory不支持分布式锁）
5. ✅ **定期检查execution_lock表大小**

## 性能调优

### 调整批量大小

当前固定100条/批次，如需调整可修改：

```java
private static final int BATCH_SIZE = 100;
```

建议：

- 快速任务：100-200条/批次
- 中等任务：50-100条/批次
- 长时间任务：10-50条/批次

### 调整轮询频率

根据业务SLA要求：

- 实时性要求高：1-5分钟
- 一般要求：5-15分钟
- 低优先级：30-60分钟
