package com.ontos.rule.biz.web;

import com.ontos.rule.biz.domain.QualityScore;
import com.ontos.rule.biz.service.QualityScoreService;
import com.ontos.rule.biz.service.ScoreCalculator;
import com.ontos.rule.biz.web.dto.HeatmapDto;
import com.ontos.rule.biz.web.dto.ScoreDto;
import com.ontos.rule.biz.web.dto.WeightDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质量评分 REST API。
 */
@RestController
@RequestMapping("/api/quality")
public class QualityScoreController {

    private final QualityScoreService scoreService;

    public QualityScoreController(QualityScoreService scoreService) {
        this.scoreService = scoreService;
    }

    /** 某对象最新评分 */
    @GetMapping("/objects/{objId}/score")
    public ScoreDto objectScore(@PathVariable String objId) {
        QualityScore q = scoreService.getLatestObject(objId);
        if (q == null) {
            // 还没算过 → 触发一次计算
            q = scoreService.recomputeObject(objId, "on-demand");
        }
        return ScoreDto.from(q);
    }

    /** 历史趋势（默认 30 天） */
    @GetMapping("/objects/{objId}/score/history")
    public List<ScoreDto> objectHistory(@PathVariable String objId,
                                        @RequestParam(defaultValue = "30") int days) {
        return scoreService.getObjectHistory(objId, days).stream().map(ScoreDto::from).toList();
    }

    /** 项目历史趋势（默认 30 天） */
    @GetMapping("/project/score/history")
    public List<ScoreDto> projectHistory(@RequestParam(defaultValue = "30") int days) {
        return scoreService.getProjectHistory(days).stream().map(ScoreDto::from).toList();
    }

    /** 项目总分 + 各对象最新得分（排行） */
    @GetMapping("/project/score")
    public Map<String, Object> projectScore() {
        QualityScore proj = scoreService.getLatestProject();
        List<QualityScore> objs = scoreService.getLatestObjectScores();
        if (proj == null && !objs.isEmpty()) {
            scoreService.recomputeAll("on-demand");
            proj = scoreService.getLatestProject();
            objs = scoreService.getLatestObjectScores();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", ScoreDto.from(proj));
        result.put("objects", objs.stream().map(ScoreDto::from).toList());
        return result;
    }

    /** 维度热力图：dimensions × objects */
    @GetMapping("/project/heatmap")
    public HeatmapDto heatmap() {
        List<QualityScore> objs = scoreService.getLatestObjectScores();
        List<String> dims = ScoreCalculator.DIMENSIONS;
        List<String> objIds = objs.stream().map(QualityScore::getTargetId).toList();
        List<HeatmapDto.Cell> cells = new ArrayList<>();
        for (QualityScore o : objs) {
            cells.add(new HeatmapDto.Cell(o.getTargetId(), "completeness", o.getCompleteness()));
            cells.add(new HeatmapDto.Cell(o.getTargetId(), "uniqueness", o.getUniqueness()));
            cells.add(new HeatmapDto.Cell(o.getTargetId(), "validity", o.getValidity()));
            cells.add(new HeatmapDto.Cell(o.getTargetId(), "consistency", o.getConsistency()));
            cells.add(new HeatmapDto.Cell(o.getTargetId(), "accuracy", o.getAccuracy()));
            cells.add(new HeatmapDto.Cell(o.getTargetId(), "timeliness", o.getTimeliness()));
        }
        return new HeatmapDto(dims, objIds, cells);
    }

    /**
     * 手动触发全量重算。
     */
    @PostMapping("/recompute")
    public Map<String, Object> recompute(@RequestParam(required = false) String objectId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (objectId != null && !objectId.isBlank()) {
            QualityScore q = scoreService.recomputeObject(objectId, "manual");
            result.put("scope", "object");
            result.put("objectId", objectId);
            result.put("score", ScoreDto.from(q));
        } else {
            List<QualityScore> all = scoreService.recomputeAll("manual");
            result.put("scope", "all");
            result.put("objects", all.size());
            result.put("project", ScoreDto.from(scoreService.getLatestProject()));
        }
        return result;
    }

    /** 列出权重配置 */
    @GetMapping("/weights")
    public WeightDto getWeights(@RequestParam(defaultValue = "*") String targetId) {
        return WeightDto.from(targetId, scoreService.listWeights(targetId));
    }

    /** 设置权重（全量覆盖该 targetId 的权重） */
    @PutMapping("/weights")
    public WeightDto setWeights(@RequestBody WeightDto req) {
        scoreService.setWeights(req.targetId(), req.weights());
        return WeightDto.from(req.targetId(), scoreService.listWeights(req.targetId()));
    }
}
