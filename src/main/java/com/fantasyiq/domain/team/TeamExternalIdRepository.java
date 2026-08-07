package com.fantasyiq.domain.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamExternalIdRepository extends JpaRepository<TeamExternalId, Integer> {

    Optional<TeamExternalId> findBySourceAndTeam(String source, Team team);

    Optional<TeamExternalId> findBySourceAndExternalId(String source, String externalId);
}
