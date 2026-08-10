package com.fantasyiq.domain.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StadiumLocationRepository extends JpaRepository<StadiumLocation, Integer> {

    Optional<StadiumLocation> findByTeam(Team team);
}
