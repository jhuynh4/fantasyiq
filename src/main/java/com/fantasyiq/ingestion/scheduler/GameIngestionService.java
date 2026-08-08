package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.domain.game.GameReconciliationService;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamReconciliationService;
import com.fantasyiq.ingestion.stats.RawGame;
import com.fantasyiq.ingestion.stats.RawTeam;
import com.fantasyiq.ingestion.stats.StatsProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class GameIngestionService {

    private final StatsProvider statsProvider;
    private final TeamReconciliationService teamReconciliationService;
    private final GameReconciliationService gameReconciliationService;

    public GameIngestionService(StatsProvider statsProvider,
                                 TeamReconciliationService teamReconciliationService,
                                 GameReconciliationService gameReconciliationService) {
        this.statsProvider = statsProvider;
        this.teamReconciliationService = teamReconciliationService;
        this.gameReconciliationService = gameReconciliationService;
    }

    /**
     * Each game appears in both the home and away team's schedule fetch, so
     * it's processed twice across the full loop -- resolveOrCreateFromEspn
     * is idempotent (second call finds-and-updates the row the first call
     * created), so this naturally dedupes rather than needing extra logic
     * to skip games already seen this run.
     */
    public int ingestSchedules() {
        return ingestSchedules(currentNflSeason());
    }

    public int ingestSchedules(int season) {
        Map<String, Team> teamsByEspnId = resolveTeams();
        Set<String> processedGameIds = new HashSet<>();

        int gamesIngested = 0;
        for (Map.Entry<String, Team> entry : teamsByEspnId.entrySet()) {
            for (RawGame rawGame : statsProvider.fetchSchedule(entry.getKey(), season)) {
                Team homeTeam = teamReconciliationService.findByEspnExternalId(rawGame.homeTeamExternalId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Unknown ESPN team id for home team: " + rawGame.homeTeamExternalId()));
                Team awayTeam = teamReconciliationService.findByEspnExternalId(rawGame.awayTeamExternalId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Unknown ESPN team id for away team: " + rawGame.awayTeamExternalId()));

                gameReconciliationService.resolveOrCreateFromEspn(
                        rawGame.externalId(), rawGame.season(), rawGame.week(),
                        homeTeam, awayTeam, rawGame.kickoff(), rawGame.venue(), rawGame.status());

                if (processedGameIds.add(rawGame.externalId())) {
                    gamesIngested++;
                }
            }
        }
        return gamesIngested;
    }

    /**
     * NFL seasons are named for the year they start in (the "2026 season"
     * runs Sept 2026 - Feb 2027). Jan/Feb still belong to the prior season
     * (playoffs/aftermath), so only roll over to the new year from March on.
     */
    private static int currentNflSeason() {
        LocalDate today = LocalDate.now();
        return today.getMonthValue() <= 2 ? today.getYear() - 1 : today.getYear();
    }

    private Map<String, Team> resolveTeams() {
        Map<String, Team> result = new LinkedHashMap<>();
        for (RawTeam rawTeam : statsProvider.fetchTeams()) {
            Team team = teamReconciliationService.resolveByEspnAbbreviation(
                    rawTeam.abbreviation(), rawTeam.externalId());
            result.put(rawTeam.externalId(), team);
        }
        return result;
    }
}
