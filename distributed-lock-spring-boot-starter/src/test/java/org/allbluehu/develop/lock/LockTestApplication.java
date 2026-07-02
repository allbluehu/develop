package org.allbluehu.develop.lock;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 集成测试用最小 Spring Boot 应用。
 *
 * <p>手动提供 {@link RedissonClient} Bean（连本地 6379），不依赖 redisson-spring-boot-starter。
 * 当本地无 Redis 时 Bean 仍能创建（Redisson 延迟连接），首条命令才会失败 → 测试中 assumeTrue 跳过。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class LockTestApplication {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        return Redisson.create(config);
    }
}
