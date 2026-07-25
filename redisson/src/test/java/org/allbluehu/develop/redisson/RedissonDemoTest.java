package org.allbluehu.develop.redisson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Redisson 集成测试。
 * 运行前确保本地 Redis 已启动（默认 6379 端口）。
 *
 * 或者用 Docker 启动：
 * docker run -d -p 6379:6379 --name redis-test redis:7-alpine
 */
@DisplayName("Redisson Demo Tests")
class RedissonDemoTest {

    private static RedissonClient client;
    private static RedissonDemo demo;

    @BeforeAll
    static void setUp() {
        try {
//            client = RedissonConfig.createSingle("redis://127.0.0.1:6379");
            client = RedissonConfig.createFromYaml("redisson-config.yaml");
            demo = new RedissonDemo(client);
        } catch (Exception e) {
            System.out.println("Redis 未连接，跳过测试: " + e.getMessage());
        }
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("String: RBucket 读写")
    void testString() {
        assumeRedisAvailable();
        demo.stringDemo("test:string");
    }

    @Test
    @DisplayName("Set: RSet 读写")
    void testSet() {
        assumeRedisAvailable();
        demo.setDemo("test:set");
    }

    @Test
    @DisplayName("SortedSet: RScoredSortedSet 读写")
    void testSortedSet() {
        assumeRedisAvailable();
        demo.sortedSetDemo("test:zset");
    }

    @Test
    @DisplayName("List: RList 读写")
    void testList() {
        assumeRedisAvailable();
        demo.listDemo("test:list");
    }

    @Test
    @DisplayName("Hash: RMap 读写")
    void testHash() {
        assumeRedisAvailable();
        demo.hashDemo("test:hash");
    }

    @Test
    @DisplayName("布隆过滤器")
    void testBloomFilter() {
        assumeRedisAvailable();
        demo.bloomFilterDemo("test:bloom");
    }

    @Test
    @DisplayName("分布式锁")
    void testLock() {
        assumeRedisAvailable();
        demo.lockDemo("test:lock");
    }

    @Test
    @DisplayName("原子计数器")
    void testAtomic() {
        assumeRedisAvailable();
        demo.atomicDemo();
    }

    private void assumeRedisAvailable() {
        assumeTrue(client != null && !client.isShutdown(), "Redis not available");
    }
}
