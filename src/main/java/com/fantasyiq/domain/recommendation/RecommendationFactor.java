package com.fantasyiq.domain.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "recommendation_factors")
public class RecommendationFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private Recommendation recommendation;

    @Column(name = "factor_type", nullable = false, length = 30)
    private String factorType;

    @Column(name = "factor_value")
    private BigDecimal factorValue;

    @Column(name = "factor_weight")
    private BigDecimal factorWeight;

    @Column(nullable = false)
    private BigDecimal contribution;

    @Column(nullable = false)
    private String narrative;

    protected RecommendationFactor() {
        // JPA
    }

    public RecommendationFactor(Recommendation recommendation, String factorType, BigDecimal factorValue,
                                 BigDecimal factorWeight, BigDecimal contribution, String narrative) {
        this.recommendation = recommendation;
        this.factorType = factorType;
        this.factorValue = factorValue;
        this.factorWeight = factorWeight;
        this.contribution = contribution;
        this.narrative = narrative;
    }

    public Long getId() {
        return id;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public String getFactorType() {
        return factorType;
    }

    public BigDecimal getFactorValue() {
        return factorValue;
    }

    public BigDecimal getFactorWeight() {
        return factorWeight;
    }

    public BigDecimal getContribution() {
        return contribution;
    }

    public String getNarrative() {
        return narrative;
    }
}
