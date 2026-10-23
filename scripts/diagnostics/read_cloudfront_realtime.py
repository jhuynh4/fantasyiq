#!/usr/bin/env python3
"""Reads the CloudFront real-time log from its Kinesis stream and prints the slow requests.

    python3 scripts/diagnostics/read_cloudfront_realtime.py <stream name> [--minutes 30] [--slow 1.0]

The stream name is `terraform output -raw diagnostics_cloudfront_stream`. Needs the AWS CLI signed in.

Records are tab-separated in the order CloudFront documents the fields, which is the order of the
`fields` list in infra/diagnostics.tf; FIELDS below must stay in step with it.

What to look at in a slow request:
  time-to-first-byte  what the viewer waited for, as CloudFront measured it
  origin-fbl          first-byte latency between CloudFront and the origin (the load balancer)
  x-edge-detailed-result-type
                      OriginConnectError / OriginCommError mean CloudFront itself had trouble
                      reaching the origin

A request with a long time-to-first-byte and a long origin-fbl lost its time on the CloudFront to
load balancer leg or beyond. A long time-to-first-byte with a short origin-fbl lost it before CloudFront
asked the origin at all.
"""
import argparse
import base64
import json
import subprocess
import sys
import time

FIELDS = [
    "timestamp",
    "c-ip",
    "s-ip",
    "time-to-first-byte",
    "sc-status",
    "cs-method",
    "cs-uri-stem",
    "x-edge-location",
    "x-edge-request-id",
    "time-taken",
    "x-edge-detailed-result-type",
    "origin-fbl",
    "origin-lbl",
]


def aws(*args):
    result = subprocess.run(["aws", *args, "--output", "json"], capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"aws {' '.join(args[:2])} failed: {result.stderr.strip()}")
    return json.loads(result.stdout) if result.stdout.strip() else {}


def read_shard(stream, shard_id, since):
    iterator = aws(
        "kinesis", "get-shard-iterator", "--stream-name", stream, "--shard-id", shard_id,
        "--shard-iterator-type", "AT_TIMESTAMP", "--timestamp", str(since),
    )["ShardIterator"]
    rows = []
    while iterator:
        page = aws("kinesis", "get-records", "--shard-iterator", iterator, "--limit", "1000")
        for record in page.get("Records", []):
            text = base64.b64decode(record["Data"]).decode("utf-8", errors="replace")
            for line in text.splitlines():
                values = line.split("\t")
                if len(values) == len(FIELDS):
                    rows.append(dict(zip(FIELDS, values)))
        if page.get("MillisBehindLatest", 0) == 0:
            break
        iterator = page.get("NextShardIterator")
    return rows


def number(value):
    try:
        return float(value)
    except ValueError:
        return 0.0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("stream")
    parser.add_argument("--minutes", type=float, default=30)
    parser.add_argument("--slow", type=float, default=1.0, help="seconds; print requests slower than this")
    args = parser.parse_args()

    since = time.time() - args.minutes * 60
    shards = [s["ShardId"] for s in aws("kinesis", "list-shards", "--stream-name", args.stream)["Shards"]]
    rows = [row for shard in shards for row in read_shard(args.stream, shard, since)]
    slow = [r for r in rows if number(r["time-to-first-byte"]) > args.slow or number(r["origin-fbl"]) > args.slow]

    print(f"{len(rows)} requests in the last {args.minutes:g} minutes, {len(slow)} slower than {args.slow:g}s\n")
    print(f"{'edge':<9}{'status':<7}{'ttfb':>8}{'origin-fbl':>12}{'taken':>8}  {'result':<22}{'request id':<56}uri")
    for r in sorted(slow, key=lambda r: number(r["timestamp"])):
        print(
            f"{r['x-edge-location']:<9}{r['sc-status']:<7}{number(r['time-to-first-byte']):>8.2f}"
            f"{number(r['origin-fbl']):>12.2f}{number(r['time-taken']):>8.2f}  "
            f"{r['x-edge-detailed-result-type']:<22}{r['x-edge-request-id']:<56}{r['cs-uri-stem']}"
        )


if __name__ == "__main__":
    main()
