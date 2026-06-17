package com.team.study.controller;

import com.team.study.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Result<Map<String, String>> ping() {
        return Result.success(Map.of("message", "pong"));
    }
}
