package org.allbluehu.develop.rocketmq.exp;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionExperimentTest {

    @ParameterizedTest
    @CsvSource({
            "commit, COMMIT_MESSAGE, COMMIT_MESSAGE",
            "rollback, ROLLBACK_MESSAGE, ROLLBACK_MESSAGE",
            "unknown, UNKNOW, COMMIT_MESSAGE"
    })
    void transactionCallbacksDecodeBodyThroughRocketMqAdapter(String mode,
            LocalTransactionState expectedExecution, LocalTransactionState expectedCheck) {
        TransactionExperiment experiment = new TransactionExperiment(new RocketMQTemplate());
        // 使用 SDK 的真实适配层重建回调消息，防止用 String payload 的测试掩盖类型错误。
        TransactionListener listener = RocketMQUtil.convert(new TransactionExperiment.TxListener(experiment));
        String orderId = "TX-订单-123";
        byte[] body = orderId.getBytes(StandardCharsets.UTF_8);
        Message message = new Message(TransactionExperiment.TOPIC, body);

        assertThat(listener.executeLocalTransaction(message, mode)).isEqualTo(expectedExecution);
        if ("unknown".equals(mode)) {
            assertThat(experiment.stats()).contains(orderId);
        }

        MessageExt checkMessage = new MessageExt();
        checkMessage.setTopic(TransactionExperiment.TOPIC);
        checkMessage.setBody(body);
        checkMessage.setBornHost(new InetSocketAddress("127.0.0.1", 10911));
        assertThat(listener.checkLocalTransaction(checkMessage)).isEqualTo(expectedCheck);
        assertThat(experiment.stats()).isEqualTo("回查累计=1, 等待回查=[]");
        // Broker 可能再次回查，已提交的结果不能因观测标记被移除而变成回滚。
        assertThat(listener.checkLocalTransaction(checkMessage)).isEqualTo(expectedCheck);
    }
}
