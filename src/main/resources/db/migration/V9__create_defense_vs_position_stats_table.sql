-- V9: computed table, not ingested -- derived from player_game_stats + games
-- for games already in our DB. Extends the original system-design.md sketch
-- (a single fantasy_points_allowed column) with separate PPR/standard
-- columns and ranks, matching how player_game_stats already tracks both
-- formats; a single column would silently pick one format for every caller.

CREATE TABLE defense_vs_position_stats (
    id BIGSERIAL PRIMARY KEY,
    team_id INT NOT NULL REFERENCES teams(id),
    season INT NOT NULL,
    week INT NOT NULL,
    position VARCHAR(4) NOT NULL,
    fantasy_points_allowed_ppr NUMERIC(6,2) NOT NULL,
    fantasy_points_allowed_standard NUMERIC(6,2) NOT NULL,
    rank_ppr INT NOT NULL,        -- 1 = toughest matchup (fewest points allowed), 32 = easiest
    rank_standard INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (team_id, season, week, position)
);

CREATE INDEX idx_defense_vs_position_lookup ON defense_vs_position_stats (season, week, position);
