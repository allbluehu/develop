package org.allbluehu.develop.redisson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;

import static org.junit.jupiter.api.Assertions.*;
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
            client = RedissonConfig.createSingle("redis://127.0.0.1:6379");
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
    @DisplayName("RBucket 读写")
    void testBucket() {
        assumeRedisAvailable();
        demo.bucketDemo();
    }

    @Test
    @DisplayName("RMap 读写")
    void testMap() {
        assumeRedisAvailable();
        demo.mapDemo();
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
