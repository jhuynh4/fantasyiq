package com.fantasyiq.domain.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Static reference data (seeded once in V10, never ingested or updated at
 * runtime) -- no reconciliation service, no update method. team_id is both
 * the primary key and the FK, a shared-PK association rather than its own
 * generated identity.
 */
@Entity
@Table(name = "stadium_locations")
public class StadiumLocation {

    @Id
    @Column(name = "team_id")
    private Integer teamId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false)
    private BigDecimal latitude;

    @Column(nullable = false)
    private BigDecimal longitude;

    @Column(name = "is_dome", nullable = false)
    private boolean isDome;

    protected StadiumLocation() {
        // JPA
    }

    public Integer getTeamId() {
        return teamId;
    }

    public Team getTeam() {
        return team;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public boolean isDome() {
        return isDome;
    }
}
