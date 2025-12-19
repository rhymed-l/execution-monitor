package cn.rhymed.execution.monitor.interfaces.config;

import cn.rhymed.execution.monitor.common.enums.AlertMessageType;
import cn.rhymed.execution.monitor.common.enums.RecoveryStrategy;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 执行监控配置属性
 * <p>
 * 配置前缀：execution.monitor
 * <p>
 * 该配置类包含了执行监控框架的所有配置项，包括：
 * <ul>
 *   <li>基础配置：启用开关、存储类型</li>
 *   <li>心跳监控：任务心跳上报和超时检测</li>
 *   <li>重试机制：失败重试、指数退避</li>
 *   <li>自动恢复：启动时恢复未完成的任务</li>
 *   <li>参数序列化：任务参数的保存和恢复</li>
 *   <li>记录清理：定期清理过期的执行记录</li>
 *   <li>分布式锁：多实例部署时防止任务重复执行</li>
 *   <li>告警通知：钉钉、飞书告警</li>
 * </ul>
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "execution.monitor")
public class MonitorProperties {

    /**
     * 是否启用执行监控功能
     * <p>
     * true: 启用监控（默认）<br>
     * false: 禁用监控
     * <p>
     * 禁用后，所有监控相关的功能都不会生效，包括AOP切面、重试、恢复等。
     * <p>
     * 配置项：execution.monitor.enabled
     * <p>
     * 默认值：true
     */
    private boolean enabled = true;

    /**
     * 存储类型
     * <p>
     * 支持三种存储后端：
     * <ul>
     *   <li>memory: 内存存储（适用于单实例、测试环境，重启后数据丢失）</li>
     *   <li>redis: Redis存储（适用于分布式部署，支持多实例共享数据）</li>
     *   <li>database: 数据库存储（适用于生产环境，数据持久化）</li>
     * </ul>
     * <p>
     * 配置项：execution.monitor.storage-type
     * <p>
     * 默认值：memory
     */
    private String storageType = "memory";

    /**
     * 心跳监控配置
     * <p>
     * 配置长时间运行任务的心跳上报和超时检测。
     */
    private Heartbeat heartbeat = new Heartbeat();

    /**
     * 重试配置
     * <p>
     * 配置任务失败后的自动重试机制。
     */
    private Retry retry = new Retry();

    /**
     * 自动恢复配置
     * <p>
     * 配置应用启动时对未完成任务的恢复策略。
     */
    private Recovery recovery = new Recovery();

    /**
     * 参数序列化配置
     * <p>
     * 配置任务参数的序列化和保存规则。
     */
    private Serialization serialization = new Serialization();

    /**
     * 执行记录清理配置
     * <p>
     * 配置过期执行记录的定期清理策略。
     */
    private Cleanup cleanup = new Cleanup();

    /**
     * 分布式锁配置
     * <p>
     * 配置多实例部署时的分布式锁机制。
     */
    private DistributedLock distributedLock = new DistributedLock();

    /**
     * 告警配置
     * <p>
     * 配置任务失败时的告警通知渠道。
     */
    private Alert alert = new Alert();

    /**
     * 心跳监控配置
     */
    @Setter
    @Getter
    public static class Heartbeat {
        /**
         * 是否启用心跳监控
         * <p>
         * true: 启用心跳监控（默认）<br>
         * false: 禁用心跳监控
         * <p>
         * 启用后，长时间运行的任务会定期上报心跳，系统会监控任务是否超时。
         * <p>
         * 默认值：true
         */
        private boolean enabled = true;

        /**
         * 任务心跳上报间隔（秒）
         * <p>
         * 长时间运行的任务会定期上报心跳，证明任务仍在正常执行。
         * <p>
         * 建议值：60-300秒
         * <p>
         * 默认值：300秒（5分钟）
         */
        private int intervalSeconds = 300;

        /**
         * 系统心跳检查间隔（秒）
         * <p>
         * 系统定期检查是否有任务心跳超时。
         * 建议设置为 intervalSeconds 的 2 倍，以便及时发现超时任务。
         * <p>
         * 默认值：600秒（10分钟）
         */
        private int checkIntervalSeconds = 600;

    }

    /**
     * 重试配置
     */
    @Setter
    @Getter
    public static class Retry {
        /**
         * 默认最大重试次数
         * <p>
         * 当任务执行失败时，系统会自动重试的最大次数。
         * 例如设置为 3，表示任务失败后最多重试 3 次（总共执行 4 次：首次 + 3次重试）
         * <p>
         * 注意：可以在 @Monitor 注解中通过 maxRetry 属性覆盖此全局配置
         * <p>
         * 默认值：3
         */
        private int maxRetry = 3;

        /**
         * 基础重试间隔（秒）
         * <p>
         * 两次重试之间的基础等待时间。
         * 例如设置为 60，表示重试前至少等待 60 秒。
         * 如果启用了指数退避，实际等待时间会逐次增长。
         * <p>
         * 默认值：60秒
         */
        private int baseIntervalSeconds = 60;

        /**
         * 是否启用指数退避策略
         * <p>
         * true: 每次重试的等待时间呈指数增长（如 60s, 120s, 240s...）<br>
         * false: 每次重试使用固定的等待时间（都是 baseIntervalSeconds）
         * <p>
         * 指数退避可以避免频繁重试对系统造成压力，推荐启用。
         * <p>
         * 默认值：true
         */
        private boolean exponentialBackoff = true;

        /**
         * 可重试的异常类型（类名包含匹配）
         * <p>
         * 当抛出这些异常时，任务会被标记为可重试状态。
         * 适用于临时性错误，如网络超时、数据库连接失败等。
         * <p>
         * 示例配置：
         * <ul>
         *   <li>java.util.concurrent.TimeoutException - 超时异常</li>
         *   <li>java.io.IOException - IO异常</li>
         *   <li>java.sql.SQLException - 数据库异常</li>
         *   <li>org.springframework.dao.DataAccessException - Spring数据访问异常</li>
         * </ul>
         */
        private List<String> retryableExceptions = new ArrayList<>();

        /**
         * 可忽略的异常类型（类名包含匹配）
         * <p>
         * 当抛出这些异常时，任务会直接标记为失败，不会进行重试。
         * 适用于业务逻辑错误等不应该重试的场景。
         * 例如参数错误、空指针异常等，重试也无法成功。
         * <p>
         * 示例配置：
         * <ul>
         *   <li>java.lang.IllegalArgumentException - 非法参数异常</li>
         *   <li>java.lang.NullPointerException - 空指针异常</li>
         * </ul>
         */
        private List<String> ignorableExceptions = new ArrayList<>();

    }

    /**
     * 自动恢复配置
     */
    @Setter
    @Getter
    public static class Recovery {
        /**
         * 是否启用自动恢复功能
         * <p>
         * true: 启用自动恢复<br>
         * false: 禁用自动恢复（默认）
         * <p>
         * 启用后，系统会在启动时检查未完成的任务并尝试恢复。
         * <p>
         * 默认值：false
         */
        private boolean enabled = false;

        /**
         * 默认恢复策略
         * <p>
         * AUTO: 根据任务状态自动决定是否恢复（推荐）<br>
         * ALWAYS: 总是尝试恢复未完成的任务<br>
         * NEVER: 从不自动恢复<br>
         * CUSTOM: 使用自定义恢复处理器
         * <p>
         * 默认值：AUTO
         */
        private RecoveryStrategy strategy = RecoveryStrategy.AUTO;

        /**
         * 启动时是否立即执行恢复检查
         * <p>
         * true: 应用启动时立即检查并恢复（默认）<br>
         * false: 不在启动时检查
         * <p>
         * 默认值：true
         */
        private boolean checkOnStartup = true;

        /**
         * 定期恢复检查间隔（秒）
         * <p>
         * 0: 不启用定期检查（默认）<br>
         * &gt;0: 定期检查并恢复未完成的任务
         * <p>
         * 默认值：0（不启用）
         */
        private int checkIntervalSeconds = 0;

    }

    /**
     * 参数序列化配置
     */
    @Setter
    @Getter
    public static class Serialization {
        /**
         * 是否启用智能序列化
         * <p>
         * true: 启用智能序列化（默认）<br>
         * false: 禁用序列化
         * <p>
         * 启用后会自动判断参数是否需要序列化保存，用于任务重试时恢复参数。
         * <p>
         * 默认值：true
         */
        private boolean enabled = true;

        /**
         * 参数序列化大小限制（字节）
         * <p>
         * 超过此大小的参数将不会被序列化保存。
         * 建议根据实际业务场景调整，避免保存过大的参数影响性能。
         * <p>
         * 默认值：10240字节（10KB）
         */
        private int maxSizeBytes = 10240; // 10KB

    }

    /**
     * 执行记录清理配置
     */
    @Setter
    @Getter
    public static class Cleanup {
        /**
         * 是否启用定期清理功能
         * <p>
         * true: 启用定期清理<br>
         * false: 禁用定期清理（默认）
         * <p>
         * 推荐生产环境启用，定期清理过期的执行记录，避免数据堆积。
         * <p>
         * 默认值：false
         */
        private boolean enabled = false;

        /**
         * 执行记录保留天数
         * <p>
         * 超过此天数的已完成/已失败记录会被自动清理。
         * <p>
         * 建议值：7-30天
         * <p>
         * 默认值：7天
         */
        private int retentionDays = 7;

        /**
         * 清理任务执行间隔（秒）
         * <p>
         * 定期执行清理任务的时间间隔。
         * 建议在业务低峰期执行清理任务。
         * <p>
         * 默认值：86400秒（1天）
         */
        private int intervalSeconds = 86400; // 1天

    }

    /**
     * 分布式锁配置
     * <p>
     * 用于多实例部署时防止重复执行任务，确保任务的唯一性和一致性。
     */
    @Setter
    @Getter
    public static class DistributedLock {
        /**
         * 是否启用分布式锁
         * <p>
         * true: 启用分布式锁（默认，推荐多实例部署时启用）<br>
         * false: 禁用分布式锁（仅适用于单实例部署）
         * <p>
         * 注意：
         * <ul>
         *   <li>单实例部署时可以设置为false以提升性能</li>
         *   <li>内存存储模式会自动使用本地锁实现</li>
         *   <li>多实例部署时建议使用database或redis存储，并启用分布式锁</li>
         * </ul>
         * <p>
         * 默认值：true（安全优先）
         */
        private boolean enabled = true;

        /**
         * 锁超时时间（秒）
         * <p>
         * 防止实例宕机后锁永远不释放，导致任务无法执行。
         * 建议设置为任务最大执行时间的 2-3 倍。
         * <p>
         * 默认值：300秒（5分钟）
         */
        private int timeoutSeconds = 300;

    }

    /**
     * 告警配置
     * <p>
     * 支持多种告警渠道，可以同时启用多个告警服务。
     */
    @Setter
    @Getter
    public static class Alert {
        /**
         * 钉钉告警配置
         */
        private DingTalk dingtalk = new DingTalk();

        /**
         * 飞书(Lark)告警配置
         */
        private Lark lark = new Lark();

        /**
         * 钉钉告警配置
         */
        @Setter
        @Getter
        public static class DingTalk {
            /**
             * 是否启用钉钉告警
             * <p>
             * true: 启用钉钉告警<br>
             * false: 禁用钉钉告警（默认）
             * <p>
             * 启用后，任务失败或重试时会发送告警消息到钉钉群。
             * <p>
             * 默认值：false
             */
            private boolean enabled = false;

            /**
             * 钉钉机器人 Webhook URL
             * <p>
             * 格式：https://oapi.dingtalk.com/robot/send?access_token=xxx
             * <p>
             * 获取方式：
             * <ol>
             *   <li>打开钉钉群</li>
             *   <li>群设置 -> 智能群助手</li>
             *   <li>添加机器人 -> 自定义机器人</li>
             *   <li>复制 Webhook URL</li>
             * </ol>
             */
            private String webhookUrl;

            /**
             * 钉钉机器人加签密钥（可选）
             * <p>
             * 用于增强安全性，建议配置。
             * <p>
             * 获取方式：创建机器人时选择"加签"安全设置，系统会生成密钥。
             */
            private String secret;

            /**
             * 要发送的告警消息类型
             * <p>
             * 可选值：
             * <ul>
             *   <li>FAILURE: 任务最终失败告警（推荐始终启用）</li>
             *   <li>RETRY: 任务执行失败告警（失败但还能重试时）</li>
             *   <li>HEARTBEAT_TIMEOUT: 心跳超时告警</li>
             * </ul>
             * <p>
             * 示例配置：
             * <pre>
             * # 只发送最终失败告警
             * message-types:
             *   - FAILURE
             *
             * # 发送失败和重试告警
             * message-types:
             *   - FAILURE
             *   - RETRY
             *
             * # 发送所有类型的告警
             * message-types:
             *   - FAILURE
             *   - RETRY
             *   - HEARTBEAT_TIMEOUT
             * </pre>
             * <p>
             * 默认值：[FAILURE, RETRY, HEARTBEAT_TIMEOUT] （发送所有类型）
             */
            private Set<AlertMessageType> messageTypes = new HashSet<>(Arrays.asList(
                    AlertMessageType.FAILURE,
                    AlertMessageType.RETRY,
                    AlertMessageType.HEARTBEAT_TIMEOUT
            ));
        }

        /**
         * 飞书(Lark)告警配置
         */
        @Setter
        @Getter
        public static class Lark {
            /**
             * 是否启用飞书告警
             * <p>
             * true: 启用飞书告警<br>
             * false: 禁用飞书告警（默认）
             * <p>
             * 启用后，任务失败或重试时会发送告警消息到飞书群。
             * <p>
             * 默认值：false
             */
            private boolean enabled = false;

            /**
             * 飞书机器人 Webhook URL
             * <p>
             * 格式：https://open.feishu.cn/open-apis/bot/v2/hook/xxx
             * <p>
             * 获取方式：
             * <ol>
             *   <li>打开飞书群</li>
             *   <li>群设置 -> 群机器人</li>
             *   <li>添加机器人 -> 自定义机器人</li>
             *   <li>复制 Webhook URL</li>
             * </ol>
             */
            private String webhookUrl;

            /**
             * 飞书机器人签名密钥（可选）
             * <p>
             * 用于增强安全性，建议配置。
             * <p>
             * 获取方式：创建机器人时选择"签名校验"，系统会生成密钥。
             */
            private String secret;

            /**
             * 要发送的告警消息类型
             * <p>
             * 可选值：
             * <ul>
             *   <li>FAILURE: 任务最终失败告警（推荐始终启用）</li>
             *   <li>RETRY: 任务执行失败告警（失败但还能重试时）</li>
             *   <li>HEARTBEAT_TIMEOUT: 心跳超时告警</li>
             * </ul>
             * <p>
             * 示例配置：
             * <pre>
             * # 只发送最终失败告警
             * message-types:
             *   - FAILURE
             *
             * # 发送失败和重试告警
             * message-types:
             *   - FAILURE
             *   - RETRY
             *
             * # 发送所有类型的告警
             * message-types:
             *   - FAILURE
             *   - RETRY
             *   - HEARTBEAT_TIMEOUT
             * </pre>
             * <p>
             * 默认值：[FAILURE, RETRY, HEARTBEAT_TIMEOUT] （发送所有类型）
             */
            private Set<AlertMessageType> messageTypes = new HashSet<>(Arrays.asList(
                    AlertMessageType.FAILURE,
                    AlertMessageType.RETRY,
                    AlertMessageType.HEARTBEAT_TIMEOUT
            ));
        }
    }
}
