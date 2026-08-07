package com.fantasyiq.domain.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "team_external_ids")
public class TeamExternalId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    protected TeamExternalId() {
        // JPA
    }

    public TeamExternalId(Team team, String source, String externalId) {
        this.team = team;
        this.source = source;
        this.externalId = externalId;
    }

    public Integer getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public String getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }
}
