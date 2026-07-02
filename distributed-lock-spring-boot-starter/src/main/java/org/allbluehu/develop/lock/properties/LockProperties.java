package org.allbluehu.develop.lock.properties;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分布式锁全局配置，前缀 {@code allblue.lock}。
 *
 * <pre>
 * allblue:
 *   lock:
 *     enabled: true               # 总开关；满足「容器内有 RedissonClient」条件时默认生效
 *     default-wait-time: 3000     # 默认等待时间（毫秒）
 *     default-lease-time: -1      # 默认持有时间（毫秒）；-1 = 走看门狗自动续期
 *     key-namespace: ""           # key 命名空间，建议设为应用名以隔离不同业务
 * </pre>
 */
@ConfigurationProperties(prefix = "allblue.lock")
public class LockProperties {

    /** 总开关。关闭后切面与相关 Bean 不装配。 */
    private boolean enabled = true;

    /** 默认等待获取锁的时间（毫秒）。注解 waitTime=-1 时取此值。 */
    private long defaultWaitTime = 3000;

    /** 默认持有锁的时间（毫秒）。注解 leaseTime=-1 时取此值；-1 表示走看门狗续期。 */
    private long defaultLeaseTime = -1;

    /** key 命名空间，拼接在 prefix 与 SpEL 值之间；为空则不拼接。 */
    private String keyNamespace = "";

    /** 全局默认时间单位（当前仅用于文档与未来扩展，注解的时间单位以注解上的 unit 为准）。 */
    private TimeUnit unit = TimeUnit.MILLISECONDS;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getDefaultWaitTime() { return defaultWaitTime; }
    public void setDefaultWaitTime(long defaultWaitTime) { this.defaultWaitTime = defaultWaitTime; }

    public long getDefaultLeaseTime() { return defaultLeaseTime; }
    public void setDefaultLeaseTime(long defaultLeaseTime) { this.defaultLeaseTime = defaultLeaseTime; }

    public String getKeyNamespace() { return keyNamespace; }
    public void setKeyNamespace(String keyNamespace) { this.keyNamespace = keyNamespace; }

    public TimeUnit getUnit() { return unit; }
    public void setUnit(TimeUnit unit) { this.unit = unit; }
}
