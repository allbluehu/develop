package org.allbluehu.develop.lock.annotation;

/**
 * 分布式锁类型，对应 Redisson 提供的不同 RLock 实现。
 *
 * <pre>
 * REENTRANT  可重入锁（默认）       RedissonClient.getLock(key)
 * FAIR       公平锁（按等待顺序获取）  RedissonClient.getFairLock(key)
 * READ       读锁（共享）            RedissonClient.getReadWriteLock(key).readLock()
 * WRITE      写锁（排他）            RedissonClient.getReadWriteLock(key).writeLock()
 * MULTI      多 key 联锁             new RedissonMultiLock(lock1, lock2, ...) —— 同时加多把锁，全部成功才算持有
 * </pre>
 *
 * <p>READ / WRITE 取 keys 的第一个元素作为读写锁名；MULTI 用 keys 全部元素组合成联锁。
 */
public enum LockType {
    REENTRANT,
    FAIR,
    READ,
    WRITE,
    MULTI
}
