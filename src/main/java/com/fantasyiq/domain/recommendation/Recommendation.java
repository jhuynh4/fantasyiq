package com.fantasyiq.domain.recommendation;

import com.fantasyiq.domain.player.Player;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recommendations")
public class Recommendation {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(nullable = false)
    private Integer season;

    @Column(nullable = false)
    private Integer week;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private BigDecimal score;

    @Column(nullable = false, length = 10)
    private String confidence;

    @Column(name = "scoring_version", nullable = false, length = 10)
    private String scoringVersion;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @OneToMany(mappedBy = "recommendation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<RecommendationFactor> factors = new ArrayList<>();

    protected Recommendation() {
        // JPA
    }

    public Recommendation(Player player, Integer season, Integer week, String type,
                           BigDecimal score, String confidence, String scoringVersion) {
        this.player = player;
        this.season = season;
        this.week = week;
        this.type = type;
        this.score = score;
        this.confidence = confidence;
        this.scoringVersion = scoringVersion;
    }

    public void updateFrom(BigDecimal score, String confidence, String scoringVersion) {
        this.score = score;
        this.confidence = confidence;
        this.scoringVersion = scoringVersion;
        this.generatedAt = Instant.now();
    }

    public void replaceFactors(List<RecommendationFactor> newFactors) {
        this.factors.clear();
        this.factors.addAll(newFactors);
    }

    @PrePersist
    void onCreate() {
        this.generatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Player getPlayer() {
        return player;
    }

    public Integer getSeason() {
        return season;
    }

    public Integer getWeek() {
        return week;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getScore() {
        return score;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getScoringVersion() {
        return scoringVersion;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public List<RecommendationFactor> getFactors() {
        return factors;
    }
}
