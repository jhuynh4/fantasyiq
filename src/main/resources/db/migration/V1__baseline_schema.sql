-- V1: Baseline schema — identity/auth + core football domain (teams, players)
-- Matches Phase 1 of the development plan.

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- for fuzzy player name search

-- ---------- Identity & Access ----------

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    default_scoring_format VARCHAR(20) NOT NULL DEFAULT 'PPR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- ---------- Core Football Domain ----------

CREATE TABLE teams (
    id SERIAL PRIMARY KEY,
    abbreviation VARCHAR(4) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    conference VARCHAR(10),
    division VARCHAR(20)
);

CREATE TABLE team_external_ids (
    id SERIAL PRIMARY KEY,
    team_id INT NOT NULL REFERENCES teams(id),
    source VARCHAR(20) NOT NULL,       -- 'ESPN' | 'ODDS_API'
    external_id VARCHAR(100) NOT NULL,
    UNIQUE (source, external_id)
);

CREATE TABLE stadium_locations (
    team_id INT PRIMARY KEY REFERENCES teams(id),
    latitude NUMERIC(9,6) NOT NULL,
    longitude NUMERIC(9,6) NOT NULL,
    is_dome BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE players (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(150) NOT NULL,
    normalized_name VARCHAR(150) NOT NULL,
    position VARCHAR(4) NOT NULL,
    current_team_id INT REFERENCES teams(id),
    jersey_number INT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    birth_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_players_name_trgm ON players USING GIN (full_name gin_trgm_ops);
CREATE INDEX idx_players_normalized_name ON players (normalized_name);
CREATE INDEX idx_players_position_team ON players (position, current_team_id);

CREATE TABLE player_external_ids (
    id BIGSERIAL PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id),
    source VARCHAR(20) NOT NULL,       -- 'ESPN' | 'SLEEPER'
    external_id VARCHAR(100) NOT NULL,
    UNIQUE (source, external_id)
);

CREATE TABLE unmatched_external_players (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(20) NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    raw_name VARCHAR(150) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved BOOLEAN NOT NULL DEFAULT FALSE
);

-- ---------- Operational ----------

CREATE TABLE ingestion_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source VARCHAR(30) NOT NULL,       -- STATS_PROVIDER / INJURY_PROVIDER / ODDS_PROVIDER / WEATHER_PROVIDER / TRENDING_PROVIDER
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,       -- SUCCESS / PARTIAL / FAILED / RUNNING
    records_processed INT,
    error_message TEXT
);

CREATE INDEX idx_ingestion_runs_source_started ON ingestion_runs (source, started_at DESC);
