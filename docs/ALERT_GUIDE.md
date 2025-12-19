# 告警服务配置指南

本指南详细说明如何配置和自定义执行监控的告警服务。

## 目录

- [内置告警服务](#内置告警服务)
- [自定义告警服务](#自定义告警服务)
- [多种告警方式示例](#多种告警方式示例)
- [最佳实践](#最佳实践)

## 告警服务概述

Execution Monitor 提供灵活的告警机制，当任务执行出现异常时及时通知相关人员。

### 告警触发场景

框架提供四种告警类型，每种告警都有明确的触发时机和使用场景：

#### 1. 任务失败告警 (sendFailureAlert)

**触发时机**：

- 任务执行失败，且已达到最大重试次数（无法再重试）
- 在批量重试扫描中，发现任务已超过最大重试次数

**告警级别**：🔴 **最高** - 表示任务彻底失败，需要人工介入处理

**触发位置**：

- `ExecutionRetryExecutor.executeDefaultRetry()` - 当 `retryCount >= maxRetry` 时
- `ExecutionRetryExecutor.executeBatchRetry()` - 检测到任务超过最大重试次数时

**告警内容**：

- 任务名称、任务ID、业务标识
- 最终状态（FAILED）
- 异常类型和异常消息
- 重试统计（例如：3/3，表示已重试3次，最大重试3次）
- 主机信息和时间

**示例场景**：

```java
// 场景1：任务执行3次重试后仍然失败
@Monitor(name = "processOrder", maxRetry = 3)
public void processOrder(String orderId) {
    // 业务代码抛出异常，框架自动重试3次
    // 第3次重试失败后，触发"任务失败告警"
}

// 场景2：任务不允许重试，首次失败即触发
@Monitor(name = "sendEmail", maxRetry = 0)
public void sendEmail(String email) {
    // 业务代码抛出异常，由于 maxRetry = 0
    // 直接触发"任务失败告警"
}
```

---

#### 2. 任务重试告警 (sendRetryAlert)

**触发时机**：

- 任务执行失败，但还有重试机会（retryCount < maxRetry）
- 表示任务暂时失败，框架将会自动重试

**告警级别**：🟡 **中等** - 表示任务遇到问题但还在恢复中，无需立即处理

**触发位置**：

- `ExecutionRetryExecutor.executeDefaultRetry()` - 当 `retryCount < maxRetry` 时

**告警内容**：

- 任务名称、任务ID
- 当前重试次数（例如：第2次重试）
- 最大重试次数
- 异常信息（可选，取决于实现）

**示例场景**：

```java
// 任务配置了3次重试
@Monitor(name = "syncData", maxRetry = 3)
public void syncData(String dataId) {
    // 第1次执行失败 -> 触发"任务重试告警"（1/3）
    // 第2次执行失败 -> 触发"任务重试告警"（2/3）
    // 第3次执行失败 -> 触发"任务失败告警"（3/3，最终失败）
}
```

**重要说明**：

- 此告警仅表示"暂时失败"，任务状态会变为 `RETRYABLE_FAILED`
- 框架会自动将任务标记为 `AWAITING_RETRY` 状态，等待调度器在指定时间执行重试
- 重试采用指数退避策略（1分钟 -> 5分钟 -> 15分钟 -> 30分钟）
- 建议根据重试次数调整告警级别，前几次重试失败可以降低告警优先级

---

#### 3. 心跳超时告警 (sendHeartbeatTimeoutAlert)

**触发时机**：

- 长时间运行的任务，超过指定时间未更新心跳
- 任务可能已经"僵死"或执行环境出现问题

**告警级别**：🟠 **高** - 表示任务可能挂起，需要关注

**触发位置**：

- `HeartbeatMonitor` 或 `HealthCheckScheduler` - 定期检查任务心跳超时

**配置方式**：

```java
// 启用心跳监控，每30秒更新一次心跳
@Monitor(name = "longRunningTask", heartbeatIntervalSeconds = 30)
public void longRunningTask() {
    for (int i = 0; i < 1000; i++) {
        // 执行长时间任务
        processItem(i);

        // 框架会自动更新心跳，无需手动调用
    }
}
```

**告警内容**：

- 任务名称、任务ID、业务标识
- 心跳间隔配置
- 最后心跳时间
- 主机信息和当前时间

**示例场景**：

```java
// 任务配置了30秒心跳间隔
@Monitor(name = "batchImport", heartbeatIntervalSeconds = 30)
public void batchImport(String fileId) {
    // 正常情况：每30秒框架自动更新心跳
    // 异常情况：如果超过 30秒 * 2 = 60秒 未更新心跳
    //          触发"心跳超时告警"
}
```

**重要说明**：

- 心跳超时不代表任务一定失败，可能只是执行缓慢
- 建议结合实际业务场景调整心跳间隔
- 心跳超时后，任务状态会变为 `HEARTBEAT_TIMEOUT`，可以选择重试或标记失败

---

#### 4. 自定义告警 (sendCustomAlert)

**触发时机**：

- **由业务代码主动调用**，完全由开发者控制
- 用于发送业务相关的特殊告警

**告警级别**：⚪ **自定义** - 由业务代码决定

**使用方式**：

```java

@Service
public class OrderService {

    @Autowired
    private AlertService alertService;  // 注入告警服务

    @Monitor(name = "processOrder")
    public void processOrder(String orderId) {
        Order order = orderRepository.findById(orderId);

        // 业务场景1：订单金额异常大
        if (order.getAmount() > 100000) {
            alertService.sendCustomAlert(
                    "订单金额异常",
                    String.format("订单 %s 金额高达 %.2f 元，请人工核实",
                            orderId, order.getAmount())
            );
        }

        // 业务场景2：库存不足
        if (inventory.getStock() < order.getQuantity()) {
            alertService.sendCustomAlert(
                    "库存不足警告",
                    String.format("商品 %s 库存不足，当前库存：%d，需求量：%d",
                            order.getProductId(), inventory.getStock(), order.getQuantity())
            );
        }

        // 业务场景3：关键数据变更
        if (order.getStatus() == OrderStatus.CANCELLED) {
            alertService.sendCustomAlert(
                    "订单取消通知",
                    String.format("订单 %s 已被用户取消，金额：%.2f",
                            orderId, order.getAmount())
            );
        }
    }
}
```

**告警内容**：

- 完全由调用者自定义标题（title）和内容（content）
- 框架会添加主机信息和时间戳

**示例场景**：

1. **业务指标异常**：交易金额异常、数据量异常、耗时异常等
2. **关键事件通知**：重要订单状态变更、VIP用户操作、数据变更审计
3. **资源告警**：库存不足、配额耗尽、连接池满
4. **安全告警**：异常登录、权限变更、敏感操作

**最佳实践**：

```java
// 建议：根据告警重要性调整告警方式
@Service
public class AlertHelper {

    @Autowired
    private AlertService alertService;

    // 一般信息 - 仅记录日志
    public void infoAlert(String title, String content) {
        log.info("📢 {} - {}", title, content);
    }

    // 警告信息 - 发送告警但不紧急
    public void warningAlert(String title, String content) {
        log.warn("⚠️  {} - {}", title, content);
        alertService.sendCustomAlert("⚠️  " + title, content);
    }

    // 严重问题 - 发送高优先级告警
    public void criticalAlert(String title, String content) {
        log.error("🔥 {} - {}", title, content);
        alertService.sendCustomAlert("🔥 【严重】" + title, content);
    }
}
```

---

### 告警级别总览

| 告警类型   | 触发方式 | 告警级别  | 典型场景      | 是否需要立即处理            |
|--------|------|-------|-----------|---------------------|
| 任务失败告警 | 自动触发 | 🔴 最高 | 所有重试都失败   | ✅ 是 - 需要人工介入        |
| 任务重试告警 | 自动触发 | 🟡 中等 | 暂时失败，还会重试 | ⚠️  关注 - 如多次重试失败需关注 |
| 心跳超时告警 | 自动触发 | 🟠 高  | 长任务可能挂起   | ⚠️  关注 - 检查任务是否正常   |
| 自定义告警  | 手动调用 | ⚪ 自定义 | 业务特定场景    | ❓ 取决于业务逻辑           |

### 灵活的告警配置

Execution Monitor 支持在方法级别指定告警方式，您可以：

- **默认行为**：使用所有已启用的告警服务
- **指定服务**：只使用钉钉、只使用飞书、或同时使用多个
- **禁用告警**：对于某些不重要的任务，可以完全禁用告警

```java
// 默认：使用所有已启用的告警
@Monitor(name = "task1")
public void task1() {
}

// 只用飞书告警
@Monitor(name = "task2", alertTypes = {AlertType.FEISHU})
public void task2() {
}

// 同时使用钉钉和飞书
@Monitor(name = "task3", alertTypes = {AlertType.DINGTALK, AlertType.FEISHU})
public void task3() {
}

// 禁用告警
@Monitor(name = "task4", alertTypes = {AlertType.NONE})
public void task4() {
}
```

### 告警接口定义

```java
public interface AlertService {
    /**
     * 发送任务失败告警
     */
    void sendFailureAlert(ExecutionRecord execution);

    /**
     * 发送心跳超时告警
     */
    void sendHeartbeatTimeoutAlert(ExecutionRecord execution);

    /**
     * 发送任务重试告警
     */
    void sendRetryAlert(ExecutionRecord execution, int retryCount);

    /**
     * 发送自定义告警
     */
    void sendCustomAlert(String title, String content);
}
```

---

## 内置告警服务

Execution Monitor 内置了钉钉和飞书两种告警实现。

### 钉钉告警

#### 1. 获取钉钉机器人Webhook

1. 在钉钉群中添加「自定义机器人」
2. 复制 Webhook URL
3. （可选）启用「加签」安全设置并复制密钥

#### 2. 配置钉钉告警

```yaml
execution:
  monitor:
    alert:
      dingtalk:
        enabled: true
        webhook-url: https://oapi.dingtalk.com/robot/send?access_token=xxx
        secret: SEC***  # 可选，加签密钥
```

或通过系统属性配置（优先级更高）：

```bash
java -jar your-app.jar \
  -Dexecution.monitor.alert.dingtalk.webhook-url=https://oapi.dingtalk.com/robot/send?access_token=xxx \
  -Dexecution.monitor.alert.dingtalk.secret=SEC***
```

#### 3. 告警消息格式

钉钉告警使用Markdown格式，包含以下信息：

**任务失败告警**：

```
【任务失败告警】

**任务名称**: processOrder
**任务ID**: exec-20231218-001
**业务标识**: order-12345
**状态**: FAILED
**异常类型**: java.net.SocketTimeoutException
**异常消息**: Read timed out
**重试次数**: 3/3

---
**主机**: hostname@12345
**时间**: 2023-12-18 14:30:00
```

---

### 飞书告警

#### 1. 获取飞书机器人Webhook

1. 在飞书群中添加「自定义机器人」
2. 复制 Webhook URL（格式：https://open.feishu.cn/open-apis/bot/v2/hook/xxx）
3. （可选）启用「签名校验」安全设置并复制签名密钥

#### 2. 配置飞书告警

```yaml
execution:
  monitor:
    alert:
      feishu:
        enabled: true
        webhook-url: https://open.feishu.cn/open-apis/bot/v2/hook/xxx
        secret: xxx  # 可选，签名密钥
```

或通过系统属性配置：

```bash
java -jar your-app.jar \
  -Dexecution.monitor.alert.feishu.webhook-url=https://open.feishu.cn/open-apis/bot/v2/hook/xxx \
  -Dexecution.monitor.alert.feishu.secret=xxx
```

#### 3. 告警消息格式

飞书告警使用富文本格式，支持更丰富的样式：

**任务失败告警**：

```
【任务失败告警】

任务名称: processOrder
任务ID: exec-20231218-001
业务标识: order-12345
状态: FAILED
异常类型: java.net.SocketTimeoutException
异常消息: Read timed out
重试次数: 3/3

---
主机: hostname@12345
时间: 2023-12-18 14:30:00
```

#### 4. 飞书 vs 钉钉

| 特性   | 钉钉         | 飞书          |
|------|------------|-------------|
| 消息格式 | Markdown   | 富文本         |
| 安全验证 | 加签         | 签名校验        |
| 样式支持 | 基础Markdown | 富文本（颜色、加粗等） |
| 性能   | 稳定         | 稳定          |

---

#### 5. 禁用告警的影响

如果未配置告警服务，任务失败时会在日志中输出 **WARN** 级别的警告：

```
⚠️  【告警】任务已达到最大重试次数，最终失败 - executionId: exec-001, executionName: processOrder,
    retryCount: 3/3, error: SocketTimeoutException: Read timed out.
    建议配置告警服务以接收实时通知！
```

---

## 自定义告警服务

你可以实现自己的告警服务来支持短信、邮件、电话、企业微信等多种告警方式。

### 实现步骤

#### 1. 实现 AlertService 接口

```java
package com.example.alert;

import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CustomAlertService implements AlertService {

    @Override
    public void sendFailureAlert(ExecutionRecord execution) {
        // 实现任务失败告警逻辑
        String message = buildFailureMessage(execution);
        sendAlert(message);
    }

    @Override
    public void sendHeartbeatTimeoutAlert(ExecutionRecord execution) {
        // 实现心跳超时告警逻辑
        String message = buildHeartbeatMessage(execution);
        sendAlert(message);
    }

    @Override
    public void sendRetryAlert(ExecutionRecord execution, int retryCount) {
        // 实现重试告警逻辑
        String message = buildRetryMessage(execution, retryCount);
        sendAlert(message);
    }

    @Override
    public void sendCustomAlert(String title, String content) {
        // 实现自定义告警逻辑
        sendAlert(title + ": " + content);
    }

    private void sendAlert(String message) {
        // 实现具体的告警发送逻辑
        log.info("发送告警: {}", message);
    }

    private String buildFailureMessage(ExecutionRecord execution) {
        return String.format("任务失败 - 任务名称: %s, 任务ID: %s, 错误: %s",
                execution.getExecutionName().getValue(),
                execution.getExecutionId().getValue(),
                execution.getErrorInfo() != null ? execution.getErrorInfo().getErrorMessage() : "未知错误");
    }

    private String buildHeartbeatMessage(ExecutionRecord execution) {
        return String.format("心跳超时 - 任务名称: %s, 任务ID: %s",
                execution.getExecutionName().getValue(),
                execution.getExecutionId().getValue());
    }

    private String buildRetryMessage(ExecutionRecord execution, int retryCount) {
        return String.format("任务重试 - 任务名称: %s, 第%d次重试",
                execution.getExecutionName().getValue(),
                retryCount);
    }
}
```

#### 2. 注册为Spring Bean

Spring Boot会自动扫描并注册你的 `AlertService` 实现。如果你的实现类添加了 `@Component` 注解，Spring会自动使用它替代默认的钉钉实现。

如果需要更精细的控制，可以通过 `@Configuration` 类注册：

```java
package com.example.config;

import com.example.alert.CustomAlertService;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AlertConfiguration {

    /**
     * 注册自定义告警服务
     * @Primary 注解确保优先使用此实现
     */
    @Bean
    @Primary
    public AlertService customAlertService() {
        return new CustomAlertService();
    }
}
```

---

## 多种告警方式示例

### 示例1: 短信告警（阿里云短信）

```java
package com.example.alert;

import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertService;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmsAlertService implements AlertService {

    private final Client smsClient;

    @Value("${alert.sms.phone-numbers}")
    private String phoneNumbers;

    @Value("${alert.sms.sign-name}")
    private String signName;

    @Value("${alert.sms.template-code}")
    private String templateCode;

    public SmsAlertService(Client smsClient) {
        this.smsClient = smsClient;
    }

    @Override
    public void sendFailureAlert(ExecutionRecord execution) {
        try {
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phoneNumbers)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam(buildTemplateParam(execution));

            smsClient.sendSms(request);
            log.info("短信告警发送成功: executionId={}", execution.getExecutionId());
        } catch (Exception e) {
            log.error("发送短信告警失败", e);
        }
    }

    @Override
    public void sendHeartbeatTimeoutAlert(ExecutionRecord execution) {
        // 心跳超时可能不需要短信告警，避免过度打扰
        log.debug("跳过心跳超时短信告警: {}", execution.getExecutionId());
    }

    @Override
    public void sendRetryAlert(ExecutionRecord execution, int retryCount) {
        // 重试告警可能不需要短信，避免短信轰炸
        log.debug("跳过重试短信告警: {}", execution.getExecutionId());
    }

    @Override
    public void sendCustomAlert(String title, String content) {
        // 自定义告警逻辑
        log.info("自定义短信告警: {} - {}", title, content);
    }

    private String buildTemplateParam(ExecutionRecord execution) {
        return String.format("{\"taskName\":\"%s\",\"taskId\":\"%s\",\"error\":\"%s\"}",
                execution.getExecutionName().getValue(),
                execution.getExecutionId().getValue(),
                execution.getErrorInfo() != null ? execution.getErrorInfo().getErrorMessage() : "未知错误");
    }
}
```

**配置文件**：

```yaml
alert:
  sms:
    phone-numbers: "13800138000,13900139000"  # 接收告警的手机号，逗号分隔
    sign-name: "系统告警"
    template-code: "SMS_123456789"
```

### 示例2: 邮件告警

```java
package com.example.alert;

import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailAlertService implements AlertService {

    private final JavaMailSender mailSender;

    @Value("${alert.email.from}")
    private String from;

    @Value("${alert.email.to}")
    private String[] to;

    public EmailAlertService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendFailureAlert(ExecutionRecord execution) {
        String subject = String.format("【任务失败告警】%s", execution.getExecutionName().getValue());
        String content = buildFailureContent(execution);
        sendEmail(subject, content);
    }

    @Override
    public void sendHeartbeatTimeoutAlert(ExecutionRecord execution) {
        String subject = String.format("【心跳超时告警】%s", execution.getExecutionName().getValue());
        String content = buildHeartbeatContent(execution);
        sendEmail(subject, content);
    }

    @Override
    public void sendRetryAlert(ExecutionRecord execution, int retryCount) {
        String subject = String.format("【任务重试告警】%s - 第%d次重试",
                execution.getExecutionName().getValue(), retryCount);
        String content = buildRetryContent(execution, retryCount);
        sendEmail(subject, content);
    }

    @Override
    public void sendCustomAlert(String title, String content) {
        sendEmail(title, content);
    }

    private void sendEmail(String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            log.info("邮件告警发送成功");
        } catch (Exception e) {
            log.error("发送邮件告警失败", e);
        }
    }

    private String buildFailureContent(ExecutionRecord execution) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务执行失败通知\n\n");
        sb.append("任务名称: ").append(execution.getExecutionName().getValue()).append("\n");
        sb.append("任务ID: ").append(execution.getExecutionId().getValue()).append("\n");

        if (execution.getBizKey() != null && execution.getBizKey().isPresent()) {
            sb.append("业务标识: ").append(execution.getBizKey().getValue()).append("\n");
        }

        sb.append("状态: ").append(execution.getStatus()).append("\n");
        sb.append("重试次数: ").append(execution.getRetryCount())
                .append("/").append(execution.getMaxRetry()).append("\n");

        if (execution.getErrorInfo() != null) {
            sb.append("异常类型: ").append(execution.getErrorInfo().getExceptionType()).append("\n");
            sb.append("异常消息: ").append(execution.getErrorInfo().getErrorMessage()).append("\n");
        }

        return sb.toString();
    }

    private String buildHeartbeatContent(ExecutionRecord execution) {
        return String.format("任务心跳超时\n\n任务名称: %s\n任务ID: %s\n心跳间隔: %d秒",
                execution.getExecutionName().getValue(),
                execution.getExecutionId().getValue(),
                execution.getHeartbeatIntervalSeconds());
    }

    private String buildRetryContent(ExecutionRecord execution, int retryCount) {
        return String.format("任务正在重试\n\n任务名称: %s\n任务ID: %s\n当前重试: 第%d次\n最大重试: %d次",
                execution.getExecutionName().getValue(),
                execution.getExecutionId().getValue(),
                retryCount,
                execution.getMaxRetry());
    }
}
```

**配置文件**：

```yaml
alert:
  email:
    from: noreply@example.com
    to:
      - admin@example.com
      - ops@example.com

spring:
  mail:
    host: smtp.example.com
    port: 587
    username: your-email@example.com
    password: your-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

### 示例3: 企业微信告警

企业微信告警与飞书类似，参考飞书实现即可。

```java
package com.example.alert;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WeChatWorkAlertService implements AlertService {

    @Value("${alert.wechat.webhook-url}")
    private String webhookUrl;

    @Override
    public void sendFailureAlert(ExecutionRecord execution) {
        String content = buildFailureContent(execution);
        sendMarkdownMessage("任务失败告警", content);
    }

    @Override
    public void sendHeartbeatTimeoutAlert(ExecutionRecord execution) {
        String content = buildHeartbeatContent(execution);
        sendMarkdownMessage("心跳超时告警", content);
    }

    @Override
    public void sendRetryAlert(ExecutionRecord execution, int retryCount) {
        String content = buildRetryContent(execution, retryCount);
        sendMarkdownMessage("任务重试告警", content);
    }

    @Override
    public void sendCustomAlert(String title, String content) {
        sendMarkdownMessage(title, content);
    }

    private void sendMarkdownMessage(String title, String content) {
        try {
            JSONObject message = new JSONObject();
            message.set("msgtype", "markdown");

            JSONObject markdown = new JSONObject();
            markdown.set("content", String.format("### %s\n\n%s", title, content));

            message.set("markdown", markdown);

            String response = HttpUtil.post(webhookUrl, message.toString());
            log.debug("企业微信告警发送成功: {}", response);
        } catch (Exception e) {
            log.error("发送企业微信告警失败", e);
        }
    }

    private String buildFailureContent(ExecutionRecord execution) {
        return String.format(
                "> 任务名称: <font color=\"warning\">%s</font>\n" +
                        "> 任务ID: %s\n" +
                        "> 状态: <font color=\"warning\">%s</font>\n" +
                        "> 错误: %s",
                execution.getExecutionName().getValue(),
                execution.getExecutionId().getValue(),
                execution.getStatus(),
                execution.getErrorInfo() != null ? execution.getErrorInfo().getErrorMessage() : "未知错误"
        );
    }

    private String buildHeartbeatContent(ExecutionRecord execution) {
        return String.format(
                "> 任务名称: %s\n> 任务ID: %s\n> 心跳间隔: %d秒",
                execution.getExecutionName().getValue(),
                execution.getExecutionId().getValue(),
                execution.getHeartbeatIntervalSeconds()
        );
    }

    private String buildRetryContent(ExecutionRecord execution, int retryCount) {
        return String.format(
                "> 任务名称: %s\n> 任务ID: %s\n> 当前重试: 第%d次\n> 最大重试: %d次",
                execution.getExecutionName().getValue(),
                execution.getExecutionId().getValue(),
                retryCount,
                execution.getMaxRetry()
        );
    }
}
```

**配置文件**：

```yaml
alert:
  wechat:
    webhook-url: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
```

> **提示**：企业微信机器人配置方式与钉钉类似，可参考内置的钉钉和飞书实现进行扩展。

### 示例4: 电话告警（阿里云语音）

```java
package com.example.alert;

import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertService;
import com.aliyun.dyvmsapi20170525.Client;
import com.aliyun.dyvmsapi20170525.models.SingleCallByTtsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PhoneAlertService implements AlertService {

    private final Client voiceClient;

    @Value("${alert.phone.numbers}")
    private String phoneNumbers;

    @Value("${alert.phone.tts-code}")
    private String ttsCode;

    public PhoneAlertService(Client voiceClient) {
        this.voiceClient = voiceClient;
    }

    @Override
    public void sendFailureAlert(ExecutionRecord execution) {
        // 只有严重失败才发送电话告警
        if (execution.getRetryCount() >= execution.getMaxRetry()) {
            String message = String.format("任务%s执行失败，请立即处理",
                    execution.getExecutionName().getValue());
            makePhoneCall(message);
        }
    }

    @Override
    public void sendHeartbeatTimeoutAlert(ExecutionRecord execution) {
        // 心跳超时不发送电话告警，避免过度打扰
        log.debug("跳过心跳超时电话告警");
    }

    @Override
    public void sendRetryAlert(ExecutionRecord execution, int retryCount) {
        // 重试不发送电话告警
        log.debug("跳过重试电话告警");
    }

    @Override
    public void sendCustomAlert(String title, String content) {
        makePhoneCall(title + "，" + content);
    }

    private void makePhoneCall(String message) {
        try {
            SingleCallByTtsRequest request = new SingleCallByTtsRequest()
                    .setCalledNumber(phoneNumbers)
                    .setTtsCode(ttsCode)
                    .setTtsParam(String.format("{\"message\":\"%s\"}", message));

            voiceClient.singleCallByTts(request);
            log.info("电话告警发送成功");
        } catch (Exception e) {
            log.error("发送电话告警失败", e);
        }
    }
}
```

### 示例5: 组合告警（多种方式）

```java
package com.example.alert;

import cn.rhymed.execution.monitor.domain.aggregate.ExecutionRecord;
import cn.rhymed.execution.monitor.infrastructure.alert.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 组合告警服务 - 根据告警级别选择不同的告警方式
 *
 * 告警级别:
 * - 低: 仅钉钉/企业微信
 * - 中: 钉钉/企业微信 + 邮件
 * - 高: 钉钉/企业微信 + 邮件 + 短信
 * - 紧急: 所有方式 + 电话
 */
@Slf4j
@Component
public class CompositeAlertService implements AlertService {

    private final List<AlertService> alertServices;

    public CompositeAlertService(List<AlertService> alertServices) {
        // 移除自己，避免循环调用
        this.alertServices = alertServices.stream()
                .filter(service -> !(service instanceof CompositeAlertService))
                .toList();
    }

    @Override
    public void sendFailureAlert(ExecutionRecord execution) {
        AlertLevel level = determineAlertLevel(execution);

        switch (level) {
            case LOW:
                // 仅即时通讯工具
                sendToInstantMessaging(execution);
                break;
            case MEDIUM:
                // 即时通讯 + 邮件
                sendToInstantMessaging(execution);
                sendToEmail(execution);
                break;
            case HIGH:
                // 即时通讯 + 邮件 + 短信
                sendToInstantMessaging(execution);
                sendToEmail(execution);
                sendToSms(execution);
                break;
            case CRITICAL:
                // 所有方式
                alertServices.forEach(service ->
                        service.sendFailureAlert(execution));
                break;
        }
    }

    @Override
    public void sendHeartbeatTimeoutAlert(ExecutionRecord execution) {
        // 心跳超时仅发送即时通讯告警
        sendToInstantMessaging(execution);
    }

    @Override
    public void sendRetryAlert(ExecutionRecord execution, int retryCount) {
        // 重试仅发送即时通讯告警
        sendToInstantMessaging(execution);
    }

    @Override
    public void sendCustomAlert(String title, String content) {
        // 自定义告警发送到所有渠道
        alertServices.forEach(service ->
                service.sendCustomAlert(title, content));
    }

    private AlertLevel determineAlertLevel(ExecutionRecord execution) {
        // 根据任务名称、重试次数等判断告警级别
        String taskName = execution.getExecutionName().getValue();
        int retryCount = execution.getRetryCount();

        if (taskName.contains("critical") || taskName.contains("payment")) {
            return AlertLevel.CRITICAL;
        }

        if (retryCount >= 3) {
            return AlertLevel.HIGH;
        }

        if (retryCount >= 1) {
            return AlertLevel.MEDIUM;
        }

        return AlertLevel.LOW;
    }

    private void sendToInstantMessaging(ExecutionRecord execution) {
        alertServices.stream()
                .filter(service -> service instanceof DingTalkAlertService
                        || service instanceof WeChatWorkAlertService)
                .forEach(service -> service.sendFailureAlert(execution));
    }

    private void sendToEmail(ExecutionRecord execution) {
        alertServices.stream()
                .filter(service -> service instanceof EmailAlertService)
                .forEach(service -> service.sendFailureAlert(execution));
    }

    private void sendToSms(ExecutionRecord execution) {
        alertServices.stream()
                .filter(service -> service instanceof SmsAlertService)
                .forEach(service -> service.sendFailureAlert(execution));
    }

    private enum AlertLevel {
        LOW,      // 低
        MEDIUM,   // 中
        HIGH,     // 高
        CRITICAL  // 紧急
    }
}
```

---

## 最佳实践

### 1. 告警分级

不同严重程度的问题使用不同的告警方式：

| 告警级别   | 使用场景            | 推荐方式              |
|--------|-----------------|-------------------|
| **低**  | 一般重试、非关键任务失败    | 钉钉/企业微信           |
| **中**  | 多次重试失败、重要任务异常   | 钉钉/企业微信 + 邮件      |
| **高**  | 达到最大重试次数、关键任务失败 | 钉钉/企业微信 + 邮件 + 短信 |
| **紧急** | 支付、订单等核心业务失败    | 所有方式 + 电话         |

### 2. 避免告警轰炸

- 重试告警不要发送短信/电话
- 心跳超时仅发送即时通讯告警
- 设置告警频率限制（如5分钟内相同任务只告警一次）

示例：添加告警频率限制

```java

@Slf4j
@Component
public class RateLimitedAlertService implements AlertService {

    private final AlertService delegate;
    private final Map<String, Long> lastAlertTimes = new ConcurrentHashMap<>();

    // 告警间隔（毫秒）
    private static final long ALERT_INTERVAL = 5 * 60 * 1000; // 5分钟

    public RateLimitedAlertService(AlertService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void sendFailureAlert(ExecutionRecord execution) {
        String key = execution.getExecutionName().getValue();

        if (shouldSendAlert(key)) {
            delegate.sendFailureAlert(execution);
            lastAlertTimes.put(key, System.currentTimeMillis());
        } else {
            log.debug("告警频率限制，跳过发送: {}", key);
        }
    }

    private boolean shouldSendAlert(String key) {
        Long lastTime = lastAlertTimes.get(key);
        if (lastTime == null) {
            return true;
        }
        return System.currentTimeMillis() - lastTime >= ALERT_INTERVAL;
    }

    // ... 实现其他方法
}
```

### 3. 异步发送告警

告警发送可能耗时，建议使用异步方式：

```java

@Slf4j
@Component
public class AsyncAlertService implements AlertService {

    private final AlertService delegate;
    private final ExecutorService executor;

    public AsyncAlertService(AlertService delegate) {
        this.delegate = delegate;
        this.executor = Executors.newFixedThreadPool(2,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public void sendFailureAlert(ExecutionRecord execution) {
        executor.submit(() -> {
            try {
                delegate.sendFailureAlert(execution);
            } catch (Exception e) {
                log.error("异步发送告警失败", e);
            }
        });
    }

    // ... 实现其他方法
}
```

### 4. 告警内容要点

告警消息应包含以下关键信息：

- **任务名称**：快速识别出问题的任务
- **任务ID**：用于排查和关联日志
- **业务标识**（bizKey）：定位具体业务数据
- **错误信息**：了解失败原因
- **重试信息**：当前重试次数和最大重试次数
- **时间和主机**：确定问题发生的时间和位置

### 5. 环境隔离

不同环境使用不同的告警配置：

```yaml
# application-dev.yml
execution:
  monitor:
    alert:
      dingtalk:
        enabled: true
        webhook-url: ${DEV_DINGTALK_WEBHOOK}

# application-prod.yml
execution:
  monitor:
    alert:
      dingtalk:
        enabled: true
        webhook-url: ${PROD_DINGTALK_WEBHOOK}
```

或者使用Spring Profile：

```java

@Configuration
@Profile("prod")
public class ProdAlertConfiguration {

    @Bean
    @Primary
    public AlertService prodAlertService() {
        // 生产环境使用组合告警
        return new CompositeAlertService();
    }
}

@Configuration
@Profile("dev")
public class DevAlertConfiguration {

    @Bean
    @Primary
    public AlertService devAlertService() {
        // 开发环境仅使用日志
        return new LogAlertService();
    }
}
```

---

## 常见问题

### Q1: 如何同时使用多个告警服务？

使用组合模式，参考 [示例5: 组合告警](#示例5-组合告警多种方式)。

### Q2: 告警发送失败会影响业务吗？

不会。告警发送是异步的，即使失败也只会记录日志，不会影响业务执行。

### Q3: 如何测试告警是否正常工作？

可以使用 `sendCustomAlert` 方法手动触发测试：

```java

@Autowired
private AlertService alertService;

public void testAlert() {
    alertService.sendCustomAlert("测试告警", "这是一条测试消息");
}
```

### Q4: 告警服务未配置时会怎样？

如果没有配置 AlertService，任务失败时会在日志中输出 WARN 级别的警告，确保问题不会被忽略。

### Q5: 如何禁用特定类型的告警？

在你的 AlertService 实现中，针对不需要的告警类型返回即可：

```java

@Override
public void sendRetryAlert(ExecutionRecord execution, int retryCount) {
    // 不发送重试告警
    log.debug("跳过重试告警");
}
```

---

## 总结

Execution Monitor 的告警机制非常灵活：

1. **开箱即用**：内置钉钉告警，配置即可使用
2. **易于扩展**：实现 AlertService 接口即可支持任意告警方式
3. **灵活组合**：可以同时使用多种告警方式
4. **安全可靠**：告警失败不影响业务，WARN日志兜底

选择合适的告警方式，让你的系统异常"看得见"，问题"处理快"！
