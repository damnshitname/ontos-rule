package com.ontos.rule.biz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 评分定时任务。
 *
 * <p>默认每天凌晨 2 点全量重算（cron 可在 application.yml 调整）。
 * 应用启动时也会触发一次（避免初次访问 API 时数据空）。
 */
@Component
public class QualityScoreScheduler {

    private static final Logger log = LoggerFactory.getLogger(QualityScoreScheduler.class);

    private final QualityScoreService scoreService;

    @Value("${ontos.rule.quality.scheduler-enabled:true}")
    private boolean enabled;

    public QualityScoreScheduler(QualityScoreService scoreService) {
        this.scoreService = scoreService;
    }

    /**
     * 每天凌晨 2 点全量重算。
     * cron 可通过 {@code ontos.rule.quality.cron} 覆盖。
     */
    @Scheduled(cron = "${ontos.rule.quality.cron:0 0 2 * * ?}")
    public void dailyRecompute() {
        if (!enabled) {
            log.debug("Quality scheduler disabled, skip");
            return;
        }
        Instant start = Instant.now();
        try {
            var scores = scoreService.recomputeAll("scheduled");
            log.info("Daily quality recompute done · {} objects · cost {} ms",
                scores.size(), java.time.Duration.between(start, Instant.now()).toMillis());
        } catch (RuntimeException e) {
            log.error("Daily quality recompute failed", e);
        }
    }

    /**
     * 应用启动 30 秒后自动跑一次（让大盘 API 有数据可看）。
     * 仅当数据库尚无评分记录时触发。
     */
    @Scheduled(initialDelay = 30_000, fixedDelay = Long.MAX_VALUE)
    public void onStartupSeed() {
        if (!enabled) return;
        if (scoreService.getLatestProject() != null) {
            log.debug("Quality scores already exist, skip startup seed");
            return;
        }
        try {
            var scores = scoreService.recomputeAll("startup-seed");
            log.info("Startup-seed quality recompute done · {} objects", scores.size());
        } catch (RuntimeException e) {
            log.warn("Startup-seed quality recompute failed (will retry on next cron): {}", e.getMessage());
        }
    }
}
