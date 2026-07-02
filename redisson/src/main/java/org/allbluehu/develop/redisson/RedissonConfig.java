package org.allbluehu.develop.redisson;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * Redisson 配置与客户端工厂。
 *
 * 学习要点：
 * 1. Config 支持多种模式：单节点、哨兵、集群、主从
 * 2. RedissonClient 是线程安全的，全局持有一个实例即可
 * 3. 生产环境建议通过 Spring Boot starter 自动装配
 */
public final class RedissonConfig {

    private RedissonConfig() {
        // utility class
    }

    /**
     * 单节点模式 —— 最简配置，适合本地开发和学习。
     *
     * @param address redis 地址，格式 redis://host:port
     */
    public static RedissonClient createSingle(String address) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(address)
                .setConnectionPoolSize(8)
                .setConnectionMinimumIdleSize(2);
        return Redisson.create(config);
    }

    /**
     * 单节点模式 —— 从配置文件加载。
     *
     * @param configPath classpath 下的配置文件路径，如 "redisson-config.yaml"
     */
    public static RedissonClient createFromYaml(String configPath) {
        try {
            Config config = Config.fromYAML(
                    RedissonConfig.class.getClassLoader().getResource(configPath));
            return Redisson.create(config);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Redisson config from: " + configPath, e);
        }
    }

    /**
     * 哨兵模式。
     */
    public static RedissonClient createSentinel(String masterName, String... sentinelAddresses) {
        Config config = new Config();
        config.useSentinelServers()
                .setMasterName(masterName)
                .addSentinelAddress(sentinelAddresses)
                .setCheckSentinelsList(false);
        return Redisson.create(config);
    }

    /**
     * 集群模式。
     */
    public static RedissonClient createCluster(String... nodeAddresses) {
        Config config = new Config();
        config.useClusterServers()
                .addNodeAddress(nodeAddresses)
                .setScanInterval(2000);
        return Redisson.create(config);
    }
}
