# FantasyIQ — Connecting the Data Sources

How ESPN, The Odds API, OpenWeatherMap, and Sleeper actually plug into the system: real endpoints, the ID-reconciliation problem (the hardest part), adapter code shape, and the orchestration flow that ties it together.

---

## 1. The Real Hard Problem: Every Source Has Its Own Player IDs

Before any endpoint details matter, understand this: **ESPN, Sleeper, and The Odds API each identify things differently, and none of them agree with each other or with your own database.**

| Source | Identifies players/teams by | Example |
|---|---|---|
| ESPN | Numeric "athlete ID" (their internal ID), numeric team ID (1–34ish, non-contiguous) | Patrick Mahomes = athlete ID `3139477`; Chiefs = team ID `12` |
| Sleeper | Its own numeric-string `player_id` | Patrick Mahomes = `4046` |
| The Odds API | Team names as plain strings, no player-level data at all (it's game/team-level odds only) | `"Kansas City Chiefs"` |
| OpenWeatherMap | No player or team concept at all — just lat/long or city name for a stadium | Arrowhead Stadium ≈ `39.0489, -94.4839` |
| Your database (`players` table) | Your own `UUID` | Whatever you generate on first ingest |

**This means player/team identity reconciliation is not an edge case — it's core infrastructure you build once, early, and every ingestion job depends on.**

### 1.1 The reconciliation strategy

Your `players` table already has an `external_ref` column (from the schema doc) — extend this to a small **cross-reference table** instead of a single column, since you now have two identity sources (ESPN, Sleeper) that both need mapping to the same internal player:

```sql
player_external_ids (
  id BIGSERIAL PK,
  player_id UUID FK -> players,
  source VARCHAR NOT NULL,      -- 'ESPN' | 'SLEEPER'
  external_id VARCHAR NOT NULL,
  UNIQUE (source, external_id)
)

team_external_ids (
  id SERIAL PK,
  team_id INT FK -> teams,
  source VARCHAR NOT NULL,      -- 'ESPN' | 'ODDS_API'
  external_id VARCHAR NOT NULL, -- ESPN numeric id, or the exact team name string Odds API uses
  UNIQUE (source, external_id)
)
```

### 1.2 How reconciliation actually happens, source by source

- **ESPN → your DB:** ESPN is your primary roster/player source, so ESPN *is* effectively your onboarding path for new players. When ESPN's roster/athlete endpoint returns a player you haven't seen, you create the `players` row and immediately write the `player_external_ids` row (`source='ESPN'`). No matching needed — you're creating the canonical record from this source.
- **Sleeper → your DB:** Sleeper publishes a **full player dump endpoint** (`GET /v1/players/nfl`) that conveniently includes each player's full name, team, position, *and* useful cross-reference fields in its payload (Sleeper's player objects commonly include alternate IDs for other platforms). Match Sleeper players to your existing `players` rows by **normalized full name + team + position** (strip suffixes like Jr./Sr./III, lowercase, trim whitespace) as a first pass; log unmatched players to a review table rather than silently dropping them, since name-matching always has edge cases (defenses, practice-squad players, name changes). Once matched, write the `player_external_ids` row (`source='SLEEPER'`) so you never have to re-match that player again.
- **Odds API → your DB:** Odds API only operates at the **team/game level**, not player level, so reconciliation is much smaller in scope — just a one-time mapping of ~32 team name strings to your `teams` table, done once, by hand, in a seed migration. This is the easiest of the three.
- **OpenWeatherMap → your DB:** No reconciliation needed at all — you maintain a small static `stadium_locations` seed table (`team_id`, `latitude`, `longitude`, `is_dome`) yourself, since this never changes and there's no vendor identity to match against.

This asymmetry (heavy matching for Sleeper, trivial for Odds API and weather) is exactly why the adapter pattern matters — each adapter owns its own reconciliation logic, and the orchestration layer above just asks "give me normalized domain objects," never touching vendor-specific IDs directly.

---

## 2. The Actual Endpoints You'll Call

### 2.1 ESPN (stats, rosters, schedules, injuries) — no API key required

| Purpose | Endpoint |
|---|---|
| List all 32 teams | `GET https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams` |
| Team roster (players + basic info) | `GET https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/{team_id}/roster` |
| Team schedule | `GET https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/{team_id}/schedule` |
| Week's scoreboard (games, scores, status) | `GET https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?seasontype=2&week={week}&dates={year}` |
| Player season stats | `GET https://sports.core.api.espn.com/v2/sports/football/leagues/nfl/seasons/{year}/types/2/athletes/{athlete_id}/statistics` |
| Player game log (per-game stats) | `GET https://site.web.api.espn.com/apis/common/v3/sports/football/nfl/athletes/{athlete_id}/gamelog?season={year}` — note the different host (`site.web.api.espn.com`), a third ESPN base URL alongside `base-url`/`core-base-url` |
| News (often contains injury-relevant blurbs) | `GET https://site.api.espn.com/apis/site/v2/sports/football/nfl/news?limit=50` |

**Important caveat, worth planning around:** ESPN doesn't have one clean "current injury status" endpoint the way a paid provider would — status is often embedded inside roster/athlete responses (a `status` or `injuries` field) or inferred from news items. Build your `InjuryProvider` adapter to check the roster/athlete payload's status field first, and treat news-derived injury detection as a secondary, lower-confidence signal (tag it as such in `injury_reports.source`).

### 2.2 The Odds API (betting lines) — requires a free API key

```
GET https://api.the-odds-api.com/v4/sports/americanfootball_nfl/odds
    ?apiKey={key}
    &regions=us
    &markets=spreads,totals
    &oddsFormat=american
```
Returns an array of games, each with `home_team`, `away_team`, commence time, and a `bookmakers` array containing `spreads` and `totals` markets. In practice, average across 2–3 major bookmakers (or just use the first well-known one consistently) rather than trying to pick "the best" line — consistency matters more than optimality here.

**Derive implied team total:**
```
favorite_implied_total = (over_under / 2) + (abs(spread) / 2)
underdog_implied_total = (over_under / 2) - (abs(spread) / 2)
```

### 2.3 OpenWeatherMap (weather) — requires a free API key

```
GET https://api.openweathermap.org/data/2.5/forecast
    ?lat={stadium_lat}
    &lon={stadium_lon}
    &appid={key}
    &units=imperial
```
Returns a 5-day/3-hour-step forecast array. Pick the forecast entry closest to actual kickoff time. Only call this for games where `stadiums.is_dome = false` and kickoff is within the forecast window (~5 days out) — calling earlier just wastes quota on a forecast that will change anyway.

### 2.4 Sleeper (trending signal) — no key required

```
GET https://api.sleeper.app/v1/players/nfl/trending/add?lookback_hours=48&limit=100
GET https://api.sleeper.app/v1/players/nfl/trending/drop?lookback_hours=48&limit=100
```
Returns `[{ "player_id": "4046", "count": 45 }, ...]` — just an ID and an add/drop count, nothing else. You'll cross-reference `player_id` against `player_external_ids` (source='SLEEPER') to attach it to your internal player.

There's also a bulk player dump (`GET https://api.sleeper.app/v1/players/nfl`) — **Sleeper explicitly asks this be called sparingly (it's a large ~5MB payload)**, so pull it once and cache it (e.g., once a day at most) purely to build/refresh your name-matching table, not on every ingestion run.

---

## 3. Adapter Interfaces (Java/Spring shape)

Each source is isolated behind an interface your orchestration layer depends on — never the vendor specifics directly.

```java
public interface StatsProvider {
    List<RawRoster> fetchRosters();
    List<RawGame> fetchSchedule(int season, int week);
    List<RawPlayerGameStats> fetchGameStats(String externalGameId);
}

public interface InjuryProvider {
    List<RawInjuryReport> fetchCurrentInjuries();
}

public interface OddsProvider {
    List<RawGameOdds> fetchOdds(int season, int week);
}

public interface WeatherProvider {
    Optional<RawWeatherForecast> fetchForecast(double lat, double lon, Instant kickoff);
}

public interface TrendingProvider {
    List<RawTrendingSignal> fetchTrending(TrendingType type, int lookbackHours);
}
```

Each `Raw*` type is a plain DTO shaped like the vendor's JSON (or close to it) — normalization into your domain model (`Player`, `Game`, etc.) happens in a **separate mapper class**, not inside the adapter, so adapters stay thin and swappable and mappers stay independently testable.

```java
@Component
public class EspnStatsProvider implements StatsProvider {

    private final RestClient restClient; // Spring 6 RestClient, or WebClient

    @Override
    public List<RawRoster> fetchRosters() {
        List<RawRoster> rosters = new ArrayList<>();
        for (int teamId : ESPN_TEAM_IDS) {
            EspnRosterResponse response = restClient.get()
                .uri("https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/{id}/roster", teamId)
                .retrieve()
                .body(EspnRosterResponse.class);
            rosters.add(mapToRawRoster(teamId, response));
        }
        return rosters;
    }
    // ...
}
```

Wrap every external call with Resilience4j:

```java
@CircuitBreaker(name = "espnApi", fallbackMethod = "fallbackRosters")
@Retry(name = "espnApi")
public List<RawRoster> fetchRosters() { ... }
```

---

## 4. Reconciliation & Normalization Service

Sits between adapters and the database — this is where the ID-matching logic from §1 lives.

```java
@Service
public class PlayerReconciliationService {

    public UUID resolveOrCreate(RawRoster.Athlete espnAthlete, int teamId) {
        return playerExternalIdRepo
            .findBySourceAndExternalId("ESPN", espnAthlete.id())
            .map(PlayerExternalId::getPlayerId)
            .orElseGet(() -> createNewPlayer(espnAthlete, teamId));
    }

    public Optional<UUID> resolveSleeperPlayer(SleeperPlayer sleeperPlayer) {
        // 1. exact ID match if we've seen this Sleeper ID before
        Optional<UUID> byId = playerExternalIdRepo
            .findBySourceAndExternalId("SLEEPER", sleeperPlayer.playerId())
            .map(PlayerExternalId::getPlayerId);
        if (byId.isPresent()) return byId;

        // 2. fuzzy match by normalized name + team + position
        String normalizedName = normalize(sleeperPlayer.fullName());
        Optional<Player> candidate = playerRepo
            .findByNormalizedNameAndTeamAndPosition(normalizedName, sleeperPlayer.team(), sleeperPlayer.position());

        if (candidate.isPresent()) {
            // cache the mapping so we never fuzzy-match this player again
            playerExternalIdRepo.save(new PlayerExternalId(candidate.get().getId(), "SLEEPER", sleeperPlayer.playerId()));
            return Optional.of(candidate.get().getId());
        }

        // 3. no match — log for manual review, don't silently drop
        unmatchedPlayerRepo.save(new UnmatchedExternalPlayer("SLEEPER", sleeperPlayer.playerId(), sleeperPlayer.fullName()));
        return Optional.empty();
    }
}
```

The unmatched-player log table is a small but important piece — in a system with automated cross-source matching, silent failures are worse than visible ones. A handful of unmatched players per week is normal (practice squad churn, name formatting edge cases) and reviewable in a few minutes.

---

## 5. Orchestration: How the Jobs Actually Run

```java
@Component
public class DailyIngestionScheduler {

    @Scheduled(cron = "0 0 6 * * *") // 6am daily
    public void runDailyIngestion() {
        IngestionRun run = ingestionRunService.start("DAILY_COMBINED");
        try {
            List<RawRoster> rosters = statsProvider.fetchRosters();
            rosters.forEach(playerIngestionService::upsert);       // uses PlayerReconciliationService

            List<RawInjuryReport> injuries = injuryProvider.fetchCurrentInjuries();
            injuries.forEach(injuryIngestionService::upsert);

            List<RawTrendingSignal> trending = trendingProvider.fetchTrending(ADD, 48);
            trending.forEach(trendingIngestionService::upsert);    // uses resolveSleeperPlayer

            ingestionRunService.markSuccess(run, rosters.size() + injuries.size() + trending.size());
        } catch (Exception e) {
            ingestionRunService.markFailed(run, e);
            // circuit breaker + retry already attempted at the adapter level;
            // this catch is the last line of defense so one failed job doesn't crash the scheduler
        }
    }

    @Scheduled(cron = "0 0 10,18 * * TUE,THU,FRI,SUN") // odds move throughout the week
    public void runOddsIngestion() { /* similar shape */ }

    @Scheduled(cron = "0 0 7 * * *") // daily, only pulls for upcoming outdoor games
    public void runWeatherIngestion() { /* similar shape, filters by is_dome and kickoff proximity */ }

    @Scheduled(cron = "0 0 3 * * MON") // post-Sunday/Monday games, recompute box scores
    public void runPostGameStatsIngestion() { /* pulls gamelogs, recomputes defense_vs_position */ }
}
```

Each job is independent, has its own `ingestion_runs` row, and a failure in one (say, weather) never blocks another (say, injuries) — this is exactly why they're separate scheduled methods rather than one monolithic "sync everything" job.

---

## 6. Practical Gotchas Worth Knowing Upfront

- **ESPN rate-limits informally** (no published limit, but hammering it can get you temporarily throttled) — space out your 32 per-team roster calls with a small delay, and cache aggressively; rosters don't change every day.
- **ESPN's injury data is genuinely thin — confirmed, not just anticipated.** Pulled real data during Phase 2: the roster-embedded `injuries` array is just `{status, date}`, nothing else. No body part, no practice-participation detail at all. `injury_reports.body_part`/`practice_participation` will always be `NULL` from this source.
- **The team schedule endpoint defaults to whatever season/type is currently happening on the calendar** — during preseason, the bare `/teams/{id}/schedule` call silently returns only preseason games, even though the real regular-season schedule is already fully public. `season` and `seasontype=2` must be passed explicitly or you'll wrongly conclude the data isn't published yet.
- **The gamelog endpoint's response is column-oriented, not field-oriented like everything else.** A top-level `names` array defines what each positional index in an event's `stats` array means, and *the set of names differs by position* (a QB's `names` has no `receptions`; a WR's has no `passingYards`). The actual per-game stat lines live at `seasonTypes[].categories[].events[]` — not the top-level `events` object, which only carries descriptive metadata (week/opponent/date) with zero stats, an easy structure to misread on a first pass. Look values up by name (`names.indexOf(statName)`) rather than assuming a fixed field set, and confirmed absent for both positions: `snaps`, `snap_pct`, `red_zone_touches`, and fantasy points themselves — ESPN gives you raw box score numbers only, you compute fantasy points yourself.
- **That same top-level `events` map is still worth reading, though — it's the only source for per-game team.** Each entry (keyed by event id) carries a `team` field alongside its week/opponent/date metadata. This is how `player_game_stats.team_id` gets populated without a second API call: look the event id up in this map to find which team the athlete suited up for *in that specific game*, which matters for anyone traded mid-season (their season-long roster team on `players.team_id` isn't necessarily their team in an earlier game's box score). ESPN occasionally omits an entry for a given event id, so treat the lookup as nullable rather than guaranteed.
- **Sleeper's bulk player dump is ~5MB — don't call it per-request.** Pull once daily at most, cache in memory or a table, and use it only for building/refreshing the name-matching index.
- **The Odds API's free tier has a monthly request cap** — batch your calls (one call gets you the whole week's slate of games at once, not per-game), so a 2–3x/week pull pattern costs you only 2–3 requests total, not 2–3 times the number of games.
- **Team ID drift:** ESPN's team IDs are stable but non-contiguous and not obviously ordered — don't assume you can loop `1..32`; fetch the team list endpoint once and store the real IDs in your `team_external_ids` table rather than hardcoding a guessed range.

---

## Next Steps

A good next step is designing the **unmatched-player review workflow** in more detail (how you'll periodically resolve the Sleeper fuzzy-match misses), or writing the actual `EspnRosterResponse`/`SleeperPlayer` DTO classes and mapper logic against real captured JSON payloads before Phase 1 begins.
