package org.allbluehu.develop.lock.autoconfigure;

import org.allbluehu.develop.lock.aspect.LockAspect;
import org.allbluehu.develop.lock.properties.LockProperties;
import org.allbluehu.develop.lock.support.LockFactory;
import org.allbluehu.develop.lock.support.LockFailureHandler;
import org.allbluehu.develop.lock.support.LockKeyResolver;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 分布式锁自动装配。
 *
 * <p>生效条件（全部满足）：
 * <ol>
 *   <li>classpath 上有 Redisson（{@link RedissonClient} / {@link RLock}）；</li>
 *   <li>容器中存在 {@link RedissonClient} Bean —— 即「配置了 Redis」；</li>
 *   <li>{@code allblue.lock.enabled} 未显式关闭（默认 true）。</li>
 * </ol>
 *
 * <p>使用方通常引入 {@code redisson-spring-boot-starter} 并配置 {@code spring.data.redis.*}，
 * 由其自动创建 RedissonClient；本 starter 在其后装配（{@link AutoConfigureAfter}）。
 * 没有配置 Redis 时 RedissonClient 不存在，本配置类整体跳过，对业务零影响。
 */
@AutoConfiguration
@ConditionalOnClass({RedissonClient.class, RLock.class})
@ConditionalOnBean(RedissonClient.class)
@ConditionalOnProperty(prefix = "allblue.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LockProperties.class)
@AutoConfigureAfter(name = {
        "org.redisson.spring.starter.RedissonAutoConfigurationV2",
        "org.redisson.spring.starter.RedissonAutoConfiguration"
})
public class LockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LockKeyResolver lockKeyResolver() {
        return new LockKeyResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public LockFactory lockFactory(RedissonClient redissonClient) {
        return new LockFactory(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public LockFailureHandler lockFailureHandler() {
        return new LockFailureHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public LockAspect lockAspect(RedissonClient redissonClient, LockProperties lockProperties,
                                 LockKeyResolver lockKeyResolver, LockFactory lockFactory,
                                 LockFailureHandler lockFailureHandler) {
        return new LockAspect(redissonClient, lockProperties, lockKeyResolver, lockFactory, lockFailureHandler);
    }
}
