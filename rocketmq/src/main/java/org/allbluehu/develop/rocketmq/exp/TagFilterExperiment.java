package org.allbluehu.develop.rocketmq.exp;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 实验七：Tag 过滤——同一个 Topic 的消息按业务类型分流给不同消费组。
 * <p>
 * 服务化场景常用姿势：一个 ORDER_TOPIC 承载订单域全部事件（PAY、
 * STOCK、LOGISTICS 各打一个 Tag），财务系统订阅 "PAY || STOCK"，
 * 物流系统只订 "LOGISTICS"，避免人人各建一个 Topic。
 * <p>
 * 过滤发生在 Broker 端，两级机制：ConsumeQueue 索引里存有 Tag 的
 * hashcode（8 字节），Broker 先按 hash 粗筛掉绝大多数不匹配消息，再把
 * 候选消息传回客户端做字符串精确比对——既省带宽又不失准确性。
 * 复杂条件（如 amount > 100 AND region = 'hz'）可用 selectorType =
 * SQL92，但需 Broker 开 enablePropertyFilter。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagFilterExperiment {

    public static final String TOPIC = "ORDER_EVENT_TOPIC";

    private final RocketMQTemplate template;

    /** 向 ORDER_EVENT_TOPIC 发三种 Tag 各 2 条。 */
    public String send() {
        for (int i = 1; i <= 2; i++) {
            // destination 写法 "topic:tag"，冒号后即 Tag
            template.syncSend(TOPIC + ":PAY", "支付成功事件-" + i);
            template.syncSend(TOPIC + ":STOCK", "扣减库存事件-" + i);
            template.syncSend(TOPIC + ":LOGISTICS", "物流发货事件-" + i);
        }
        return "已发送 PAY/STOCK/LOGISTICS 各 2 条，对比 [财务消费者](只收前两种) 与 [全量消费者] 的日志";
    }

    /** selectorExpression 就是订阅表达式，"||" 连接多个 Tag */
    @Slf4j
    @Component
    @RocketMQMessageListener(topic = TOPIC, consumerGroup = "lab-tag-finance-group",
            selectorExpression = "PAY || STOCK")
    public static class FinanceConsumer implements RocketMQListener<String> {

        @Override
        public void onMessage(String message) {
            log.info("[财务消费者] {} （订阅了 PAY || STOCK，永远看不到物流消息）", message);
        }
    }

    @Slf4j
    @Component
    @RocketMQMessageListener(topic = TOPIC, consumerGroup = "lab-tag-all-group",
            selectorExpression = "*")
    public static class AllConsumer implements RocketMQListener<String> {

        @Override
        public void onMessage(String message) {
            log.info("[全量消费者] {}", message);
        }
    }
}
