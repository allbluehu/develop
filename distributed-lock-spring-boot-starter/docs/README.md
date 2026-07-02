# develop 项目文档导览

本仓库是 JDK21 + Maven 多模块学习/开发项目。当前包含两个模块：

- **redisson** —— Redisson 基础学习 demo（纯 Java，无 Spring）。
- **distributed-lock-spring-boot-starter** —— 基于 Redisson 的分布式锁 Spring Boot Starter。

## 从哪开始

| 想了解 | 读这份 |
|---|---|
| starter 要解决什么、边界在哪 | [requirements.md](requirements.md) |
| 架构与关键设计 | [design.md](design.md) |
| 为什么这么决策（含被否方案） | [decisions.md](decisions.md) |
| 当前进度 / 下一步 / 开放问题 | [tasks.md](tasks.md) |
| 怎么装、怎么跑、怎么测 | [development.md](development.md) |
| 踩坑与非显而易见约束 | [gotchas.md](gotchas.md) |

## 一句话概览

starter 通过自定义 `@DistributedLock` 注解 + AOP 切面，把 Redisson 的 `RLock` 包装成声明式分布式锁；只有当 Spring 容器中存在 `RedissonClient` Bean（即配置了 Redis）时才装配，注解支持 SpEL key、多种锁类型、等待/持有时间、失败策略。
