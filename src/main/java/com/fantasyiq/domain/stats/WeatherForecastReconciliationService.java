package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.game.Game;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class WeatherForecastReconciliationService {

    private final WeatherForecastRepository weatherForecastRepository;

    public WeatherForecastReconciliationService(WeatherForecastRepository weatherForecastRepository) {
        this.weatherForecastRepository = weatherForecastRepository;
    }

    @Transactional
    public WeatherForecast resolveOrCreate(Game game, Integer temperatureF, Integer windMph,
                                            Integer precipitationPct, String conditions) {
        Optional<WeatherForecast> existing = weatherForecastRepository.findByGame(game);

        if (existing.isPresent()) {
            WeatherForecast forecast = existing.get();
            forecast.updateFrom(temperatureF, windMph, precipitationPct, conditions);
            return forecast;
        }

        return weatherForecastRepository.save(
                new WeatherForecast(game, temperatureF, windMph, precipitationPct, conditions));
    }
}
