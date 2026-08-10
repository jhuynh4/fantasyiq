package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.player.Player;
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
@Table(name = "player_game_stats")
public class PlayerGameStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    // The player's team FOR THIS SPECIFIC GAME, not their current team --
    // matters for anyone traded mid-season. Sourced from the gamelog
    // response's per-event team field, not Player.currentTeam. Nullable:
    // defensively tolerate ESPN occasionally omitting it rather than
    // failing the whole stat line.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    // Not populated from ESPN's free tier -- confirmed absent from real
    // gamelog responses for both QB and skill positions. Left nullable
    // rather than defaulted, same as injury_reports.body_part.
    private Integer snaps;
    @Column(name = "snap_pct")
    private BigDecimal snapPct;
    @Column(name = "red_zone_touches")
    private Integer redZoneTouches;

    private Integer targets;
    private Integer receptions;
    @Column(name = "rec_yards")
    private Integer recYards;
    @Column(name = "rush_attempts")
    private Integer rushAttempts;
    @Column(name = "rush_yards")
    private Integer rushYards;

    @Column(name = "passing_attempts")
    private Integer passingAttempts;
    @Column(name = "passing_completions")
    private Integer passingCompletions;
    @Column(name = "passing_yards")
    private Integer passingYards;
    @Column(name = "passing_touchdowns")
    private Integer passingTouchdowns;
    private Integer interceptions;

    private Integer touchdowns;
    @Column(name = "fantasy_points_ppr")
    private BigDecimal fantasyPointsPpr;
    @Column(name = "fantasy_points_standard")
    private BigDecimal fantasyPointsStandard;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlayerGameStats() {
        // JPA
    }

    public PlayerGameStats(Player player, Game game, Team team, Integer targets, Integer receptions,
                            Integer recYards, Integer rushAttempts, Integer rushYards, Integer passingAttempts,
                            Integer passingCompletions, Integer passingYards, Integer passingTouchdowns,
                            Integer interceptions, Integer touchdowns, BigDecimal fantasyPointsPpr,
                            BigDecimal fantasyPointsStandard) {
        this.player = player;
        this.game = game;
        this.team = team;
        applyStats(targets, receptions, recYards, rushAttempts, rushYards, passingAttempts, passingCompletions,
                passingYards, passingTouchdowns, interceptions, touchdowns, fantasyPointsPpr, fantasyPointsStandard);
    }

    public void updateFrom(Team team, Integer targets, Integer receptions, Integer recYards, Integer rushAttempts,
                            Integer rushYards, Integer passingAttempts, Integer passingCompletions,
                            Integer passingYards, Integer passingTouchdowns, Integer interceptions,
                            Integer touchdowns, BigDecimal fantasyPointsPpr, BigDecimal fantasyPointsStandard) {
        this.team = team;
        applyStats(targets, receptions, recYards, rushAttempts, rushYards, passingAttempts, passingCompletions,
                passingYards, passingTouchdowns, interceptions, touchdowns, fantasyPointsPpr, fantasyPointsStandard);
    }

    private void applyStats(Integer targets, Integer receptions, Integer recYards, Integer rushAttempts,
                             Integer rushYards, Integer passingAttempts, Integer passingCompletions,
                             Integer passingYards, Integer passingTouchdowns, Integer interceptions,
                             Integer touchdowns, BigDecimal fantasyPointsPpr, BigDecimal fantasyPointsStandard) {
        this.targets = targets;
        this.receptions = receptions;
        this.recYards = recYards;
        this.rushAttempts = rushAttempts;
        this.rushYards = rushYards;
        this.passingAttempts = passingAttempts;
        this.passingCompletions = passingCompletions;
        this.passingYards = passingYards;
        this.passingTouchdowns = passingTouchdowns;
        this.interceptions = interceptions;
        this.touchdowns = touchdowns;
        this.fantasyPointsPpr = fantasyPointsPpr;
        this.fantasyPointsStandard = fantasyPointsStandard;
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

    public Player getPlayer() {
        return player;
    }

    public Game getGame() {
        return game;
    }

    public Team getTeam() {
        return team;
    }

    public Integer getSnaps() {
        return snaps;
    }

    public BigDecimal getSnapPct() {
        return snapPct;
    }

    public Integer getRedZoneTouches() {
        return redZoneTouches;
    }

    public Integer getTargets() {
        return targets;
    }

    public Integer getReceptions() {
        return receptions;
    }

    public Integer getRecYards() {
        return recYards;
    }

    public Integer getRushAttempts() {
        return rushAttempts;
    }

    public Integer getRushYards() {
        return rushYards;
    }

    public Integer getPassingAttempts() {
        return passingAttempts;
    }

    public Integer getPassingCompletions() {
        return passingCompletions;
    }

    public Integer getPassingYards() {
        return passingYards;
    }

    public Integer getPassingTouchdowns() {
        return passingTouchdowns;
    }

    public Integer getInterceptions() {
        return interceptions;
    }

    public Integer getTouchdowns() {
        return touchdowns;
    }

    public BigDecimal getFantasyPointsPpr() {
        return fantasyPointsPpr;
    }

    public BigDecimal getFantasyPointsStandard() {
        return fantasyPointsStandard;
    }
}
