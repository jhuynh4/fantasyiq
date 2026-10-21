# Runbook: an AWS session (apply, verify, destroy)

The stack costs money while it exists, so every session follows the same shape: **apply, check that it works, destroy**. This session is the first time three sets of changes meet real AWS:

- **PR #29** (the `aws` profile, the admin account, the no-healthy-hosts alarm, correlation-id logging): verified locally only.
- **The hosting work** (S3, CloudFront, the frontend deploy job): verified with `terraform validate` only.
- **The rate-limit fix and the ALB lockdown** (slices 6b and 6c): same.

Plan for about two hours, most of it waiting on RDS, ElastiCache and CloudFront. While the stack is up it costs roughly 10-15 cents an hour (RDS, ElastiCache, the ALB and a small Fargate task; S3 and CloudFront are close to free at this size), so a long session is cheap and a forgotten stack is the real cost: **do the destroy at the end (step 12) even if something failed.**

Commands are for a bash shell (Git Bash is fine) unless stated. Run Terraform from `infra/`.

## 0. Before you start

You need, on your machine:

- `terraform` (>= 1.6), the AWS CLI, `curl`, `docker` (only if you push an image by hand)
- AWS credentials for the account (`aws sts get-caller-identity` should print account `018326344181`)
- the two vendor API keys, if you want weather/odds to work this session (optional, see step 6). **You type them yourself, in your own shell. They never go into this repo or a chat.**
- access to the inbox for the `alert_email` address in `infra/terraform.tfvars` (you will click a confirmation link)
- a browser tab on the repo's Actions page

```bash
aws sts get-caller-identity
terraform -version
git checkout main && git pull
```

Check that nothing is left over from last time:

```bash
cd infra
terraform init
terraform state list
```

The list should be empty (or the command should say there is no state). If it lists resources, a previous destroy did not finish: run `terraform destroy` first and sort that out before applying.

## 1. Plan

```bash
terraform fmt -check
terraform validate
terraform plan -out=session.plan
```

Expect roughly 60 resources to add and **nothing to change or destroy**. Skim the list for the new ones: `aws_s3_bucket.frontend`, `aws_cloudfront_distribution.frontend`, `aws_cloudfront_function.spa_fallback`, `aws_lb_listener_rule.from_our_cloudfront`, `random_password.origin_verify`, `aws_secretsmanager_secret.admin_password`, `aws_cloudwatch_metric_alarm.alb_no_healthy_hosts`. The plan file can contain secret values, so `infra/*.plan` is git-ignored; delete it after the apply.

## 2. Apply

```bash
terraform apply session.plan
```

This takes 15-25 minutes. RDS and CloudFront are the slow parts, and the command stays quiet for long stretches. Things that can go wrong here:

| Symptom | Cause and fix |
|---|---|
| `RulesPerSecurityGroupLimitExceeded` (or similar) on the ALB security group rule | The CloudFront prefix list counts as ~55 rules against the default 60. If something else is on that group, remove it. If AWS now counts it as more than 60, request a higher quota ("Inbound or outbound rules per security group") and retry. As a last resort, remove the prefix-list rule (keep the secret-header listener rule, which is what actually protects the API) and note it. |
| `BucketAlreadyExists` / name taken | The bucket name is `fantasyiq-frontend-<account id>`; another account is not going to have it, so this means a leftover bucket in your account. Delete it in the console and re-apply. |
| A secret "scheduled for deletion" | All secrets use `recovery_window_in_days = 0`, so this should not happen. If it does, force-delete it: `aws secretsmanager delete-secret --secret-id <name> --force-delete-without-recovery`. |
| Apply fails part-way | Fix the cause and run `terraform apply` again. It resumes. Do not delete `terraform.tfstate`. |

When it finishes, keep the outputs handy:

```bash
terraform output
```

You will use `frontend_url`, `frontend_bucket`, `admin_email`, `admin_password_secret_name` and `dashboard_url`.

## 3. Confirm the alarm email

AWS emails `alert_email` a subscription confirmation. **Click the link**, or the alarms will never notify you.

Right now there is no image in ECR, so the ECS task cannot start. That is useful for one check: the **no-healthy-hosts alarm** (`fantasyiq-alb-no-healthy-hosts`) should go to `ALARM` about 5 minutes after the service is created and send you an email. If you want to verify that, wait here (check the alarm in the CloudWatch console) before continuing. If you do not care, go straight on; the alarm will clear itself once a task is healthy.

## 4. Deploy (backend image and frontend)

GitHub Actions does both. In the repo's **Actions** tab, choose **Deploy**, then **Run workflow** on `main`.

Two jobs run:

- **deploy**: builds the image, pushes it to ECR tagged with the commit, registers a new task definition and updates the service. It waits for the service to become stable (5-10 minutes; the app takes ~90 seconds to start and has a 180-second health grace period).
- **frontend**: builds the app, uploads it to the bucket and invalidates CloudFront.

If a job fails at "Configure AWS credentials via OIDC" with `Not authorized to perform sts:AssumeRoleWithWebIdentity`: the trust policy in `infra/github-oidc.tf` does not match the token. Compare the role's `sub` condition with the run's token claims (see CLAUDE.md, "immutable subject claims"); the run must be on `main`.

If the workflow cannot be started by hand (no "Run workflow" button), `workflow_dispatch` did not reach `main`; check that the Deploy workflow on `main` contains it.

**Fallback, pushing the image by hand** (only if the pipeline is broken): the task definition expects tag `manual-1`.

```bash
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 018326344181.dkr.ecr.us-east-1.amazonaws.com
docker build -t 018326344181.dkr.ecr.us-east-1.amazonaws.com/fantasyiq:manual-1 .
docker push 018326344181.dkr.ecr.us-east-1.amazonaws.com/fantasyiq:manual-1
```

Run those from the repo root. ECS retries on its own, so the task starts within a minute or two of the push. The frontend would then need uploading by hand (`aws s3 sync frontend/dist s3://<frontend_bucket>` after `npm run build`, then a CloudFront invalidation).

## 5. Check that the backend is up

```bash
aws ecs describe-services --cluster fantasyiq --services fantasyiq --query "services[0].{running:runningCount,desired:desiredCount,events:events[0:3].message}"
aws logs tail /ecs/fantasyiq --since 15m
```

In the log, look for:

- `Started FantasyIqApplication in ... seconds`
- Flyway applying migrations through **V15** (a fresh database starts empty)
- the **admin account being created** (`Created admin account for admin@fantasyiq.dev`; this proves the execution role can read the new `admin-password` secret; if it could not, the task would never have started)
- lines that are **JSON** with a `correlationId` field on ingestion lines, and **no** SQL statements and no DEBUG logging (this proves the `aws` profile is active)

If the task keeps stopping, find out why:

```bash
aws ecs list-tasks --cluster fantasyiq --desired-status STOPPED
aws ecs describe-tasks --cluster fantasyiq --tasks <task arn> --query "tasks[0].{reason:stoppedReason,containers:containers[0].reason}"
```

| Stopped reason | Likely cause |
|---|---|
| `CannotPullContainerError` | No image with that tag in ECR yet (step 4 has not finished). |
| `ResourceInitializationError ... secret` | The execution role cannot read one of the five secrets (`infra/iam.tf`). |
| Exits during startup | Read the log: usually a bad datasource URL, Flyway, or a missing env var. |
| `Task failed ELB health checks` right as it finishes starting | The health grace period (180s) was shorter than the boot; check the log for the real start time. |

## 6. Vendor API keys (optional this session)

The two vendor secrets start with the placeholder `REPLACE_ME`, so the app starts either way. Weather and odds only matter for upcoming games, and this session's data is the 2025 season, so you can **skip this step**. If you want to check the wiring, set them, typing each key when prompted (it is not echoed and does not enter your shell history):

```bash
read -rsp "OpenWeatherMap key: " KEY; echo; aws secretsmanager put-secret-value --secret-id fantasyiq/openweather-api-key --secret-string "$KEY"; unset KEY
read -rsp "The Odds API key: " KEY; echo; aws secretsmanager put-secret-value --secret-id fantasyiq/odds-api-key --secret-string "$KEY"; unset KEY
```

The task only reads secrets when it starts, so restart it afterwards:

```bash
aws ecs update-service --cluster fantasyiq --service fantasyiq --force-new-deployment >/dev/null
aws ecs wait services-stable --cluster fantasyiq --services fantasyiq
```

## 7. Check the front door (CloudFront)

Set the address once:

```bash
URL=$(terraform output -raw frontend_url)
echo "$URL"
```

Open it in a browser. Then check each of these:

1. **The sign-in page loads over HTTPS** with the right fonts and no console errors.
2. **A reload on a deep link works.** Go to `$URL/compare` and `$URL/players/00000000-0000-0000-0000-000000000000` directly. You should get the app (the second one shows "We couldn't find that player"), not an XML or 403 error page. (Signed-out, they will redirect to the sign-in page, which is also fine.)
3. **The API is a real API, not the HTML page.** A missing or bad route under `/api` must answer with JSON, because the SPA fallback only applies to the app's own paths:

   ```bash
   curl -si "$URL/api/players/00000000-0000-0000-0000-000000000000" | head -15
   ```

   Expect `401` with a JSON body (no token). If you see `200` and HTML, the fallback is leaking into `/api` and the CloudFront behavior is wrong.
4. **Cache headers:**

   ```bash
   curl -sI "$URL/" | grep -i -E "^(HTTP|cache-control|x-cache|strict-transport)"
   ASSET=$(curl -s "$URL/" | grep -o 'assets/[^"]*\.js' | head -1)
   curl -sI "$URL/$ASSET" | grep -i -E "^(HTTP|cache-control)"
   ```

   `index.html` should say `no-cache`; the file under `assets/` should say `public,max-age=31536000,immutable`. The response should also carry `strict-transport-security` (the security headers policy).
5. **The load balancer is closed to the world** (slice 6c). Find its address and try to reach it directly:

   ```bash
   ALB=$(aws elbv2 describe-load-balancers --names fantasyiq-alb --query "LoadBalancers[0].DNSName" --output text)
   curl -m 10 -si "http://$ALB/api/auth/login" | head -3
   ```

   It should **time out** (the security group drops anything that is not CloudFront). If it answers with `403 Forbidden`, the security group is open but the secret-header rule is working. If it answers with anything else, the lockdown has failed: stop and fix it before going on.

## 8. Sign in as the admin and load data

The admin password is generated by Terraform and changes with every apply:

```bash
PASSWORD=$(aws secretsmanager get-secret-value --secret-id "$(terraform output -raw admin_password_secret_name)" --query SecretString --output text)
EMAIL=$(terraform output -raw admin_email)
TOKEN=$(curl -s -X POST "$URL/api/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
echo "${TOKEN:0:20}..."
```

Getting a token through CloudFront proves the whole path: browser, CloudFront, secret header, ALB, task, RDS. The access token lasts 15 minutes; repeat the `TOKEN=` line when it expires.

A fresh database is empty. Load the 2025 season, in this order (each depends on the one before):

```bash
post() { curl -s -m 600 -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $TOKEN" "$URL$1"; }
post "/api/players/ingest"
post "/api/games/ingest?season=2025"
post "/api/injuries/ingest"
```

Then the long one:

```bash
post "/api/player-game-stats/ingest?season=2025"
```

**Expect this one to answer `504`.** It makes hundreds of ESPN calls and takes minutes, but CloudFront gives up on an origin after 60 seconds (and the load balancer's idle timeout is 60 seconds too). The job itself keeps running on the server and writes its audit row either way. **Do not run it again.** Watch it finish:

```bash
aws logs tail /ecs/fantasyiq --since 30m --follow --filter-pattern "Ingestion run"
```

Wait for `Ingestion run succeeded: source=...`, then continue:

```bash
for week in $(seq 1 18); do post "/api/defense-vs-position/compute?season=2025&week=$week"; done
post "/api/recommendations/generate?season=2025&week=10"
```

If `players/ingest` or any ESPN step fails with `503`: **ESPN's edge sometimes blocks cloud IP addresses.** The app has only ever called ESPN from a home network. If the logs show `EspnUnavailableException` with a 403 from ESPN, the deployed app cannot ingest on its own and the rest of this step is blocked. That is a real finding, not a bug in this session: write it down, skip to step 9 with whatever loaded, and decide afterwards how to get data in (a dump restored from the local database, or a proxy).

### Check the logging (PR #29)

Find the ingestion runs in the logs and confirm the correlation id ties lines together. In the CloudWatch console, open **Logs Insights**, choose the log group `/ecs/fantasyiq`, and run:

```
fields @timestamp, correlationId, message
| filter message like /Ingestion run/
| sort @timestamp desc
| limit 20
```

Copy one `correlationId` and run:

```
fields @timestamp, message
| filter correlationId = "<that id>"
| sort @timestamp asc
```

You should see that run's start and finish lines. This is what `docs/runbooks/injury-ingestion-failure.md` relies on.

### Check the rate limit per visitor (slice 6b)

Login attempts are limited to 10 a minute per visitor. From your machine, make 11 bad attempts:

```bash
for i in $(seq 1 11); do curl -s -o /dev/null -w "%{http_code} " -X POST "$URL/api/auth/login" -H 'Content-Type: application/json' -d '{"email":"nobody@example.com","password":"wrong-password"}'; done; echo
```

Expect ten `401`s and then a `429`. Then, from a **different network** (your phone on mobile data, signing in at `$URL`), signing in should still work. If it is also rate-limited, every visitor shares one bucket and the forwarded-address fix is not working (check `fantasyiq.rate-limit.auth.forwarded-hops` and the `X-Forwarded-For` the app receives). Wait a minute before carrying on, so your own login works again.

## 9. Use the app

In the browser, signed in as the admin (or register a normal account, which also exercises sign-up):

- **Rankings** for 2025 week 10: the list loads, position tabs work, the factor bars draw.
- **A player page** (click a row): the breakdown and the arithmetic add up.
- **Compare**: pick two quarterbacks.
- **Trade**: add a player to each side and analyze. Note how long it takes on the small Fargate task; locally it is about 3 seconds.
- **Sign out and back in**, and **reload while signed in** (this exercises the refresh token through CloudFront).
- **A normal (non-admin) account**: `POST /api/players/ingest` with its token must return `403`.

Note anything slow or broken; do not fix it during the session.

## 10. Look at the monitoring

- Open `terraform output -raw dashboard_url`: the ALB, ECS and RDS widgets should show data from this session.
- In CloudWatch **Alarms**, `fantasyiq-alb-no-healthy-hosts` should be back to `OK`, and the others should not be in `ALARM`.
- The CloudFront distribution's own metrics (requests, error rates) are in the CloudFront console under the distribution's **Monitoring** tab.

## 11. Record what happened

Before destroying, write down, for each numbered check above, whether it passed, plus anything surprising. The destroy removes the evidence. Then update `CURRENT_WORK.md`: strike the checks that passed from "Pending: first apply of PR #29", and turn any failure into an item under "What remains".

## 12. Destroy

```bash
cd infra
terraform destroy
```

Type `yes` when asked. This takes 15-25 minutes: CloudFront has to be disabled before it can be deleted (10+ minutes), and the internet gateway waits for the Fargate task's network interface to go away (see CLAUDE.md). It is not stuck, so leave it alone.

Afterwards:

```bash
terraform state list
```

should print nothing. Then confirm in the console that nothing billable is left: no RDS instances, ElastiCache clusters, load balancers, NAT gateways, CloudFront distributions or S3 buckets named `fantasyiq-*`. The ECR repository and the secrets are removed by the destroy (`force_delete` and zero recovery window). CloudWatch log groups are removed as well.

If the destroy fails part-way, run `terraform destroy` again. If a resource is stuck, find it in the console and delete it by hand, then run it once more so the state agrees.

After this, the **Deploy workflow shows a red X on every push to `main`** until the next apply (the role it assumes no longer exists). That is expected.

## Quick reference

| What | Value |
|---|---|
| Region | `us-east-1` |
| ECS cluster / service | `fantasyiq` / `fantasyiq` |
| Log group | `/ecs/fantasyiq` |
| ALB | `fantasyiq-alb` |
| Frontend bucket | `terraform output -raw frontend_bucket` |
| Secrets | `fantasyiq/jwt-secret`, `fantasyiq/admin-password`, `fantasyiq/openweather-api-key`, `fantasyiq/odds-api-key` |
| Deploy role | `fantasyiq-github-deploy` |
| Alarms | `fantasyiq-alb-5xx`, `-alb-unhealthy-hosts`, `-alb-no-healthy-hosts`, `-ecs-cpu-high`, `-rds-cpu-high` |
