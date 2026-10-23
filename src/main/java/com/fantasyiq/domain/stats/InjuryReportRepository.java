package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.player.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InjuryReportRepository extends JpaRepository<InjuryReport, Long> {

    Optional<InjuryReport> findByPlayerAndReportDateAndSource(Player player, LocalDate reportDate, String source);

    Optional<InjuryReport> findTopByPlayerOrderByReportDateDesc(Player player);

    // Each player's most recent report, for every player at once. Two reports on the same
    // latest date (different sources) both come back; the caller keeps one.
    @Query("select r from InjuryReport r join fetch r.player where r.reportDate = "
            + "(select max(r2.reportDate) from InjuryReport r2 where r2.player = r.player)")
    List<InjuryReport> findLatestPerPlayer();

    /** A player's most recent status, as plain values rather than tracked entities. */
    interface LatestStatus {
        UUID getPlayerId();

        String getStatus();
    }

    // Every player's latest status in one query, for callers scoring a whole week. Two reports on the same
    // latest date both come back; the caller keeps one.
    @Query("select r.player.id as playerId, r.status as status from InjuryReport r where r.reportDate = "
            + "(select max(r2.reportDate) from InjuryReport r2 where r2.player = r.player)")
    List<LatestStatus> findLatestStatuses();
}
