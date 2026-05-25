package com.ontos.rule.biz.web;

import com.ontos.rule.biz.domain.RuleRun;
import com.ontos.rule.biz.repo.RuleRunRepository;
import com.ontos.rule.biz.repo.ViolationRepository;
import com.ontos.rule.biz.web.dto.RuleRunDto;
import com.ontos.rule.biz.web.dto.ViolationDto;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 执行历史 + 违规记录查询 API。
 */
@RestController
@RequestMapping("/api")
public class RunController {

    private final RuleRunRepository runRepo;
    private final ViolationRepository vioRepo;

    public RunController(RuleRunRepository runRepo, ViolationRepository vioRepo) {
        this.runRepo = runRepo;
        this.vioRepo = vioRepo;
    }

    @GetMapping("/runs")
    public List<RuleRunDto> recent(
        @RequestParam(value = "ruleId", required = false) String ruleId,
        @RequestParam(value = "limit", defaultValue = "20") int limit) {
        List<RuleRun> runs = (ruleId != null)
            ? runRepo.findByRuleIdOrderByStartedAtDesc(ruleId, PageRequest.of(0, limit))
            : runRepo.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit));
        return runs.stream().map(RuleRunDto::from).toList();
    }

    @GetMapping("/runs/{id}")
    public RuleRunDto get(@PathVariable Long id) {
        return runRepo.findById(id)
            .map(RuleRunDto::from)
            .orElseThrow(() -> new EntityNotFoundException("RuleRun 不存在: " + id));
    }

    @GetMapping("/runs/{id}/violations")
    public List<ViolationDto> violations(@PathVariable Long id) {
        return vioRepo.findByRunIdOrderBySampledAtAsc(id).stream()
            .map(ViolationDto::from)
            .toList();
    }
}
