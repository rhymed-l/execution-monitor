package examples;

import cn.rhymed.execution.monitor.application.dto.ExecutionLogDTO;
import cn.rhymed.execution.monitor.interfaces.annotation.ExecutionMonitor;
import cn.rhymed.execution.monitor.interfaces.annotation.ExecutionRecoveryHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Random;

/**
 * Execution Monitor 使用示例
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
@SpringBootApplication
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }

    /**
     * 示例Controller
     */
    @RestController
    public static class ExecutionController {

        private final ExampleService exampleService;

        public ExecutionController(ExampleService exampleService) {
            this.exampleService = exampleService;
        }

        @GetMapping("/example/basic")
        public String basicExecution() {
            exampleService.basicExecution("Hello World");
            return "Basic execution executed";
        }

        @GetMapping("/example/with-bizkey")
        public String executionWithBizKey() {
            exampleService.processOrder("ORDER-12345", "Product A");
            return "Execution with bizKey executed";
        }

        @GetMapping("/example/with-serialization")
        public String executionWithSerialization() {
            exampleService.importData(new String[]{"data1", "data2", "data3"});
            return "Execution with serialization executed";
        }

        @GetMapping("/example/with-heartbeat")
        public String executionWithHeartbeat() {
            exampleService.longRunningExecution("Execution-001");
            return "Long running execution started";
        }

        @GetMapping("/example/with-retry")
        public String executionWithRetry() {
            try {
                exampleService.unstableExecution("API-Call");
            } catch (Exception e) {
                return "Execution failed: " + e.getMessage();
            }
            return "Unstable execution executed";
        }

        @GetMapping("/example/with-custom-recovery")
        public String executionWithCustomRecovery() {
            exampleService.processFile("/tmp/example.txt");
            return "File processing execution executed";
        }
    }

    /**
     * 示例Service
     */
    @Slf4j
    @Service
    public static class ExampleService {

        /**
         * 示例1: 基础任务监控
         */
        @ExecutionMonitor(executionName = "basicExecution")
        public void basicExecution(String message) {
            log.info("执行基础任务: {}", message);
            // 任务执行状态会被自动记录
        }

        /**
         * 示例2: 使用业务键
         */
        @ExecutionMonitor(
                executionName = "processOrder",
                bizKey = "#orderId"  // SpEL表达式
        )
        public void processOrder(String orderId, String product) {
            log.info("处理订单: {}, 产品: {}", orderId, product);
            // bizKey可以用于快速定位特定订单的任务记录
        }

        /**
         * 示例3: 启用参数序列化
         */
        @ExecutionMonitor(
                executionName = "importData",
                serializeParams = true
        )
        public void importData(String[] data) {
            log.info("导入数据，记录数: {}", data.length);
            // 参数会被序列化，失败后可以恢复
            for (String item : data) {
                log.info("处理数据: {}", item);
            }
        }

        /**
         * 示例4: 长时间运行任务，启用心跳
         */
        @ExecutionMonitor(
                executionName = "longRunningExecution",
                bizKey = "#executionId",
                enableHeartbeat = true,
                heartbeatIntervalSeconds = 30
        )
        public void longRunningExecution(String executionId) {
            log.info("开始长时间运行任务: {}", executionId);
            try {
                // 模拟长时间运行
                for (int i = 0; i < 10; i++) {
                    Thread.sleep(10000); // 10秒
                    log.info("任务进度: {}%", (i + 1) * 10);
                    // 系统会自动发送心跳
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("长时间运行任务完成: {}", executionId);
        }

        /**
         * 示例5: 不稳定任务，可能失败需要重试
         */
        @ExecutionMonitor(
                executionName = "unstableExecution",
                bizKey = "#executionId",
                maxRetry = 3
        )
        public void unstableExecution(String executionId) {
            log.info("执行不稳定任务: {}", executionId);
            // 模拟随机失败
            Random random = new Random();
            if (random.nextInt(10) < 7) {  // 70%失败率
                throw new RuntimeException("模拟任务失败");
            }
            log.info("任务成功: {}", executionId);
        }

        /**
         * 示例6: 文件处理任务（配合自定义恢复处理器）
         */
        @ExecutionMonitor(
                executionName = "processFile",
                bizKey = "#filePath",
                serializeParams = true
        )
        public void processFile(String filePath) {
            log.info("处理文件: {}", filePath);
            File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException("文件不存在: " + filePath);
            }
            // 文件处理逻辑
            log.info("文件处理完成: {}", filePath);
        }
    }

    /**
     * 自定义恢复处理器示例
     */
    @Slf4j
    @Component
    public static class CustomRecoveryHandlers {

        /**
         * 文件处理任务的自定义恢复逻辑
         */
        @ExecutionRecoveryHandler(executionName = "processFile", priority = 0)
        public void recoverFileProcessing(ExecutionLogDTO executionLog) {
            log.info("自定义恢复文件处理任务: {}", executionLog.getExecutionId());

            String filePath = executionLog.getBizKey();
            File file = new File(filePath);

            if (file.exists()) {
                log.info("文件存在，重新处理: {}", filePath);
                // 重新处理逻辑
            } else {
                log.warn("文件不存在，无法恢复: {}", filePath);
                // 可以发送告警、记录日志等
            }
        }

        /**
         * 订单处理任务的自定义恢复逻辑
         */
        @ExecutionRecoveryHandler(executionName = "processOrder", priority = 0)
        public void recoverOrderProcessing(ExecutionLogDTO executionLog) {
            log.info("自定义恢复订单处理任务: {}", executionLog.getExecutionId());

            String orderId = executionLog.getBizKey();
            log.info("检查订单状态: {}", orderId);

            // 查询订单状态
            // 根据状态决定如何恢复
            // 可能需要补偿操作、回滚、或重新执行
        }
    }
}
