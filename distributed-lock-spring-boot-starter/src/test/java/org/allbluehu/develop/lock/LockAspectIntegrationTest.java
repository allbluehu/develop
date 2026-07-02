package org.allbluehu.develop.lock;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.allbluehu.develop.lock.exception.LockAcquireFailureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * LockAspect 集成测试 —— 需要本地 Redis（127.0.0.1:6379）。
 *
 * <p>用 {@link EnabledIf} 在加载 Spring 上下文前做 socket 探测：Redisson 3.36 在
 * {@code Redisson.create()} 时即建立连接，Redis 不在会导致上下文加载失败，必须提前整类跳过。
 *
 * <p>启动 Redis：{@code docker run -d -p 6379:6379 --name redis-test redis:7-alpine}
 */
@SpringBootTest(classes = LockTestApplication.class)
@EnabledIf("redisAvailable")
@DisplayName("分布式锁切面集成测试")
class LockAspectIntegrationTest {

    @Autowired
    RedissonClient client;
    @Autowired
    DemoService demoService;

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
    @DisplayName("同一 key 多线程互斥：最大并发应为 1")
    void mutexOnSameKey() throws Exception {
        demoService.maxInFlight.set(0);
        demoService.inFlight.set(0);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        List<Exception> errors = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    demoService.process("order-1");
                } catch (Exception e) {
                    synchronized (errors) { errors.add(e); }
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(60, TimeUnit.SECONDS), "threads did not finish in time");
        pool.shutdown();

        assertTrue(errors.isEmpty(), "unexpected exceptions: " + errors);
        assertEquals(1, demoService.maxInFlight.get(),
                "maxInFlight should be 1, got " + demoService.maxInFlight.get());
    }

    @Test
    @DisplayName("锁被占用时 THROW 策略抛 LockAcquireFailureException")
    void throwOnLockFailure() throws Exception {
        String key = "lock:occupied-throw";
        withLockHeldByOtherThread(key, () ->
                assertThrows(LockAcquireFailureException.class,
                        () -> demoService.processThrow("occupied-throw")));
    }

    @Test
    @DisplayName("锁被占用时 FAILBACK 策略返回兜底值")
    void fallbackOnLockFailure() throws Exception {
        String key = "lock:occupied-fb";
        withLockHeldByOtherThread(key, () -> {
            String result = demoService.processFallback("occupied-fb");
            assertEquals("busy:occupied-fb", result);
        });
    }

    /**
     * 在另一个线程上占用 key 对应的 Redisson 锁，执行断言，结束后释放。
     * 用不同线程持有是因为 Redisson 可重入锁对同线程会重入而不阻塞。
     */
    private void withLockHeldByOtherThread(String redisKey, AssertRunnable assertion) throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Exception> holderError = new AtomicReference<>();

        Thread holder = new Thread(() -> {
            try {
                RLock lock = client.getLock(redisKey);
                lock.lock(30, TimeUnit.SECONDS);
                held.countDown();
                release.await();
                lock.unlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                holderError.set(e);
            }
        });
        holder.start();
        assertTrue(held.await(10, TimeUnit.SECONDS), "holder did not acquire lock");

        try {
            assertion.run();
        } finally {
            release.countDown();
            holder.join(10_000);
        }
        if (holderError.get() != null) {
            throw holderError.get();
        }
    }

    @FunctionalInterface
    interface AssertRunnable {
        void run() throws Exception;
    }
}
