package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "defense_vs_position_stats")
public class DefenseVsPositionStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private Integer season;

    @Column(nullable = false)
    private Integer week;

    @Column(nullable = false, length = 4)
    private String position;

    @Column(name = "fantasy_points_allowed_ppr", nullable = false)
    private BigDecimal fantasyPointsAllowedPpr;

    @Column(name = "fantasy_points_allowed_standard", nullable = false)
    private BigDecimal fantasyPointsAllowedStandard;

    @Column(name = "rank_ppr", nullable = false)
    private Integer rankPpr;

    @Column(name = "rank_standard", nullable = false)
    private Integer rankStandard;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DefenseVsPositionStats() {
        // JPA
    }

    public DefenseVsPositionStats(Team team, Integer season, Integer week, String position,
                                   BigDecimal fantasyPointsAllowedPpr, BigDecimal fantasyPointsAllowedStandard,
                                   Integer rankPpr, Integer rankStandard) {
        this.team = team;
        this.season = season;
        this.week = week;
        this.position = position;
        this.fantasyPointsAllowedPpr = fantasyPointsAllowedPpr;
        this.fantasyPointsAllowedStandard = fantasyPointsAllowedStandard;
        this.rankPpr = rankPpr;
        this.rankStandard = rankStandard;
    }

    public void updateFrom(BigDecimal fantasyPointsAllowedPpr, BigDecimal fantasyPointsAllowedStandard,
                            Integer rankPpr, Integer rankStandard) {
        this.fantasyPointsAllowedPpr = fantasyPointsAllowedPpr;
        this.fantasyPointsAllowedStandard = fantasyPointsAllowedStandard;
        this.rankPpr = rankPpr;
        this.rankStandard = rankStandard;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public Integer getSeason() {
        return season;
    }

    public Integer getWeek() {
        return week;
    }

    public String getPosition() {
        return position;
    }

    public BigDecimal getFantasyPointsAllowedPpr() {
        return fantasyPointsAllowedPpr;
    }

    public BigDecimal getFantasyPointsAllowedStandard() {
        return fantasyPointsAllowedStandard;
    }

    public Integer getRankPpr() {
        return rankPpr;
    }

    public Integer getRankStandard() {
        return rankStandard;
    }
}
