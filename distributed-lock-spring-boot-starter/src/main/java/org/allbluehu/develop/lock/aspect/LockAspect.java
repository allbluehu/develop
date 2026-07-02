package org.allbluehu.develop.lock.aspect;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.allbluehu.develop.lock.annotation.DistributedLock;
import org.allbluehu.develop.lock.exception.LockAcquireFailureException;
import org.allbluehu.develop.lock.properties.LockProperties;
import org.allbluehu.develop.lock.support.LockFactory;
import org.allbluehu.develop.lock.support.LockFailureHandler;
import org.allbluehu.develop.lock.support.LockKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 分布式锁切面：拦截 {@link DistributedLock} 注解方法，加锁→执行→释放。
 *
 * <p>{@code @Order} 设为较高优先级（数值小），使锁切面位于事务切面之外：
 * 「先拿锁、再开事务，先提交事务、再放锁」，避免事务回滚后锁已释放、或锁内读到未提交数据的并发问题。
 * 事务切面默认优先级为 {@link Ordered#LOWEST_PRECEDENCE}，本切面取较小值即可保证在外层。
 *
 * <p>注意：AOP 注解只对外部调用生效，类内部 self-invocation 不会触发切面。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class LockAspect {

    private final LockProperties props;
    private final LockKeyResolver keyResolver;
    private final LockFactory lockFactory;
    private final LockFailureHandler failureHandler;

    public LockAspect(RedissonClient client, LockProperties props,
                      LockKeyResolver keyResolver, LockFactory lockFactory, LockFailureHandler failureHandler) {
        this.props = props;
        this.keyResolver = keyResolver;
        this.lockFactory = lockFactory;
        this.failureHandler = failureHandler;
    }

    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint pjp, DistributedLock annotation) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();

        List<String> keys = keyResolver.resolveKeys(
                annotation.keys(), annotation.prefix(), props.getKeyNamespace(),
                method, pjp.getArgs());

        long waitMillis = resolveMillis(annotation.waitTime(), props.getDefaultWaitTime(), annotation.unit());
        long leaseMillis = resolveMillis(annotation.leaseTime(), props.getDefaultLeaseTime(), annotation.unit());

        RLock lock = lockFactory.create(annotation.type(), keys);
        boolean acquired;
        try {
            acquired = lock.tryLock(waitMillis, leaseMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquireFailureException(keys, e);
        }

        if (!acquired) {
            return failureHandler.handle(pjp, method, annotation, keys);
        }

        try {
            return pjp.proceed();
        } finally {
            // 锁可能已因租期到期自动释放，释放前必须判断是否仍被当前线程持有，
            // 否则会抛 IllegalMonitorStateException。
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 把注解上的时间值换算成毫秒；-1 表示取全局默认值（默认值本身已是毫秒）。
     */
    private long resolveMillis(long value, long defaultMillis, TimeUnit unit) {
        if (value == -1) {
            return defaultMillis;
        }
        return unit.toMillis(value);
    }
}
