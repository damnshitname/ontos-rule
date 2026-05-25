package com.ontos.rule.biz.repo;

import com.ontos.rule.biz.domain.RuleRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleRunRepository extends JpaRepository<RuleRun, Long> {
    List<RuleRun> findByRuleIdOrderByStartedAtDesc(String ruleId, Pageable pageable);
    List<RuleRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
