package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.player.Player;
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

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "injury_reports")
public class InjuryReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "body_part", length = 50)
    private String bodyPart;

    @Column(name = "practice_participation", length = 20)
    private String practiceParticipation;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected InjuryReport() {
        // JPA
    }

    public InjuryReport(Player player, LocalDate reportDate, String status, String source) {
        this.player = player;
        this.reportDate = reportDate;
        this.status = status;
        this.source = source;
    }

    @PrePersist
    void onCreate() {
        this.fetchedAt = Instant.now();
    }

    public void updateStatus(String status) {
        this.status = status;
        this.fetchedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Player getPlayer() {
        return player;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public String getStatus() {
        return status;
    }

    public String getBodyPart() {
        return bodyPart;
    }

    public String getPracticeParticipation() {
        return practiceParticipation;
    }

    public String getSource() {
        return source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
