# 开发与运行

## 环境要求

- JDK 21
- Maven 3.6+
- （集成测试）本地 Redis，127.0.0.1:6379

## 构建

```bash
# 全量构建
mvn clean install

# 只编译 starter
mvn -pl distributed-lock-spring-boot-starter -am test-compile

# 跑 starter 测试（无 Redis 时集成测试自动跳过）
mvn -pl distributed-lock-spring-boot-starter test
```

## 跑集成测试

需要本地 Redis：

```bash
docker run -d -p 6379:6379 --name redis-test redis:7-alpine
mvn -pl distributed-lock-spring-boot-starter test
```

无 Redis 时，`LockAspectIntegrationTest` 整类跳过，`LockKeyResolverTest`（SpEL 解析单测）正常执行。

## 使用方接入

1. 引入本 starter + `redisson-spring-boot-starter`：

```xml
<dependency>
  <groupId>org.allbluehu.develop</groupId>
  <artifactId>distributed-lock-spring-boot-starter</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>org.redisson</groupId>
  <artifactId>redisson-spring-boot-starter</artifactId>
  <version>3.36.0</version>
</dependency>
```

2. 配置 Redis：

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
allblue:
  lock:
    key-namespace: order-service   # 建议设应用名
```

3. 标注注解：

```java
@DistributedLock(keys = "#orderId")
public void cancel(String orderId) { ... }
```

## 编译约定（本 starter 模块）

- `maven-compiler-plugin`：`release=21`、`parameters=true`。
- `parameters=true` 是必须的：SpEL `#paramName` 依赖编译期保留参数名；Spring 6 已移除基于调试信息的参数名回退发现。
