package org.allbluehu.develop.redisson;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * Redisson 常用分布式对象演示 —— 每个接口都值得深入看源码。
 *
 * Redis 数据类型与 Redisson 接口对照：
 * ┌──────────────────┬──────────────────────┬──────────────────────────────────┐
 * │  Redis 类型        │  Redisson 接口          │  说明                             │
 * ├──────────────────┼──────────────────────┼──────────────────────────────────┤
 * │  String            │  RBucket<V>           │  单值容器，存任意可序列化对象        │
 * │  Set               │  RSet<V>              │  实现了 java.util.Set             │
 * │  ZSet(按score)     │  RScoredSortedSet<V>  │  按分数排序（排行榜常用）           │
 * │  ZSet(按自然序)     │  RSortedSet<V>        │  按元素 Comparable 排序，无 score   │
 * │  List              │  RList<V>             │  实现了 java.util.List            │
 * │  Hash              │  RMap<K,V>            │  实现了 ConcurrentMap             │
 * └──────────────────┴──────────────────────┴──────────────────────────────────┘
 *
 * 其它常用分布式对象速查：
 * ┌──────────────────────┬──────────────────────────────────┐
 * │  接口                 │  用途                             │
 * ├──────────────────────┼──────────────────────────────────┤
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

    // ==================== String（RBucket）===================

    /**
     * Redis 的 String 类型对应 Redisson 的 RBucket，可存任意可序列化对象。
     * 源码重点：set/get 走单条命令，compareAndSet 用 Lua 脚本保证原子。
     */
    public void stringDemo(String key) {
        var bucket = client.<String>getBucket(key);
        bucket.set("hello redisson");

        // getAndSet：返回旧值并写入新值
        String old = bucket.getAndSet("hello world");
        System.out.println("RBucket 旧值: " + old + ", 新值: " + bucket.get());

        // setIfExists：仅当 key 已存在时写入（类似 SET ... XX）
        boolean updated = bucket.setIfExists("updated");
        System.out.println("setIfExists 结果: " + updated + ", 当前值: " + bucket.get());

        // compareAndSet：乐观锁，期望值匹配才更新（Lua 原子）
        boolean cas = bucket.compareAndSet("hello world", "cas-value");
        System.out.println("CAS 结果: " + cas + ", 当前值: " + bucket.get());

        bucket.delete();
    }

    // ==================== Set（RSet）===================

    /**
     * Redis 的 Set 对应 RSet，实现了 java.util.Set，元素去重、无序。
     */
    public void setDemo(String key) {
        var set = client.<String>getSet(key);
        set.add("java");
        set.add("redis");
        set.add("redis");                       // 重复元素会被忽略
        set.addAll(java.util.List.of("kafka", "mysql"));

        System.out.println("RSet 大小: " + set.size());
        System.out.println("包含 redis: " + set.contains("redis"));

        // readAll：一次性拉取全部元素到本地
        System.out.println("RSet 元素: " + set.readAll());

        set.remove("mysql");
        set.delete();
    }

    // ==================== SortedSet（ZSet）===================

    /**
     * Redis 的 ZSet 对应 Redisson 的 RScoredSortedSet（按 score 排序），排行榜常用。
     * 注意还有 RSortedSet：按元素自然顺序（Comparable）排序，没有 score 概念。
     */
    public void sortedSetDemo(String key) {
        var zset = client.<String>getScoredSortedSet(key);
        zset.add(100.0, "math");
        zset.add(90.0, "english");
        zset.add(85.0, "history");
        zset.add(95.0, "physics");

        // valueRange(startIndex, endIndex)：按分数升序取区间，含端点，-1 表末尾
        System.out.println("分数升序: " + zset.valueRange(0, -1));
        // rank：升序排名（从 0 开始）；getScore：取某成员分数
        System.out.println("math 排名(从0): " + zset.rank("math") + ", 分数: " + zset.getScore("math"));

        zset.delete();
    }

    // ==================== List（RList）===================

    /**
     * Redis 的 List 对应 RList，实现了 java.util.List，按下标访问。
     * RList 没有字面叫 rpush/lpop 的方法，用 List 语义对应：
     *   RPUSH → add(e)             追加到尾部
     *   LPUSH → add(0, e)          插入到头部
     *   LPOP  → remove(0)          移除并返回头部
     *   RPOP  → remove(size - 1)   移除并返回尾部
     * 想要带名字的 push/pop API，可用 RDeque（继承自 RList，操作同一个 Redis list）：
     *   addFirst / addLast / pollFirst / pollLast —— 对应 LPUSH / RPUSH / LPOP / RPOP
     */
    public void listDemo(String key) {
        var list = client.<String>getList(key);
        list.add("a");                           // RPUSH a
        list.add("b");                           // RPUSH b
        list.add(0, "head");                     // LPUSH head
        list.addAll(java.util.List.of("c", "d"));// RPUSH c d

        System.out.println("RList: " + list.readAll());
        System.out.println("下标0: " + list.get(0) + ", 大小: " + list.size());

        // LPOP：移除并返回头部元素
        String lpop = list.remove(0);
        System.out.println("LPOP: " + lpop + ", 剩余: " + list.readAll());
        // RPOP：移除并返回尾部元素
        String rpop = list.remove(list.size() - 1);
        System.out.println("RPOP: " + rpop + ", 剩余: " + list.readAll());

        // RDeque 提供带名字的 push/pop API（RDeque extends RList，操作同一个 Redis list）
        var deque = client.<String>getDeque(key);
        deque.addFirst("front");                 // LPUSH
        deque.addLast("back");                   // RPUSH
        System.out.println("pollFirst(LPOP): " + deque.pollFirst()
                + ", pollLast(RPOP): " + deque.pollLast());

        list.delete();
    }

    // ==================== Hash（RMap）===================

    /**
     * Redis 的 Hash 对应 RMap，实现了 java.util.concurrent.ConcurrentMap。
     */
    public void hashDemo(String key) {
        var map = client.<String, String>getMap(key);
        map.put("name", "redisson");
        map.put("lang", "java");
        map.putIfAbsent("name", "dup");         // 已存在不会覆盖
        map.putAll(java.util.Map.of("v1", "1", "v2", "2"));

        System.out.println("name: " + map.get("name"));
        System.out.println("包含 lang: " + map.containsKey("lang"));
        System.out.println("RMap 大小: " + map.size());

        map.remove("v1");
        map.delete();
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

    /**
     * 布隆过滤器：空间高效的「可能存在 / 一定不存在」判断，常用于缓存穿透防护。
     * tryInit(expectedInsertions, fpp)：期望元素数 + 误判率，仅首次初始化生效，再次调用返回 false。
     */
    public void bloomFilterDemo(String key) {
        var bloomFilter = client.<String>getBloomFilter(key);
        // 期望放入 10w 元素，误判率 3%
        bloomFilter.tryInit(100_000, 0.03);
        bloomFilter.add("user:1");
        bloomFilter.add("user:2");

        // contains 命中：可能存在（有误判概率）
        System.out.println("user:1 可能存在: " + bloomFilter.contains("user:1"));
        // contains 未命中：一定不存在
        System.out.println("user:999 一定不存在: " + bloomFilter.contains("user:999"));
        System.out.println("预计容量: " + bloomFilter.getExpectedInsertions() + ", 误判率: " + bloomFilter.getFalseProbability());
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
