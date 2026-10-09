package org.allbluehu.develop.rocketmq.exp;

import java.nio.charset.StandardCharsets;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 实验一：三种发送方式对比（同步 / 异步 / 单向）。
 * <p>
 * 核心差异在「等不等 Broker 应答」：
 * <ul>
 *   <li>syncSend：阻塞等应答，拿到 {@link SendResult}（含发送状态、消息
 *       全局唯一 ID、落入哪个队列），重要消息用它 + 检查 sendStatus；</li>
 *   <li>asyncSend：不等应答，成功/失败走 {@link SendCallback}，吞吐高，
 *       适合链路里 RT 敏感的批量通知；</li>
 *   <li>sendOneWay：发出去就不管，没有应答也不重试，可能丢——只用于
 *       可容忍丢失的场景（如埋点日志）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendModeExperiment {

    public static final String TOPIC = "SEND_MODE_TOPIC";

    private final RocketMQTemplate template;

    /** 依次用三种方式各发一条，观察日志里三者的返回与耗时差异。 */
    public String run() {
        long start = System.currentTimeMillis();

        // ① 同步发送：阻塞直到 Broker 应答，SendResult 是可靠性判断的依据
        SendResult syncResult = template.syncSend(TOPIC, buildMsg("hello-sync", "key-sync-1"));
        log.info("[syncSend] status={}, msgId={}, queueId={}, broker={}, 耗时={}ms",
                syncResult.getSendStatus(), syncResult.getMsgId(), syncResult.getMessageQueue().getQueueId(),
                syncResult.getMessageQueue().getBrokerName(), System.currentTimeMillis() - start);

        // ② 异步发送：立刻返回，结果在 IO 线程回调（注意日志线程名不同）
        final long asyncStart = System.currentTimeMillis();
        template.asyncSend(TOPIC, buildMsg("hello-async", "key-async-1"), new SendCallback() {
            @Override
            public void onSuccess(SendResult result) {
                log.info("[asyncSend] onSuccess: msgId={}, queueId={}, 回调耗时={}ms",
                        result.getMsgId(), result.getMessageQueue().getQueueId(),
                        System.currentTimeMillis() - asyncStart);
            }

            @Override
            public void onException(Throwable e) {
                // 生产环境这里必须有兜底：落库重发 / 告警，否则异步发送失败会被静默吞掉
                log.error("[asyncSend] onException: 发送失败, 需要业务补偿", e);
            }
        });

        // ③ 单向发送：无应答、无回调、无重试，发完即忘
        template.sendOneWay(TOPIC, buildMsg("hello-oneway", "key-oneway-1"));
        log.info("[sendOneWay] 已发出（没有任何应答信息，丢了也不知道）");

        return "三种发送方式各发一条到 " + TOPIC + "，对比上面三条日志";
    }

    private Message<String> buildMsg(String payload, String key) {
        // RocketMQHeaders.KEYS 即消息的业务 Key：可按 Key 查询消息轨迹，也是幂等去重的天然抓手
        return MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, key)
                .build();
    }

    /**
     * 消费者用 {@link MessageExt} 接收可以拿到元信息（msgId、重试次数、消息诞生时间），
     * 用 String 接收则直接拿消息体——本实验演示 MessageExt 用法。
     */
    @Slf4j
    @Component
    @RocketMQMessageListener(topic = TOPIC, consumerGroup = "lab-sendmode-group")
    public static class SendModeConsumer implements RocketMQListener<MessageExt> {

        @Override
        public void onMessage(MessageExt message) {
            log.info("[消费者] body={}, msgId={}, queueId={}, 重试次数={}",
                    new String(message.getBody(), StandardCharsets.UTF_8), message.getMsgId(),
                    message.getQueueId(), message.getReconsumeTimes());
        }
    }
}
