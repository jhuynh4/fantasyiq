# FantasyIQ — Data Sourcing & Recommendation Algorithms

A concrete technical deep-dive: exactly where player data comes from, and the actual algorithmic logic behind waiver wire recommendations (and the shared scoring engine underneath start/sit, rankings, and trades).

---

## Part 1 — Where Player Data Actually Comes From

### 1.1 The core problem: no single source has everything

You need five distinct kinds of data, and realistically no single vendor gives you all five well. Plan for a small set of specialized sources, each behind its own adapter (per the `StatsProvider`/`InjuryProvider`/etc. interfaces from the architecture doc).

### 1.2 Recommended sources, concretely

**Stats, rosters, schedules — the backbone**

| Option | What you get | Cost | Fit |
|---|---|---|---|
| **SportsDataIO** | Player bios, rosters, box scores, play-by-play-derived stats (snaps, targets, red zone touches), schedules, projections | Free trial tier, then paid (~$25–100+/mo depending on tier) | Best "just works" REST API for real-time-ish weekly data; closest to what a real commercial product would use |
| **nflverse / nflfastR data** (public GitHub repos, e.g. `nflverse/nflverse-data`) | Complete historical play-by-play and derived stats back to 1999, updated after each week's games | Free | Not a live REST API — it's versioned CSV/Parquet releases. Excellent for backtesting (Phase 3) and as a free backbone if you don't want to pay for SportsDataIO yet |
| **ESPN's undocumented public API** (`site.api.espn.com/apis/site/v2/sports/football/nfl/...`) | Rosters, basic stats, some injury data | Free, no key required | Unofficial and unstable — endpoints and shapes can change without notice. Fine as a secondary/backup source behind your adapter interface, risky as your only source |

**Recommendation for MVP:** start with **nflverse data** for historical backtesting (free, lets you build and validate the scoring engine against real completed seasons before the current season even starts) and **SportsDataIO's free/trial tier** for live weekly data once the season is active. This gets you a working pipeline without upfront cost, with a clear upgrade path.

**Injuries**

| Option | Notes |
|---|---|
| SportsDataIO injury endpoint | Structured, includes practice participation (DNP/Limited/Full) — the single most useful injury signal for a scoring model |
| ESPN's public API | Has basic injury status; less structured, no practice participation detail |

Practice participation trend (DNP → Limited → Full across a week) is a materially better predictive signal than the final Tuesday/Wednesday status alone — prioritize a source that gives you the full week's practice reports, not just the final tag.

**Betting lines / Vegas**

| Option | Notes |
|---|---|
| **The Odds API** | Purpose-built, has a workable free tier (500 requests/month on the free plan as of general availability — verify current limits when you sign up), gives spreads, totals, and you derive implied team total as `(over_under / 2) + (spread / 2)` for the favored team (and the inverse for the underdog) |

Vegas implied team total is arguably your single highest-signal non-usage factor — it's the market's aggregated prediction of scoring environment, which correlates strongly with fantasy output across skill positions.

**Weather**

| Option | Notes |
|---|---|
| **OpenWeatherMap** | Free tier sufficient for once-daily forecast pulls on ~16 outdoor games/week; only call for `games.is_dome = false` to conserve quota |
| **Tomorrow.io** | Similar free tier, sometimes better precipitation granularity |

**Trending/ownership signal (useful for waiver logic specifically)**

| Option | Notes |
|---|---|
| **Sleeper's public read API** (`api.sleeper.app`, no auth required) | Exposes trending "most added" / "most dropped" players league-wide over rolling windows — a genuinely useful *externally aggregated* signal for waiver recommendations, since it reflects what real fantasy managers across many leagues are already doing |

This is worth calling out specifically: Sleeper's trending endpoint gives you a free, real, crowd-sourced "the market is moving on this player" signal that's complementary to (not a replacement for) your own usage-based trend detection. Using both — your own opportunity-based signal, plus the crowd signal — is stronger than either alone, and it's a good example of triangulating multiple independent signals rather than trusting one source.

### 1.3 How the pieces fit into ingestion (recap + specifics)

```
Daily:      injury reports (practice participation changes fast during the week)
2–3x/week:  betting lines (lines move as the week progresses; grab Tue, Thu/Fri, and right before kickoff)
Daily:      weather forecasts (only for outdoor games, only once forecasts are within ~7 days of kickoff)
Post-game:  player_game_stats, defense_vs_position recalculation
Weekly:     Sleeper trending pull (rolling 24hr add/drop counts)
```

Each of these is a separate scheduled job writing to its own table, with its own row in `ingestion_runs` — this is what lets you reason about staleness per-signal rather than treating "the data" as one monolithic freshness concept.

---

## Part 2 — The Waiver Wire Algorithm, In Detail

### 2.1 The core insight the algorithm is built on

**Fantasy points lag opportunity.** A player who gets a real, sustained increase in snaps/targets/carries will *eventually* produce points, but by the time the box score shows it, the player is already widely rostered and the waiver value is gone. The waiver algorithm's entire job is to detect the *opportunity* shift before the *points* catch up — which is why it deliberately does not simply rank by recent fantasy points.

### 2.2 The five signals that drive a waiver score

| # | Signal | What it captures | Why it matters for waivers specifically |
|---|---|---|---|
| 1 | **Opportunity trend** | Week-over-week change in snap %, target share (WR/TE), or carry share (RB) over a trailing 2–3 week window | The primary signal — a sustained increase is the clearest "before it's obvious" indicator |
| 2 | **Role-change trigger** | A discrete event: a starter ahead of this player got hurt, benched, or left the game | Explains *why* the trend is happening, and discrete triggers are more reliable predictors than gradual trends because they're less likely to regress |
| 3 | **Red zone opportunity share** | Red zone touches/targets as a share of team red zone plays, trailing window | TDs (the highest-variance, highest-value fantasy events) concentrate in the red zone — a player getting more red zone work is undervalued if the box score hasn't reflected it in TDs yet (TD variance means it often hasn't) |
| 4 | **Upcoming schedule favorability** | Average defense-vs-position rank of the player's next 2–3 opponents | A trending player facing a soft near-term schedule is a stronger add than one facing a brutal stretch, even at equal current opportunity |
| 5 | **Roster scarcity / crowd signal** | Sleeper trending add-count (proxy for how "already found" this player is across the broader fantasy community) | A high crowd-add-count signal both validates your own signal (convergent evidence) and tells you urgency — if the crowd already found it, act fast or don't bother |

### 2.3 Waiver scoring formula (concrete)

```
opportunity_delta = current_window_share - prior_window_share
  where share = snap_pct (all positions) blended with target_share (pass-catchers) or carry_share (RBs)
  window = trailing 2 games vs. the 2 games before that

trigger_bonus =
  + 15 points  if a role-change trigger occurred in the last 7 days (injury/benching to a player ahead on the depth chart)
  + 0          otherwise

red_zone_score = red_zone_share_current_window (0–1) * 20

schedule_score = (32 - avg_def_rank_next_3_games) / 32 * 10
  (defense rank: 1 = toughest, 32 = easiest, so this rewards facing weak defenses)

crowd_score = normalize(sleeper_trending_add_count, 0–100 percentile within position) * 10
  (capped contribution — this is corroborating evidence, not the primary driver)

waiver_score =
    (opportunity_delta * 100 * 40)     -- opportunity trend is weighted heaviest
  + trigger_bonus
  + red_zone_score
  + schedule_score
  + crowd_score

# Exclude entirely if:
  - player's own status is OUT/IR
  - player is already rostered above a rostered_pct threshold (e.g., >60%, since "waiver" implies actually available in most leagues — this threshold should be configurable since league sizes/depths vary)
```

This is intentionally a **weighted linear model with explicit, inspectable terms** rather than a black-box ML model. That's a deliberate product and engineering choice (see §2.5).

### 2.4 Turning the score into the "why now" narrative

Each term above maps directly to a `recommendation_factors` row (from the schema in the design doc):

```
factor_type: USAGE
  narrative: "Target share up from 11% to 23% over the last 2 games"
  contribution: +16.0

factor_type: TREND (role-change trigger)
  narrative: "Became the primary receiving back after [teammate] left Week 6 with a hamstring injury"
  contribution: +15.0

factor_type: RED_ZONE
  narrative: "3 of the team's last 9 red zone touches, tied for the team lead"
  contribution: +8.4

factor_type: SOS (strength of schedule)
  narrative: "Faces the 27th and 29th ranked run defenses over the next two weeks"
  contribution: +7.2

factor_type: TREND (crowd signal)
  narrative: "Among the 15 most-added players league-wide over the last 24 hours"
  contribution: +4.0
```

Sum of contributions = `waiver_score`, and this exact factor list is what renders as the "why now" panel in the UI (§4.8 of the PRD). This is also why the factor table design matters so much architecturally — the score and the explanation are the same computation, not a score plus a bolted-on explanation generated separately.

### 2.5 Why a weighted/explainable model instead of ML

You could train a gradient-boosted model or similar on historical data to predict next-week fantasy points, and it would likely be *more accurate* in isolation. But it directly conflicts with the product's core requirement (explanation-first, per PRD §5) unless you also build a whole separate explainability layer (SHAP values etc.) on top of it — which is significantly more engineering complexity for a solo dev, for a product where the explanation *is* the value proposition, not just a nice-to-have.

The weighted-linear approach also has real engineering benefits that map to your stated learning goals:
- Fully unit-testable per factor (Phase 3 of the dev plan).
- Versionable (`scoring_version`) without needing to retrain/redeploy a model artifact.
- Debuggable in production — "why did this recommendation drop?" is answerable by reading factor rows, not by introspecting a model.

**Where ML could fit later (post-MVP, optional):** once you have a season or more of your own recommendation-outcome data, a good "Phase 10+" experiment is training a model to *learn the weights* (e.g., a simple regularized linear regression predicting next-week fantasy points from the same factor inputs, replacing hand-tuned weights with learned ones) while keeping the explanation structure identical — this gets you real applied-ML experience without abandoning explainability, since the *inputs* stay the same interpretable factors, only the weighting becomes learned instead of hand-tuned.

---

## Part 3 — The Shared Scoring Engine (Start/Sit, Rankings, Trade)

Waiver scoring above is one *specialization* of a general factor-scoring engine reused across features. Here's the shared core.

### 3.1 Universal factor set

| Factor | Formula sketch | Position relevance |
|---|---|---|
| **Matchup (defense vs. position)** | `(32 - opponent_def_rank_vs_position) / 32 * weight` | All |
| **Usage/opportunity** | Trailing 3-game average snap %, target/carry share, normalized within position | All, especially RB/WR/TE |
| **Red zone share** | Trailing 3-game red zone touch/target share | All, higher weight for RB/WR/TE |
| **Vegas implied team total** | `(implied_total - position_average_baseline) / position_stddev` (z-score) | All, especially skill positions in high-total games |
| **Weather** | Penalty term for high wind (passing-heavy positions) and precipitation (all positions, fumble/footing risk); zero if dome | QB/WR primarily for wind |
| **Injury status** | Hard override: OUT/IR → forces SIT regardless of other factors; QUESTIONABLE → confidence downgrade, not a hard override | All |
| **Strength of schedule (multi-week)** | Average opponent def rank over the recommendation's relevant horizon (1 week for start/sit, rest-of-season for trade) | All |
| **Historical trend** | Rolling z-score of recent fantasy points vs. the player's own season baseline (captures "hot/cold" independent of opportunity) | All |

### 3.2 Position-specific weighting

Different positions should not share one universal weight set — a QB's matchup factor (pass defense rank) matters differently than an RB's (run defense rank + game script), and red zone share matters far more for RB/WR/TE than for QB (whose value is spread across the whole field, not concentrated near the goal line). Concretely:

```
QB weights:   matchup 20%, usage 15%, vegas 25%, weather 15%, injury (override), sos 10%, trend 15%
RB weights:   matchup 20%, usage 30%, red_zone 20%, vegas 10%, injury (override), sos 10%, trend 10%
WR/TE weights: matchup 15%, usage 30%, red_zone 15%, vegas 15%, weather 5%, injury (override), sos 10%, trend 10%
```
(Illustrative starting weights — tune via backtesting per §2.5's Phase 3 backtest step, and store the active weight set alongside `scoring_version` so you can compare weight-set performance across historical weeks.)

### 3.3 From factor scores to a verdict

```
composite_score = sum(factor_value * position_weight for each factor)   -- normalized 0-100 scale

percentile = composite_score's percentile within same-position, same-week distribution

verdict:
  START  if percentile >= configurable_start_threshold (e.g., top 60% at position, tunable per §7 of the PRD)
  SIT    if percentile <= configurable_sit_threshold (e.g., bottom 25%)
  TOSS-UP otherwise, OR if the score is within a narrow band of a roster-relevant cutoff (e.g., RB2/FLEX borderline)

confidence:
  HIGH    if percentile is in the extreme tail (top/bottom ~15%) AND no conflicting factors (e.g., great matchup but QUESTIONABLE injury)
  LOW     if factors meaningfully disagree (e.g., strong Vegas number but a tough individual matchup) or data is stale
  MEDIUM  otherwise
```

This is the concrete mechanism behind PRD §7's open question about toss-up thresholds and confidence — worth tuning against your Phase 3 backtest before locking in numbers.

### 3.4 Trade and rankings as reuses, not reimplementations

- **Rankings** = same composite score, just listed and sorted per position for the week, with the "SOS" factor optionally weighted toward a full-season horizon rather than one week, depending on whether you're showing weekly or season-long rankings.
- **Trade analyzer** = same composite score computed with a **rest-of-season horizon** (SOS averaged over remaining weeks instead of just next week) plus a **positional scarcity adjustment**: subtract a position-specific "replacement level" baseline (e.g., the score of a typical waiver-available player at that position) so a top RB is worth more than a top-scoring but replaceable K/DST, matching how real fantasy value works.

---

## Part 4 — Practical Notes for Implementation

- **Backtest before trusting weights.** Before the current season starts, run the full engine against a complete past season (free via nflverse data) week by week, and manually spot-check whether "trending up" and "waiver recommendation" lists would have flagged real breakouts (e.g., a backup RB who became a starter mid-season) *before* the point spike, not after.
- **Log every factor input, not just the final score**, from day one — when a recommendation looks wrong later, you want to be able to answer "what were the raw inputs" without re-deriving them from scratch.
- **Treat weights as configuration, not code constants** — store them in a config table or versioned config file keyed by `scoring_version`, so adjusting a weight doesn't require a full redeploy and so you can A/B or compare weight sets against your backtest.
- **Don't let one missing data source silently zero out a factor.** If weather data fails to fetch for an outdoor game, that factor should be flagged "unavailable" and excluded from the composite (with a slightly lowered confidence), not silently treated as a neutral 0 that skews the score.

---

## Next Steps

Good follow-ups from here: (1) work out the exact backtest methodology and success criteria before Phase 3 starts, (2) pin down the position-specific weight sets with real numbers you'd actually start with, or (3) design the `recommendation_factors` narrative-generation logic (how raw numbers become the plain-English sentences shown in the UI).
