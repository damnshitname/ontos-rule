package com.ontos.rule.biz.web;

import com.ontos.rule.biz.config.DataSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示后台：一键重置 + 重新种入 mock 数据。
 *
 * <p>主要用于演示场景的"清空回到初始状态"。
 * 生产环境建议关闭（{@code ontos.seed.enabled=false}）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final DataSeeder.Seeder seeder;

    public AdminController(DataSeeder.Seeder seeder) {
        this.seeder = seeder;
    }

    /**
     * 重置演示数据。
     * @param wipe 是否先清空 5 张表（默认 true）。false 时只补缺，不动现有数据。
     */
    @PostMapping("/reseed")
    public Map<String, Object> reseed(@RequestParam(defaultValue = "true") boolean wipe) {
        log.info("[admin] /reseed 触发 · wipe={}", wipe);
        if (wipe) seeder.wipeAll();
        DataSeeder.SeedSummary s = seeder.seedAll();
        return Map.of(
            "wiped", wipe,
            "rules", s.rules(),
            "weights", s.weights(),
            "runs", s.runs(),
            "violations", s.violations(),
            "scores", s.scores(),
            "message", wipe ? "已清空并重新种入" : "增量补缺完成"
        );
    }
}
