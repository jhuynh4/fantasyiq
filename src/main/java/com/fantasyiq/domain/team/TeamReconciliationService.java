package com.fantasyiq.domain.team;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves ESPN team identity against our seeded teams table, caching the
 * mapping in team_external_ids. Shared by every ingestion job that needs to
 * know "which of our teams is ESPN team X" (players, games, ...) so the
 * bootstrap logic exists in exactly one place.
 */
@Service
public class TeamReconciliationService {

    private static final String ESPN_SOURCE = "ESPN";

    private final TeamRepository teamRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;

    public TeamReconciliationService(TeamRepository teamRepository,
                                      TeamExternalIdRepository teamExternalIdRepository) {
        this.teamRepository = teamRepository;
        this.teamExternalIdRepository = teamExternalIdRepository;
    }

    @Transactional
    public Team resolveByEspnAbbreviation(String espnAbbreviation, String espnTeamExternalId) {
        Team team = teamRepository.findByAbbreviation(espnAbbreviation)
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown team abbreviation from ESPN: " + espnAbbreviation));

        teamExternalIdRepository.findBySourceAndTeam(ESPN_SOURCE, team)
                .orElseGet(() -> teamExternalIdRepository.save(
                        new TeamExternalId(team, ESPN_SOURCE, espnTeamExternalId)));

        return team;
    }

    @Transactional(readOnly = true)
    public Optional<Team> findByEspnExternalId(String espnTeamExternalId) {
        return teamExternalIdRepository.findBySourceAndExternalId(ESPN_SOURCE, espnTeamExternalId)
                .map(TeamExternalId::getTeam);
    }
}
