package org.allbluehu.develop.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解 —— 标注在方法上，方法执行前按 key 加锁、执行后释放锁。
 *
 * <p>触发条件：Spring 容器中存在 {@code RedissonClient} Bean（即配置了 Redis）。
 * 没有 RedissonClient 时整个切面不会装配，注解形同虚设，对业务零影响。
 *
 * <h3>示例</h3>
 * <pre>{@code
 * // 锁单个参数（SpEL）
 * @DistributedLock(keys = "#orderId")
 * public void cancel(String orderId) { ... }
 *
 * // 锁对象属性 + 固定 5s 租期
 * @DistributedLock(keys = "'user:' + #user.id", leaseTime = 5, unit = TimeUnit.SECONDS)
 * public void update(User user) { ... }
 *
 * // 多 key 联锁
 * @DistributedLock(keys = {"#from", "#to"}, type = LockType.MULTI)
 * public void transfer(String from, String to) { ... }
 *
 * // 拿不到锁走兜底方法
 * @DistributedLock(keys = "#id", failStrategy = FailStrategy.FAILBACK, fallbackMethod = "fallback")
 * public String run(String id) { ... }
 * public String fallback(String id) { return "busy"; }
 * }</pre>
 *
 * <h3>waitTime / leaseTime 取值约定</h3>
 * <ul>
 *   <li>{@code -1}（默认）：使用全局默认值（见 {@code allblue.lock.*} 配置）。</li>
 *   <li>{@code waitTime = 0}：不等待，tryLock 立即返回。</li>
 *   <li>{@code leaseTime = -1} 且全局默认也是 {@code -1}：走 Redisson 看门狗自动续期
 *       ——持锁期间不会因租期到期被抢，前提是持锁线程存活；业务死循环会导致锁被一直占用，需业务层自行避免。</li>
 *   <li>{@code leaseTime > 0}：固定租期，到期自动释放，看门狗不启动。注意：业务执行时间必须小于租期，
 *       否则锁提前释放会导致并发问题。</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /** 锁 key 表达式（SpEL），支持多个。为空时按「类名.方法名」整体加锁。 */
    String[] keys() default {};

    /** 等待获取锁的最长时间；-1 表示用全局默认。单位由 {@link #unit()} 指定。 */
    long waitTime() default -1;

    /** 持有锁的最长时间；-1 表示用全局默认（默认走看门狗续期）。单位由 {@link #unit()} 指定。 */
    long leaseTime() default -1;

    /** waitTime / leaseTime 的时间单位。 */
    TimeUnit unit() default TimeUnit.MILLISECONDS;

    /** 锁类型，默认可重入锁。 */
    LockType type() default LockType.REENTRANT;

    /** key 前缀，默认 {@code "lock:"}，用于命名空间隔离。 */
    String prefix() default "lock:";

    /** 获取锁失败时的处理策略，默认抛异常。 */
    FailStrategy failStrategy() default FailStrategy.THROW;

    /** 兜底方法名（仅 {@link FailStrategy#FAILBACK} 生效）。签名应与原方法一致（参数列表相同）。 */
    String fallbackMethod() default "";
}
