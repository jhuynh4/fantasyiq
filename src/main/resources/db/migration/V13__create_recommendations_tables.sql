-- V13: recommendations + recommendation_factors (Phase 3). One recommendation
-- row per (player, season, week, type); regenerating a week updates the row
-- in place (see RecommendationReconciliationService) rather than duplicating.
-- recommendation_factors is fully replaced on each regeneration -- it's a
-- derived breakdown of the score, not independently upserted per factor.

CREATE TABLE recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL REFERENCES players(id),
    season INT NOT NULL,
    week INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    score NUMERIC(8,3) NOT NULL,
    confidence VARCHAR(10) NOT NULL,
    scoring_version VARCHAR(10) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (player_id, season, week, type)
);

CREATE INDEX idx_recommendations_season_week_type ON recommendations (season, week, type);

CREATE TABLE recommendation_factors (
    id BIGSERIAL PRIMARY KEY,
    recommendation_id UUID NOT NULL REFERENCES recommendations(id) ON DELETE CASCADE,
    factor_type VARCHAR(30) NOT NULL,
    factor_value NUMERIC(10,4),
    factor_weight NUMERIC(5,4),
    contribution NUMERIC(8,4) NOT NULL,
    narrative TEXT NOT NULL
);

CREATE INDEX idx_recommendation_factors_recommendation_id ON recommendation_factors (recommendation_id);
