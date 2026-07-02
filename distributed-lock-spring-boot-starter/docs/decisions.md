# 决策记录（append-only）

## D1: 底层用 Redisson 而非手写 SETNX + Lua

- **决定**：基于 Redisson `RLock`。
- **为什么**：Redisson 已用 Lua + Pub/Sub 实现可重入锁、看门狗续期、公平锁、读写锁、联锁，久经验证；手写易错（原子性、续期、释放校验）。
- **否决**：手写 `SET key val NX PX` + Lua 释放脚本——需自己处理可重入计数、续期、网络重试，重复造轮子且易踩坑。

## D2: 只消费 RedissonClient Bean，不自带创建

- **决定**：starter 不引入 `redisson-spring-boot-starter`，只引用 `redisson` 核心类型；`RedissonClient` 由使用方提供。
- **为什么**：解耦。使用方可能用 `redisson-spring-boot-starter`，也可能自建 Bean（如本项目测试）。`@ConditionalOnBean(RedissonClient)` 让"配了 Redis 才生效"自然成立。
- **否决**：starter 内置 `redisson-spring-boot-starter`——会强绑 Redisson 的 Spring Boot 自动配置版本，且把 Redis 连接配置混进锁 starter 职责。

## D3: 不做限流，限流不纳入本组件

- **决定**：starter 只做分布式锁，不做限流。
- **为什么**：锁是"互斥访问"，限流是"流量整形"，领域概念不同，参数模型与失败语义也不同；硬合进一个注解会让语义糊掉。
- **否决**：
  - 合并成 `@DistributedLock` 扩展属性——概念混淆，被否。
  - 同模块加 `@RateLimit`（基于 Redisson `RRateLimiter`）——讨论后明确：多数"接口限流"诉求是每实例自我保护，Sentinel 更合适；集群共享配额场景才用 `RRateLimiter`，且秒杀的共享需求在库存扣减不在限流。最终撤掉限流设计，保持单一职责。详见需求边界。

## D4: leaseTime 默认走看门狗（-1），非固定租期

- **决定**：`defaultLeaseTime = -1`，默认触发 Redisson 看门狗自动续期。
- **为什么**：分布式锁最危险的失败模式是"业务未完成、锁租期到期被抢"，直接导致并发问题/超卖。看门狗保证持锁期间不被抢。代价是业务死循环会一直占锁，需业务层避免。
- **否决**：默认固定租期——若租期估算短于业务执行，锁提前释放，比占锁更危险。固定租期作为可选（注解显式给 `leaseTime > 0`）。

## D5: 失败默认 THROW，非 RETURN_NULL

- **决定**：`failStrategy` 默认 `THROW`，抛 `LockAcquireFailureException`。
- **为什么**：`RETURN_NULL` 隐蔽，调用方可能未意识到拿锁失败就按 null 走，引发 NPE 或错误结果。抛异常显式逼处理。
- **否决**：默认 `RETURN_NULL`——静默失败风险高。

## D6: 单模块，不分 annotations / autoconfigure 两个 jar

- **决定**：单 Maven 模块，注解与自动配置同 jar。
- **为什么**：学习项目规模，分两 jar 增加复杂度无收益。生产级 starter 常见拆分（annotations 零依赖、autoconfigure 含实现）留作后续若发布再拆。
- **否决**：双模块拆分——当前过度工程。

## D7: 使用 Spring Boot BOM import，不继承 spring-boot-starter-parent

- **决定**：parent pom 用 `spring-boot-dependencies` BOM `import`，不继承 `spring-boot-starter-parent`。
- **为什么**：parent 已是多模块聚合 pom，改继承 starter-parent 会影响 redisson 等其它模块的构建约定。BOM import 只管版本，侵入性最小。
- **代价**：需自行配置 maven-compiler-plugin（`release=21`、`parameters=true`），无法继承 starter-parent 的默认插件配置。

## D8: 集成测试用 @EnabledIf socket 探测，不依赖 Testcontainers

- **决定**：集成测试 `@EnabledIf("redisAvailable")`，启动前 socket 探测 127.0.0.1:6379，不可达整类跳过。
- **为什么**：Redisson 3.36 在 `Redisson.create()` 即建立连接，Redis 不在会导致 Spring 上下文加载失败，`@BeforeAll` 来不及 skip；必须上下文加载前判定。
- **否决**：Testcontainers——增加测试依赖与 Docker 依赖，与现有 redisson 模块"假定本地 Redis"的测试风格不一致。后续若上 CI 可再引入。
