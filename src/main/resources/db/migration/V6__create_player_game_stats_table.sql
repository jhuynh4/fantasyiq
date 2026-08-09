-- V6: player_game_stats table (Phase 2). Scoped to QB/RB/WR/TE -- ESPN's
-- per-athlete gamelog endpoint doesn't cover K/DST at all (those are
-- team-level, not athlete-level, in ESPN's model; a separate future task).
--
-- Confirmed against real 2025 season data that ESPN's free tier does NOT
-- provide snaps, snap_pct, or red_zone_touches for any position -- those
-- columns exist (matching the original system-design.md schema) but will
-- always be NULL from this source, same as injury_reports.body_part.
--
-- passing_attempts/passing_completions/passing_yards/passing_touchdowns/
-- interceptions were not in the original schema sketch, which only
-- anticipated skill-position stats -- added here since QBs are obviously
-- fantasy-relevant and ESPN's QB gamelog data is otherwise unused.

CREATE TABLE player_game_stats (
    id BIGSERIAL PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id),
    game_id UUID NOT NULL REFERENCES games(id),
    snaps INT,
    snap_pct NUMERIC(5,2),
    targets INT,
    receptions INT,
    rec_yards INT,
    rush_attempts INT,
    rush_yards INT,
    red_zone_touches INT,
    passing_attempts INT,
    passing_completions INT,
    passing_yards INT,
    passing_touchdowns INT,
    interceptions INT,
    touchdowns INT,
    fantasy_points_ppr NUMERIC(6,2),
    fantasy_points_standard NUMERIC(6,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (player_id, game_id)
);

CREATE INDEX idx_player_game_stats_player ON player_game_stats (player_id);
CREATE INDEX idx_player_game_stats_game ON player_game_stats (game_id);
