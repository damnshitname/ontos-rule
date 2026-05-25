package com.ontos.rule.biz.service;

import com.ontos.rule.biz.domain.QualityScore;
import com.ontos.rule.biz.domain.Rule;
import com.ontos.rule.biz.domain.RuleStatus;
import com.ontos.rule.biz.domain.ScoreWeight;
import com.ontos.rule.biz.repo.QualityScoreRepository;
import com.ontos.rule.biz.repo.RuleRepository;
import com.ontos.rule.biz.repo.ScoreWeightRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 质量评分服务 - 入口层。
 *
 * <p>负责：
 * <ul>
 *   <li>定时/手动触发"全量重算"</li>
 *   <li>查询最新评分 / 历史趋势 / 项目大盘 / 热力图</li>
 *   <li>权重 CRUD</li>
 * </ul>
 */
@Service
public class QualityScoreService {

    private static final Logger log = LoggerFactory.getLogger(QualityScoreService.class);

    public static final String DEFAULT_PROJECT_ID = "default-project";

    private final ScoreCalculator calculator;
    private final QualityScoreRepository scoreRepo;
    private final ScoreWeightRepository weightRepo;
    private final RuleRepository ruleRepo;

    public QualityScoreService(ScoreCalculator calculator,
                                QualityScoreRepository scoreRepo,
                                ScoreWeightRepository weightRepo,
                                RuleRepository ruleRepo) {
        this.calculator = calculator;
        this.scoreRepo = scoreRepo;
        this.weightRepo = weightRepo;
        this.ruleRepo = ruleRepo;
    }

    /**
     * 全量重算：所有对象 + 项目总分。
     */
    @Transactional
    public List<QualityScore> recomputeAll(String triggerType) {
        Set<String> objects = discoverObjects();
        log.info("Recompute all · trigger={} · objects={}", triggerType, objects);

        List<QualityScore> objectScores = new ArrayList<>();
        for (String objId : objects) {
            QualityScore os = calculator.computeObject(objId);
            os.setTriggerType(triggerType);
            scoreRepo.save(os);
            objectScores.add(os);
        }

        if (!objectScores.isEmpty()) {
            QualityScore proj = calculator.computeProject(DEFAULT_PROJECT_ID, objectScores);
            proj.setTriggerType(triggerType);
            scoreRepo.save(proj);
            log.info("Project score: {} · grade={} · {} objects", proj.getOverallScore(), proj.getGrade(), objectScores.size());
        }

        return objectScores;
    }

    /**
     * 重算单个对象（不级联项目分）。
     */
    @Transactional
    public QualityScore recomputeObject(String objectId, String triggerType) {
        QualityScore qs = calculator.computeObject(objectId);
        qs.setTriggerType(triggerType);
        return scoreRepo.save(qs);
    }

    public QualityScore getLatestObject(String objectId) {
        return scoreRepo.findTopByTargetTypeAndTargetIdOrderBySnapshotAtDesc("object", objectId)
            .orElse(null);
    }

    public QualityScore getLatestProject() {
        return scoreRepo.findTopByTargetTypeAndTargetIdOrderBySnapshotAtDesc("project", DEFAULT_PROJECT_ID)
            .orElse(null);
    }

    public List<QualityScore> getObjectHistory(String objectId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return scoreRepo
            .findByTargetTypeAndTargetIdAndSnapshotAtGreaterThanEqualOrderBySnapshotAtAsc(
                "object", objectId, since);
    }

    /** 项目维度的历史快照（趋势 / delta 用）。 */
    public List<QualityScore> getProjectHistory(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return scoreRepo
            .findByTargetTypeAndTargetIdAndSnapshotAtGreaterThanEqualOrderBySnapshotAtAsc(
                "project", DEFAULT_PROJECT_ID, since);
    }

    /**
     * 项目大盘 = 项目总分 + 各对象最新得分（用于评分页对象排行）。
     */
    public List<QualityScore> getLatestObjectScores() {
        return scoreRepo.findLatestPerTarget("object");
    }

    /**
     * 自动发现项目里的所有 object（从规则 target 字段解析前缀）。
     */
    public Set<String> discoverObjects() {
        Set<String> result = new TreeSet<>();
        for (Rule r : ruleRepo.findAll()) {
            if (r.getStatus() != RuleStatus.ACTIVE) continue;
            String t = r.getTarget();
            if (t == null || t.isBlank() || t.startsWith("*")) continue;
            int dot = t.indexOf('.');
            result.add(dot > 0 ? t.substring(0, dot) : t);
        }
        return result;
    }

    // ===== 权重管理 =====

    @Transactional
    public List<ScoreWeight> listWeights(String targetId) {
        return weightRepo.findByTargetId(targetId == null ? "*" : targetId);
    }

    @Transactional
    public List<ScoreWeight> setWeights(String targetId, Map<String, BigDecimal> weights) {
        String tid = targetId == null ? "*" : targetId;
        // 删除旧的
        for (ScoreWeight old : weightRepo.findByTargetId(tid)) {
            weightRepo.delete(old);
        }
        // 插入新的
        List<ScoreWeight> out = new ArrayList<>();
        weights.forEach((dim, w) -> out.add(weightRepo.save(new ScoreWeight(tid, dim, w))));
        return out;
    }
}
