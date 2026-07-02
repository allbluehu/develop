package org.allbluehu.develop.lock.exception;

import java.util.List;

/**
 * 获取分布式锁失败时抛出（{@code failStrategy = THROW} 默认策略）。
 *
 * <p>可能是「等待超时未拿到锁」或「加锁过程被中断」。业务可捕获此异常做降级。
 */
public class LockAcquireFailureException extends RuntimeException {

    private final transient List<String> keys;

    public LockAcquireFailureException(List<String> keys) {
        super("Failed to acquire distributed lock for keys: " + keys);
        this.keys = keys;
    }

    public LockAcquireFailureException(List<String> keys, Throwable cause) {
        super("Failed to acquire distributed lock for keys: " + keys, cause);
        this.keys = keys;
    }

    public List<String> getKeys() {
        return keys;
    }
}
