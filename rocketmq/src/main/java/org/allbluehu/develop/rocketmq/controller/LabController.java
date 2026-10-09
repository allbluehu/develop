package org.allbluehu.develop.rocketmq.controller;

import org.allbluehu.develop.rocketmq.exp.BacklogExperiment;
import org.allbluehu.develop.rocketmq.exp.DelayOrderExperiment;
import org.allbluehu.develop.rocketmq.exp.IdempotentExperiment;
import org.allbluehu.develop.rocketmq.exp.NativeApiExperiment;
import org.allbluehu.develop.rocketmq.exp.OrderlyExperiment;
import org.allbluehu.develop.rocketmq.exp.RetryDlqExperiment;
import org.allbluehu.develop.rocketmq.exp.SendModeExperiment;
import org.allbluehu.develop.rocketmq.exp.TagFilterExperiment;
import org.allbluehu.develop.rocketmq.exp.TransactionExperiment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RocketMQ 实验触发接口。
 * <p>
 * 实验现象主要看应用日志（消费者日志都是实验组件自己打的）。
 * 建议开着 rocketmq-dashboard 对照观察 Topic / 消费进度 / 重试队列。
 */
@Slf4j
@RestController
@RequestMapping("/exp")
@RequiredArgsConstructor
public class LabController {

    private final SendModeExperiment sendMode;
    private final RetryDlqExperiment retryDlq;
    private final IdempotentExperiment idempotent;
    private final OrderlyExperiment orderly;
    private final DelayOrderExperiment delayOrder;
    private final TransactionExperiment transaction;
    private final TagFilterExperiment tagFilter;
    private final NativeApiExperiment nativeApi;
    private final BacklogExperiment backlog;

    // ============ 实验一：三种发送方式 ============

    /** 同步/异步/单向各发一条。示例: GET /exp/send/modes */
    @GetMapping("/send/modes")
    public ResponseEntity<String> sendModes() {
        return ResponseEntity.ok(sendMode.run());
    }

    // ============ 实验二：消费重试与死信 ============

    /** 触发重试。mode=flap 前2次失败后成功; mode=always 永远失败进死信。示例: GET /exp/retry/trigger?mode=flap */
    @GetMapping("/retry/trigger")
    public ResponseEntity<String> retryTrigger(@RequestParam(defaultValue = "flap") String mode) {
        return ResponseEntity.ok(retryDlq.trigger(mode));
    }

    /** 查看重试/死信累计。示例: GET /exp/retry/stats */
    @GetMapping("/retry/stats")
    public ResponseEntity<String> retryStats() {
        return ResponseEntity.ok(retryDlq.stats());
    }

    // ============ 实验三：幂等消费 ============

    /** 同一业务 Key 连发 N 条模拟重复投递。示例: GET /exp/idempotent/send?duplicates=3 */
    @GetMapping("/idempotent/send")
    public ResponseEntity<String> idempotentSend(@RequestParam(defaultValue = "3") int duplicates) {
        return ResponseEntity.ok(idempotent.sendDuplicates(duplicates));
    }

    /** 查看接收/真处理/去重计数。示例: GET /exp/idempotent/stats */
    @GetMapping("/idempotent/stats")
    public ResponseEntity<String> idempotentStats() {
        return ResponseEntity.ok(idempotent.stats());
    }

    // ============ 实验四：顺序消息 ============

    /** 正确姿势：按 orderId 路由 + 顺序消费。示例: GET /exp/orderly/send?orders=3 */
    @GetMapping("/orderly/send")
    public ResponseEntity<String> orderlySend(@RequestParam(defaultValue = "3") int orders) {
        return ResponseEntity.ok(orderly.sendOrderly(orders));
    }

    /** 对照组：散列发送 + 并发消费，观察乱序。示例: GET /exp/orderly/chaos?orders=3 */
    @GetMapping("/orderly/chaos")
    public ResponseEntity<String> orderlyChaos(@RequestParam(defaultValue = "3") int orders) {
        return ResponseEntity.ok(orderly.sendChaos(orders));
    }

    // ============ 实验五：延迟消息（订单超时取消） ============

    /** 下单并发 10s 延迟消息。示例: POST /exp/delay/order?sku=book */
    @PostMapping("/delay/order")
    public ResponseEntity<String> createOrder(@RequestParam(defaultValue = "sku") String sku) {
        return ResponseEntity.ok(delayOrder.createOrder(sku));
    }

    /** 在延迟到期前支付，让取消逻辑扑空。示例: POST /exp/delay/pay?orderId=ORD-12345 */
    @PostMapping("/delay/pay")
    public ResponseEntity<String> payOrder(@RequestParam String orderId) {
        return ResponseEntity.ok(delayOrder.pay(orderId));
    }

    /** 查看订单状态机。示例: GET /exp/delay/orders */
    @GetMapping("/delay/orders")
    public ResponseEntity<String> listOrders() {
        return ResponseEntity.ok(delayOrder.listOrders());
    }

    // ============ 实验六：事务消息 ============

    /** 提交事务消息。mode=commit/rollback/unknown。示例: GET /exp/tx/submit?mode=unknown */
    @GetMapping("/tx/submit")
    public ResponseEntity<String> txSubmit(@RequestParam(defaultValue = "commit") String mode) {
        return ResponseEntity.ok(transaction.submit(mode));
    }

    /** 查看回查情况。示例: GET /exp/tx/stats */
    @GetMapping("/tx/stats")
    public ResponseEntity<String> txStats() {
        return ResponseEntity.ok(transaction.stats());
    }

    // ============ 实验七：Tag 过滤 ============

    /** 三种 Tag 各发两条，观察不同订阅表达式的消费组各收什么。示例: GET /exp/tag/send */
    @GetMapping("/tag/send")
    public ResponseEntity<String> tagSend() {
        return ResponseEntity.ok(tagFilter.send());
    }

    // ============ 实验八：原生 API 对照 ============

    /** 裸客户端一发一收。示例: GET /exp/native/run */
    @GetMapping("/native/run")
    public ResponseEntity<String> nativeRun() throws Exception {
        return ResponseEntity.ok(nativeApi.run());
    }

    // ============ 实验九：消息积压 ============

    /** 灌入消息制造积压。示例: GET /exp/backlog/flood?count=3000 */
    @GetMapping("/backlog/flood")
    public ResponseEntity<String> backlogFlood(@RequestParam(defaultValue = "3000") int count) {
        return ResponseEntity.ok(backlog.flood(count));
    }

    /** 设置慢消费耗时。示例: POST /exp/backlog/slow?millis=200 */
    @PostMapping("/backlog/slow")
    public ResponseEntity<String> backlogSlow(@RequestParam(defaultValue = "0") long millis) {
        return ResponseEntity.ok(backlog.setSlow(millis));
    }

    /** 查看积压。示例: GET /exp/backlog/stats */
    @GetMapping("/backlog/stats")
    public ResponseEntity<String> backlogStats() {
        return ResponseEntity.ok(backlog.stats());
    }
}
