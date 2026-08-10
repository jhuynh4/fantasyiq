package com.fantasyiq.domain.game;

import com.fantasyiq.domain.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "external_ref", nullable = false, unique = true)
    private String externalRef;

    @Column(nullable = false)
    private Integer season;

    @Column(nullable = false)
    private Integer week;

    // EAGER for the same reason as Player.currentTeam: read outside any
    // service-level transaction, and open-in-view is off.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @Column(nullable = false)
    private Instant kickoff;

    private String venue;

    @Column(name = "is_dome")
    private Boolean isDome;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Game() {
        // JPA
    }

    public Game(String externalRef, Integer season, Integer week, Team homeTeam, Team awayTeam,
                Instant kickoff, String venue, String status) {
        this.externalRef = externalRef;
        this.season = season;
        this.week = week;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.kickoff = kickoff;
        this.venue = venue;
        this.status = status;
    }

    public void updateFrom(Integer season, Integer week, Team homeTeam, Team awayTeam,
                            Instant kickoff, String venue, String status) {
        this.season = season;
        this.week = week;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.kickoff = kickoff;
        this.venue = venue;
        this.status = status;
    }

    public void markDomeStatus(boolean isDome) {
        this.isDome = isDome;
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

    public UUID getId() {
        return id;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public Integer getSeason() {
        return season;
    }

    public Integer getWeek() {
        return week;
    }

    public Team getHomeTeam() {
        return homeTeam;
    }

    public Team getAwayTeam() {
        return awayTeam;
    }

    public Instant getKickoff() {
        return kickoff;
    }

    public String getVenue() {
        return venue;
    }

    public Boolean getIsDome() {
        return isDome;
    }

    public String getStatus() {
        return status;
    }
}
