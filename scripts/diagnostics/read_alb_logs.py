#!/usr/bin/env python3
"""Downloads the load balancer's access logs from S3 and prints the slow or failed requests.

    python3 scripts/diagnostics/read_alb_logs.py <diagnostics bucket> [--slow 1.0]

The bucket is `terraform output -raw diagnostics_bucket`. Needs the AWS CLI signed in. The load
balancer writes a file per node every five minutes, so logs for the last few minutes may not be
there yet.

Fields used (positions per AWS's access log syntax, 1-based in their table):
  request_processing_time  time from the load balancer receiving the request until it sent it to the
                           target. -1 means it could not reach a target, including when a TCP connection
                           was not established within its 10-second timeout.
  target_processing_time   time from sending the request until the target began its response (the app)
  response_processing_time time to start sending the response to the client
  request_creation_time    when the load balancer received the request from the client (CloudFront)

A stall with a long request_processing_time is between the load balancer and the app. A request whose
request_creation_time is late compared with CloudFront's own record of when it received it lost its time
before the load balancer.
"""
import argparse
import gzip
import os
import shlex
import subprocess
import sys
import tempfile

# 0-based indexes into a space-delimited, quote-aware split of one log line.
TIME, CLIENT, TARGET = 1, 3, 4
REQUEST_PROCESSING, TARGET_PROCESSING, RESPONSE_PROCESSING = 5, 6, 7
ELB_STATUS, TARGET_STATUS = 8, 9
REQUEST_LINE = 12
RULE_PRIORITY, REQUEST_CREATION = 20, 21
ERROR_REASON = 24


def number(value):
    try:
        return float(value)
    except ValueError:
        return 0.0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("bucket")
    parser.add_argument("--slow", type=float, default=1.0, help="seconds")
    args = parser.parse_args()

    with tempfile.TemporaryDirectory() as folder:
        sync = subprocess.run(
            ["aws", "s3", "sync", f"s3://{args.bucket}/alb/", folder, "--quiet"], capture_output=True, text=True
        )
        if sync.returncode != 0:
            sys.exit(f"download failed: {sync.stderr.strip()}")

        entries = []
        for root, _, files in os.walk(folder):
            for name in files:
                if not name.endswith(".log.gz"):
                    continue
                with gzip.open(os.path.join(root, name), "rt", errors="replace") as handle:
                    for line in handle:
                        try:
                            fields = shlex.split(line)
                        except ValueError:
                            continue
                        if len(fields) > REQUEST_CREATION:
                            entries.append(fields)

    entries.sort(key=lambda f: f[TIME])
    flagged = []
    for f in entries:
        times = [number(f[REQUEST_PROCESSING]), number(f[TARGET_PROCESSING]), number(f[RESPONSE_PROCESSING])]
        status = f[ELB_STATUS]
        if max(times) > args.slow or min(times) < 0 or status.startswith("5"):
            flagged.append(f)

    print(f"{len(entries)} requests logged, {len(flagged)} slow, failed or unreachable (over {args.slow:g}s)\n")
    print(f"{'received (creation time)':<28}{'req':>8}{'target':>8}{'resp':>8}  {'elb':<4}{'tgt':<4}{'rule':<5} request")
    for f in flagged:
        print(
            f"{f[REQUEST_CREATION]:<28}{f[REQUEST_PROCESSING]:>8}{f[TARGET_PROCESSING]:>8}{f[RESPONSE_PROCESSING]:>8}  "
            f"{f[ELB_STATUS]:<4}{f[TARGET_STATUS]:<4}{f[RULE_PRIORITY]:<5} {f[REQUEST_LINE]}"
        )


if __name__ == "__main__":
    main()
