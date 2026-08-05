# FantasyIQ — Product Requirements Document (PRD)

**Status:** Draft v1.0 — MVP scope
**Owner:** You (solo founder/developer)

---

## 1. Product Vision

FantasyIQ is a companion analytics platform for fantasy football managers who already play on Sleeper, ESPN, Yahoo, or similar platforms. It does not replace those platforms — it makes the manager smarter *before* they act on them. The product's core differentiator is that every recommendation comes with a transparent, structured explanation of the underlying reasoning, so the user learns *how to think* about fantasy decisions, not just what to click.

**One-line pitch:** "Know why, not just who."

**Who this is for:** fantasy managers who are moderately engaged — they check waivers weekly, read some analysis, but don't have time to manually cross-reference snap counts, target share, Vegas lines, and injury reports every week themselves.

**Who this is NOT for (at MVP):** casual players who set a lineup once and never touch it again (they won't return weekly); hardcore analysts who already build their own models in spreadsheets (they don't need this — though they might use it to validate their own work).

---

## 2. Target Users & Personas

### Persona 1 — "Weekly Checker" Dana
- Plays in 1–2 leagues (home league + work league), PPR and standard mix.
- Checks her league app 2–3 times a week: Tuesday (waivers), Saturday/Sunday morning (lineup).
- Doesn't read long-form analysis articles; wants a fast, confident answer with just enough "why" to trust it.
- **Primary jobs-to-be-done:** "Tell me who to start this week" and "Tell me who to pick up off waivers."

### Persona 2 — "League Commissioner" Marcus
- Plays in 3+ leagues, some dynasty/keeper.
- Enjoys the strategy layer — wants to understand matchup and trend reasoning, not just get a verdict.
- Frequently proposes/evaluates trades and wants a second opinion before pulling the trigger.
- **Primary jobs-to-be-done:** "Is this trade fair?" and "Who's trending up that I should get ahead of?"

### Persona 3 — "Data-Curious" Priya
- Newer to fantasy football (1–2 seasons in), wants to get better and understand the "why" behind good decisions, not just follow rankings blindly.
- **Primary jobs-to-be-done:** "Help me understand why Player X is a good/bad start this week" — the explanation is the product for her, not a side feature.

All three personas converge on the same underlying need: **trustworthy, explained, weekly-cadence decisions** — which is why explanation is a first-class feature, not a tooltip.

---

## 3. MVP Scope

### 3.1 In Scope (MVP)
1. Account creation & authentication
2. Player search
3. Player profile & historical performance view
4. Injury updates feed
5. Matchup analysis (per game/team, and per player-vs-defense)
6. Trending players view
7. Weekly start/sit recommendations with explanations
8. Weekly waiver wire recommendations with explanations
9. Trade analyzer with explanations
10. Player rankings (positional, scoring-format aware)

### 3.2 Explicitly Out of Scope for MVP
- Connecting a real Sleeper/ESPN league or roster (Phase 9+ — see long-term vision)
- Push notifications
- Draft assistant / pre-draft rankings and mock drafts
- Multi-sport support
- Social features (league chat, comments, sharing)
- Mobile native app (responsive web only)
- Payment/subscription billing (defer monetization decisions until there's a working product to monetize)

Keeping league-connection out of MVP is the single most important scope cut: it lets every other feature be built and demoed against *global* player data (no per-user roster complexity), which is dramatically simpler while still being genuinely useful — a user can look up any player and get real recommendations without connecting anything.

---

## 4. Feature Specifications

Each feature below includes: purpose, user stories, functional requirements, and explicit acceptance criteria.

### 4.1 Account Creation & Authentication

**Purpose:** Let users have a persistent identity, mainly to support saved preferences later (favorite players, scoring format default) and to gate the product minimally.

**User Stories**
- As a new user, I can register with email + password so I can access the app.
- As a returning user, I can log in and stay logged in across sessions (until I explicitly log out or my session expires).

**Functional Requirements**
- Email/password registration and login.
- Session persistence via access + refresh token (frontend silently refreshes).
- Basic profile: display name, default scoring format preference (PPR / Half-PPR / Standard).
- Logout invalidates refresh token.

**Acceptance Criteria**
- [ ] Duplicate email registration is rejected with a clear error.
- [ ] Incorrect password shows a generic "invalid credentials" message (no user enumeration).
- [ ] A logged-in user's session survives a page refresh without re-entering credentials.
- [ ] Changing default scoring format immediately affects rankings/recommendations views on next load.

---

### 4.2 Player Search

**Purpose:** Fast entry point into any player's data — the most frequent interaction in the app.

**User Stories**
- As a user, I can type a partial player name and see matching results within a second.
- As a user, I can filter search by position and/or team.

**Functional Requirements**
- Typeahead search, fuzzy-matched (handles typos, partial names, common nicknames if feasible).
- Filter chips: position, team.
- Each result shows: name, position, team, headshot (if available from provider), current injury status badge if not fully healthy.

**Acceptance Criteria**
- [ ] Searching "mahomes" and "mahomess" (typo) both return Patrick Mahomes.
- [ ] Results are ranked with exact/prefix matches above fuzzy matches.
- [ ] An injured player's search result visibly shows their status (e.g., a small "Q" or "OUT" badge) without needing to open the profile.
- [ ] Empty/no-match state gives a clear message, not a blank screen.

---

### 4.3 Player Profile & Historical Performance

**Purpose:** The canonical "everything about this player" view — supports all the personas' need to sanity-check a recommendation by digging in.

**User Stories**
- As a user, I can view a player's season stat line and recent game-by-game performance.
- As a user, I can see usage trends (snap %, target share, red zone touches) over recent weeks, not just box-score stats.
- As a user, I can see the player's current injury status and recent injury history.
- As a user, I can see this week's matchup context for the player (opponent, defensive rank vs. position, Vegas implied total, weather if outdoor).

**Functional Requirements**
- Season summary: total points (by scoring format), games played, position rank.
- Game log table: last N games with core stats + fantasy points.
- Usage trend mini-chart: snap %, target share/carry share, red zone share over last 4–6 games.
- Injury status panel: current status + recent report history.
- "This week" panel: opponent, defensive matchup rank, Vegas implied total, weather (if applicable).

**Acceptance Criteria**
- [ ] Profile loads for any searchable player, including players on bye week or currently injured (shows appropriate empty/contextual states, not errors).
- [ ] Usage trend chart correctly reflects a real recent shift (e.g., a player whose snap % jumped after an injury to a teammate shows that jump).
- [ ] "This week" panel is absent or clearly labeled "no game this week" for bye-week players.
- [ ] Data staleness is visible — a small "updated X hours ago" indicator sourced from the ingestion timestamp.

---

### 4.4 Injury Updates

**Purpose:** Surface the injury signal that most directly and immediately changes a start/sit decision.

**User Stories**
- As a user, I can see a feed of recent injury report changes across the league (not just one player).
- As a user, I can filter the injury feed by my positions of interest or by team.

**Functional Requirements**
- Feed of injury report changes, most recent first: player, team, status change (e.g., "Limited → Full," "New: Questionable"), body part, date.
- Filters: position, team.
- Each entry links to the player's profile.

**Acceptance Criteria**
- [ ] A status change from the last ingestion run appears in the feed within one ingestion cycle (not stale by more than the job's scheduled interval).
- [ ] Filtering by position (e.g., RB) hides non-RB entries.
- [ ] Feed clearly distinguishes "new report" vs. "status changed" vs. "no change from last report" (only show actual changes, not repeated identical statuses).

---

### 4.5 Matchup Analysis

**Purpose:** Team- and player-level view of "how hard is this matchup," which underlies several other features (start/sit, rankings) but also stands alone as something users browse.

**User Stories**
- As a user, I can see this week's slate of games with key context (spread, total, weather).
- As a user, I can see, for a given team's defense, which positions it's tough or easy against.

**Functional Requirements**
- Weekly matchup list: all games for the selected week, with spread, over/under, implied team totals, weather summary (outdoor games only).
- Defense-vs-position breakdown per team: fantasy points allowed by position, with a rank (1 = toughest, 32 = easiest) and short trend note (e.g., "trending tougher over last 3 weeks").

**Acceptance Criteria**
- [ ] All games for the selected week appear, including ones not yet started and ones in progress/completed (with a status indicator).
- [ ] Defense-vs-position ranks are recalculated on each ingestion cycle and reflect the most recent completed week's data.
- [ ] Dome games never show a weather panel; outdoor games always do (even if forecast data is temporarily unavailable, show a clear "unavailable" state rather than a blank).

---

### 4.6 Trending Players

**Purpose:** Surface players gaining relevance before they're obviously "hot," which is a core waiver-wire use case.

**User Stories**
- As a user, I can see a list of players whose usage or opportunity is trending up recently, independent of whether they've already scored a lot of points (points often lag opportunity).

**Functional Requirements**
- "Trending up" list: ranked by a trend score (e.g., week-over-week increase in snap %/target share/red zone share), not raw fantasy points.
- "Trending down" list (useful for identifying players to bench/drop before it's obvious).
- Each entry shows the specific metric driving the trend (e.g., "Target share up from 12% to 24% over 2 weeks").

**Acceptance Criteria**
- [ ] A player who scored 0 points last week but saw a real snap-count jump can still appear on "trending up" (this is the feature's whole point — decoupling from box score).
- [ ] Trend entries show the specific underlying metric, not just a generic "trending" label.
- [ ] Lists exclude injured/OUT players from "trending up" (a snap increase during a since-injured week isn't actionable).

---

### 4.7 Weekly Start/Sit Recommendations *(flagship feature)*

**Purpose:** The signature feature — a confident, explained answer to "should I start this player this week."

**User Stories**
- As a user, I can look up any rosterable player and get a start/sit recommendation for the current week with a confidence level.
- As a user, I can see the specific factors behind the recommendation (matchup, usage, red zone share, Vegas total, weather, injury, SOS, trend), each in plain language.
- As a user, I can compare two players head-to-head to decide between them (a very common real decision: "Player A or Player B this week").

**Functional Requirements**
- Recommendation verdict: START / SIT / TOSS-UP, with confidence (HIGH/MEDIUM/LOW).
- Explanation panel: ordered list of contributing factors, each with a short narrative sentence and a visual indicator of whether it favors or hurts the player (e.g., a green "+" or red "–").
- Head-to-head comparison view: select two players at the same position, see both verdicts and factor breakdowns side by side.
- Filterable by scoring format (PPR/Half-PPR/Standard) since usage-heavy players (PPR) vs. TD-dependent players (Standard) can flip recommendations.

**Acceptance Criteria**
- [ ] Every recommendation includes at least 3 distinct factor types in its explanation (never a bare verdict with no reasoning).
- [ ] Switching scoring format can change the verdict for a pass-catching-back type player, and the UI reflects this without a page reload feeling broken (clear loading state).
- [ ] A player ruled OUT always shows SIT with injury as the dominant/only factor needed — the system doesn't bury an OUT status under unrelated positive factors.
- [ ] Head-to-head view works for any two players at the same position, even across different teams' bye weeks (a bye-week player is clearly marked SIT — no game).

---

### 4.8 Waiver Wire Recommendations

**Purpose:** Turn the trending-players signal into a prioritized, position-aware "who to add" action list — the second most frequent weekly action after start/sit.

**User Stories**
- As a user, I can see a ranked list of recommended waiver adds for the week, by position.
- As a user, I can see why each recommended player is being surfaced (opportunity change, injury to a teammate opening a role, favorable upcoming schedule, etc.).
- As a user, I can see a suggested "drop" candidate alongside adds, to make the add/drop decision easier (even without roster connection, this can be framed generically — "if you need to drop someone, deprioritize your least-used bench RB/WR").

**Functional Requirements**
- Ranked waiver recommendation list, filterable by position.
- Each entry: player, recommendation strength, explanation factors (same factor engine as start/sit, weighted toward opportunity/trend signals rather than single-week matchup).
- "Why now" framing specifically calls out *the trigger* (e.g., "starter left the game with injury," "target share doubled over 2 weeks," "easy upcoming stretch of matchups").

**Acceptance Criteria**
- [ ] List updates meaningfully week to week (not a static "popular players" list) — verified by comparing two consecutive weeks' outputs during testing.
- [ ] A player who is added and then, in later data, becomes widely rostered (approximated via a rostered-percentage or trend signal if available from a data source) drops off the list appropriately.
- [ ] Every entry has an explicit "why now" trigger sentence, not just a generic factor list.

---

### 4.9 Trade Analyzer

**Purpose:** Give a fast, reasoned second opinion on a proposed trade — directly serves Persona 2 (Marcus) and is a strong differentiator vs. simple "trade value chart" tools.

**User Stories**
- As a user, I can enter two sets of players (mine vs. theirs) and get a value comparison.
- As a user, I can see the reasoning behind the valuation, not just a single number.
- As a user, I can see how the trade looks both "rest of season" and "this week only," since those can differ meaningfully.

**Functional Requirements**
- Input: two lists of players (supports uneven trades, e.g., 2-for-1).
- Output: overall verdict (favors you / favors them / fair), value delta, and per-player reasoning reusing the same factor engine with a rest-of-season time horizon plus positional scarcity adjustment.
- Secondary "this week" lens showing immediate-week impact separately from rest-of-season value, since a trade can be fair long-term but bad for this week's lineup.

**Acceptance Criteria**
- [ ] Analyzer correctly handles uneven trades (2-for-1, 3-for-2) and clearly shows aggregate value on each side.
- [ ] Reasoning for each player traces back to real factor rows, not a separate hidden model — verifiable in that the same player's factors are consistent whether viewed via start/sit or via the trade analyzer.
- [ ] Analyzer flags when a key player in the trade is injured/bye-week in a way that materially changes the "this week" lens vs. rest-of-season lens.

---

### 4.10 Player Rankings

**Purpose:** A familiar, expected fantasy-football artifact (positional rankings), but explainable and scoring-format-aware rather than a static list.

**User Stories**
- As a user, I can view rankings by position for the current week, in my preferred scoring format.
- As a user, I can see a short reason for a player's rank, especially for players whose rank moved notably since last week.

**Functional Requirements**
- Positional rankings (QB/RB/WR/TE/K/DST), sortable, scoring-format toggle.
- Rank-change indicator (up/down arrows vs. last week) with a one-line reason for notable movers.
- Rankings sourced from the same underlying scoring engine as start/sit (consistency across the app is a hard requirement, not a nice-to-have).

**Acceptance Criteria**
- [ ] Switching scoring format re-sorts the list appropriately (e.g., a high-target-share, low-TD WR ranks higher in PPR than Standard).
- [ ] A player who moved 5+ spots week over week shows a one-line reason (e.g., "Moved up after starting QB return from injury increased passing volume").
- [ ] Rankings and the start/sit verdict for the same player in the same week are never contradictory (e.g., a player ranked #3 at his position cannot simultaneously show a SIT verdict without a very clear, visible reason like a last-minute injury update).

---

## 5. Cross-Cutting Product Requirements

- **Explanation-first design:** no screen shows a recommendation, ranking, or verdict without a path to "why" that's at most one click/tap away. This is the product's core promise and should be treated as a non-negotiable requirement, not a nice-to-have polish item.
- **Data freshness transparency:** every data-driven view shows when its underlying data was last refreshed, so users calibrate trust correctly (especially important right before kickoff when injury statuses can change fast).
- **Scoring-format awareness:** PPR/Half-PPR/Standard should be a persistent, easy-to-change setting that affects every relevant view consistently.
- **Consistent verdicts across features:** the same player's underlying "goodness" shouldn't silently contradict itself between the rankings page, the start/sit page, and the trade analyzer — because they all read from the same factor engine, this should hold naturally, but it's worth explicit QA attention.
- **Graceful empty/edge states:** bye weeks, injured/OUT players, and just-signed/newly-relevant players are common edge cases in fantasy football (not rare exceptions) and need explicit, clear states everywhere rather than blanks or errors.

---

## 6. Success Metrics (how you'll know the MVP works)

Since this is a solo/portfolio project without a live user base initially, treat these as **design targets to build toward and validate against**, not live dashboards from day one:

- **Explanation completeness:** 100% of recommendations/rankings surface at least 3 factor types — measurable directly from your own data.
- **Recommendation consistency:** 0 contradictions between start/sit, rankings, and trade analyzer for the same player/week in QA spot-checks.
- **Data freshness:** injury and odds data never more than one scheduled ingestion cycle stale (directly measurable from `ingestion_runs`).
- **Backtest credibility:** when run against a past completed season, start/sit and waiver recommendations should directionally agree with what's obvious in hindsight for clear-cut cases (a player who scored 30 points on high usage should have scored well on your engine's factors *before* the game, not just in retrospect) — this is your best pre-launch quality signal in the absence of real users.

If/when you do get real users, the metrics to add are: weekly active usage (checked in on both a waiver day and a lineup day), and self-reported trust (a simple "was this recommendation helpful?" thumbs up/down per recommendation, which also becomes a future data source for tuning weights).

---

## 7. Open Product Questions (worth deciding before Phase 1)

- **Scoring format default:** pick one default (PPR is most common in modern leagues) rather than forcing a choice on first login.
- **Confidence levels:** decide the actual thresholds (e.g., HIGH = top/bottom quintile of score distribution at the position) before Phase 3, so it's not an arbitrary label.
- **"Toss-up" verdict threshold:** decide how close two players' scores need to be before you show TOSS-UP instead of a forced START/SIT — this is a real UX decision, not just a modeling detail, since users lose trust in a tool that never admits uncertainty.
- **DST and K handling:** these positions behave very differently from skill positions (streaming-based decisions, matchup-dominant) — decide whether they get a simplified factor set rather than forcing the full model onto them.

---

## Next Steps

This PRD maps directly onto Phases 1–5 of the development plan (auth → data → scoring engine → caching → trade analyzer). A good next step would be turning §4.7 (start/sit) and §4.9 (trade analyzer) into detailed UX wireframes, or defining the exact factor weighting/confidence thresholds referenced in §7 before you start Phase 3.
