package org.allbluehu.develop.rocketmq.exp;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 实验九：消息积压——观察慢消费与消费实例扩容的效果。
 * <p>
 * flood 灌入消息，slow 设置每条处理耗时，再用 Dashboard 观察消费组积压。
 * 可另起 --server.port=8083 的实例对照；队列数限制能分到队列的实例数，
 * 单实例内仍可由多个线程并发消费，扩容收益需实测，不能假定吞吐翻倍。
 * Topic 实际队列数应以 Dashboard 为准；自动创建时受 Broker 和生产者配置共同影响。
 * 本地 stats 仅提供当前进程累计收发观测值，不能代替消费组积压指标。
 * <p>
 * 面试延伸：线上真积压了的处置顺序——先看消费耗时定位慢在哪（DB 慢查？
 * 外部接口抖动？），能提速先提速；临时扩容受队列数限制时，用"新建
 * N 倍队列的临时 Topic + 快速消费者搬运 + 新消费组分流"三板斧；
 * 实在不行的过期数据评估跳过或落库离线补。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BacklogExperiment {

    public static final String TOPIC = "BACKLOG_TOPIC";

    private final RocketMQTemplate template;

    private final AtomicLong produced = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong consumed = new AtomicLong();
    private final AtomicLong slowMillis = new AtomicLong(0);

    /** 灌入 count 条消息（默认 3000，完成时间取决于 Broker 与客户端负载）。 */
    public String flood(int count) {
        Assert.isTrue(count >= 1 && count <= 50000, "count 必须在 1 到 50000 之间");
        for (int i = 1; i <= count; i++) {
            template.asyncSend(TOPIC, "backlog-" + i, new SendCallback() {
                @Override
                public void onSuccess(SendResult result) {
                    produced.incrementAndGet();
                }

                @Override
                public void onException(Throwable e) {
                    failed.incrementAndGet();
                    log.error("[积压实验] 发送失败", e);
                }
            });
        }
        return "已提交 " + count + " 条异步发送，用 /exp/backlog/stats 观察收发计数，实际积压查看 Dashboard";
    }

    /** 设置每条消息的处理耗时（毫秒），0 = 关闭慢消费。 */
    public String setSlow(long millis) {
        Assert.isTrue(millis >= 0 && millis <= 2000, "millis 必须在 0 到 2000 之间");
        slowMillis.set(millis);
        return "慢消费已设为 " + millis + "ms/条（0 表示关闭）";
    }

    public String stats() {
        long p = produced.get();
        long c = consumed.get();
        // 收发计数属于当前进程；多实例、历史消息及回调时序都会使差值偏离 Broker lag。
        return "本进程累计发送回调成功=" + p + ", 异步失败=" + failed.get()
                + ", 消费完成=" + c + ", 收发差值=" + (p - c)
                + "（非 Broker 积压量，实际积压请查看 Dashboard）";
    }

    @Slf4j
    @Component
    @RocketMQMessageListener(topic = TOPIC, consumerGroup = "lab-backlog-group")
    public static class BacklogConsumer implements RocketMQListener<String> {

        private final BacklogExperiment exp;

        BacklogConsumer(BacklogExperiment exp) {
            this.exp = exp;
        }

        @Override
        public void onMessage(String message) {
            long sleep = exp.slowMillis.get();
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep);   // 模拟慢业务：一条 200ms，单线程每秒 5 条
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("慢消费被中断，交由 RocketMQ 重试", e);
                }
            }
            exp.consumed.incrementAndGet();
        }
    }
}
