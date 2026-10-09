package org.allbluehu.develop.startertest.controller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.allbluehu.develop.lock.exception.LockAcquireFailureException;
import org.allbluehu.develop.startertest.service.LockTestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分布式锁测试接口
 */
@Slf4j
@RestController
@RequestMapping("/lock-test")
@RequiredArgsConstructor
public class LockTestController {

    private final LockTestService lockTestService;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    /**
     * 基本加锁测试
     * 示例: GET /lock-test/process?orderId=123
     */
    @GetMapping("/process")
    public ResponseEntity<String> process(@RequestParam String orderId) throws InterruptedException {
        return ResponseEntity.ok(lockTestService.process(orderId));
    }

    /**
     * 并发测试 - 同时发起多个请求测试锁的互斥性
     * 示例: GET /lock-test/concurrent?orderId=123&count=5
     */
    @GetMapping("/concurrent")
    public ResponseEntity<String> concurrent(@RequestParam String orderId, @RequestParam(defaultValue = "3") int count)
            throws InterruptedException, ExecutionException {
        lockTestService.resetStats();
        log.info("开始并发测试: orderId={}, count={}", orderId, count);

        var futures = new java.util.ArrayList<CompletableFuture<Void>>();
        for (int i = 0; i < count; i++) {
            final int idx = i;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    lockTestService.process(orderId + "-" + idx);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        Thread.sleep(300); // 等待最后的请求完成

        return ResponseEntity.ok(lockTestService.getStats());
    }

    /**
     * 同一 key 并发测试 - 验证锁的互斥性
     * 示例: GET /lock-test/concurrent-same-key?orderId=123&count=5
     */
    @GetMapping("/concurrent-same-key")
    public ResponseEntity<String> concurrentSameKey(@RequestParam String orderId,
            @RequestParam(defaultValue = "3") int count) throws InterruptedException, ExecutionException {
        lockTestService.resetStats();
        log.info("开始同一 key 并发测试: orderId={}, count={}", orderId, count);

        var futures = new java.util.ArrayList<CompletableFuture<Void>>();
        for (int i = 0; i < count; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    lockTestService.process(orderId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        Thread.sleep(500); // 等待所有请求完成

        String stats = lockTestService.getStats();
        int maxConcurrent = Integer.parseInt(stats.split(",")[1].split(":")[1].trim());
        String result = maxConcurrent <= 1 ? "锁生效 ✓" : "锁失效 ✗";
        return ResponseEntity.ok(stats + ", 结果: " + result);
    }

    /**
     * 拿不到锁抛异常测试
     * 示例: GET /lock-test/process-throw?orderId=123
     */
    @GetMapping("/process-throw")
    public ResponseEntity<String> processThrow(@RequestParam String orderId) {
        try {
            return ResponseEntity.ok(lockTestService.processThrow(orderId));
        } catch (LockAcquireFailureException e) {
            return ResponseEntity.status(429).body("获取锁失败: " + e.getMessage());
        }
    }

    /**
     * 兜底方法测试
     * 示例: GET /lock-test/process-fallback?orderId=123
     */
    @GetMapping("/process-fallback")
    public ResponseEntity<String> processFallback(@RequestParam String orderId) {
        return ResponseEntity.ok(lockTestService.processFallback(orderId));
    }

    /**
     * 静默返回 null 测试
     * 示例: GET /lock-test/process-return-null?orderId=123
     */
    @GetMapping("/process-return-null")
    public ResponseEntity<String> processReturnNull(@RequestParam String orderId) {
        String result = lockTestService.processReturnNull(orderId);
        return ResponseEntity.ok(result != null ? result : "null (获取锁失败)");
    }

    /**
     * 公平锁测试
     * 示例: GET /lock-test/process-fair?orderId=123
     */
    @GetMapping("/process-fair")
    public ResponseEntity<String> processFair(@RequestParam String orderId) throws InterruptedException {
        return ResponseEntity.ok(lockTestService.processFair(orderId));
    }

    /**
     * 读锁测试 - 多个读请求可以并发
     * 示例: GET /lock-test/read?resourceId=123
     */
    @GetMapping("/read")
    public ResponseEntity<String> read(@RequestParam String resourceId) throws InterruptedException {
        return ResponseEntity.ok(lockTestService.readResource(resourceId));
    }

    /**
     * 写锁测试 - 写锁与读锁互斥
     * 示例: GET /lock-test/write?resourceId=123
     */
    @GetMapping("/write")
    public ResponseEntity<String> write(@RequestParam String resourceId) throws InterruptedException {
        return ResponseEntity.ok(lockTestService.writeResource(resourceId));
    }

    /**
     * 读写锁并发测试 - 验证读写互斥
     * 示例: GET /lock-test/read-write-concurrent?resourceId=123&readCount=3&writeCount=2
     */
    @GetMapping("/read-write-concurrent")
    public ResponseEntity<String> readWriteConcurrent(
            @RequestParam String resourceId,
            @RequestParam(defaultValue = "3") int readCount,
            @RequestParam(defaultValue = "2") int writeCount) throws InterruptedException, ExecutionException {
        lockTestService.resetStats();
        log.info("开始读写锁并发测试: resourceId={}, readCount={}, writeCount={}", resourceId, readCount, writeCount);

        var futures = new java.util.ArrayList<CompletableFuture<Void>>();

        // 读请求
        for (int i = 0; i < readCount; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    lockTestService.readResource(resourceId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor));
        }

        // 写请求
        for (int i = 0; i < writeCount; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    lockTestService.writeResource(resourceId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        Thread.sleep(300);

        return ResponseEntity.ok(lockTestService.getStats());
    }

    /**
     * 多 key 联锁测试
     * 示例: GET /lock-test/transfer?from=A&to=B
     */
    @GetMapping("/transfer")
    public ResponseEntity<String> transfer(@RequestParam String from, @RequestParam String to)
            throws InterruptedException {
        return ResponseEntity.ok(lockTestService.transfer(from, to));
    }

    /**
     * 获取统计信息
     * 示例: GET /lock-test/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<String> stats() {
        return ResponseEntity.ok(lockTestService.getStats());
    }

    /**
     * 重置统计信息
     * 示例: POST /lock-test/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<String> reset() {
        lockTestService.resetStats();
        return ResponseEntity.ok("统计已重置");
    }
}