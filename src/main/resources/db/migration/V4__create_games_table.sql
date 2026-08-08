-- V4: games table (Phase 2). Regular-season games only -- ingestion filters
-- out preseason/postseason at the mapping layer, not here, so this table
-- only ever holds what the scoring engine (Phase 3) actually cares about.

CREATE TABLE games (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_ref VARCHAR(50) NOT NULL UNIQUE,
    season INT NOT NULL,
    week INT NOT NULL,
    home_team_id INT NOT NULL REFERENCES teams(id),
    away_team_id INT NOT NULL REFERENCES teams(id),
    kickoff TIMESTAMPTZ NOT NULL,
    venue VARCHAR(150),
    -- Nullable: only resolvable once stadium_locations is populated
    -- (weather ingestion, later in Phase 2) -- NULL means "not yet known",
    -- never assume false.
    is_dome BOOLEAN,
    status VARCHAR(20) NOT NULL,   -- SCHEDULED / IN_PROGRESS / FINAL
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_games_season_week ON games (season, week);
CREATE INDEX idx_games_home_team ON games (home_team_id);
CREATE INDEX idx_games_away_team ON games (away_team_id);
