package com.ontos.rule.biz.service;

import com.ontos.rule.biz.domain.QualityScore;
import com.ontos.rule.biz.domain.Rule;
import com.ontos.rule.biz.domain.RuleRun;
import com.ontos.rule.biz.domain.RuleStatus;
import com.ontos.rule.biz.domain.ScoreWeight;
import com.ontos.rule.biz.repo.RuleRepository;
import com.ontos.rule.biz.repo.RuleRunRepository;
import com.ontos.rule.biz.repo.ScoreWeightRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 质量评分计算器。
 *
 * <p>算法（4 层聚合）：
 * <pre>
 * 1. 单规则得分 = (1 - violations/totalRows) × 100
 * 2. 单维度得分 = 该维度所有规则得分的算术平均
 * 3. 对象总分   = 6 维度的加权平均（权重来自 ScoreWeight；缺省 1.0）
 * 4. 项目总分   = 所有对象总分的加权平均（按对象权重）
 * </pre>
 *
 * <p>等级映射：A+(≥95) / A(≥85) / B(≥70) / C(≥50) / D(其他)
 */
@Service
public class ScoreCalculator {

    private static final Logger log = LoggerFactory.getLogger(ScoreCalculator.class);

    public static final List<String> DIMENSIONS = List.of(
        "completeness", "uniqueness", "validity", "consistency", "accuracy", "timeliness"
    );

    private final RuleRepository ruleRepo;
    private final RuleRunRepository runRepo;
    private final ScoreWeightRepository weightRepo;

    public ScoreCalculator(RuleRepository ruleRepo, RuleRunRepository runRepo,
                            ScoreWeightRepository weightRepo) {
        this.ruleRepo = ruleRepo;
        this.runRepo = runRepo;
        this.weightRepo = weightRepo;
    }

    /**
     * 计算一个对象的评分快照（不持久化，仅返回结果）。
     *
     * @param objectId 对象 ID，如 "Lot" / "Equipment"
     * @return 评分快照（已填充 6 维 + 总分 + 等级，未持久化）
     */
    public QualityScore computeObject(String objectId) {
        // 1. 找该对象的所有 ACTIVE 规则
        List<Rule> rules = ruleRepo.findAll().stream()
            .filter(r -> r.getStatus() == RuleStatus.ACTIVE)
            .filter(r -> matchesObject(r.getTarget(), objectId))
            .toList();

        // 2. 对每条规则计算得分 + 按维度归类
        Map<String, List<BigDecimal>> byDim = new HashMap<>();
        for (String d : DIMENSIONS) byDim.put(d, new ArrayList<>());

        long sumViolations = 0, sumRows = 0;
        for (Rule rule : rules) {
            List<RuleRun> latest = runRepo.findByRuleIdOrderByStartedAtDesc(
                rule.getId(), PageRequest.of(0, 1));
            if (latest.isEmpty()) continue;
            RuleRun run = latest.get(0);
            if (run.getTotalRows() == 0) continue;

            BigDecimal passRate = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(run.getViolationCount())
                    .divide(BigDecimal.valueOf(run.getTotalRows()), 4, RoundingMode.HALF_UP)
            );
            BigDecimal score = passRate.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

            sumViolations += run.getViolationCount();
            sumRows += run.getTotalRows();

            // 该规则的维度
            Set<String> dims = parseDimensions(rule.getDimensions());
            for (String d : dims) {
                if (byDim.containsKey(d)) {
                    byDim.get(d).add(score);
                }
            }
        }

        // 3. 计算每维度得分
        Map<String, BigDecimal> dimScores = new LinkedHashMap<>();
        for (String d : DIMENSIONS) {
            dimScores.put(d, avg(byDim.get(d)));
        }

        // 4. 对象总分 = 加权平均
        Map<String, BigDecimal> weights = loadWeights(objectId);
        BigDecimal overall = weightedAvg(dimScores, weights);

        QualityScore qs = new QualityScore();
        qs.setTargetType("object");
        qs.setTargetId(objectId);
        qs.setSnapshotAt(Instant.now());
        qs.setOverallScore(overall);
        qs.setGrade(gradeFromScore(overall));
        qs.setCompleteness(dimScores.get("completeness"));
        qs.setUniqueness(dimScores.get("uniqueness"));
        qs.setValidity(dimScores.get("validity"));
        qs.setConsistency(dimScores.get("consistency"));
        qs.setAccuracy(dimScores.get("accuracy"));
        qs.setTimeliness(dimScores.get("timeliness"));
        qs.setRulesCount(rules.size());
        qs.setViolationsCount(sumViolations);
        qs.setTotalRows(sumRows);
        return qs;
    }

    /**
     * 项目总分 = 所有对象总分的加权平均（按对象权重）。
     */
    public QualityScore computeProject(String projectId, List<QualityScore> objectScores) {
        Map<String, BigDecimal> objWeights = loadWeights("*");

        // 6 维度跨对象加权平均
        Map<String, BigDecimal> projDims = new LinkedHashMap<>();
        for (String d : DIMENSIONS) {
            BigDecimal sum = BigDecimal.ZERO, totalW = BigDecimal.ZERO;
            for (QualityScore os : objectScores) {
                BigDecimal v = readDim(os, d);
                if (v == null) continue;
                BigDecimal w = objWeights.getOrDefault(os.getTargetId(), BigDecimal.ONE);
                sum = sum.add(v.multiply(w));
                totalW = totalW.add(w);
            }
            projDims.put(d, totalW.signum() == 0 ? null
                : sum.divide(totalW, 2, RoundingMode.HALF_UP));
        }

        // 项目总分
        BigDecimal overall = weightedAvg(projDims, Map.of());

        long sumRules = objectScores.stream().mapToLong(s -> s.getRulesCount() == null ? 0 : s.getRulesCount()).sum();
        long sumViolations = objectScores.stream().mapToLong(s -> s.getViolationsCount() == null ? 0 : s.getViolationsCount()).sum();
        long sumRows = objectScores.stream().mapToLong(s -> s.getTotalRows() == null ? 0 : s.getTotalRows()).sum();

        QualityScore qs = new QualityScore();
        qs.setTargetType("project");
        qs.setTargetId(projectId);
        qs.setSnapshotAt(Instant.now());
        qs.setOverallScore(overall);
        qs.setGrade(gradeFromScore(overall));
        qs.setCompleteness(projDims.get("completeness"));
        qs.setUniqueness(projDims.get("uniqueness"));
        qs.setValidity(projDims.get("validity"));
        qs.setConsistency(projDims.get("consistency"));
        qs.setAccuracy(projDims.get("accuracy"));
        qs.setTimeliness(projDims.get("timeliness"));
        qs.setRulesCount((int) sumRules);
        qs.setViolationsCount(sumViolations);
        qs.setTotalRows(sumRows);
        return qs;
    }

    // ===== 工具方法 =====

    private boolean matchesObject(String target, String objectId) {
        // target 形如 "Lot" / "Lot.lotId" / "Lot.temperature"
        return target.equals(objectId) || target.startsWith(objectId + ".");
    }

    private Set<String> parseDimensions(String dims) {
        if (dims == null || dims.isBlank()) return Set.of("validity");
        return Set.copyOf(Arrays.stream(dims.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private BigDecimal avg(List<BigDecimal> scores) {
        if (scores == null || scores.isEmpty()) return null;
        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> loadWeights(String targetId) {
        Map<String, BigDecimal> result = new HashMap<>();
        // 先加载全局默认
        for (ScoreWeight w : weightRepo.findByTargetId("*")) {
            result.put(w.getDimension(), w.getWeight());
        }
        // 对象级覆写
        for (ScoreWeight w : weightRepo.findByTargetId(targetId)) {
            result.put(w.getDimension(), w.getWeight());
        }
        return result;
    }

    private BigDecimal weightedAvg(Map<String, BigDecimal> values, Map<String, BigDecimal> weights) {
        BigDecimal sum = BigDecimal.ZERO, totalW = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : values.entrySet()) {
            if (e.getValue() == null) continue;
            BigDecimal w = weights.getOrDefault(e.getKey(), BigDecimal.ONE);
            sum = sum.add(e.getValue().multiply(w));
            totalW = totalW.add(w);
        }
        return totalW.signum() == 0 ? BigDecimal.ZERO
            : sum.divide(totalW, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal readDim(QualityScore s, String dim) {
        return switch (dim) {
            case "completeness" -> s.getCompleteness();
            case "uniqueness" -> s.getUniqueness();
            case "validity" -> s.getValidity();
            case "consistency" -> s.getConsistency();
            case "accuracy" -> s.getAccuracy();
            case "timeliness" -> s.getTimeliness();
            default -> null;
        };
    }

    public static String gradeFromScore(BigDecimal score) {
        if (score == null) return "D";
        double s = score.doubleValue();
        if (s >= 95) return "A+";
        if (s >= 85) return "A";
        if (s >= 70) return "B";
        if (s >= 50) return "C";
        return "D";
    }
}
