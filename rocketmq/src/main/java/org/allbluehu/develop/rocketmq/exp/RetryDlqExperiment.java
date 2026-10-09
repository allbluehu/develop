package org.allbluehu.develop.rocketmq.exp;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 实验二：消费重试与死信队列。
 * <p>
 * 并发消费模式下，监听方法抛异常 = 消费失败，Broker 会把消息转入
 * 重试队列 %RETRY%消费组名，按延迟级别梯度重投（首约 10s，再 30s、1m…），
 * 超过 maxReconsumeTimes 后进死信 %DLQ%消费组名，等人工兜底。
 * <p>
 * 理解两个关键点：
 * <ol>
 *   <li>「重试」不是本地 for 循环，而是 Broker 侧的延迟重投——所以
 *       at-least-once 语义下消息必然可能被投递多次，幂等是必修课（见实验三）；</li>
 *   <li>顺序消费（ORDERLY）失败走的是「挂起当前队列本地重试」，不进
 *       %RETRY% 队列，避免乱序——对比见实验四注释。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryDlqExperiment {

    public static final String TOPIC = "RETRY_DLQ_TOPIC";
    /** 死信 Topic 的命名规则：%DLQ% + 消费组名 */
    public static final String DLQ_TOPIC = "%DLQ%lab-retry-group";

    private final RocketMQTemplate template;

    private final AtomicLong retryHitCount = new AtomicLong();
    private final AtomicLong dlqCount = new AtomicLong();

    /**
     * mode=flap：前 2 次消费抛异常、第 3 次成功——观察 10s/30s 梯度重投后最终成功。
     * mode=always：永远失败——重试耗尽（maxReconsumeTimes=2）后进死信，被 DlqWatcher 收到。
     */
    public String trigger(String mode) {
        Assert.isTrue(Set.of("flap", "always").contains(String.valueOf(mode)), "mode 必须为 flap 或 always");
        String bizId = "biz-" + UUID.randomUUID();
        String payload = switch (mode) {
            case "always" -> "ALWAYS_FAIL:" + bizId;
            default -> "NEED_RETRY:" + bizId;
        };
        template.syncSend(TOPIC, MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, bizId).build());
        return "已发送 " + bizId + "（mode=" + mode + "），盯日志：flap 约 40s 内成功；always 约 40s 后进死信";
    }

    public String stats() {
        return "重试后成功累计=" + retryHitCount.get() + ", 进入死信累计=" + dlqCount.get();
    }

    @Slf4j
    @Component
    @RocketMQMessageListener(topic = TOPIC, consumerGroup = "lab-retry-group", maxReconsumeTimes = 2)
    public static class RetryConsumer implements RocketMQListener<MessageExt> {

        private final RetryDlqExperiment exp;

        RetryConsumer(RetryDlqExperiment exp) {
            this.exp = exp;
        }

        @Override
        public void onMessage(MessageExt message) {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            String bizId = message.getKeys();
            // 使用 Broker 携带的重投次数，避免本地计数在重启、多实例或发送回调竞态下失真。
            int times = message.getReconsumeTimes() + 1;

            if (body.startsWith("ALWAYS_FAIL")) {
                // 抛异常 = 告诉容器消费失败（等价返回 RECONSUME_LATER）
                throw new IllegalStateException("模拟业务持续失败 bizId=" + bizId
                        + " 第" + times + "次消费, reconsumeTimes=" + message.getReconsumeTimes());
            }
            if (body.startsWith("NEED_RETRY") && times < 3) {
                throw new IllegalStateException("模拟业务抖动 bizId=" + bizId
                        + " 第" + times + "次消费失败, 稍后由 Broker 梯度重投");
            }
            // 走到这里：要么一次成功，要么重试成功。注意观察正常路径下这行日志的时间戳间隔
            if (times > 1) {
                exp.retryHitCount.incrementAndGet();
            }
            log.info("[重试消费者] bizId={} 第{}次消费成功, 累计重投命中={}", bizId, times, exp.retryHitCount.get());
        }
    }

    /**
     * 死信观察者：订阅 %DLQ%lab-retry-group。应用启动时该 Topic 还不存在，
     * 消费者只是空转（日志有 no route 告警）；第一条死信落进去后自动开始接收。
     * <p>
     * 生产上的标准姿势：死信消费者接到消息后告警 + 落库 + 人工/程序补偿。
     */
    @Slf4j
    @Component
    @RocketMQMessageListener(topic = DLQ_TOPIC, consumerGroup = "lab-dlq-watcher")
    public static class DlqWatcher implements RocketMQListener<MessageExt> {

        private final RetryDlqExperiment exp;

        DlqWatcher(RetryDlqExperiment exp) {
            this.exp = exp;
        }

        @Override
        public void onMessage(MessageExt message) {
            exp.dlqCount.incrementAndGet();
            log.error("[死信观察者] 收到死信! body={}, keys={}, 原始重试次数={} —— 生产上这里应告警+入库+补偿",
                    new String(message.getBody(), StandardCharsets.UTF_8), message.getKeys(), message.getReconsumeTimes());
        }
    }
}
