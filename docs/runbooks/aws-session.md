# Runbook: an AWS session (apply, verify, destroy)

The stack costs money while it exists, so every session follows the same shape: **apply, check that it works, destroy**. This session is the first time three sets of changes meet real AWS:

- **PR #29** (the `aws` profile, the admin account, the no-healthy-hosts alarm, correlation-id logging): verified locally only.
- **The hosting work** (S3, CloudFront, the frontend deploy job): verified with `terraform validate` only.
- **The rate-limit fix and the ALB lockdown** (slices 6b and 6c): same.

Plan for about two hours, most of it waiting on RDS, ElastiCache and CloudFront. While the stack is up it costs roughly 10-15 cents an hour (RDS, ElastiCache, the ALB and a small Fargate task; S3 and CloudFront are close to free at this size), so a long session is cheap and a forgotten stack is the real cost: **do the destroy at the end (step 12) even if something failed.**

Commands are for a bash shell (Git Bash is fine) unless stated. Run Terraform from `infra/`. **On Windows PowerShell use the equivalents in the appendix at the end:** several bash commands do not work there (`read -rsp`, `$(...)` inside strings, and `-out=session.plan` is split at the dot unless it is quoted).

## 0. Before you start

You need, on your machine:

- `terraform` (>= 1.6), the AWS CLI, `curl`, `docker` (only if you push an image by hand)
- AWS credentials for the account (`aws sts get-caller-identity` should print account `018326344181`)
- the two vendor API keys (OpenWeatherMap and The Odds API; see step 6). **You type them yourself, in your own shell. They never go into this repo or a chat.** The Odds API's free tier is 500 requests a month and this session spends one.
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

**Check that the confirmation really happened.** In session 1 the confirmation email never arrived, so both alarm actions sat at "Pending confirmation" for the whole session and no alarm could have notified anyone; it only showed up at the end. Check now:

```bash
aws sns list-subscriptions-by-topic --topic-arn $(aws sns list-topics --query "Topics[?contains(TopicArn,'fantasyiq-alerts')].TopicArn | [0]" --output text)
```

`SubscriptionArn` should be a real ARN, not `PendingConfirmation`. If it is still pending: search the inbox and spam for `sns.amazonaws.com`; check that `Endpoint` is the right address (a typo in `terraform.tfvars` is the likeliest cause); or open the SNS console, select the subscription and choose **Request confirmation** to have the email sent again.

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

## 6. Vendor API keys

Two of the six scoring factors come from outside APIs that need a key: **weather** (OpenWeatherMap) and the **Vegas implied total** (The Odds API). ESPN, which supplies everything else, needs none. The secrets start with the placeholder `REPLACE_ME`, so the app starts either way, but with the placeholder those two ingestion jobs fail and the factors are simply missing from every score.

This session loads the **current** week (step 8), where both factors apply: weather forecasts only exist for games in the next ~5 days, and betting lines are only on the board for upcoming games. So set both keys, typing each when prompted (it is not echoed and does not enter your shell history):

```bash
read -rsp "OpenWeatherMap key: " KEY; echo; aws secretsmanager put-secret-value --secret-id fantasyiq/openweather-api-key --secret-string "$KEY"; unset KEY
read -rsp "The Odds API key: " KEY; echo; aws secretsmanager put-secret-value --secret-id fantasyiq/odds-api-key --secret-string "$KEY"; unset KEY
```

The task only reads secrets when it starts, so restart it afterwards:

```bash
aws ecs update-service --cluster fantasyiq --service fantasyiq --force-new-deployment >/dev/null
aws ecs wait services-stable --cluster fantasyiq --services fantasyiq
```

**The keys must be in place before the task starts.** ECS passes secrets to the container only at startup. If the keys are stored after the task is already running, the app has empty keys, the vendors answer `401`, and weather and odds return `503` (session 2 hit this). Either store the keys before the deploy, or restart the task once afterwards:

```bash
aws ecs update-service --cluster fantasyiq --service fantasyiq --force-new-deployment > /dev/null
aws ecs wait services-stable --cluster fantasyiq --services fantasyiq
```

In Git Bash, prefix `aws logs tail /ecs/...` with `MSYS_NO_PATHCONV=1`, or the leading `/` is rewritten into a Windows path.

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

A fresh database is empty. Load the **current season** (2026 at the time of writing; the week the app should open on is the one `GET /api/weeks/current` reports, 7 when this was written). Substitute the real season and week below if they have moved on. Each step depends on the one before:

```bash
SEASON=2026
WEEK=7
post() { curl -s -m 600 -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $TOKEN" "$URL$1"; }
post "/api/players/ingest"
post "/api/games/ingest?season=$SEASON"
post "/api/injuries/ingest"
```

Then the long one, box scores for every player, for the weeks already played:

```bash
post "/api/player-game-stats/ingest?season=$SEASON"
```

**This is the slowest job, but in session 1 it finished well inside the limit and answered `200`.** Measured: players 2,553 records in 42 s, games 272 in 14 s, injuries 460 in 9.5 s, box scores 2,382 in 39.5 s (six played weeks; it grows as the season goes on). CloudFront gives up on an origin after 60 seconds (and the load balancer's idle timeout is 60 seconds too), so a slower run answers `504` while the job keeps running on the server and writes its audit row either way. **If you see a `504`, do not run it again.** Watch it finish:

```bash
aws logs tail /ecs/fantasyiq --since 30m --follow --filter-pattern "Ingestion run"
```

Wait for `Ingestion run succeeded: source=...`, then compute how each defense has fared against each position, for the weeks before the current one, and bring in the forecasts and betting lines for the current week:

```bash
for week in $(seq 1 $((WEEK - 1))); do post "/api/defense-vs-position/compute?season=$SEASON&week=$week"; done
post "/api/weather/ingest?season=$SEASON&week=$WEEK"
post "/api/odds/ingest"
post "/api/recommendations/generate?season=$SEASON&week=$WEEK"
```

Weather and odds have no scheduled job, so they are only ever loaded by hand like this (a known gap in `CURRENT_WORK.md`). Games in domes get no forecast, by design. If either returns `503`, the key is wrong or the vendor is unavailable; the log line says which.

If `players/ingest` or any ESPN step fails with `503`, get the real cause before doing anything else. A failed run logs `rootCause=...` and the full stack trace:

```bash
aws logs tail /ecs/fantasyiq --since 10m --filter-pattern "rootCause"
```

**Session 1 hit exactly this.** Every ESPN job failed with `403 Forbidden: Access Denied` (an Akamai error page), because with no `User-Agent` configured the JDK's older HTTP class announces itself as `Java/21.x`, and ESPN's edge refuses that name from cloud addresses (it never did from a home network). It is fixed: the app now sends `FantasyIQ/1.0` on every vendor call (`fantasyiq.http-client.user-agent`). To tell a name-based block from a block on all AWS addresses, open **AWS CloudShell** (it runs on an AWS address) and try different names. In session 1, `Java/21.0.5` got `403` while `Java-http-client/21.0.5`, `FantasyIQ/1.0` and `curl` all got `200`:

```bash
for ua in "Java-http-client/21.0.5" "Java/21.0.5" "FantasyIQ/1.0" "curl/8.5.0"; do echo -n "$ua  ->  "; curl -s -o /dev/null -w "%{http_code}\n" -m 15 -A "$ua" "https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams"; done
```

If a plain `curl` from CloudShell also gets `403`, ESPN is blocking AWS addresses outright: write it down, skip to step 9 with whatever loaded, and decide afterwards how to get data in (a dump restored from the local database, or a proxy). Do not keep re-running a failing job: after enough failures the circuit breaker opens and refuses calls for 30 seconds.

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

- **Rankings**: with no week in the address it should **open on the current week** (this is the first live proof of `/api/weeks/current`). The list loads, position tabs work, the factor bars draw.
- **A player page** (click a row): the breakdown and the arithmetic add up. Open one for an outdoor game and check that **weather** and **Vegas implied total** both appear among the factors (and that a player in a dome has no weather row), which proves the two vendor keys work end to end.
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

## Appendix: finding where a slow request is slow

From session 2, for an intermittent stall on `/api/*` through CloudFront. Everything below needs no token unless stated.

**1. Which phase is slow.** `curl` can split a request into its phases. A slow `connect` or `tls` is the path to CloudFront; a long `firstbyte` with a fast `connect` and `tls` is CloudFront waiting on its origin:

```bash
curl -s -o /dev/null -w "dns %{time_namelookup}s  connect %{time_connect}s  tls %{time_appconnect}s  firstbyte %{time_starttransfer}s  total %{time_total}s\n" "$URL/api/players/search?q=a"
```

**2. A soak, to catch a rare stall.** One request every 2-3 seconds, printing anything over a second (a `401` is fine; it is the timing that counts, and it uses neither Redis nor the database):

```bash
for i in $(seq 1 150); do
  for u in "/" "/api/players/search?q=a"; do
    curl -s -o /dev/null -w "$(date +%T)  $u  %{http_code}  ttfb %{time_starttransfer}  total %{time_total}\n" "$URL$u"
  done
  sleep 2
done | tee soak.txt
awk '{ if ($NF+0 > 1) print }' soak.txt
```

`/` comes from S3 and never touches the app. If it never stalls and the API path does, the problem is on the API origin's path, not the client or CloudFront's edge.

**3. Take the app out of the path.** A temporary listener rule makes the load balancer answer a path itself. Compare `/api/pingtest` with the app path in the same soak. Undo it afterwards, and note that it is a change outside Terraform:

```bash
ARN=$(aws elbv2 describe-load-balancers --names fantasyiq-alb --query "LoadBalancers[0].LoadBalancerArn" --output text)
LISTENER=$(aws elbv2 describe-listeners --load-balancer-arn $ARN --query "Listeners[0].ListenerArn" --output text)
RULE=$(aws elbv2 describe-rules --listener-arn $LISTENER --query "Rules[?Priority=='1'].RuleArn | [0]" --output text)
aws elbv2 set-rule-priorities --rule-priorities RuleArn=$RULE,Priority=5
NEWRULE=$(MSYS_NO_PATHCONV=1 aws elbv2 create-rule --listener-arn $LISTENER --priority 1 --conditions Field=path-pattern,Values=/api/pingtest --actions "Type=fixed-response,FixedResponseConfig={StatusCode=200,ContentType=text/plain,MessageBody=pong}" --query "Rules[0].RuleArn" --output text)
# ... soak both paths ...
aws elbv2 delete-rule --rule-arn $NEWRULE
aws elbv2 set-rule-priorities --rule-priorities RuleArn=$RULE,Priority=1
```

**4. What the load balancer saw.** Per-minute maximum app response time and request count (the table's times are in your local zone). A stall with a tiny `TargetResponseTime` means the app was fast and the time was lost before or around it:

```bash
D=${ARN#*loadbalancer/}
aws cloudwatch get-metric-statistics --namespace AWS/ApplicationELB --metric-name TargetResponseTime --dimensions Name=LoadBalancer,Value=$D --start-time $(date -u -d '-30 min' +%FT%TZ) --end-time $(date -u +%FT%TZ) --period 60 --statistics Maximum --output table
```

For the ECS task, the same with `--namespace AWS/ECS --metric-name CPUUtilization` (or `MemoryUtilization`) and `--dimensions Name=ClusterName,Value=fantasyiq Name=ServiceName,Value=fantasyiq`.

**5. Next, not yet done:** turn on CloudFront standard logs and ALB access logs (the ALB log splits `request_processing_time`, `target_processing_time` and `response_processing_time` per request), and, as an experiment, open the ALB security group's port 80 to `0.0.0.0/0` for a soak to see whether some CloudFront connections are being dropped by the prefix-list rule (the listener's secret-header rule still protects it; close it again afterwards).

## Appendix: Windows PowerShell equivalents

These are the commands as they were actually run in session 1. Use `curl.exe`, not `curl`: in PowerShell `curl` is a different command.

Plan, with the argument quoted (otherwise it is split at the dot):

```powershell
terraform plan "-out=session.plan"
```

Variables used by the later steps:

```powershell
$URL = terraform output -raw frontend_url
```

```powershell
$password = aws secretsmanager get-secret-value --secret-id fantasyiq/admin-password --query SecretString --output text
```

Vendor keys, typed at a hidden prompt (repeat with `fantasyiq/odds-api-key` for the second key):

```powershell
$s = Read-Host "OpenWeatherMap key" -AsSecureString; $k = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)); aws secretsmanager put-secret-value --secret-id fantasyiq/openweather-api-key --secret-string $k | Out-Null; Remove-Variable k,s
```

Restart the task so it reads the new secrets, then wait:

```powershell
aws ecs update-service --cluster fantasyiq --service fantasyiq --force-new-deployment | Out-Null
```

```powershell
aws ecs wait services-stable --cluster fantasyiq --services fantasyiq
```

The log lines that prove the backend is healthy (step 5), and a count of SQL statements that should be 0:

```powershell
aws logs tail /ecs/fantasyiq --since 30m | Select-String "Started FantasyIq|Created admin|migrat|Flyway|Successfully applied"
```

```powershell
aws logs tail /ecs/fantasyiq --since 30m | Select-String "org.hibernate.SQL" | Measure-Object
```

Front-door checks (step 7):

```powershell
curl.exe -si "$URL/api/players/00000000-0000-0000-0000-000000000000" | Select-Object -First 12
```

```powershell
curl.exe -sI "$URL/" | Select-String -Pattern "^(HTTP|cache-control|x-cache|strict-transport)"
```

```powershell
$asset = (curl.exe -s "$URL/" | Select-String -Pattern 'assets/[^"]*\.js').Matches[0].Value; curl.exe -sI "$URL/$asset" | Select-String -Pattern "^(HTTP|cache-control)"
```

The closed load balancer: this should time out, and the last line should print `28`:

```powershell
$alb = aws elbv2 describe-load-balancers --names fantasyiq-alb --query "LoadBalancers[0].DNSName" --output text; curl.exe -m 10 -si "http://$alb/api/auth/login"; $LASTEXITCODE
```

Sign in as the admin, and a helper that posts to an endpoint and prints only the status code (step 8). The token lasts 15 minutes; run the first line again when it expires:

```powershell
$body = @{ email = "admin@fantasyiq.dev"; password = $password } | ConvertTo-Json; $token = (Invoke-RestMethod -Method Post -Uri "$URL/api/auth/login" -ContentType "application/json" -Body $body).accessToken; $token.Substring(0,20)
```

```powershell
function Post($path) { try { (Invoke-WebRequest -Method Post -Uri "$URL$path" -Headers @{Authorization="Bearer $token"} -TimeoutSec 600 -UseBasicParsing).StatusCode } catch { if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { $_.Exception.Message } } }; $season = 2026; $week = 7
```

Load the data, in order (each should print `200`), then the computed steps:

```powershell
Post "/api/players/ingest"
```

```powershell
Post "/api/games/ingest?season=$season"
```

```powershell
Post "/api/injuries/ingest"
```

```powershell
Post "/api/player-game-stats/ingest?season=$season"
```

```powershell
1..($week - 1) | ForEach-Object { "week $_ : " + (Post "/api/defense-vs-position/compute?season=$season&week=$_") }
```

```powershell
Post "/api/weather/ingest?season=$season&week=$week"
```

```powershell
Post "/api/odds/ingest"
```

```powershell
Post "/api/recommendations/generate?season=$season&week=$week"
```

The weeks before the current one can be generated too, so the week picker has something to show (each prints `200`; earlier weeks use today's injury status, so a player who is out now shows as out in every earlier week):

```powershell
1..($week - 1) | ForEach-Object { "week $_ : " + (Post "/api/recommendations/generate?season=$season&week=$_") }
```

Check that the vendor factors reached a score (look for `VEGAS` and `WEATHER` among the factors):

```powershell
(Invoke-RestMethod -Uri "$URL/api/recommendations/start-sit?season=$season&week=$week&position=QB" -Headers @{Authorization="Bearer $token"})[0] | ConvertTo-Json -Depth 5
```

The rate limit: eleven wrong logins, expecting ten `401`s and then a `429`:

```powershell
1..11 | ForEach-Object { try { (Invoke-WebRequest -Method Post -Uri "$URL/api/auth/login" -ContentType "application/json" -Body '{"email":"nobody@example.com","password":"wrong-password"}' -UseBasicParsing).StatusCode } catch { [int]$_.Exception.Response.StatusCode } }
```

A normal account must get `403` from an admin endpoint:

```powershell
$r = Invoke-RestMethod -Method Post -Uri "$URL/api/auth/register" -ContentType "application/json" -Body (@{ email = "viewer@example.com"; password = "throwaway-passphrase-1"; displayName = "Viewer" } | ConvertTo-Json); $viewer = $r.accessToken
```

```powershell
try { Invoke-WebRequest -Method Post -Uri "$URL/api/players/ingest" -Headers @{Authorization="Bearer $viewer"} -UseBasicParsing } catch { [int]$_.Exception.Response.StatusCode }
```
