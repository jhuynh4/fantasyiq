package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.game.Game;
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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "betting_lines")
public class BettingLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "implied_team_total")
    private BigDecimal impliedTeamTotal;

    private BigDecimal spread;

    @Column(name = "over_under")
    private BigDecimal overUnder;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected BettingLine() {
        // JPA
    }

    public BettingLine(Game game, Team team, BigDecimal impliedTeamTotal, BigDecimal spread,
                        BigDecimal overUnder, String source) {
        this.game = game;
        this.team = team;
        this.impliedTeamTotal = impliedTeamTotal;
        this.spread = spread;
        this.overUnder = overUnder;
        this.source = source;
    }

    public void updateFrom(BigDecimal impliedTeamTotal, BigDecimal spread, BigDecimal overUnder, String source) {
        this.impliedTeamTotal = impliedTeamTotal;
        this.spread = spread;
        this.overUnder = overUnder;
        this.source = source;
        this.fetchedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        this.fetchedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public Team getTeam() {
        return team;
    }

    public BigDecimal getImpliedTeamTotal() {
        return impliedTeamTotal;
    }

    public BigDecimal getSpread() {
        return spread;
    }

    public BigDecimal getOverUnder() {
        return overUnder;
    }

    public String getSource() {
        return source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
