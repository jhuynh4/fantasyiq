package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.player.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface InjuryReportRepository extends JpaRepository<InjuryReport, Long> {

    Optional<InjuryReport> findByPlayerAndReportDateAndSource(Player player, LocalDate reportDate, String source);

    Optional<InjuryReport> findTopByPlayerOrderByReportDateDesc(Player player);
}
