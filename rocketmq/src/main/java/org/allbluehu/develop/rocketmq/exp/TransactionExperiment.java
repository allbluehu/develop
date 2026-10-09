package org.allbluehu.develop.rocketmq.exp;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 实验六：事务消息——解决「本地事务 与 发消息」的原子性。
 * <p>
 * 场景：下单成功（DB 落库）才允许发"订单已创建"消息。先落库后发消息，
 * 发送失败消息就丢了；先发消息后落库，落库失败下游却已经开跑。两阶段：
 * <ol>
 *   <li>半消息（half message）：生产者先发一条对消费者不可见的消息
 *       （存于 RMQ_SYS_TRANS_HALF_TOPIC），Broker 应答后执行本地事务；</li>
 *   <li>二次确认：本地事务成功 → COMMIT（消息搬到真实 Topic，消费者可见）；
 *       失败 → ROLLBACK（半消息作废）；返回 UNKNOWN / 进程挂了 → Broker
 *       定时<b>回查</b> checkLocalTransaction（本环境 broker.conf 配为
 *       6s 后开始、每 15s 一轮、最多 5 次）。</li>
 * </ol>
 * 事务消息通过回查实现最终一致；生产环境需要持久化事务结果供回查读取。
 * 本实验使用进程内存，仅适合单实例演示，重启后不能恢复事务结果。
 * <p>
 * 注意与 Kafka 事务的区别：Kafka 事务解决的是"一批消息跨分区写入的
 * 原子性"（consume-transform-produce 场景），不管你的本地 DB。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionExperiment {

    public static final String TOPIC = "TX_ORDER_TOPIC";

    private final RocketMQTemplate template;

    /** 模拟本地订单表：orderId → 是否已落库 */
    private final Map<String, Boolean> localDb = new ConcurrentHashMap<>();
    /** executeLocalTransaction 返回 UNKNOWN、等待回查的订单 */
    private final Set<String> pendingCheck = ConcurrentHashMap.newKeySet();
    private final AtomicInteger checkCount = new AtomicInteger();

    /**
     * mode=commit：本地事务成功 → 消费者立刻收到消息。
     * mode=rollback：本地事务失败 → 半消息作废，消费者永远收不到。
     * mode=unknown：模拟「本地事务已成功，但确认结果超时」→ 等待 Broker 回查后补 COMMIT。
     */
    public String submit(String mode) {
        Assert.isTrue(Set.of("commit", "rollback", "unknown").contains(String.valueOf(mode)),
                "mode 必须为 commit、rollback 或 unknown");
        String orderId = "TX-" + UUID.randomUUID();
        Message<String> msg = MessageBuilder.withPayload(orderId).setHeader(RocketMQHeaders.KEYS, orderId).build();

        // 第三参 arg 会原样传给 executeLocalTransaction，这里用来指定模拟行为
        var result = template.sendMessageInTransaction(TOPIC, msg, mode);
        return "orderId=%s mode=%s, 半消息发送=%s, 本地事务状态=%s —— 后续看 [事务消费者] 与 [回查] 日志"
                .formatted(orderId, mode, result.getSendStatus(), result.getLocalTransactionState());
    }

    public String stats() {
        return "回查累计=" + checkCount.get() + ", 等待回查=" + pendingCheck;
    }

    /**
     * 事务监听器：一个 RocketMQTemplate 只能绑一个。
     * 回查时 Broker 按「生产者组」反查生产者，所以 producer.group 不能不配。
     */
    @Slf4j
    @RocketMQTransactionListener
    @Component
    public static class TxListener implements RocketMQLocalTransactionListener {

        private final TransactionExperiment exp;

        TxListener(TransactionExperiment exp) {
            this.exp = exp;
        }

        /** 半消息发送成功后立即回调：在这里执行本地事务（插订单、扣库存……）。 */
        @Override
        public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
            String orderId = readOrderId(msg);
            String mode = String.valueOf(arg);

            switch (mode) {
                case "commit":
                    exp.localDb.put(orderId, true);   // 模拟 DB 插入成功
                    log.info("[本地事务] orderId={} 落库成功 → COMMIT，消息对消费者可见", orderId);
                    return RocketMQLocalTransactionState.COMMIT;
                case "rollback":
                    log.warn("[本地事务] orderId={} 落库失败（唯一键冲突/超时回滚）→ ROLLBACK，半消息作废", orderId);
                    return RocketMQLocalTransactionState.ROLLBACK;
                default:
                    // 先记录成功事实，再模拟确认超时；重复回查必须读取同一个最终结果。
                    exp.localDb.put(orderId, true);
                    exp.pendingCheck.add(orderId);
                    log.warn("[本地事务] orderId={} 确认结果未知（模拟超时）→ UNKNOWN，等 Broker 回查", orderId);
                    return RocketMQLocalTransactionState.UNKNOWN;
            }
        }

        /** Broker 主动回查：生产者失联或返回 UNKNOWN 后定期来问"这笔事务到底成没成"。 */
        @Override
        public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
            String orderId = readOrderId(msg);
            int n = exp.checkCount.incrementAndGet();
            boolean dbHasIt = exp.localDb.getOrDefault(orderId, false);
            exp.pendingCheck.remove(orderId); // 仅清理观测标记，不删除事务结果。

            if (dbHasIt) {
                log.warn("[回查] 第{}次: orderId={} 查库=已落库 → 补 COMMIT，消费者此刻才看到消息", n, orderId);
                return RocketMQLocalTransactionState.COMMIT;
            }
            log.warn("[回查] 第{}次: orderId={} 查库=不存在 → ROLLBACK", n, orderId);
            return RocketMQLocalTransactionState.ROLLBACK;
        }

        private String readOrderId(Message<?> msg) {
            // 事务执行和回查均由 RocketMQUtil 从消息体重建 Message，payload 是 byte[]，不是发送时的 String。
            return new String((byte[]) msg.getPayload(), StandardCharsets.UTF_8);
        }
    }

    @Slf4j
    @Component
    @RocketMQMessageListener(topic = TOPIC, consumerGroup = "lab-tx-group")
    public static class TxConsumer implements RocketMQListener<String> {

        @Override
        public void onMessage(String orderId) {
            log.info("[事务消费者] 收到 orderId={} —— 只有半消息被 COMMIT 过，这行日志才会出现", orderId);
        }
    }
}
