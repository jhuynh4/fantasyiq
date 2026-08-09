package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.domain.player.PlayerReconciliationService;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamReconciliationService;
import com.fantasyiq.ingestion.stats.RawAthlete;
import com.fantasyiq.ingestion.stats.RawTeam;
import com.fantasyiq.ingestion.stats.StatsProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlayerIngestionService {

    private static final String SOURCE = "STATS_PROVIDER_PLAYERS";

    private final StatsProvider statsProvider;
    private final TeamReconciliationService teamReconciliationService;
    private final PlayerReconciliationService playerReconciliationService;
    private final IngestionRunService ingestionRunService;

    public PlayerIngestionService(StatsProvider statsProvider,
                                   TeamReconciliationService teamReconciliationService,
                                   PlayerReconciliationService playerReconciliationService,
                                   IngestionRunService ingestionRunService) {
        this.statsProvider = statsProvider;
        this.teamReconciliationService = teamReconciliationService;
        this.playerReconciliationService = playerReconciliationService;
        this.ingestionRunService = ingestionRunService;
    }

    public int ingestRosters() {
        return ingestionRunService.track(SOURCE, Integer::intValue, this::doIngestRosters);
    }

    /**
     * Not wrapped in a single @Transactional -- this makes ~33 outbound HTTP
     * calls (1 team list + 32 rosters), and a DB transaction shouldn't span
     * that. Each write below is its own short transaction instead.
     */
    private int doIngestRosters() {
        Map<String, Team> teamsByEspnId = resolveTeams();

        int playersIngested = 0;
        for (Map.Entry<String, Team> entry : teamsByEspnId.entrySet()) {
            List<RawAthlete> athletes = statsProvider.fetchRoster(entry.getKey());
            for (RawAthlete athlete : athletes) {
                playerReconciliationService.resolveOrCreateFromEspn(
                        athlete.externalId(), athlete.fullName(), athlete.position(),
                        entry.getValue(), athlete.jerseyNumber(), athlete.status(), athlete.birthDate());
                playersIngested++;
            }
        }
        return playersIngested;
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
