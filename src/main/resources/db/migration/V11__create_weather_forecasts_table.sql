-- V11: weather_forecasts table (Phase 2). One row per game -- upserted by
-- game_id as the forecast gets refreshed on repeated runs closer to kickoff.
-- Only ever populated for outdoor-stadium games (see stadium_locations.is_dome).

CREATE TABLE weather_forecasts (
    id BIGSERIAL PRIMARY KEY,
    game_id UUID NOT NULL UNIQUE REFERENCES games(id),
    temperature_f INT,
    wind_mph INT,
    precipitation_pct INT,
    conditions VARCHAR(100),
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
