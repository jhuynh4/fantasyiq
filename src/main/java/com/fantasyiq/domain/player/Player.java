package com.fantasyiq.domain.player;

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
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(nullable = false, length = 4)
    private String position;

    // EAGER: read outside any service-level transaction (controllers map
    // straight from repository results to DTOs), and open-in-view is off.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_team_id")
    private Team currentTeam;

    @Column(name = "jersey_number")
    private Integer jerseyNumber;

    @Column(nullable = false)
    private String status;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Player() {
        // JPA
    }

    public Player(String fullName, String position, Team currentTeam, Integer jerseyNumber,
                  String status, LocalDate birthDate) {
        this.fullName = fullName;
        this.normalizedName = normalize(fullName);
        this.position = position;
        this.currentTeam = currentTeam;
        this.jerseyNumber = jerseyNumber;
        this.status = status;
        this.birthDate = birthDate;
    }

    public void updateFrom(String fullName, String position, Team currentTeam, Integer jerseyNumber,
                            String status, LocalDate birthDate) {
        this.fullName = fullName;
        this.normalizedName = normalize(fullName);
        this.position = position;
        this.currentTeam = currentTeam;
        this.jerseyNumber = jerseyNumber;
        this.status = status;
        this.birthDate = birthDate;
    }

    static String normalize(String fullName) {
        return fullName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ");
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

    public String getFullName() {
        return fullName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getPosition() {
        return position;
    }

    public Team getCurrentTeam() {
        return currentTeam;
    }

    public Integer getJerseyNumber() {
        return jerseyNumber;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
