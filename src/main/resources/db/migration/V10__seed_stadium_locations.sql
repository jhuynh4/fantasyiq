-- V10: seed stadium_locations with real lat/long + roof type for all 32
-- teams' home stadiums. is_dome covers both permanent domes and
-- retractable/fixed roofs that are effectively always closed for games --
-- weather ingestion (V11) skips fetching a forecast for any of these.

INSERT INTO stadium_locations (team_id, latitude, longitude, is_dome)
SELECT id, v.latitude, v.longitude, v.is_dome
FROM teams
JOIN (VALUES
    ('ARI', 33.527600, -112.262600, true),
    ('ATL', 33.755400,  -84.400800, true),
    ('BAL', 39.278000,  -76.622700, false),
    ('BUF', 42.773800,  -78.787000, false),
    ('CAR', 35.225800,  -80.852800, false),
    ('CHI', 41.862300,  -87.616700, false),
    ('CIN', 39.095500,  -84.516000, false),
    ('CLE', 41.506100,  -81.699500, false),
    ('DAL', 32.747300,  -97.094500, true),
    ('DEN', 39.743900, -105.020100, false),
    ('DET', 42.340000,  -83.045600, true),
    ('GB',  44.501300,  -88.062200, false),
    ('HOU', 29.684700,  -95.410700, true),
    ('IND', 39.760100,  -86.163900, true),
    ('JAX', 30.323900,  -81.637300, false),
    ('KC',  39.048900,  -94.483900, false),
    ('LAC', 33.953500, -118.339200, true),
    ('LAR', 33.953500, -118.339200, true),
    ('LV',  36.090900, -115.183300, true),
    ('MIA', 25.958000,  -80.238900, false),
    ('MIN', 44.973600,  -93.257500, true),
    ('NE',  42.090900,  -71.264300, false),
    ('NO',  29.951100,  -90.081200, true),
    ('NYG', 40.813500,  -74.074500, false),
    ('NYJ', 40.813500,  -74.074500, false),
    ('PHI', 39.900800,  -75.167500, false),
    ('PIT', 40.446800,  -80.015800, false),
    ('SEA', 47.595200, -122.331600, false),
    ('SF',  37.403000, -121.970000, false),
    ('TB',  27.975900,  -82.503300, false),
    ('TEN', 36.166500,  -86.771300, false),
    ('WAS', 38.907700,  -76.864500, false)
) AS v(abbreviation, latitude, longitude, is_dome) ON v.abbreviation = teams.abbreviation;
