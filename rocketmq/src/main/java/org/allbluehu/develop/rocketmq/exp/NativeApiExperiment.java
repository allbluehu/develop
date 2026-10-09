package org.allbluehu.develop.rocketmq.exp;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 实验八：原生 API 对照——starter 只是这套 API 的薄封装。
 * <p>
 * RocketMQTemplate 底下就是 DefaultMQProducer，
 * @RocketMQMessageListener 底下就是 DefaultMQPushConsumer。认识裸写法
 * 才能在脱离 Spring（工具类、Flink 消费、老旧项目）时照样会玩，面试被
 * 追问"starter 帮你做了什么"也有的答：生命周期托管、消息转换、
 * 异常转消费失败、监听容器并发调度。
 * <p>
 * 顺带认识"Push 消费者其实是拉"：DefaultMQPushConsumer 内部由长轮询
 * 线程向 Broker 拉取（默认 15s 超时挂起，有消息立即返回），再交给本地
 * 线程池回调监听器——推的编程模型，拉的实现机制。
 */
@Slf4j
@Component
public class NativeApiExperiment {

    public static final String TOPIC = "NATIVE_TOPIC";

    private final String namesrvAddr;

    public NativeApiExperiment(@Value("${rocketmq.name-server}") String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    /** 串行运行，避免同进程并发注册相同生产者组、消费组。 */
    public synchronized String run() throws Exception {
        String key = "native-" + UUID.randomUUID();
        CountDownLatch received = new CountDownLatch(1);
        DefaultMQProducer producer = new DefaultMQProducer("native-producer");
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("native-consumer");
        producer.setNamesrvAddr(namesrvAddr);
        consumer.setNamesrvAddr(namesrvAddr);
        // 首次使用该消费组时也能读取启动前已进入队列的消息；已有消费进度仍优先使用进度。
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        try {
            consumer.subscribe(TOPIC, "*");
            consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
                for (MessageExt message : msgs) {
                    log.info("[原生消费者] body={}, queueId={}, reconsumeTimes={}",
                            new String(message.getBody(), StandardCharsets.UTF_8),
                            message.getQueueId(), message.getReconsumeTimes());
                    // 历史消息不能被误认为本轮实验已经成功。
                    if (key.equals(message.getKeys())) {
                        received.countDown();
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();
            producer.start();
            Message message = new Message(TOPIC, null, key,
                    "hello from native client".getBytes(StandardCharsets.UTF_8));
            SendResult result = producer.send(message);
            log.info("[原生生产者] sendStatus={}, msgId={}", result.getSendStatus(), result.getMsgId());
            return received.await(10, TimeUnit.SECONDS)
                    ? "原生客户端一发一收完成，key=" + key
                    : "消息已发送但 10 秒内未观察到本实例消费，key=" + key + "，请检查消费进度与其他实例";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            // 包括启动失败、发送异常、等待中断在内的路径都要释放客户端资源。
            try {
                consumer.shutdown();
            } finally {
                producer.shutdown();
            }
        }
    }
}
