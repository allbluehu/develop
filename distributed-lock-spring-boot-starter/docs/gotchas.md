# 踩坑与已知约束（append-only）

## G1: 必须开 `-parameters`，否则 SpEL 取不到参数名

Spring 6 移除了 `LocalVariableTableParameterNameDiscoverer`，`DefaultParameterNameDiscoverer` 只能用 `-parameters` 反射。未开 `-parameters` 时 `#orderId` 会求值为 null（不报错），导致所有 key 退化为相同值或抛"evaluated to null"。

**处理**：starter 模块 pom 已配 `<parameters>true</parameters>`。使用方若自行编译需注意。

## G2: Redisson 3.36 `Redisson.create()` 立即连接验证

3.36 在 `create()` 时即建连，Redis 不可达直接抛 `RedisConnectionException`，不像旧版本延迟连接。

**影响**：测试中 `@SpringBootTest` 提供的 RedissonClient Bean 创建会因无 Redis 失败，导致上下文加载失败。`@BeforeAll` 来不及 `assumeTrue` 跳过。

**处理**：集成测试用 `@EnabledIf` socket 探测，在上下文加载前整类跳过。

## G3: 锁与事务的顺序——必须锁在事务外

若锁切面在事务内部，会出现：事务还未提交锁已释放 → 并发请求读到未提交数据；或事务回滚但锁已放 → 脏数据被并发覆盖。

**处理**：`LockAspect` 标 `@Order(HIGHEST_PRECEDENCE + 100)`，事务默认 `LOWEST_PRECEDENCE`，保证「先锁后事务、先提交后放锁」。使用方若自定义了事务 order，需确保锁 order 更小（更外层）。

## G4: 释放锁前必须判 isHeldByCurrentThread

固定租期下，业务执行时间可能超过 `leaseTime`，锁已被 Redis 自动释放。此时 `unlock()` 会抛 `IllegalMonitorStateException`。

**处理**：`finally` 中先 `lock.isHeldByCurrentThread()` 再 `unlock()`。

## G5: self-invocation 不触发切面

同类内部 A 方法调本类 B 方法（B 上有 `@DistributedLock`），不经过代理，注解失效。Spring AOP 通用限制。

**规避**：被锁方法放到独立 Bean，或通过 `AopContext.currentProxy()` 调用（需开 `@EnableAspectJAutoProxy(exposeProxy=true)`）。

## G6: 看门狗的前提是持锁线程存活

`leaseTime=-1` 走看门狗，Redisson 默认每 10s 续期到 30s。若持锁线程死循环或被阻塞，锁会被一直占用直到客户端断开。

**规避**：业务方法必须保证可结束；对不可控的外部调用慎用看门狗，可改用固定 `leaseTime` 上限兜底。

## G7: FAILBACK 兜底方法签名需与原方法一致

`LockFailureHandler` 用反射按方法名 + 原方法参数类型查找兜底方法。参数列表不同会找不到并抛 `IllegalStateException`。兜底方法需与原方法在**同一个类**、**同参数列表**。

## G8: READ/WRITE 只用第一个 key

`LockFactory` 对 READ/WRITE 仅取 `keys[0]` 作为读写锁名，多 key 会被忽略。多 key 互斥请用 `MULTI`。需在文档/使用处说明，避免误用。

## G9: waitTime 与 leaseTime 的 -1 语义不同

- `waitTime = -1`：取全局默认 `defaultWaitTime`（3000ms）。**不支持无限等待**（-1 不会阻塞永久，会被替换为默认值）。
- `leaseTime = -1`：取全局默认 `defaultLeaseTime`；若该默认也是 -1，则触发看门狗续期。

两者都是 -1 但含义不同，使用时注意。
