package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerExternalId;
import com.fantasyiq.domain.player.PlayerExternalIdRepository;
import com.fantasyiq.domain.stats.InjuryReconciliationService;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamReconciliationService;
import com.fantasyiq.ingestion.injuries.InjuryProvider;
import com.fantasyiq.ingestion.injuries.RawInjuryReport;
import com.fantasyiq.ingestion.stats.RawTeam;
import com.fantasyiq.ingestion.stats.StatsProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class InjuryIngestionService {

    private static final String ESPN_SOURCE = "ESPN";

    private final StatsProvider statsProvider;
    private final InjuryProvider injuryProvider;
    private final TeamReconciliationService teamReconciliationService;
    private final PlayerExternalIdRepository playerExternalIdRepository;
    private final InjuryReconciliationService injuryReconciliationService;

    public InjuryIngestionService(StatsProvider statsProvider,
                                   InjuryProvider injuryProvider,
                                   TeamReconciliationService teamReconciliationService,
                                   PlayerExternalIdRepository playerExternalIdRepository,
                                   InjuryReconciliationService injuryReconciliationService) {
        this.statsProvider = statsProvider;
        this.injuryProvider = injuryProvider;
        this.teamReconciliationService = teamReconciliationService;
        this.playerExternalIdRepository = playerExternalIdRepository;
        this.injuryReconciliationService = injuryReconciliationService;
    }

    /**
     * Depends on player ingestion having already run at least once -- an
     * injury report for a player we haven't seen via roster ingestion yet
     * is skipped rather than failing the whole batch, since that's a
     * legitimate ordering gap between two independently-triggerable jobs,
     * not a data-integrity bug.
     */
    public int ingestInjuries() {
        Map<String, Team> teamsByEspnId = resolveTeams();

        int reportsIngested = 0;
        for (String espnTeamId : teamsByEspnId.keySet()) {
            for (RawInjuryReport report : injuryProvider.fetchCurrentInjuries(espnTeamId)) {
                Optional<Player> player = playerExternalIdRepository
                        .findBySourceAndExternalId(ESPN_SOURCE, report.espnAthleteId())
                        .map(PlayerExternalId::getPlayer);

                if (player.isEmpty()) {
                    continue;
                }

                injuryReconciliationService.resolveOrCreateFromEspn(
                        player.get(), report.status(), report.reportDate());
                reportsIngested++;
            }
        }
        return reportsIngested;
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
