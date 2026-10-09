package org.allbluehu.develop.rocketmq.exp;

import java.util.List;
import java.util.UUID;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 实验四：顺序消息。
 * <p>
 * RocketMQ 只保证「队列内 FIFO、队列间无序」，所以顺序消息 = 两件事：
 * <ol>
 *   <li><b>发送端</b>：同一业务 Key（如同一 orderId）的消息必须路由到同一队列。
 *       syncSendOrderly 内部用 MessageQueueSelector 按 shardingKey 做
 *       hash 选队列——和 Kafka「按 key 选 partition」完全同构；</li>
 *   <li><b>消费端</b>：ConsumeMode.ORDERLY，对队列加锁单线程消费，
 *       保证前一条没消费完不碰下一条。</li>
 * </ol>
 * 注意顺序消费的失败处理与并发模式完全不同：抛异常只是「挂起当前队列
 * 稍后本地重试」，不走 %RETRY% 延迟重投——因为一旦重投到别的批次/队列，
 * 顺序就破了。代价是：一条毒消息卡住整个队列（生产上要设好最大重试
 * 后落库跳过）。
 * <p>
 * 对照组（CHAOS_TOPIC）：普通 asyncSend 随机散列到各队列 + 并发消费，
 * 多数情况下能看到同一订单的步骤交错乱序。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderlyExperiment {

    public static final String ORDERLY_TOPIC = "ORDERLY_TOPIC";
    public static final String CHAOS_TOPIC = "CHAOS_TOPIC";

    private static final List<String> STEPS = List.of("CREATE", "PAY", "SHIP", "FINISH");

    private final RocketMQTemplate template;

    /** 正确姿势：每个订单的 4 个步骤经 syncSendOrderly 按 orderId 路由到同一队列。 */
    public String sendOrderly(int orders) {
        Assert.isTrue(orders >= 1 && orders <= 1000, "orders 必须在 1 到 1000 之间");
        String batchId = UUID.randomUUID().toString();
        for (int o = 1; o <= orders; o++) {
            String orderId = "order-" + batchId + "-" + o;
            for (String step : STEPS) {
                template.syncSendOrderly(ORDERLY_TOPIC, orderId + ":" + step, orderId);
            }
        }
        return orders + " 个订单各 " + STEPS.size() + " 步已按 orderId 分队列发送，看 [顺序消费者] 日志";
    }

    /** 对照组：同样的步骤流用普通异步发送 + 并发消费，观察乱序。 */
    public String sendChaos(int orders) {
        Assert.isTrue(orders >= 1 && orders <= 1000, "orders 必须在 1 到 1000 之间");
        String batchId = UUID.randomUUID().toString();
        for (int o = 1; o <= orders; o++) {
            String orderId = "order-" + batchId + "-" + o;
            for (String step : STEPS) {
                template.asyncSend(CHAOS_TOPIC, orderId + ":" + step, LOGGING_CALLBACK);
            }
        }
        return "同样的事件流已散列发送到 " + CHAOS_TOPIC + "，对比 [乱序消费者] 日志（乱序是概率性的，可多触发几次）";
    }

    /** asyncSend 的回调不允许传 null（底层直接调用它），对照组记录发送异常，便于区分乱序与发送失败 */
    private static final SendCallback LOGGING_CALLBACK = new SendCallback() {
        @Override
        public void onSuccess(SendResult result) {
        }

        @Override
        public void onException(Throwable e) {
            log.warn("[乱序发送] 发送失败", e);
        }
    };

    @Slf4j
    @Component
    @RocketMQMessageListener(topic = ORDERLY_TOPIC, consumerGroup = "lab-orderly-group",
            consumeMode = ConsumeMode.ORDERLY)
    public static class OrderlyConsumer implements RocketMQListener<String> {

        @Override
        public void onMessage(String message) {
            // 队列被锁 + 单线程，同一订单的步骤必然按发送顺序到达这里
            log.info("[顺序消费者] {} （队列内 FIFO，锁定队列串行消费）", message);
        }
    }

    @Slf4j
    @Component
    @RocketMQMessageListener(topic = CHAOS_TOPIC, consumerGroup = "lab-chaos-group")
    public static class ChaosConsumer implements RocketMQListener<String> {

        @Override
        public void onMessage(String message) {
            // 并发消费：20 个线程抢消息，网络与调度抖动都会打乱到达顺序
            log.info("[乱序消费者] {} （并发消费，同一订单的步骤可能交错）", message);
        }
    }
}
