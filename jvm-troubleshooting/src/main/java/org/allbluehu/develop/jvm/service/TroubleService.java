package org.allbluehu.develop.jvm.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

/**
 * JVM 故障场景触发器。
 * <p>
 * 每个场景都通过独立的后台线程模拟真实生产故障，线程带有语义化名称，
 * 方便用 {@code jstack} 抓取线程快照时一眼定位（例如 {@code cpu-burn-1}、
 * {@code gc-allocator}、{@code leak-feeder}、{@code deadlock-thread-A}）。
 * <p>
 * 所有场景都是"可启动 / 可停止"的，配合 REST 接口随时复现或终止。
 */
@Slf4j
@Service
public class TroubleService {

    // ================================================================
    // 场景一：CPU 飙高
    // 思路：启动若干线程跑紧密计算循环，把 CPU 打满。
    // 排查：top 找进程 -> top -Hp 找高 CPU 线程 -> jstack 找该线程在干什么。
    // ================================================================

    private volatile boolean cpuRunning = false;
    private final List<Thread> cpuThreads = new ArrayList<>();

    /**
     * 启动 CPU 飙高场景。
     *
     * @param threads 起多少条 CPU 消耗线程，默认等于 CPU 核心数
     */
    public synchronized String startCpu(int threads) {
        if (cpuRunning) {
            return "CPU 场景已在运行中，线程数=" + cpuThreads.size();
        }
        if (threads <= 0) {
            threads = Runtime.getRuntime().availableProcessors();
        }
        cpuRunning = true;
        cpuThreads.clear();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(this::burnCpu, "cpu-burn-" + i);
            t.setDaemon(true);
            t.start();
            cpuThreads.add(t);
        }
        log.info("CPU 飙高场景已启动，线程数={}", threads);
        return "CPU 场景已启动，线程数=" + threads + "，用 top -Hp <pid> 观察";
    }

    /** 停止 CPU 飙高场景。 */
    public synchronized String stopCpu() {
        cpuRunning = false;
        for (Thread t : cpuThreads) {
            t.interrupt();
        }
        cpuThreads.clear();
        log.info("CPU 飙高场景已停止");
        return "CPU 场景已停止";
    }

    /** 紧密计算循环：纯 CPU 占用，不做任何 IO/睡眠。 */
    private void burnCpu() {
        double acc = 0;
        while (cpuRunning && !Thread.currentThread().isInterrupted()) {
            // 一段无意义的浮点运算，防止 JIT 把空循环优化掉
            for (int i = 0; i < 100_000; i++) {
                acc += Math.pow(Math.sqrt(i), 1.0001);
            }
            // 偶尔归零，避免 acc 溢出成 NaN 还在空转
            if (acc > 1e18) {
                acc = 0;
            }
        }
    }

    // ================================================================
    // 场景二：频繁 GC / GC 停顿
    // 思路：高速分配大对象并快速丢弃，制造高分配速率，触发频繁 Young GC。
    // 排查：jstat -gc <pid> 1s 看 YGC/YGCT/FGC/FGCT 增长；或看 GC 日志。
    // ================================================================

    private volatile boolean gcRunning = false;
    private Thread gcThread;

    /**
     * 启动频繁 GC 场景：以环形队列高速分配 1MB 对象，
     * 旧对象被挤出队列后即成为垃圾，制造持续的高分配速率。
     */
    public synchronized String startGc() {
        if (gcRunning) {
            return "GC 场景已在运行中";
        }
        gcRunning = true;
        gcThread = new Thread(this::allocateFast, "gc-allocator");
        gcThread.setDaemon(true);
        gcThread.start();
        log.info("频繁 GC 场景已启动");
        return "GC 场景已启动，用 jstat -gc <pid> 1000 观察 YGC/FGC";
    }

    /** 停止频繁 GC 场景。 */
    public synchronized String stopGc() {
        gcRunning = false;
        if (gcThread != null) {
            gcThread.interrupt();
            gcThread = null;
        }
        log.info("频繁 GC 场景已停止");
        return "GC 场景已停止";
    }

    /** 高速分配并丢弃大对象，制造频繁 GC。 */
    private void allocateFast() {
        // 环形缓冲：只保留最近若干个对象，老的一直被替换 → 持续产生垃圾
        Deque<byte[]> buffer = new ArrayDeque<>(32);
        while (gcRunning && !Thread.currentThread().isInterrupted()) {
            buffer.addLast(new byte[1024 * 1024]); // 1MB
            if (buffer.size() > 32) {
                buffer.pollFirst(); // 旧对象出队，随即被回收
            }
        }
    }

    // ================================================================
    // 场景三：内存泄漏 / OOM
    // 思路：把大对象持续塞进一个 static 列表且永不释放，堆迟早撑爆。
    // 排查：-XX:+HeapDumpOnOutOfMemoryError 自动 dump，用 MAT/jmap 分析支配树。
    // ================================================================

    /** 静态列表持有大对象引用，永不释放 —— 这是"泄漏"的本质。 */
    private static final List<byte[]> LEAK_HOLDER = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean leakRunning = false;
    private Thread leakThread;

    /**
     * 启动内存泄漏场景：每秒往静态列表追加 4MB 对象，永不释放。
     * 小堆（如 -Xmx256m）下几十秒内会 OOM。
     */
    public synchronized String startLeak() {
        if (leakRunning) {
            return "内存泄漏场景已在运行中，当前已泄漏 " + LEAK_HOLDER.size() + " 个对象";
        }
        leakRunning = true;
        leakThread = new Thread(this::leakMemory, "leak-feeder");
        leakThread.setDaemon(true);
        leakThread.start();
        log.info("内存泄漏场景已启动，当前已泄漏对象数={}", LEAK_HOLDER.size());
        return "内存泄漏场景已启动，已泄漏对象数=" + LEAK_HOLDER.size()
                + "，等待 OOM 后查看 heap dump";
    }

    /** 停止追加，但已泄漏的对象仍被 LEAK_HOLDER 持有，无法回收（这正是泄漏）。 */
    public synchronized String stopLeak() {
        leakRunning = false;
        if (leakThread != null) {
            leakThread.interrupt();
            leakThread = null;
        }
        log.info("内存泄漏场景停止追加，但已泄漏 {} 个对象仍在内存中，需重启 JVM 才能释放",
                LEAK_HOLDER.size());
        return "已停止追加。注意：已泄漏的 " + LEAK_HOLDER.size()
                + " 个对象仍被持有，重启 JVM 才能彻底释放";
    }

    /** 手动清空泄漏列表（仅用于练习重置，真实泄漏无法这样修复）。 */
    public synchronized String clearLeak() {
        int size = LEAK_HOLDER.size();
        LEAK_HOLDER.clear();
        log.info("已手动清空泄漏列表，释放 {} 个对象", size);
        return "已清空泄漏列表，释放 " + size + " 个对象";
    }

    /** 持续向静态列表追加 4MB 对象。 */
    private void leakMemory() {
        try {
            while (leakRunning && !Thread.currentThread().isInterrupted()) {
                LEAK_HOLDER.add(new byte[4 * 1024 * 1024]); // 4MB
                log.info("leak-feeder: 已持有 {} 个对象，堆使用约 {} MB",
                        LEAK_HOLDER.size(),
                        usedHeapMb());
                // 稍微 sleep 一下，让 OOM 来得慢一点，方便观察 jstat / dump
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (OutOfMemoryError oom) {
            log.error("leak-feeder: 触发 OOM！此时 LEAK_HOLDER.size()={}", LEAK_HOLDER.size(), oom);
            // 不吞掉 OOM，让它继续向上传播，JVM 配置了 HeapDumpOnOutOfMemoryError 会自动 dump
            throw oom;
        }
    }

    // ================================================================
    // 场景四：线程死锁
    // 思路：两条线程以相反顺序获取两把锁，经典锁顺序反转死锁。
    // 排查：jstack <pid> 末尾会直接给出 "Found 1 deadlock"。
    // ================================================================

    private final Object lock1 = new Object();
    private final Object lock2 = new Object();

    /**
     * 触发一次线程死锁：thread-A 先拿 lock1 再拿 lock2，
     * thread-B 先拿 lock2 再拿 lock1，互相等待 → 死锁。
     */
    public synchronized String triggerDeadlock() {
        Thread a = new Thread(() -> {
            synchronized (lock1) {
                log.info("deadlock-thread-A: 拿到 lock1，准备拿 lock2");
                sleepQuiet(200); // 给 B 时间先拿到 lock2
                synchronized (lock2) {
                    log.info("deadlock-thread-A: 拿到 lock2（实际不会走到这里）");
                }
            }
        }, "deadlock-thread-A");

        Thread b = new Thread(() -> {
            synchronized (lock2) {
                log.info("deadlock-thread-B: 拿到 lock2，准备拿 lock1");
                sleepQuiet(200); // 给 A 时间先拿到 lock1
                synchronized (lock1) {
                    log.info("deadlock-thread-B: 拿到 lock1（实际不会走到这里）");
                }
            }
        }, "deadlock-thread-B");

        a.start();
        b.start();
        log.info("死锁场景已触发，用 jstack <pid> 查看末尾的 deadlock 报告");
        return "死锁已触发，用 jstack <pid> 查看末尾会输出 'Found 1 deadlock'";
    }

    // ================================================================
    // 状态汇总
    // ================================================================

    /** 汇总各场景运行状态。 */
    public String status() {
        return String.format(
                "CPU飙高=%s(%d线程), 频繁GC=%s, 内存泄漏=%s(已泄漏%d个/约%dMB), 死锁见jstack",
                cpuRunning, cpuThreads.size(),
                gcRunning,
                leakRunning, LEAK_HOLDER.size(), LEAK_HOLDER.size() * 4);
    }

    private static long usedHeapMb() {
        Runtime r = Runtime.getRuntime();
        return (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
