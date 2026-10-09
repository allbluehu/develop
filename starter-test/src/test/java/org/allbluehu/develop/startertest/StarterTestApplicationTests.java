package org.allbluehu.develop.startertest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.InetSocketAddress;
import java.net.Socket;

import org.allbluehu.develop.lock.aspect.LockAspect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文加载 + 锁切面装配验证 —— 需要本地 Redis（127.0.0.1:6379）。
 *
 * <p>本模块引了 redisson-spring-boot-starter，RedissonClient 在上下文加载时即建连，
 * Redis 不可达会导致上下文加载失败，所以用 {@link EnabledIf} 在加载前整类跳过
 * （与 starter 模块的 LockAspectIntegrationTest 同一套路）。
 */
@SpringBootTest
@EnabledIf("redisAvailable")
class StarterTestApplicationTests {

    @Autowired
    LockAspect lockAspect;

    /** 上下文加载前探测 Redis，不可达则整类跳过。 */
    static boolean redisAvailable() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 6379), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void contextLoads() {
        // 切面 Bean 在，说明 LockAutoConfiguration 真的装配了，@DistributedLock 不是空转
        assertNotNull(lockAspect);
    }
}
