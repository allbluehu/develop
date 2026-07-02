# 任务与进度

## 已完成

- [x] parent pom 引入 Spring Boot BOM (3.3.5)、新增 starter 模块
- [x] 注解层：`@DistributedLock`、`LockType`、`FailStrategy`
- [x] 异常：`LockAcquireFailureException`
- [x] 属性：`LockProperties`（`allblue.lock.*`）
- [x] 支持：`LockKeyResolver`（SpEL）、`LockFactory`（5 种锁类型）、`LockFailureHandler`（THROW/RETURN_NULL/FAILBACK）
- [x] AOP：`LockAspect`（@Around，@Order 早于事务，释放前判 isHeldByCurrentThread）
- [x] 自动装配：`LockAutoConfiguration`（@ConditionalOnBean RedissonClient + imports 文件）
- [x] 单元测试：`LockKeyResolverTest`（6 例，SpEL 解析）—— **通过**
- [x] 集成测试：`LockAspectIntegrationTest`（互斥 / THROW / FAILBACK）—— 编写完成，**因本地无 Redis 未执行验证，自动跳过**
- [x] docs：requirements / design / decisions / development / gotchas / README

## 验证状态（如实）

- 编译：通过（`mvn test-compile`）。
- 单元测试：6/6 通过。
- 集成测试：3 例因无本地 Redis 被 `@EnabledIf` 跳过，**未实际验证**锁互斥、THROW、FAILBACK 三条路径。
- 后续待补：起本地 Redis 跑通集成测试，确认切面行为。

## 下一步 / 开放问题

- [ ] 起本地 Redis 实跑 `LockAspectIntegrationTest`，验证互斥与失败策略。
- [ ] MULTI 联锁、READ/WRITE 读写并发场景补测。
- [ ] 考虑是否补 `spring-configuration-metadata`（已引 configuration-processor，编译时自动生成，可校验 IDE 提示）。
- [ ] 开放：FAIR 锁与 REENTRANT 在高并发下的行为对比测试是否必要。
- [ ] 开放：是否需要按 `spring.application.name` 自动设 `key-namespace` 默认值（当前默认空）。
