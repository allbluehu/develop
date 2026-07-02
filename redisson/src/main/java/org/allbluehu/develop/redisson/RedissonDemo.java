package org.allbluehu.develop.redisson;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * Redisson 常用分布式对象演示 —— 每个接口都值得深入看源码。
 *
 * 常用分布式对象速查：
 * ┌──────────────────────┬──────────────────────────────────┐
 * │  接口                 │  用途                             │
 * ├──────────────────────┼──────────────────────────────────┤
 * │  RBucket<V>           │  分布式对象容器（任意类型）          │
 * │  RMap<K,V>            │  分布式 Map（实现了 ConcurrentMap） │
 * │  RLock                │  可重入分布式锁                    │
 * │  RReadWriteLock       │  分布式读写锁                      │
 * │  RSemaphore           │  分布式信号量                      │
 * │  RCountDownLatch      │  分布式倒计数门闩                   │
 * │  RAtomicLong          │  分布式原子 Long                   │
 * │  RQueue<V>            │  分布式队列                        │
 * │  RDeque<V>            │  分布式双端队列                     │
 * │  RTopic               │  发布/订阅                         │
 * │  RLongAdder           │  分布式累加器                      │
 * │  RHyperLogLog<V>      │  基数统计（大数据去重计数）          │
 * │  RBloomFilter<V>      │  布隆过滤器                        │
 * │  RRateLimiter         │  分布式限流器                      │
 * │  RScheduledExecutor   │  分布式定时任务执行器                │
 * │  RLocalCachedMap<K,V> │  本地缓存 + Redis 同步的 Map        │
 * └──────────────────────┴──────────────────────────────────┘
 *
 * 源码学习路径建议：
 * 1. Redisson 入口 → 看 Redisson.create() 怎么做连接管理
 * 2. 挑一个对象（如 RLock）→ 看它怎么用 Lua 脚本 + Redis Pub/Sub 实现可重入锁
 * 3. 看 CommandAsyncService → 理解异步命令如何编码、发送、解码
 * 4. 看 ConnectionManager → 理解 Netty 连接池和事件驱动模型
 */
public final class RedissonDemo {

    private final RedissonClient client;

    public RedissonDemo(RedissonClient client) {
        this.client = client;
    }

    // ==================== 分布式锁 ====================

    /**
     * 演示可重入锁 —— Redisson 中最核心的功能之一。
     * 源码重点：RedissonLock.tryLockInnerAsync() 中的 Lua 脚本。
     */
    public void lockDemo(String lockKey) {
        var lock = client.getLock(lockKey);
        // tryLock() 无参版本立即返回，不会抛 InterruptedException
        if (lock.tryLock()) {
            try {
                System.out.println("获得锁，执行业务逻辑...");
            } finally {
                lock.unlock();
            }
        }
    }

    // ==================== 分布式 Map ====================

    public void mapDemo() {
        var map = client.<String, String>getMap("demo:map");
        // RMap 实现了 java.util.concurrent.ConcurrentMap
        map.put("key1", "value1");
        String value = map.get("key1");
        System.out.println("RMap get: " + value);
    }

    // ==================== 对象容器 ====================

    public void bucketDemo() {
        var bucket = client.<String>getBucket("demo:bucket");
        bucket.set("hello redisson");
        System.out.println("RBucket get: " + bucket.get());
    }

    // ==================== 发布订阅 ====================

    public void topicDemo() {
        var topic = client.getTopic("demo:topic");
        // 添加监听
        int listenerId = topic.addListener(String.class, (channel, msg) -> {
            System.out.println("收到消息: " + msg);
        });
        // 发布消息
        topic.publish("hello from publisher");
        topic.removeListener(listenerId);
    }

    // ==================== 原子操作 ====================

    public void atomicDemo() {
        var atomicLong = client.getAtomicLong("demo:counter");
        atomicLong.set(0);
        long newVal = atomicLong.incrementAndGet();
        System.out.println("计数器: " + newVal);
    }

    // ==================== 布隆过滤器 ====================

    public void bloomFilterDemo() {
        var bloomFilter = client.<String>getBloomFilter("demo:bloom");
        bloomFilter.tryInit(100_000, 0.03);
        bloomFilter.add("item1");
        System.out.println("item1 可能存在: " + bloomFilter.contains("item1"));
        System.out.println("item2 可能存在: " + bloomFilter.contains("item2"));
    }

    // ==================== 限流器 ====================

    public void rateLimiterDemo() {
        var limiter = client.getRateLimiter("demo:limiter");
        // 每秒 10 个令牌
        limiter.trySetRate(org.redisson.api.RateType.OVERALL, 10, 1,
                org.redisson.api.RateIntervalUnit.SECONDS);
        if (limiter.tryAcquire()) {
            System.out.println("获得令牌，放行");
        } else {
            System.out.println("被限流");
        }
    }

    public RedissonClient getClient() {
        return client;
    }
}
