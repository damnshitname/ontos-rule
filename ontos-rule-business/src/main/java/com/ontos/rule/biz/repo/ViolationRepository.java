package com.ontos.rule.biz.repo;

import com.ontos.rule.biz.domain.Violation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ViolationRepository extends JpaRepository<Violation, Long> {
    List<Violation> findByRunIdOrderBySampledAtAsc(Long runId);
    long countByRunId(Long runId);
}
