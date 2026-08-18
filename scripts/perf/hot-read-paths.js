// k6 load test for FantasyIQ's hot, cacheable read endpoints -- written to
// capture a before/after baseline around the Phase 4 Redis cache-aside work.
//
// Deliberately hammers a small, FIXED set of keys (one season/week, a
// handful of player ids) rather than a random spread across all data --
// cache-aside only pays off on repeat hits to the same key, so a random
// spread would show no before/after difference regardless of whether
// caching works.
//
// Usage:
//   k6 run scripts/perf/hot-read-paths.js
//   k6 run -e BASE_URL=http://localhost:8080 -e SUMMARY_FILE=docs/perf/baseline.json -e SUMMARY_MD=docs/perf/baseline.md scripts/perf/hot-read-paths.js
//
// Requires the app running locally with season=2025 data already ingested
// and START_SIT recommendations already generated for at least one week
// (both true as of the Phase 3 backtest work -- see CURRENT_WORK.md).

import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SEASON = 2025;
const EMAIL = 'coach@fantasyiq.dev';
const PASSWORD = 'correct-horse-battery';

const startSitAllDuration = new Trend('start_sit_all_duration', true);
const startSitPositionDuration = new Trend('start_sit_position_duration', true);
const playerDetailDuration = new Trend('player_detail_duration', true);
const playerTrendingDuration = new Trend('player_trending_duration', true);

export const options = {
    scenarios: {
        hot_reads: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 20 },
                { duration: '60s', target: 20 },
                { duration: '10s', target: 0 },
            ],
        },
    },
};

function authHeaders(token) {
    return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
}

function login() {
    const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email: EMAIL, password: PASSWORD }), {
        headers: { 'Content-Type': 'application/json' },
    });
    return res;
}

function register() {
    return http.post(
        `${BASE_URL}/api/auth/register`,
        JSON.stringify({ email: EMAIL, password: PASSWORD, displayName: 'Perf Test Coach' }),
        { headers: { 'Content-Type': 'application/json' } }
    );
}

// Finds the first week in [1, 18] that already has generated START_SIT
// recommendations, so this script works whether last run's data started
// at week 1 or was regenerated for a different week.
function findWeekWithData(token) {
    for (let week = 1; week <= 18; week++) {
        const res = http.get(`${BASE_URL}/api/recommendations/start-sit?season=${SEASON}&week=${week}`, authHeaders(token));
        if (res.status === 200 && JSON.parse(res.body).length > 0) {
            return week;
        }
    }
    return null;
}

export function setup() {
    let loginRes = login();
    if (loginRes.status !== 200) {
        register();
        loginRes = login();
    }
    if (loginRes.status !== 200) {
        fail(`could not authenticate: ${loginRes.status} ${loginRes.body}`);
    }
    const token = JSON.parse(loginRes.body).accessToken;

    const week = findWeekWithData(token);
    if (week === null) {
        fail(
            `no START_SIT recommendations found for season ${SEASON} in weeks 1-18 -- ` +
                `run POST /api/recommendations/generate?season=${SEASON}&week=<N> (or /backtest) first`
        );
    }

    const searchRes = http.get(`${BASE_URL}/api/players/search?q=a`, authHeaders(token));
    if (searchRes.status !== 200) {
        fail(`player search failed: ${searchRes.status} ${searchRes.body}`);
    }
    const players = JSON.parse(searchRes.body).slice(0, 5);
    if (players.length === 0) {
        fail('player search returned no results -- run /api/players/ingest first');
    }
    const playerIds = players.map((p) => p.id);

    console.log(`Baseline setup: season=${SEASON} week=${week} playerIds=${playerIds.length}`);
    return { token, week, playerIds };
}

export default function (data) {
    const { token, week, playerIds } = data;
    const headers = authHeaders(token);
    const playerId = playerIds[Math.floor(Math.random() * playerIds.length)];

    let res = http.get(`${BASE_URL}/api/recommendations/start-sit?season=${SEASON}&week=${week}`, {
        ...headers,
        tags: { name: 'start-sit-all' },
    });
    startSitAllDuration.add(res.timings.duration);
    check(res, { 'start-sit-all: status 200': (r) => r.status === 200 });

    res = http.get(`${BASE_URL}/api/recommendations/start-sit?season=${SEASON}&week=${week}&position=WR`, {
        ...headers,
        tags: { name: 'start-sit-wr' },
    });
    startSitPositionDuration.add(res.timings.duration);
    check(res, { 'start-sit-wr: status 200': (r) => r.status === 200 });

    res = http.get(`${BASE_URL}/api/players/${playerId}`, { ...headers, tags: { name: 'player-detail' } });
    playerDetailDuration.add(res.timings.duration);
    check(res, { 'player-detail: status 200': (r) => r.status === 200 });

    res = http.get(`${BASE_URL}/api/players/${playerId}/trending`, { ...headers, tags: { name: 'player-trending' } });
    playerTrendingDuration.add(res.timings.duration);
    check(res, { 'player-trending: status 200': (r) => r.status === 200 });

    sleep(0.3);
}

function fmt(ms) {
    return ms === undefined ? 'n/a' : `${ms.toFixed(1)}ms`;
}

function endpointRow(label, metric) {
    if (!metric) return `| ${label} | n/a | n/a | n/a | n/a |`;
    const v = metric.values;
    return `| ${label} | ${fmt(v.avg)} | ${fmt(v['p(90)'])} | ${fmt(v['p(95)'])} | ${fmt(v.max)} |`;
}

export function handleSummary(data) {
    const total = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0;
    const failed = data.metrics.http_req_failed ? (data.metrics.http_req_failed.values.rate * 100).toFixed(2) : '0.00';

    const md = `# Hot-read-path baseline

Generated: ${new Date().toISOString()}
Base URL: ${BASE_URL}
Total requests: ${total}
Failed requests: ${failed}%

| Endpoint | avg | p90 | p95 | max |
|---|---|---|---|---|
${endpointRow('GET /recommendations/start-sit (all)', data.metrics.start_sit_all_duration)}
${endpointRow('GET /recommendations/start-sit?position=WR', data.metrics.start_sit_position_duration)}
${endpointRow('GET /players/{id}', data.metrics.player_detail_duration)}
${endpointRow('GET /players/{id}/trending', data.metrics.player_trending_duration)}
`;

    const out = {
        stdout: md,
    };
    if (__ENV.SUMMARY_FILE) {
        out[__ENV.SUMMARY_FILE] = JSON.stringify(data, null, 2);
    }
    if (__ENV.SUMMARY_MD) {
        out[__ENV.SUMMARY_MD] = md;
    }
    return out;
}
