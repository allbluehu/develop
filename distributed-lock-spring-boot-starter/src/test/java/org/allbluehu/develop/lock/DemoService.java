package org.allbluehu.develop.lock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.allbluehu.develop.lock.annotation.DistributedLock;
import org.allbluehu.develop.lock.annotation.FailStrategy;
import org.springframework.stereotype.Service;

/**
 * 集成测试用的演示服务：展示 {@link DistributedLock} 的几种用法。
 */
@Service
public class DemoService {

    /** 当前持有锁的并发计数（用于断言互斥） */
    final AtomicInteger inFlight = new AtomicInteger();
    /** 历史最大并发数（>1 说明锁失效） */
    final AtomicInteger maxInFlight = new AtomicInteger();

    /**
     * 同一 orderId 串行执行：waitTime 5s 足够等待前一个释放，leaseTime 30s 固定租期。
     */
    @DistributedLock(keys = "#orderId", waitTime = 5, leaseTime = 30, unit = TimeUnit.SECONDS)
    public String process(String orderId) throws InterruptedException {
        int cur = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(cur, Math::max);
        Thread.sleep(200);
        inFlight.decrementAndGet();
        return "ok:" + orderId;
    }

    /** 拿不到锁抛异常（默认策略），waitTime 仅 200ms */
    @DistributedLock(keys = "#orderId", waitTime = 200, unit = TimeUnit.MILLISECONDS,
            failStrategy = FailStrategy.THROW)
    public String processThrow(String orderId) {
        return "ok:" + orderId;
    }

    /** 拿不到锁走兜底方法 */
    @DistributedLock(keys = "#orderId", waitTime = 200, unit = TimeUnit.MILLISECONDS,
            failStrategy = FailStrategy.FAILBACK, fallbackMethod = "fallback")
    public String processFallback(String orderId) {
        return "ok:" + orderId;
    }

    public String fallback(String orderId) {
        return "busy:" + orderId;
    }
}
