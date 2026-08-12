-- V14: recommendation_factors.factor_weight was sized as NUMERIC(5,4) in V13,
-- assuming a fractional 0..1 weight -- the actual factor calculators use
-- point-scale weights instead (e.g. MatchupFactorCalculator's WEIGHT=25),
-- which overflowed that precision. Widen to match, same shape as the
-- fantasy_points_allowed_ppr/_standard columns elsewhere in the schema.

ALTER TABLE recommendation_factors ALTER COLUMN factor_weight TYPE NUMERIC(6,2);
