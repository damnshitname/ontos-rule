package com.ontos.rule.biz.repo;

import com.ontos.rule.biz.domain.QualityScore;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QualityScoreRepository extends JpaRepository<QualityScore, Long> {

    /** 某 target 最新一条评分 */
    Optional<QualityScore> findTopByTargetTypeAndTargetIdOrderBySnapshotAtDesc(
        String targetType, String targetId);

    /** 某 target 历史（按时间倒序，限制 N 条） */
    List<QualityScore> findByTargetTypeAndTargetIdOrderBySnapshotAtDesc(
        String targetType, String targetId, Pageable pageable);

    /** 某 target 在某时间段后的所有评分（趋势用） */
    List<QualityScore> findByTargetTypeAndTargetIdAndSnapshotAtGreaterThanEqualOrderBySnapshotAtAsc(
        String targetType, String targetId, Instant since);

    /** 取每个对象的最新评分（用于项目大盘） */
    @Query("""
        SELECT q FROM QualityScore q
        WHERE q.targetType = :type
          AND q.snapshotAt = (
            SELECT MAX(q2.snapshotAt) FROM QualityScore q2
            WHERE q2.targetType = q.targetType AND q2.targetId = q.targetId
          )
        ORDER BY q.overallScore DESC
        """)
    List<QualityScore> findLatestPerTarget(@Param("type") String targetType);
}
