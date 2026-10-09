package org.allbluehu.develop.rocketmq.exp;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
 * 实验三：幂等消费。
 * <p>
 * 为什么必须做幂等：RocketMQ 的可靠语义是 at-least-once——生产端重试
 * （应对发送超时「到底发没发出去」的模糊态）、消费端重投（见实验二）、
 * Broker 主从切换都可能让同一条业务消息被投递多次。「恰好一次」在
 * 分布式下无法廉价实现，业界共识是：允许重复投递 + 消费端幂等。
 * <p>
 * 演示方式：同一条业务消息（同一个业务 Key）连发 N 次，模拟生产端
 * 重试造成的重复，消费者用「已处理集合」去重，只有第一次真正执行业务。
 * <p>
 * 生产环境的正确姿势（内存 Set 只配演示，重启即失、多实例各存各的）：
 * <ul>
 *   <li>数据库唯一索引：业务表以业务 Key 建唯一约束，插入成功才算处理，天然幂等；</li>
 *   <li>Redis SETNX + 过期时间：跨实例共享去重状态；</li>
 *   <li>状态机校验：如「已支付」订单再收到支付成功消息直接忽略。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentExperiment {

    public static final String TOPIC = "IDEMPOTENT_TOPIC";

    private final RocketMQTemplate template;

    /** 已处理的业务 Key 集合（生产环境请换成 DB 唯一索引或 Redis） */
    private final Set<String> processedBizIds = ConcurrentHashMap.newKeySet();

    private final AtomicLong received = new AtomicLong();
    private final AtomicLong reallyProcessed = new AtomicLong();
    private final AtomicLong deduplicated = new AtomicLong();

    /** 把同一业务 Key 连发 duplicates 次（默认 3），观察消费者只"真处理"一次。 */
    public String sendDuplicates(int duplicates) {
        Assert.isTrue(duplicates >= 1 && duplicates <= 1000, "duplicates 必须在 1 到 1000 之间");
        String bizId = "BIZ-" + UUID.randomUUID();
        for (int i = 1; i <= duplicates; i++) {
            template.syncSend(TOPIC, MessageBuilder.withPayload("pay-success:" + bizId)
                    .setHeader(RocketMQHeaders.KEYS, bizId).build());
        }
        return "业务 Key=" + bizId + " 已连发 " + duplicates + " 条，稍后看 /exp/idempotent/stats";
    }

    public String stats() {
        return "累计接收=" + received.get() + ", 真实处理=" + reallyProcessed.get()
                + ", 幂等去重=" + deduplicated.get();
    }

    @Slf4j
    @Component
    @RocketMQMessageListener(topic = TOPIC, consumerGroup = "lab-idempotent-group")
    public static class IdempotentConsumer implements RocketMQListener<String> {

        private final IdempotentExperiment exp;

        IdempotentConsumer(IdempotentExperiment exp) {
            this.exp = exp;
        }

        @Override
        public void onMessage(String payload) {
            // payload 格式 pay-success:BIZ-xxx，业务 Key 在发消息时同时写进了 KEYS
            String bizId = payload.substring(payload.indexOf(':') + 1);
            exp.received.incrementAndGet();

            // add 返回 false = 集合里已有 = 重复消息。这一行就是幂等的全部秘密
            if (exp.processedBizIds.add(bizId)) {
                exp.reallyProcessed.incrementAndGet();
                log.info("[幂等消费者] bizId={} 首次到达, 执行业务（给账户加钱/发货/发短信...）", bizId);
            } else {
                exp.deduplicated.incrementAndGet();
                log.warn("[幂等消费者] bizId={} 重复到达, 直接忽略 —— 没有幂等这里就会重复扣款!", bizId);
            }
        }
    }
}
