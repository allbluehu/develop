package org.allbluehu.develop.rocketmq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RocketMQ 实验场入口。
 * <p>
 * 前置条件：先在 rocketmq/docker 目录下 docker compose up -d 启动
 * NameServer(9876) + Broker(10911)，再启动本应用。
 * <p>
 * 各实验通过 HTTP 接口触发（见 {@code LabController}），
 * 消费者随应用启动自动订阅，实验现象主要看本模块打出的日志。
 */
@SpringBootApplication
public class RocketMqLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(RocketMqLabApplication.class, args);
    }
}
