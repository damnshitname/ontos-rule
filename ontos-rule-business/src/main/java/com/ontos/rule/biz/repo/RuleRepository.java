package com.ontos.rule.biz.repo;

import com.ontos.rule.biz.domain.Rule;
import com.ontos.rule.biz.domain.RuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleRepository extends JpaRepository<Rule, String> {
    List<Rule> findByStatus(RuleStatus status);
    List<Rule> findByOwnerOrderByUpdatedAtDesc(String owner);
}
