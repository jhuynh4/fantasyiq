-- V3: ESPN's live API returns "WSH" for Washington, not "WAS" as originally
-- seeded in V2. ESPN is our only ingestion source right now, so align to it
-- rather than build cross-vendor abbreviation-alias handling prematurely.
UPDATE teams SET abbreviation = 'WSH' WHERE abbreviation = 'WAS';
