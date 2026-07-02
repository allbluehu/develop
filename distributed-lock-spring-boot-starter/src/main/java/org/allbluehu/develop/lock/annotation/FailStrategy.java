package org.allbluehu.develop.lock.annotation;

/**
 * 获取锁失败时的处理策略。
 *
 * <pre>
 * THROW        抛出 LockAcquireFailureException（默认）
 * RETURN_NULL  静默返回 null
 * FAILBACK     调用注解上指定的 fallbackMethod 兜底方法
 * </pre>
 */
public enum FailStrategy {
    THROW,
    RETURN_NULL,
    FAILBACK
}
