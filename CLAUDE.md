# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目性质

JDK 21 + Maven 多模块**学习/练习**仓库（`org.allbluehu.develop:develop:1.0-SNAPSHOT`），各模块之间基本独立，只有 `starter-test` → `distributed-lock-spring-boot-starter` 一条内部依赖。父 pom 继承 `spring-boot-starter-parent:3.5.16`，并在 `dependencyManagement` 里锁 `redisson:4.6.1`。

**没有 Maven Wrapper**（`.mvn/` 是空目录，无 `mvnw`），一律用系统 `mvn`。

## 常用命令

在仓库根目录（`pom.xml` 所在处）执行：

```bash
mvn clean install                                   # 全量构建
mvn -pl <module> -am test-compile                   # 只编译某模块及其依赖
mvn -pl <module> test                               # 只跑某模块测试
mvn -pl <module> test -Dtest=LockKeyResolverTest    # 跑单个测试类
mvn -pl <module> test -Dtest='LockKeyResolverTest#方法名'   # 跑单个测试方法
```

启动可运行模块：

```bash
mvn -pl starter-test spring-boot:run          # Web 应用，端口 8081
mvn -pl jvm-troubleshooting spring-boot:run   # Web 应用，端口 8000
bash jvm-troubleshooting/run.sh               # 用练习参数（小堆/G1/GC 日志/OOM dump）跑打好的 fat jar
```

`redisson`、`redisDemo` 是纯 Java demo，通过 IDE 或 `java -cp` 跑 main 方法，无 Spring 上下文。

## 模块地图

| 模块 | 性质 | 说明 |
|---|---|---|
| `distributed-lock-spring-boot-starter` | 库（starter） | 核心产出物。注解 + AOP 的声明式分布式锁，见下节 |
| `starter-test` | Spring Boot Web | 上面 starter 的集成验证场，端口 8081 |
| `jvm-troubleshooting` | Spring Boot Web | JVM 排查练习：REST 接口触发 CPU 飙高 / 频繁 GC / 泄漏 OOM / 死锁，端口 8000 |
| `redisson` | 纯 Java demo | Redisson 客户端各模式（单点/哨兵/集群）与 API 学习 |
| `redisDemo` | 纯 Java demo | 手写简化版 Redis 事件循环（NIO Selector + 命令队列） |

根目录 `src/main/java/.../App.java` 是脚手架残留：父 pom 是 `packaging=pom`，这份源码**不参与构建**。

## distributed-lock-spring-boot-starter 架构

调用链：`@DistributedLock` → `LockAspect`（`@Around`）→ `LockKeyResolver`（SpEL 解析 key）→ `LockFactory`（按 `LockType` 造 `RLock`）→ `tryLock` → 成功则 `proceed()`，失败交 `LockFailureHandler`（THROW / RETURN_NULL / FAILBACK）。

包结构：`lock/{annotation,aspect,autoconfigure,exception,properties,support}`，`org.allbluehu.develop.lock` 下。

三个必须一起理解的设计点：

1. **装配条件**：`LockAutoConfiguration` 同时要求 classpath 有 Redisson、容器里**已存在 `RedissonClient` Bean**、`allblue.lock.enabled != false`。starter 刻意**不依赖** `redisson-spring-boot-starter`，`RedissonClient` 由使用方提供（`@AutoConfigureAfter` 指向 Redisson 的自动配置类）。没配 Redis → 整个切面不装配，注解静默失效、不报错。
2. **切面顺序**：`LockAspect` 标 `@Order(HIGHEST_PRECEDENCE + 100)`，必须比事务切面（默认 `LOWEST_PRECEDENCE`）更外层，保证「先拿锁→开事务→提交事务→再放锁」。改 order 会引入脏读/脏写。
3. **key 组装**：`prefix + (namespace 非空 ? namespace + ":" : "") + SpEL 求值`。`keys` 为空时退化为按「类名.方法名」整体加锁。

配置前缀是 **`allblue.lock.*`**（`LockProperties`）：`enabled` / `default-wait-time`(3000ms) / `default-lease-time`(-1 → 看门狗) / `key-namespace`。

### 改这个模块时必须记住的约束

- **`<parameters>true</parameters>` 不能去掉**（该模块 pom 已单独配 `maven-compiler-plugin`）。Spring 6 移除了基于调试信息的参数名发现，没有它 SpEL 的 `#paramName` 会求值为 null。
- `waitTime = -1` 和 `leaseTime = -1` 语义不同：前者取全局默认（**不是无限等待**），后者取全局默认、若默认也是 -1 则启用看门狗续期。
- 释放锁前必须 `isHeldByCurrentThread()` 判断，否则固定租期到期后 `unlock()` 抛 `IllegalMonitorStateException`。
- `LockFactory` 对 `READ`/`WRITE` 只取 `keys[0]`；多 key 互斥要用 `MULTI`。
- `FAILBACK` 的兜底方法靠反射按「同类 + 同名 + 相同参数类型」查找，签名不一致直接抛 `IllegalStateException`。
- AOP 通用限制：类内 self-invocation 不触发切面。

### 测试约定

- `LockKeyResolverTest`：纯单测，无外部依赖，随时可跑。
- `LockAspectIntegrationTest`：需要本地 Redis `127.0.0.1:6379`。用 `@EnabledIf("redisAvailable")` 做 socket 探测，**必须在 Spring 上下文加载前跳过**——`Redisson.create()` 会立即建连，Redis 不可达会让上下文加载直接失败，`@BeforeAll` 里 assume 来不及。
- 起 Redis：`docker run -d -p 6379:6379 --name redis-test redis:7-alpine`
- 集成测试的 `RedissonClient` 由测试内的 `LockTestApplication` 手动 `@Bean` 提供，不走 redisson-spring-boot-starter。

## 已知的仓库内不一致（动到相关代码时留意）

- `starter-test` 目前**跑不通锁功能**：它既没有 `redisson-spring-boot-starter` 依赖，也没有自建 `RedissonClient` Bean，`application.yml` 里写的还是不存在的 `distributed-lock.*` 前缀（实际应为 `allblue.lock.*` + `spring.data.redis.*`）。因此 `LockTestService` 上的 `@DistributedLock` 当前是空转，`contextLoads` 测试照样通过。要真正验证锁，得先补上 RedissonClient。
- `distributed-lock-spring-boot-starter/docs/` 里的版本号（Spring Boot 3.3.5、Redisson 3.36.0）落后于父 pom 实际值（3.5.16 / 4.6.1）。以 pom 为准。
- `redisson/src/main/resources/redisson-config.yaml` 未被 git 跟踪，且写着真实云服务器地址。`.gitignore` 的敏感配置规则匹配的是 `*config.yml` / `**/application-local.yml`，`.yaml` 后缀不在其中——新增含凭据的配置时注意别误提交。

## 文档

`distributed-lock-spring-boot-starter/docs/` 是全仓最完整的文档集（README / requirements / design / decisions / tasks / gotchas），虽然放在子模块下，但 README 是按整个仓库写的。按用户级规范：动手前先读 `docs/tasks.md` 看进度，改方案前读 `docs/decisions.md` 避免重提已否决方案；`decisions.md` 与 `gotchas.md` 只追加不回改。

`jvm-troubleshooting/README.md` 单独维护了四类故障的触发接口表和 `jstack`/`jstat`/`jmap` 排查命令速查。
