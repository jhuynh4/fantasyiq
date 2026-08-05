# FantasyIQ — Building It (Nearly) Free

A concrete guide to developing and even deploying FantasyIQ with $0–a few dollars a month, without giving up the production-engineering learning goals. The short version: **almost the entire build (Phases 0–6) costs nothing**, and even AWS deployment (Phase 7) can be done for pocket change if you're deliberate about it.

---

## 1. The Big Idea: Local-First Development Costs Nothing

Phases 0–6 of the development plan — everything except actual AWS deployment — run entirely on your own machine via Docker Compose. Postgres, Redis, your Spring Boot app, and all your tests run locally with **zero cloud spend**. This isn't a compromise; it's genuinely how most backend engineers build and test day to day, cloud costs are a deployment-time concern, not a development-time one. So the real cost question is really just: (1) external data APIs, and (2) the eventual AWS deploy.

---

## 2. Data Sources: Free-Tier Path

| Domain | Free option | Cost | Caveat |
|---|---|---|---|
| Stats/rosters/schedules (historical, for backtesting) | **nflverse data** (GitHub releases, CSV/Parquet) | $0, no key, no limits | Not live/real-time — batch files updated after each week. Perfect for Phase 3 backtesting and even for early MVP demo data. |
| Stats/rosters/schedules (live, in-season) | **SportsDataIO free/dev tier** | $0 (trial), often rate/scope-limited | Trial tiers are usually time-boxed or capped in scope — treat this as "enough to build and demo the pipeline," not a permanent production plan. Re-evaluate once you're actually in-season and want daily fresh data. |
| Stats/rosters/schedules (alternative live-ish, fully free) | **ESPN's undocumented public API** | $0, no key | Unofficial/unstable, but genuinely free indefinitely. A reasonable permanent free fallback behind your adapter interface if you don't want to pay anything, ever. |
| Injuries | ESPN public API, or SportsDataIO free tier | $0 | ESPN's injury data is less structured (no practice-participation detail) — acceptable trade-off for a free build. |
| Betting lines | **The Odds API free tier** | $0 (limited monthly requests — check current cap at signup, historically several hundred/month) | Plenty for once/twice-a-week pulls per game; you don't need per-minute odds. |
| Weather | **OpenWeatherMap free tier** | $0 (1,000 calls/day free tier historically) | Only call for outdoor games — you'll use a tiny fraction of the free quota even at full scale. |
| Trending/crowd signal | **Sleeper public API** | $0, no key required, no documented rate limit for reasonable use | Genuinely free indefinitely — no caveats. |

**Bottom line:** you can build 100% of the ingestion pipeline for $0 using nflverse (historical/backtest) + ESPN's public API (live, free indefinitely) + The Odds API + OpenWeatherMap + Sleeper. SportsDataIO is optional — nice if you want cleaner data and can use a free trial period, but not required.

---

## 3. Local Dev Tooling: Already Free

Everything in Phases 0–6 is free by default:
- **Java 21, Spring Boot, Gradle** — all free/open source.
- **Docker Desktop** — free for individual/personal use.
- **PostgreSQL, Redis** — free, run in Docker locally.
- **GitHub** — free for public or private repos at your scale; **GitHub Actions** gives free CI minutes (2,000 min/month on the free plan for private repos, unlimited for public repos as of standard GitHub free-tier terms — worth confirming current limits, but ample for solo-project CI).
- **Testcontainers, WireMock, JUnit, ArchUnit, Resilience4j, Micrometer** — all open source, no cost.
- **Local observability** — you can run Grafana + Prometheus in Docker Compose locally for Phase 6 instead of paying for a hosted version; this teaches the same skills without any cloud spend.

So realistically, **Phases 0–6 cost $0**, full stop — the only judgment call is whether you pay a small amount for SportsDataIO's data quality, which is optional.

---

## 4. Deployment: Two Paths, Both Cheap

### Path A — Free-tier-first, non-AWS (cheapest, still teaches real cloud concepts)

If your goal is "get it live and demoable" without spending anything:

| Concern | Free-tier service | Notes |
|---|---|---|
| Backend hosting | **Render** or **Railway** free/hobby tier, or **Fly.io** free allowance | Deploy your Spring Boot Docker image directly; both have generous free tiers for small always-on or scale-to-zero services |
| Postgres | **Neon** or **Supabase** free tier (serverless Postgres) | Real Postgres, generous free storage/compute allowances, no credit card required on Neon's free tier |
| Redis | **Upstash** free tier (serverless Redis) | Pay-per-request model with a free monthly allowance — plenty for a low-traffic portfolio project |
| CI/CD | GitHub Actions (free tier) → deploy via the host's CLI/webhook | Same CI/CD learning, just deploying somewhere free instead of ECS |

This path gets your **entire MVP live on the internet for $0/month**, with real managed Postgres/Redis (not toy substitutes), and still exercises Docker, CI/CD, and cloud deployment concepts. The trade-off: it's not literally AWS, so it doesn't directly demonstrate AWS-specific skills (ECS, RDS, Terraform-for-AWS) on a resume.

### Path B — AWS, but time-boxed and minimal (a few dollars, only when you're actively demoing)

If demonstrating **AWS specifically** matters to you (it's a strong resume signal, and it's in your stated learning goals), the trick is to **not run AWS resources continuously**:

| Concern | Approach | Cost approach |
|---|---|---|
| RDS Postgres | `db.t4g.micro`, **AWS Free Tier** covers 750 hrs/month for 12 months on eligible instance types for new accounts | $0 for the first 12 months if within free tier limits; afterwards, a few dollars/month for a micro instance run only when needed |
| ElastiCache Redis | No perpetual free tier, but `cache.t4g.micro` is inexpensive (roughly $10–12/month if left running 24/7) | **Run it only during active development/demo sessions**, tear down via Terraform (`terraform destroy`) when not in use — since your infra is codified in Terraform per Phase 7, spinning it up and down is a 5-minute command, not a manual chore |
| ECS Fargate | Charged per vCPU/memory-second while tasks run | Use the smallest task size (0.25 vCPU/0.5GB), and only run it during demo windows or short bursts, not 24/7 |
| ALB | Hourly charge while running | Same tear-down approach |

**Concretely:** build the Terraform once (this is where the real learning happens), `terraform apply` it for a demo session or interview prep, screenshot/record the working system and dashboards, then `terraform destroy`. You get the full "I deployed this to AWS with IaC and CI/CD" experience and portfolio evidence (README screenshots, architecture diagrams, a recorded demo) for a few dollars total across the whole project, rather than a recurring monthly bill.

**A middle ground worth considering:** deploy the *actual running product* on Path A (free, always-on, so it has a real live URL you can share), while doing the **AWS Terraform/ECS/RDS work as a documented, demonstrable exercise** you spin up periodically (Path B) purely to prove the skill and capture evidence for your portfolio/resume. You don't have to pick one exclusively — many solo devs run their "real" free-tier deployment while keeping a `terraform/` directory that provably works on AWS when applied.

---

## 5. Realistic Total Cost Estimate

| Scenario | Monthly cost |
|---|---|
| Full local development (Phases 0–6) | **$0** |
| Live product on Render/Railway/Fly + Neon + Upstash (Path A, ongoing) | **$0** (within free tiers at portfolio-project traffic levels) |
| AWS demo, spun up occasionally and torn down (Path B, occasional use) | **~$1–5 total**, not monthly, if you tear down between sessions |
| AWS run continuously for a full month (not recommended unless needed) | Roughly **$25–50/month** (RDS + ElastiCache + Fargate + ALB at smallest sizes) — this is the number to avoid by tearing down |
| SportsDataIO paid tier (optional, only if free/trial isn't enough) | Varies by plan; **skip this entirely** and rely on nflverse + ESPN's free public API if budget is the priority |

**The realistic target for a budget-conscious build: $0 for development, $0 for an always-on live demo (Path A), and a few one-time dollars if you also want provable AWS deployment evidence (Path B, torn down between uses).**

---

## 6. What This Changes in the Development Plan

- **Phase 1's stats provider choice:** default to **ESPN's public API or nflverse** instead of SportsDataIO, unless you specifically want to spend a trial period evaluating SportsDataIO's cleaner schema. Either way, this is exactly why the adapter pattern exists — you can start free and swap later without touching ingestion orchestration or the scoring engine.
- **Phase 6 (observability):** run Grafana/Prometheus locally in Docker Compose rather than a hosted service — same learning, no cost.
- **Phase 7 (AWS):** treat it as a **time-boxed exercise**, not a permanently running environment. Build the Terraform, apply it, capture your evidence (screenshots, a short screen recording, dashboard exports), then destroy it. Reapply whenever you want to demo it live (e.g., before an interview).
- **Consider Path A as your actual "production" URL** if you want something you can casually share or keep live indefinitely without thinking about cost at all.

---

## Next Steps

Worth deciding now: do you want a single always-on free deployment (Path A) as your real "live product," with AWS as a separate provable-but-not-continuous exercise (Path B) — or do you want to skip Path A and go straight to time-boxed AWS sessions as your only deployment story? Either is reasonable; the first gives you something to casually share at any time, the second is slightly more resume-direct if AWS is the specific skill you most want to showcase.
