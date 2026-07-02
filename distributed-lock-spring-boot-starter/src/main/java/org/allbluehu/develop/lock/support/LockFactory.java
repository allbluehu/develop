package org.allbluehu.develop.lock.support;

import java.util.List;

import org.allbluehu.develop.lock.annotation.LockType;
import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * 按 {@link LockType} 与解析出的 key 列表创建对应的 {@link RLock}。
 *
 * <p>READ / WRITE 仅使用 keys 的第一个元素作为读写锁名；
 * MULTI 用 keys 全部元素组合成联锁（每把都是可重入锁）。
 */
public class LockFactory {

    private final RedissonClient client;

    public LockFactory(RedissonClient client) {
        this.client = client;
    }

    public RLock create(LockType type, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("No lock keys resolved");
        }
        String first = keys.get(0);

        return switch (type) {
            case REENTRANT -> client.getLock(first);
            case FAIR      -> client.getFairLock(first);
            case READ      -> client.getReadWriteLock(first).readLock();
            case WRITE     -> client.getReadWriteLock(first).writeLock();
            case MULTI     -> {
                if (keys.size() == 1) {
                    yield client.getLock(first);
                }
                RLock[] locks = new RLock[keys.size()];
                for (int i = 0; i < keys.size(); i++) {
                    locks[i] = client.getLock(keys.get(i));
                }
                yield new RedissonMultiLock(locks);
            }
        };
    }
}
