package com.ontos.rule.biz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * 评分权重配置。
 *
 * <p>每行 = (对象, 维度, 权重)。缺失则按默认 1.0。
 * targetId = "*" 表示全局权重；其他表示某对象的覆写。
 */
@Entity
@Table(name = "score_weight")
@IdClass(ScoreWeight.WeightId.class)
public class ScoreWeight {

    @Id
    @Column(name = "target_id", length = 64)
    private String targetId;     // "*" = 全局；其他 = 对象 ID

    @Id
    @Column(length = 16)
    private String dimension;    // completeness / ... / timeliness

    @Column(precision = 3, scale = 2)
    private BigDecimal weight;   // 0~9.99

    public ScoreWeight() {}

    public ScoreWeight(String targetId, String dimension, BigDecimal weight) {
        this.targetId = targetId;
        this.dimension = dimension;
        this.weight = weight;
    }

    public String getTargetId() { return targetId; }
    public void setTargetId(String s) { this.targetId = s; }
    public String getDimension() { return dimension; }
    public void setDimension(String s) { this.dimension = s; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal w) { this.weight = w; }

    /** 复合主键 */
    public static class WeightId implements Serializable {
        private String targetId;
        private String dimension;

        public WeightId() {}
        public WeightId(String t, String d) { this.targetId = t; this.dimension = d; }

        public String getTargetId() { return targetId; }
        public void setTargetId(String s) { this.targetId = s; }
        public String getDimension() { return dimension; }
        public void setDimension(String s) { this.dimension = s; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WeightId w)) return false;
            return Objects.equals(targetId, w.targetId) && Objects.equals(dimension, w.dimension);
        }
        @Override
        public int hashCode() { return Objects.hash(targetId, dimension); }
    }
}
