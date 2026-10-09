package org.allbluehu.develop.rocketmq.controller;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** 临时调试端点：排查事务监听器装配问题，验证后删除。 */
@RestController
@RequiredArgsConstructor
public class DebugController {

    private final ApplicationContext ctx;

    @Value("${rocketmq.name-server}")
    private String namesrv;

    @GetMapping("/debug/tx")
    public String tx() {
        var annotated = ctx.getBeansWithAnnotation(RocketMQTransactionListener.class);
        RocketMQTemplate template = ctx.getBean(RocketMQTemplate.class);
        DefaultMQProducer producer = template.getProducer();
        Object listener = producer instanceof TransactionMQProducer txp ? txp.getTransactionListener() : null;
        return "annotatedBeans=" + annotated.keySet()
                + ", producerClass=" + producer.getClass().getName()
                + ", txListener=" + (listener == null ? "null" : listener.getClass().getName());
    }

    /** 原生客户端 + 内联监听器直发事务消息：回调跑了返回 true。 */
    @GetMapping("/debug/rawtx")
    public String rawTx() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        TransactionMQProducer producer = new TransactionMQProducer("debug-tx-producer");
        producer.setNamesrvAddr(namesrv);
        producer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
                executed.set(true);
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                return LocalTransactionState.COMMIT_MESSAGE;
            }
        });
        producer.start();
        try {
            Message msg = new Message("TX_ORDER_TOPIC", null, "debug-key",
                    "debug-raw-tx".getBytes(StandardCharsets.UTF_8));
            TransactionSendResult r = producer.sendMessageInTransaction(msg, null);
            return "raw: executed=" + executed.get() + ", sendStatus=" + r.getSendStatus()
                    + ", localState=" + r.getLocalTransactionState();
        } finally {
            producer.shutdown();
        }
    }
}
