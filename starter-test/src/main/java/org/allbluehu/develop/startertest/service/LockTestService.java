package org.allbluehu.develop.startertest.service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.allbluehu.develop.lock.annotation.DistributedLock;
import org.allbluehu.develop.lock.annotation.FailStrategy;
import org.allbluehu.develop.lock.annotation.LockType;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 分布式锁测试服务
 */
@Slf4j
@Service
public class LockTestService {

    /** 当前持有锁的并发计数（用于断言互斥） */
    final AtomicInteger inFlight = new AtomicInteger();
    /** 历史最大并发数（>1 说明锁失效） */
    final AtomicInteger maxInFlight = new AtomicInteger();

    /**
     * 基本加锁测试：同一 orderId 串行执行
     */
    @DistributedLock(keys = "#orderId", waitTime = 5, leaseTime = 10, unit = TimeUnit.SECONDS)
    public String process(String orderId) throws InterruptedException {
        log.info("Processing orderId: {}", orderId);
        int cur = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(cur, Math::max);
        Thread.sleep(200);
        inFlight.decrementAndGet();
        return "success:" + orderId;
    }

    /**
     * 拿不到锁抛异常（默认策略），waitTime 仅 200ms
     */
    @DistributedLock(keys = "#orderId", waitTime = 200, unit = TimeUnit.MILLISECONDS,
            failStrategy = FailStrategy.THROW)
    public String processThrow(String orderId) {
        log.info("Processing with throw: {}", orderId);
        return "success:" + orderId;
    }

    /**
     * 拿不到锁走兜底方法
     */
    @DistributedLock(keys = "#orderId", waitTime = 200, unit = TimeUnit.MILLISECONDS,
            failStrategy = FailStrategy.FAILBACK, fallbackMethod = "fallback")
    public String processFallback(String orderId) {
        log.info("Processing with fallback: {}", orderId);
        return "success:" + orderId;
    }

    public String fallback(String orderId) {
        log.warn("Lock acquire failed, fallback for: {}", orderId);
        return "busy:" + orderId;
    }

    /**
     * 拿不到锁静默返回 null
     */
    @DistributedLock(keys = "#orderId", waitTime = 200, unit = TimeUnit.MILLISECONDS,
            failStrategy = FailStrategy.RETURN_NULL)
    public String processReturnNull(String orderId) {
        log.info("Processing with return-null: {}", orderId);
        return "success:" + orderId;
    }

    /**
     * 公平锁测试
     */
    @DistributedLock(keys = "#orderId", type = LockType.FAIR,
            waitTime = 5, leaseTime = 10, unit = TimeUnit.SECONDS)
    public String processFair(String orderId) throws InterruptedException {
        log.info("Processing with fair lock: {}", orderId);
        Thread.sleep(200);
        return "success:" + orderId;
    }

    /**
     * 读写锁测试 - 读锁
     */
    @DistributedLock(keys = "'resource:' + #resourceId", type = LockType.READ,
            waitTime = 5, leaseTime = 10, unit = TimeUnit.SECONDS)
    public String readResource(String resourceId) throws InterruptedException {
        log.info("Reading resource: {}", resourceId);
        int cur = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(cur, Math::max);
        Thread.sleep(200);
        inFlight.decrementAndGet();
        return "read:" + resourceId;
    }

    /**
     * 读写锁测试 - 写锁
     */
    @DistributedLock(keys = "'resource:' + #resourceId", type = LockType.WRITE,
            waitTime = 5, leaseTime = 10, unit = TimeUnit.SECONDS)
    public String writeResource(String resourceId) throws InterruptedException {
        log.info("Writing resource: {}", resourceId);
        int cur = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(cur, Math::max);
        Thread.sleep(200);
        inFlight.decrementAndGet();
        return "written:" + resourceId;
    }

    /**
     * 多 key 联锁测试
     */
    @DistributedLock(keys = {"#from", "#to"}, type = LockType.MULTI,
            waitTime = 5, leaseTime = 10, unit = TimeUnit.SECONDS)
    public String transfer(String from, String to) throws InterruptedException {
        log.info("Transferring from {} to {}", from, to);
        Thread.sleep(200);
        return "transferred:" + from + "->" + to;
    }

    /**
     * 获取并发统计信息
     */
    public String getStats() {
        return String.format("当前并发: %d, 历史最大并发: %d",
                inFlight.get(), maxInFlight.get());
    }

    /**
     * 重置统计
     */
    public void resetStats() {
        inFlight.set(0);
        maxInFlight.set(0);
    }
}