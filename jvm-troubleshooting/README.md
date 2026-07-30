# JVM 实战排查练习模块

通过 REST 接口触发真实故障场景（CPU 飙高 / 频繁 GC / 内存泄漏 OOM / 线程死锁），
部署到云服务器后用 `top` / `jstack` / `jstat` / `jmap` 等工具练习排查。

## 1. 构建与启动

### 方式 A：本地打包，上传 jar 到服务器（推荐，服务器只需 JRE）

```bash
# 本地（项目根目录）
mvn -pl jvm-troubleshooting -am clean package -DskipTests
# 把 target/jvm-troubleshooting.jar 和 run.sh 一起上传到服务器同目录

# 服务器上
bash run.sh            # 检测到 jar 直接启动，无需 maven
```

### 方式 B：服务器上装了 maven，直接编译启动

```bash
bash run.sh build      # 先编译再启动
```

启动后服务监听 `8000` 端口。`run.sh` 会用小堆 + GC 日志 + OOM 自动 dump 的参数启动。

### JVM 参数说明（已在 run.sh 中配好）

| 参数 | 作用 |
|---|---|
| `-Xms256m -Xmx256m` | 小堆，让故障（尤其 OOM）快速复现 |
| `-XX:+UseG1GC` | G1 收集器，生产常见 |
| `-Xlog:gc*:file=gc.log:...` | GC 日志输出到 `gc.log` |
| `-XX:+HeapDumpOnOutOfMemoryError` | OOM 时自动生成堆 dump |
| `-XX:HeapDumpPath=heapdump/` | dump 文件目录 |

## 2. 故障场景与触发接口

| 场景 | 触发 | 停止 |
|---|---|---|
| CPU 飙高 | `GET /jvm/cpu/start?threads=4` | `GET /jvm/cpu/stop` |
| 频繁 GC | `GET /jvm/gc/start` | `GET /jvm/gc/stop` |
| 内存泄漏/OOM | `GET /jvm/leak/start` | `GET /jvm/leak/stop` （已泄漏对象需重启才释放） |
| 线程死锁 | `GET /jvm/deadlock` | 一次性触发，无需停止 |
| 状态汇总 | `GET /jvm/status` | — |

> 触发示例：`curl http://localhost:8000/jvm/cpu/start?threads=4`

后台线程都带语义化名字（`cpu-burn-*` / `gc-allocator` / `leak-feeder` /
`deadlock-thread-A|B`），`jstack` 抓出来一眼就能定位。

## 3. 排查命令速查

> 下文 `<pid>` 为 Java 进程号，用 `jps` 或 `ps -ef | grep jvm-troubleshooting` 查。

### 场景一：CPU 飙高

```bash
# 1. 找到 Java 进程
jps
# 2. 看进程级 CPU（找最占 CPU 的进程）
top
# 3. 看该进程内哪个线程最占 CPU（-H 显示线程，-p 指定进程）
top -Hp <pid>
#    记下 CPU 最高的线程 TID（十进制）
# 4. 线程 TID 转十六进制
printf "%x\n" <tid>
# 5. jstack 抓线程栈，搜索上面的十六进制 nid
jstack <pid> > stack.txt
grep <hex_tid> stack.txt -A 30
#    看这 30 行栈，就能定位到 cpu-burn-* 在跑 burnCpu()
```

### 场景二：频繁 GC

```bash
# 1. 看各代容量与 GC 次数/耗时（每 1s 刷新）
jstat -gc <pid> 1000
#    重点列：YGC(Young GC次数) YGCT(Young GC总耗时) FGC(Full GC次数) FGCT(Full GC总耗时) GCT(GC总耗时)
# 2. 或直接看 GC 日志
tail -f gc.log
# 3. 堆内存分布概览
jmap -heap <pid>
```
观察 `YGC`/`FGC` 是否快速增长、`GCT` 占比是否过高，判断是频繁 Young GC 还是 Full GC。

### 场景三：内存泄漏 / OOM

```bash
# 1. 触发后等待 OOM，自动 dump 到 heapdump/ 目录
ls -lh heapdump/
# 2. 堆使用情况
jmap -heap <pid>
# 3. 按对象大小直方图，找占用最大的类型（byte[] 会很明显）
jmap -histo:live <pid> | head -30
# 4. 导出 dump 用 MAT / VisualVM 分析
jmap -dump:format=b,file=manual.hprof <pid>
```
用 [Eclipse MAT](https://eclipse.dev/mat/) 打开 `.hprof`，看 Dominator Tree 和
Leak Suspects 报告，会指向 `TroubleService.LEAK_HOLDER` 这个 static List。

### 场景四：线程死锁

```bash
# 一次抓取，jstack 末尾会自动检测并报告死锁
jstack <pid> > deadlock.txt
tail -30 deadlock.txt
#    会出现 "Found 1 deadlock"，并列出 deadlock-thread-A / deadlock-thread-B 互相等待的锁
```
也可访问 `curl http://localhost:8000/actuator/threaddump`（Actuator 暴露）拿线程快照。

## 4. 其它观察手段

- Actuator 端点（已暴露）：`/actuator/health`、`/actuator/metrics/jvm.gc.pause`、
  `/actuator/metrics/jvm.memory.used`、`/actuator/threaddump`。
- [Arthas](https://arthas.aliyun.com/)：`arthas-boot.jar` attach 后用
  `dashboard` / `thread -n 3` / `thread -b`(找阻塞) / `jad` / `heapdump` 等命令，排查体验更好。

## 5. 练习建议顺序

1. CPU 飙高 → `top -Hp` + `jstack` 定位热线程（最经典流程）
2. 频繁 GC → `jstat -gc` + GC 日志，区分 Young/Full
3. 内存泄漏 → OOM 自动 dump + MAT 分析支配树
4. 线程死锁 → `jstack` 看末尾死锁报告
