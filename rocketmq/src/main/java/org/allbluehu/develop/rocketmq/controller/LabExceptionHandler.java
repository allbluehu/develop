package org.allbluehu.develop.rocketmq.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将实验参数错误映射为 400，避免非法输入被误报为服务故障。 */
@RestControllerAdvice(assignableTypes = LabController.class)
public class LabExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalidArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
