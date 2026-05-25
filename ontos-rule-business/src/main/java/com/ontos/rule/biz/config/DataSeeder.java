package com.ontos.rule.biz.config;

import com.ontos.rule.biz.domain.QualityScore;
import com.ontos.rule.biz.domain.RuleRun;
import com.ontos.rule.biz.domain.RunStatus;
import com.ontos.rule.biz.domain.ScoreWeight;
import com.ontos.rule.biz.domain.Violation;
import com.ontos.rule.biz.repo.QualityScoreRepository;
import com.ontos.rule.biz.repo.RuleRepository;
import com.ontos.rule.biz.repo.RuleRunRepository;
import com.ontos.rule.biz.repo.ScoreWeightRepository;
import com.ontos.rule.biz.repo.ViolationRepository;
import com.ontos.rule.biz.service.RuleService;
import com.ontos.rule.biz.web.dto.CheckSpec;
import com.ontos.rule.biz.web.dto.CreateRuleRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 启动种入 + 手动重置入口。
 *
 * <p>启动时按 ID 逐条 idempotent 插入。
 * 手动重置（AdminController）：先清空 5 张表，再调 {@link Seeder#seedAll()}。
 *
 * <p>禁用启动种入：{@code ontos.seed.enabled=false}
 */
@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    ApplicationRunner runSeederOnStartup(
            Seeder seeder,
            @Value("${ontos.seed.enabled:true}") boolean enabled) {
        return args -> {
            if (!enabled) {
                log.info("[seed] ontos.seed.enabled=false，跳过启动种入");
                return;
            }
            seeder.seedAll();
        };
    }

    // ============================================================
    // Seeder 服务（可被启动 Runner 和 AdminController 同时使用）
    // ============================================================
    @Component
    public static class Seeder {

        private final RuleService ruleService;
        private final RuleRepository ruleRepo;
        private final RuleRunRepository runRepo;
        private final ViolationRepository violationRepo;
        private final ScoreWeightRepository weightRepo;
        private final QualityScoreRepository scoreRepo;

        public Seeder(RuleService ruleService, RuleRepository ruleRepo,
                      RuleRunRepository runRepo, ViolationRepository violationRepo,
                      ScoreWeightRepository weightRepo, QualityScoreRepository scoreRepo) {
            this.ruleService = ruleService;
            this.ruleRepo = ruleRepo;
            this.runRepo = runRepo;
            this.violationRepo = violationRepo;
            this.weightRepo = weightRepo;
            this.scoreRepo = scoreRepo;
        }

        public SeedSummary seedAll() {
            int rules = seedRules();
            int weights = seedWeights();
            RunSeedResult run = seedRuns();
            int scores = seedScores();
            return new SeedSummary(rules, weights, run.runs, run.violations, scores);
        }

        /** 清空 5 张演示表（保留 schema） */
        @Transactional
        public void wipeAll() {
            violationRepo.deleteAllInBatch();
            runRepo.deleteAllInBatch();
            scoreRepo.deleteAllInBatch();
            weightRepo.deleteAllInBatch();
            ruleRepo.deleteAllInBatch();
            log.info("[seed] 已清空 5 张表");
        }

        // ============ Rules ============
        int seedRules() {
            int inserted = 0, skipped = 0;
            for (CreateRuleRequest req : RULE_SEEDS) {
                if (req.id() != null && ruleRepo.existsById(req.id())) { skipped++; continue; }
                try { ruleService.create(req); inserted++; }
                catch (Exception e) { log.warn("[seed-rule] {} 失败: {}", req.id(), e.getMessage()); }
            }
            log.info("[seed-rule] 新插 {} · 跳过 {} · 总 {}", inserted, skipped, RULE_SEEDS.size());
            return inserted;
        }

        // ============ Weights ============
        @Transactional
        int seedWeights() {
            if (weightRepo.count() > 0) {
                log.info("[seed-weight] 已有 {} 条权重，跳过", weightRepo.count());
                return 0;
            }
            List<ScoreWeight> weights = List.of(
                new ScoreWeight("*", "completeness", bd("1.0")),
                new ScoreWeight("*", "uniqueness",   bd("1.0")),
                new ScoreWeight("*", "validity",     bd("1.5")),
                new ScoreWeight("*", "consistency",  bd("1.2")),
                new ScoreWeight("*", "accuracy",     bd("1.0")),
                new ScoreWeight("*", "timeliness",   bd("0.8")),
                new ScoreWeight("Lot", "validity",   bd("2.0")),
                new ScoreWeight("Equipment", "consistency", bd("1.8")),
                new ScoreWeight("Wafer", "accuracy", bd("1.5"))
            );
            weightRepo.saveAll(weights);
            log.info("[seed-weight] 插入 {} 条权重", weights.size());
            return weights.size();
        }

        // ============ Runs + Violations ============
        @Transactional
        RunSeedResult seedRuns() {
            if (runRepo.count() > 0) {
                log.info("[seed-run] 已有 {} 条 run，跳过", runRepo.count());
                return new RunSeedResult(0, 0);
            }
            List<String> ruleIds = ruleRepo.findAll().stream().map(r -> r.getId()).toList();
            if (ruleIds.isEmpty()) {
                log.warn("[seed-run] 规则表空，跳过");
                return new RunSeedResult(0, 0);
            }

            Random rnd = new Random(42);
            String[] dataSources = {"mock-lot", "mock-equipment", "mock-wafer"};
            String[] backends    = {"JVM", "SQL"};
            String[] callers     = {"src-rest-api", "src-scheduler", "src-ci-pipeline"};

            List<RuleRun> runs = new ArrayList<>();
            List<Violation> violations = new ArrayList<>();
            Instant now = Instant.now();

            // 200 条 run · 按 PPT 实测耗时 · 分散在过去 30 天
            for (int i = 0; i < 200; i++) {
                String ruleId = ruleIds.get(rnd.nextInt(ruleIds.size()));
                long minutesAgo = (long) rnd.nextInt(30 * 24 * 60);
                Instant started = now.minus(minutesAgo, ChronoUnit.MINUTES);

                long total = rnd.nextInt(100) < 85
                    ? 100 + rnd.nextInt(9900)
                    : 50_000 + rnd.nextInt(150_000);

                String backend = backends[rnd.nextInt(backends.length)];
                long durMs = mockDuration(total, backend, rnd);

                RunStatus status;
                long viol;
                int roll = rnd.nextInt(100);
                if (roll < 2) {
                    status = RunStatus.FAILED;
                    viol = 0;
                    durMs = 5 + rnd.nextInt(80);
                } else {
                    status = RunStatus.SUCCESS;
                    viol = roll < 30 ? rnd.nextInt((int) (total / 200) + 1) : 0;
                }

                RuleRun r = new RuleRun();
                r.setRuleId(ruleId);
                r.setStartedAt(started);
                r.setFinishedAt(started.plusMillis(durMs));
                r.setDurationMs(durMs);
                r.setTotalRows(total);
                r.setViolationCount(viol);
                r.setBackendUsed(backend);
                r.setStatus(status);
                r.setDataSource(dataSources[rnd.nextInt(dataSources.length)]);
                r.setCaller(callers[rnd.nextInt(callers.length)]);
                if (status == RunStatus.FAILED) r.setError("数据源连接超时（mock）");
                runs.add(r);
            }
            List<RuleRun> savedRuns = runRepo.saveAll(runs);

            for (RuleRun r : savedRuns) {
                if (r.getViolationCount() <= 0) continue;
                int sampleSize = (int) Math.min(5, r.getViolationCount());
                for (int j = 0; j < sampleSize; j++) {
                    Violation v = new Violation();
                    v.setRunId(r.getId());
                    v.setTargetPk(mockPk(r.getRuleId(), j, rnd));
                    v.setViolatingValue(mockValue(r.getRuleId(), rnd));
                    v.setContext("{\"ruleId\":\"" + r.getRuleId() + "\",\"sample\":" + j + "}");
                    v.setSampledAt(r.getFinishedAt());
                    violations.add(v);
                }
            }
            violationRepo.saveAll(violations);
            log.info("[seed-run] 插入 {} 条 run · {} 条违规", savedRuns.size(), violations.size());
            return new RunSeedResult(savedRuns.size(), violations.size());
        }

        // ============ Scores ============
        @Transactional
        int seedScores() {
            if (scoreRepo.count() > 0) {
                log.info("[seed-score] 已有 {} 条评分，跳过", scoreRepo.count());
                return 0;
            }
            Random rnd = new Random(7);
            List<QualityScore> scores = new ArrayList<>();
            Instant now = Instant.now();

            String[] objects = {"Lot", "Equipment", "Wafer", "SkyTech-FAB1"};
            String[] types   = {"object", "object", "object", "project"};

            for (int oi = 0; oi < objects.length; oi++) {
                double base = 70 + oi * 5 + rnd.nextDouble() * 5;
                for (int week = 5; week >= 0; week--) {
                    QualityScore s = new QualityScore();
                    s.setTargetType(types[oi]);
                    s.setTargetId(objects[oi]);
                    s.setSnapshotAt(now.minus(week * 7L, ChronoUnit.DAYS));
                    double overall = Math.min(99, base + (5 - week) * 1.8 + (rnd.nextDouble() - 0.5) * 4);
                    s.setOverallScore(bd(String.format("%.2f", overall)));
                    s.setGrade(grade(overall));
                    s.setCompleteness(bd(String.format("%.2f", clamp(overall + (rnd.nextDouble() - 0.5) * 8))));
                    s.setUniqueness  (bd(String.format("%.2f", clamp(overall + (rnd.nextDouble() - 0.5) * 6))));
                    s.setValidity    (bd(String.format("%.2f", clamp(overall + (rnd.nextDouble() - 0.5) * 10))));
                    s.setConsistency (bd(String.format("%.2f", clamp(overall + (rnd.nextDouble() - 0.5) * 7))));
                    s.setAccuracy    (bd(String.format("%.2f", clamp(overall + (rnd.nextDouble() - 0.5) * 5))));
                    s.setTimeliness  (bd(String.format("%.2f", clamp(overall + (rnd.nextDouble() - 0.5) * 9))));
                    s.setRulesCount(8 + rnd.nextInt(8));
                    s.setTotalRows(1000L + rnd.nextInt(8000));
                    s.setViolationsCount((long) rnd.nextInt(80));
                    s.setTriggerType(week == 0 ? "manual" : "scheduled");
                    scores.add(s);
                }
            }
            scoreRepo.saveAll(scores);
            log.info("[seed-score] 插入 {} 条评分", scores.size());
            return scores.size();
        }
    }

    // ============================================================
    // 数据定义 + Helpers
    // ============================================================
    private static final List<CreateRuleRequest> RULE_SEEDS = List.of(
        // ===== Lot 系列 =====
        form("QR-001", "Lot.lotId 必填且唯一", "Lot.lotId", "ERROR", "AUTO", "质量管理",
            new CheckSpec("not_blank", Map.of()),
            new CheckSpec("unique", Map.of()),
            new CheckSpec("pattern", Map.of("regex", "^LOT-\\d{6}$"))),
        form("QR-002", "Lot 温度在合理区间", "Lot.temperature", "ERROR", "AUTO", "工艺工程",
            new CheckSpec("range", Map.of("min", 100, "max", 800))),
        form("QR-003", "Lot 名称长度", "Lot.name", "WARN", "AUTO", "质量管理",
            new CheckSpec("length", Map.of("min", 3, "max", 64))),
        form("QR-004", "Lot 产线编号枚举", "Lot.lineId", "WARN", "SQL", "工艺工程",
            new CheckSpec("enum", Map.of("allowed", "L01,L02,L03,L04,L05"))),
        cel("QR-005", "Lot 温度+容差不超上限", "Lot",
            "temperature + tolerance < maxLimit",
            "validity,consistency", "WARN", "AUTO", "工艺工程"),

        // ===== Equipment 系列 =====
        form("QR-006", "设备状态枚举", "Equipment.status", "WARN", "SQL", "设备组",
            new CheckSpec("enum", Map.of("allowed", "RUNNING,IDLE,MAINTENANCE,DOWN"))),
        form("QR-007", "设备型号前缀 EQ-", "Equipment.model", "WARN", "AUTO", "设备组",
            new CheckSpec("starts_with", Map.of("prefix", "EQ-"))),
        form("QR-008", "设备 ID 必填且唯一", "Equipment.equipId", "ERROR", "JVM", "设备组",
            new CheckSpec("not_null", Map.of()),
            new CheckSpec("unique", Map.of())),
        cel("QR-009", "运行中设备温度告警", "Equipment",
            "status == \"RUNNING\" && temperature < 90",
            "validity", "ERROR", "JVM", "设备组"),
        cel("QR-010", "在维护设备不应有待处理任务", "Equipment",
            "status == \"MAINTENANCE\" && pendingJobs == 0",
            "consistency", "ERROR", "AUTO", "设备组"),

        // ===== Wafer 系列 =====
        form("QR-011", "Wafer 编号格式", "Wafer.waferId", "ERROR", "JVM", "质量管理",
            new CheckSpec("not_null", Map.of()),
            new CheckSpec("pattern", Map.of("regex", "^W[0-9]{8}$"))),
        form("QR-012", "Wafer 厚度范围", "Wafer.thickness", "ERROR", "AUTO", "质量管理",
            new CheckSpec("range", Map.of("min", 0.5, "max", 1.5))),
        form("QR-013", "Wafer 良率不低于 80%", "Wafer.yield", "WARN", "AUTO", "质量管理",
            new CheckSpec("range", Map.of("min", 0.8))),

        // ===== 时效性 / 跨字段 =====
        form("QR-014", "Lot 创建时间在 30 天内", "Lot.createdAt", "WARN", "AUTO", "数据治理",
            new CheckSpec("time_within", Map.of("days", 30))),
        form("QR-015", "实际产量不超过计划", "Lot", "WARN", "AUTO", "工艺工程",
            new CheckSpec("cross_field", Map.of("left", "actualQty", "op", "<=", "right", "plannedQty"))),
        cel("QR-016", "Wafer 良率与等级一致", "Wafer",
            "(grade == \"A\" && yield >= 0.95) || (grade == \"B\" && yield >= 0.85) || grade == \"C\"",
            "consistency", "WARN", "AUTO", "质量管理"),
        cel("QR-017", "高温产品需要标记危险品", "Lot",
            "temperature < 500 || isDangerous == true",
            "consistency", "ERROR", "AUTO", "工艺工程"),
        form("QR-018", "Lot 描述字符串非空", "Lot.description", "WARN", "AUTO", "数据治理",
            new CheckSpec("not_blank", Map.of()))
    );

    public record SeedSummary(int rules, int weights, int runs, int violations, int scores) {}
    private record RunSeedResult(int runs, int violations) {}

    private static CreateRuleRequest form(String id, String name, String target,
                                          String severity, String backend, String owner,
                                          CheckSpec... checks) {
        return new CreateRuleRequest(id, name, target, List.of(checks), null, null,
            severity, backend, owner);
    }

    private static CreateRuleRequest cel(String id, String name, String target,
                                         String expression, String dimensions,
                                         String severity, String backend, String owner) {
        return new CreateRuleRequest(id, name, target, null, expression, dimensions,
            severity, backend, owner);
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private static double clamp(double v) { return Math.max(40, Math.min(99, v)); }

    private static String grade(double s) {
        if (s >= 95) return "A+";
        if (s >= 90) return "A";
        if (s >= 80) return "B";
        if (s >= 70) return "C";
        return "D";
    }

    private static long mockDuration(long totalRows, String backend, Random rnd) {
        double rowsPerMs = "JVM".equals(backend) ? 380.0 : 1200.0;
        long base = (long) Math.max(8, totalRows / rowsPerMs);
        long roundTrip = "SQL".equals(backend) ? 18 + rnd.nextInt(45) : 0;
        double jitter = 1.0 + (rnd.nextDouble() - 0.5) * 0.5;
        return Math.max(5, (long) ((base + roundTrip) * jitter));
    }

    private static String mockPk(String ruleId, int idx, Random rnd) {
        return switch (ruleId.charAt(3)) {
            case '0' -> "LOT-" + String.format("%06d", 100000 + rnd.nextInt(900000));
            case '1' -> "W" + String.format("%08d", rnd.nextInt(99999999));
            default  -> "EQ-" + String.format("%04d", rnd.nextInt(9999));
        };
    }

    private static String mockValue(String ruleId, Random rnd) {
        return switch (ruleId) {
            case "QR-002" -> String.valueOf(80 + rnd.nextInt(40));
            case "QR-005" -> String.valueOf(820 + rnd.nextInt(60));
            case "QR-012" -> String.format("%.2f", 0.3 + rnd.nextDouble() * 0.2);
            case "QR-013" -> String.format("%.3f", 0.6 + rnd.nextDouble() * 0.15);
            default       -> "null";
        };
    }
}
