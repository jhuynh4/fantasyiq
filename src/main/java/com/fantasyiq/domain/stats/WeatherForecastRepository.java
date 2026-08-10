package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.game.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, Long> {

    Optional<WeatherForecast> findByGame(Game game);
}
