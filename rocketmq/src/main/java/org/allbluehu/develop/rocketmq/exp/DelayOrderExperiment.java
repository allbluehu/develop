package org.allbluehu.develop.rocketmq.exp;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
 * 实验五：延迟消息——订单超时未支付自动取消（经典业务落地）。
 * <p>
 * 原理：消息发出后并不直接进目标 Topic，而是先写入系统 Topic
 * SCHEDULE_TOPIC_XXXX，由 Broker 的定时服务在到期后搬运到真实队列。
 * 经典客户端只支持 18 个固定级别（1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m
 * 8m 9m 10m 20m 30m 1h 2h），发送时用 delayLevel 下标；5.x 新客户端
 *（gRPC + timer）支持任意精度延迟。
 * <p>
 * 对比 Kafka：没有原生延迟消息，常见做法是按延迟档位建一组 Topic 轮询，
 * 或上时间轮组件（如 Kafka + 自研、或用 RocketMQ/Pulsar 替代）。
 * <p>
 * 演示用 level=3（10s）缩短等待；业务上"30 分钟超时"对应 level=16。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelayOrderExperiment {

    public static final String TOPIC = "DELAY_ORDER_TOPIC";
    /** 10 秒档位（18 档中第 3 档） */
    private static final int DELAY_LEVEL_10S = 3;

    private final RocketMQTemplate template;

    /** 订单表（单实例内存演示，重启即失）。状态机：UNPAID → PAID / CANCELLED */
    private final Map<String, String> orders = new ConcurrentHashMap<>();

    /** 下单：建单 + 发一条 10s 延迟消息（订单号作为消息体）。 */
    public String createOrder(String sku) {
        Assert.hasText(sku, "sku 不能为空");
        String orderId = "ORD-" + UUID.randomUUID();
        orders.put(orderId, "UNPAID");

        // 最后一个参数就是 delayLevel。10s 后这条消息才会被消费者看见。
        // 内存建单与发送不是原子操作；发送失败需补偿，生产环境应使用持久化 Outbox 等方案。
        template.syncSend(TOPIC, MessageBuilder.withPayload(orderId)
                        .setHeader(RocketMQHeaders.KEYS, orderId).build(),
                3000, DELAY_LEVEL_10S);

        return "下单成功 " + orderId + "（" + sku + "），10s 内不支付将自动取消。下单时间留意日志，可调用支付接口抢在前面";
    }

    /** 支付：把状态从 UNPAID 推到 PAID，让随后的延迟消息"扑空"。 */
    public String pay(String orderId) {
        Assert.hasText(orderId, "orderId 不能为空");
        // 支付和取消竞争同一个前置状态，只有一个操作能成功，避免覆盖终态。
        if (orders.replace(orderId, "UNPAID", "PAID")) {
            return "支付成功 " + orderId + "，等延迟消息到达时会被幂等跳过";
        }
        String status = orders.get(orderId);
        return status == null ? "订单不存在: " + orderId
                : "订单 " + orderId + " 当前状态 " + status + "，不能支付";
    }

    public String listOrders() {
        return orders.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    @Slf4j
    @Component
    @RocketMQMessageListener(topic = TOPIC, consumerGroup = "lab-delay-group")
    public static class DelayConsumer implements RocketMQListener<MessageExt> {

        private final DelayOrderExperiment exp;

        DelayConsumer(DelayOrderExperiment exp) {
            this.exp = exp;
        }

        @Override
        public void onMessage(MessageExt message) {
            String orderId = new String(message.getBody(), StandardCharsets.UTF_8);
            // bornTimestamp = 生产者发出时刻，减出真实延迟，应约等于 10s
            long realDelay = System.currentTimeMillis() - message.getBornTimestamp();
            // 与支付使用相同的原子状态转换，重复投递不会再次修改终态。
            if (exp.orders.replace(orderId, "UNPAID", "CANCELLED")) {
                log.warn("[延迟消费者] 订单 {} 实际延迟 {}ms 到达, 仍未支付 → 自动取消", orderId, realDelay);
            } else {
                log.info("[延迟消费者] 订单 {} 实际延迟 {}ms 到达, 状态={} → 什么都不做（这就是幂等的状态机校验）",
                        orderId, realDelay, exp.orders.get(orderId));
            }
        }
    }
}
