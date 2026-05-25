package com.ontos.rule.biz.repo;

import com.ontos.rule.biz.domain.ScoreWeight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoreWeightRepository extends JpaRepository<ScoreWeight, ScoreWeight.WeightId> {

    List<ScoreWeight> findByTargetId(String targetId);
}
