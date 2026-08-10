-- V8: the player's team FOR THAT SPECIFIC GAME, not their current team --
-- needed to correctly attribute defense_vs_position_stats (V9), since a
-- player's roster team can change mid-season (trades) and Player.currentTeam
-- would be wrong for games played before the trade. Nullable: ESPN's gamelog
-- occasionally omits the per-event team metadata, and an ingestion run
-- shouldn't fail a whole stat line over it.
ALTER TABLE player_game_stats ADD COLUMN team_id INT REFERENCES teams(id);

CREATE INDEX idx_player_game_stats_team ON player_game_stats (team_id);
