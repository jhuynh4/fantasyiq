package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.player.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InjuryReportRepository extends JpaRepository<InjuryReport, Long> {

    Optional<InjuryReport> findByPlayerAndReportDateAndSource(Player player, LocalDate reportDate, String source);

    Optional<InjuryReport> findTopByPlayerOrderByReportDateDesc(Player player);

    // Each player's most recent report, for every player at once. Two reports on the same
    // latest date (different sources) both come back; the caller keeps one.
    @Query("select r from InjuryReport r join fetch r.player where r.reportDate = "
            + "(select max(r2.reportDate) from InjuryReport r2 where r2.player = r.player)")
    List<InjuryReport> findLatestPerPlayer();
}
