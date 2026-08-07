package com.fantasyiq.domain.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 4)
    private String abbreviation;

    @Column(nullable = false)
    private String name;

    private String conference;

    private String division;

    protected Team() {
        // JPA
    }

    public Integer getId() {
        return id;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public String getName() {
        return name;
    }

    public String getConference() {
        return conference;
    }

    public String getDivision() {
        return division;
    }
}
