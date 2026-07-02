package org.allbluehu.develop.startertest.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 分布式锁使用示例
 */
@Slf4j
@Service
public class DistributedLockExample {

    // TODO: 注入 DistributedLockTemplate 或相关服务后进行测试
    // private final DistributedLockTemplate lockTemplate;

    public void doWithLock() {
        log.info("分布式锁示例 - 待 starter 集成完成后测试");
        // 示例代码（需要等 starter 实现完成）:
        // lockTemplate.executeWithLock("test-key", () -> {
        //     log.info("获取到锁，执行业务逻辑");
        //     // 业务逻辑
        // });
    }
}