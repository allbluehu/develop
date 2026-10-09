package org.allbluehu.develop.rocketmq.exp;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ExperimentRegressionTest {

    @Test
    void paymentAndCancellationCannotOverwriteEachOther() throws Exception {
        CapturingTemplate template = new CapturingTemplate();
        DelayOrderExperiment experiment = new DelayOrderExperiment(template);
        DelayOrderExperiment.DelayConsumer consumer = new DelayOrderExperiment.DelayConsumer(experiment);
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 30; i++) {
                experiment.createOrder("book");
                String orderId = template.orderId;
                MessageExt message = message(orderId, 0);
                CountDownLatch start = new CountDownLatch(1);
                var payment = executor.submit(() -> {
                    start.await();
                    return experiment.pay(orderId);
                });
                var cancellation = executor.submit(() -> {
                    start.await();
                    consumer.onMessage(message);
                    return true;
                });
                start.countDown();
                String result = payment.get(5, TimeUnit.SECONDS);
                cancellation.get(5, TimeUnit.SECONDS);
                String expected = result.startsWith("支付成功") ? "PAID" : "CANCELLED";
                assertThat(experiment.listOrders()).contains(orderId + "=" + expected);
                consumer.onMessage(message);
                assertThat(experiment.pay(orderId)).contains("不能支付");
                assertThat(experiment.listOrders()).contains(orderId + "=" + expected);
            }
        }
    }

    @Test
    void retryProgressSurvivesNewConsumerInstance() {
        RetryDlqExperiment experiment = new RetryDlqExperiment(new RocketMQTemplate());
        var consumer = new RetryDlqExperiment.RetryConsumer(experiment);
        assertThatIllegalStateException().isThrownBy(() -> consumer.onMessage(message("NEED_RETRY:biz", 0)));
        // 模拟重投到新实例：没有任何本地历史，也必须在第二次重投时成功。
        var restarted = new RetryDlqExperiment(new RocketMQTemplate());
        new RetryDlqExperiment.RetryConsumer(restarted).onMessage(message("NEED_RETRY:biz", 2));
        assertThat(restarted.stats()).contains("重试后成功累计=1");
    }

    @Test
    void invalidParametersAreRejectedBeforeSending() {
        RocketMQTemplate template = new RocketMQTemplate();
        assertThatIllegalArgumentException().isThrownBy(() -> new TransactionExperiment(template).submit("typo"));
        assertThatIllegalArgumentException().isThrownBy(() -> new RetryDlqExperiment(template).trigger("typo"));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotentExperiment(template).sendDuplicates(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderlyExperiment(template).sendOrderly(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> new BacklogExperiment(template).flood(50001));
        assertThatIllegalArgumentException().isThrownBy(() -> new BacklogExperiment(template).setSlow(-1));
    }

    @Test
    void interruptedConsumptionIsNotAcknowledged() {
        BacklogExperiment experiment = new BacklogExperiment(new RocketMQTemplate());
        experiment.setSlow(1);
        try {
            Thread.currentThread().interrupt();
            assertThatIllegalStateException().isThrownBy(() ->
                    new BacklogExperiment.BacklogConsumer(experiment).onMessage("backlog"));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(experiment.stats()).contains("消费完成=0");
        } finally {
            Thread.interrupted();
        }
    }

    private static MessageExt message(String body, int retries) {
        MessageExt message = new MessageExt();
        message.setBody(body.getBytes(StandardCharsets.UTF_8));
        message.setKeys("biz");
        message.setReconsumeTimes(retries);
        message.setBornTimestamp(System.currentTimeMillis());
        return message;
    }

    private static class CapturingTemplate extends RocketMQTemplate {
        /** 最近发送的订单号，用于在不连接 Broker 的情况下触发消费。 */
        private String orderId;

        @Override
        public SendResult syncSend(String destination, Message<?> message, long timeout, int delayLevel) {
            orderId = (String) message.getPayload();
            return new SendResult();
        }
    }
}
