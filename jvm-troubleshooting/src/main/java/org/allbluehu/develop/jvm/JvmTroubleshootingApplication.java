package org.allbluehu.develop.jvm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JVM 实战排查练习应用
 * <p>
 * 通过 REST 接口触发以下故障场景，用于在云服务器上练习 JVM 排查：
 * <ul>
 *   <li>CPU 飙高</li>
 *   <li>频繁 GC / GC 停顿</li>
 *   <li>内存泄漏 / OOM</li>
 *   <li>线程死锁</li>
 * </ul>
 * 启动 JVM 参数见模块 README。
 */
@SpringBootApplication
public class JvmTroubleshootingApplication {

    public static void main(String[] args) {
        SpringApplication.run(JvmTroubleshootingApplication.class, args);
    }
}
