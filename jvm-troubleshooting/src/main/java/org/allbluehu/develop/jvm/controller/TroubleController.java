package org.allbluehu.develop.jvm.controller;

import org.allbluehu.develop.jvm.service.TroubleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.tags.Param;

/**
 * JVM 故障场景触发接口。
 * <p>
 * 所有接口均为"启动后立即返回"，故障在后台线程持续运行，
 * 配套 stop 接口可随时终止。练习时先 curl 触发，再到服务器上用
 * top / jstat / jstack / jmap 等工具排查。
 */
@Slf4j
@RestController
@RequestMapping("/jvm")
@RequiredArgsConstructor
public class TroubleController {

    private final TroubleService troubleService;

    // ============ 场景一：CPU 飙高 ============

    /** 启动 CPU 飙高。示例: GET /jvm/cpu/start?threads=4 */
    @GetMapping("/cpu/start")
    public ResponseEntity<String> cpuStart(@RequestParam(value = "threads", defaultValue = "4") Integer threads) {
        return ResponseEntity.ok(troubleService.startCpu(threads));
    }

    /** 停止 CPU 飙高。示例: GET /jvm/cpu/stop */
    @GetMapping("/cpu/stop")
    public ResponseEntity<String> cpuStop() {
        return ResponseEntity.ok(troubleService.stopCpu());
    }

    // ============ 场景二：频繁 GC ============

    /** 启动频繁 GC。示例: GET /jvm/gc/start */
    @GetMapping("/gc/start")
    public ResponseEntity<String> gcStart() {
        return ResponseEntity.ok(troubleService.startGc());
    }

    /** 停止频繁 GC。示例: GET /jvm/gc/stop */
    @GetMapping("/gc/stop")
    public ResponseEntity<String> gcStop() {
        return ResponseEntity.ok(troubleService.stopGc());
    }

    // ============ 场景三：内存泄漏 / OOM ============

    /** 启动内存泄漏，最终触发 OOM。示例: GET /jvm/leak/start */
    @GetMapping("/leak/start")
    public ResponseEntity<String> leakStart() {

        return ResponseEntity.ok(troubleService.startLeak());
    }

    /** 停止追加（已泄漏对象仍占用内存）。示例: GET /jvm/leak/stop */
    @GetMapping("/leak/stop")
    public ResponseEntity<String> leakStop() {
        return ResponseEntity.ok(troubleService.stopLeak());
    }

    /** 手动清空泄漏列表，重置练习。示例: GET /jvm/leak/clear */
    @GetMapping("/leak/clear")
    public ResponseEntity<String> leakClear() {
        return ResponseEntity.ok(troubleService.clearLeak());
    }

    // ============ 场景四：线程死锁 ============

    /** 触发一次线程死锁。示例: GET /jvm/deadlock */
    @GetMapping("/deadlock")
    public ResponseEntity<String> deadlock() {
        return ResponseEntity.ok(troubleService.triggerDeadlock());
    }

    // ============ 状态 ============

    /** 查看各场景运行状态。示例: GET /jvm/status */
    @GetMapping("/status")
    public ResponseEntity<String> status() {
        return ResponseEntity.ok(troubleService.status());
    }

    /** 健康检查：确认服务在线。示例: GET /jvm/health */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }
}
