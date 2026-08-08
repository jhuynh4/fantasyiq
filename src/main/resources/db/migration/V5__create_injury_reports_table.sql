-- V5: injury_reports table (Phase 2). ESPN's injury signal is thin -- its
-- roster payload only carries a status string and a date per designation,
-- no body part or practice-participation detail, so those two columns will
-- always be NULL from this source until a richer provider is added.

CREATE TABLE injury_reports (
    id BIGSERIAL PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id),
    report_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    body_part VARCHAR(50),
    practice_participation VARCHAR(20),
    source VARCHAR(20) NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (player_id, report_date, source)
);

CREATE INDEX idx_injury_reports_player ON injury_reports (player_id);
