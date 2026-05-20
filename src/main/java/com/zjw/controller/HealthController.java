package com.zjw.controller;

import com.zjw.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口
 *
 * @author ZhangJw
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResultUtils<Map<String, Object>> health() {
        Map<String, Object> info = new HashMap<>();
        info.put("status", "UP");
        info.put("service", "ai-agent-langchain4j");
        info.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        info.put("version", "1.0.0");
        return ResultUtils.success(info);
    }
}
