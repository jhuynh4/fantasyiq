-- V12: betting_lines table (Phase 2). Two rows per game -- one per team,
-- each carrying that team's own implied point total. Upserted by
-- (game_id, team_id) since there's no single external id for "this game's
-- odds"; The Odds API only exposes current lines, so a row here reflects
-- whatever the market looked like the last time ingestion ran.

CREATE TABLE betting_lines (
    id BIGSERIAL PRIMARY KEY,
    game_id UUID NOT NULL REFERENCES games(id),
    team_id INT NOT NULL REFERENCES teams(id),
    implied_team_total NUMERIC(5,2),
    spread NUMERIC(5,2),
    over_under NUMERIC(5,2),
    source VARCHAR(50) NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (game_id, team_id)
);
