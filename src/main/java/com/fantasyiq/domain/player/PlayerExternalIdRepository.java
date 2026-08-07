package com.fantasyiq.domain.player;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerExternalIdRepository extends JpaRepository<PlayerExternalId, Long> {

    Optional<PlayerExternalId> findBySourceAndExternalId(String source, String externalId);
}
