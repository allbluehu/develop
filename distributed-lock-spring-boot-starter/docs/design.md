# 技术方案

## 模块结构

```
develop (parent pom)
├── redisson                         # Redisson 学习 demo
└── distributed-lock-spring-boot-starter
    ├── pom.xml                      # 依赖 spring-boot-autoconfigure / starter-aop / redisson
    └── src/main/
        ├── java/org/allbluehu/develop/lock/
        │   ├── annotation/          # DistributedLock, LockType, FailStrategy
        │   ├── exception/           # LockAcquireFailureException
        │   ├── properties/          # LockProperties (@ConfigurationProperties)
        │   ├── support/             # LockKeyResolver, LockFactory, LockFailureHandler
        │   ├── aspect/              # LockAspect
        │   └── autoconfigure/       # LockAutoConfiguration
        └── resources/META-INF/spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## 依赖关系

- parent pom 用 `spring-boot-dependencies` BOM 统一 Spring 版本（3.3.5），仍管理 `redisson` 3.36.0。
- starter 模块依赖：`spring-boot-autoconfigure`、`spring-boot-starter-aop`、`redisson`、`spring-boot-configuration-processor`(optional)。
- **不依赖 `redisson-spring-boot-starter`**：本 starter 只引用 Redisson 类型，`RedissonClient` Bean 由使用方提供。

## 生效条件（核心）

`LockAutoConfiguration` 装配条件三选一全满足：

1. `@ConditionalOnClass({RedissonClient, RLock})` —— classpath 有 Redisson。
2. `@ConditionalOnBean(RedissonClient.class)` —— 容器中存在 RedissonClient Bean（即配置了 Redis）。
3. `@ConditionalOnProperty(prefix="allblue.lock", name="enabled", matchIfMissing=true)` —— 总开关默认开。

`@AutoConfigureAfter` 指向 redisson-spring-boot-starter 的自动配置类，保证 RedissonClient 先于本配置创建。无 Redis → 无 RedissonClient → 整个切面与相关 Bean 不装配，注解形同虚设。

## 注解设计

`@DistributedLock`（`@Target METHOD`）属性：

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `keys` | `String[]` | `{}` | SpEL 表达式，为空按「类名.方法名」整体加锁 |
| `waitTime` | `long` | `-1` | 等待时间；-1 取全局默认；0 不等待 |
| `leaseTime` | `long` | `-1` | 持有时间；-1 取全局默认（默认看门狗续期） |
| `unit` | `TimeUnit` | `MILLIS` | waitTime/leaseTime 单位 |
| `type` | `LockType` | `REENTRANT` | REENTRANT/FAIR/READ/WRITE/MULTI |
| `prefix` | `String` | `"lock:"` | key 前缀 |
| `failStrategy` | `FailStrategy` | `THROW` | THROW/RETURN_NULL/FAILBACK |
| `fallbackMethod` | `String` | `""` | FAILBACK 时的兜底方法名 |

## key 拼接

`prefix + (namespace 为空 ? "" : namespace + ":") + SpEL值`

例：prefix=`lock:`、namespace=`order`、keys=`{"#orderId"}`、orderId=`100023` → `lock:order:100023`。

namespace 来自 `allblue.lock.key-namespace`，建议设为应用名以隔离不同服务。

## AOP 切面流程

```
解析 SpEL keys → 算 waitMillis/leaseMillis → LockFactory 按 type 建 RLock
  → tryLock(wait, lease, MS)
       失败 → LockFailureHandler（THROW/RETURN_NULL/FAILBACK）
       成功 → pjp.proceed() → finally: isHeldByCurrentThread() 判释放
```

- `LockAspect` 标 `@Order(HIGHEST_PRECEDENCE + 100)`，保证位于事务切面（默认 LOWEST_PRECEDENCE）之外。
- 释放前判 `isHeldByCurrentThread()`，避免锁因租期到期自动释放后被误 unlock。

## 锁类型映射

| LockType | Redisson 实现 | key 使用 |
|---|---|---|
| REENTRANT | `getLock` | 第一个 |
| FAIR | `getFairLock` | 第一个 |
| READ | `getReadWriteLock(k).readLock()` | 第一个 |
| WRITE | `getReadWriteLock(k).writeLock()` | 第一个 |
| MULTI | `new RedissonMultiLock(...)` | 全部 |

## 时间换算

`resolveMillis(value, defaultMillis, unit)`：`value == -1` 取全局默认（已是毫秒）；否则 `unit.toMillis(value)`。统一以毫秒传给 `tryLock`。`leaseTime == -1`（含全局默认）时 Redisson 走看门狗续期。

## 配置项

```yaml
allblue:
  lock:
    enabled: true               # 默认开
    default-wait-time: 3000     # ms
    default-lease-time: -1      # -1 = 看门狗续期
    key-namespace: ""           # 建议设为应用名
```
