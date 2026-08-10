package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.game.Game;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "weather_forecasts")
public class WeatherForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, unique = true)
    private Game game;

    @Column(name = "temperature_f")
    private Integer temperatureF;

    @Column(name = "wind_mph")
    private Integer windMph;

    @Column(name = "precipitation_pct")
    private Integer precipitationPct;

    private String conditions;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected WeatherForecast() {
        // JPA
    }

    public WeatherForecast(Game game, Integer temperatureF, Integer windMph,
                            Integer precipitationPct, String conditions) {
        this.game = game;
        this.temperatureF = temperatureF;
        this.windMph = windMph;
        this.precipitationPct = precipitationPct;
        this.conditions = conditions;
    }

    public void updateFrom(Integer temperatureF, Integer windMph, Integer precipitationPct, String conditions) {
        this.temperatureF = temperatureF;
        this.windMph = windMph;
        this.precipitationPct = precipitationPct;
        this.conditions = conditions;
        this.fetchedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        this.fetchedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public Integer getTemperatureF() {
        return temperatureF;
    }

    public Integer getWindMph() {
        return windMph;
    }

    public Integer getPrecipitationPct() {
        return precipitationPct;
    }

    public String getConditions() {
        return conditions;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
