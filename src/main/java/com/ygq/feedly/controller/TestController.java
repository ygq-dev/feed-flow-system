package com.ygq.feedly.controller;

import com.ygq.feedly.common.Result;
import com.ygq.feedly.service.DataGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final DataGenerator dataGenerator;

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("Feedly Feed流系统启动成功！");
    }

    @PostMapping("/generate-data")
    public Result<String> generateData() {
        dataGenerator.generateTestData();
        return Result.success("测试数据生成完成！");
    }
}