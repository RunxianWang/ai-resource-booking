#!/usr/bin/env python3
"""基础预约链路验收与并发压测。"""

from __future__ import annotations

import argparse
import csv
import json
import os
import subprocess
import threading
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import requests


THREAD_STATE = threading.local()
TEST_MACHINE_ID = 999002
BOOKING_DB_USERNAME = os.environ.get("BOOKING_DB_USERNAME")
BOOKING_DB_PASSWORD = os.environ.get("BOOKING_DB_PASSWORD")


def http_session() -> requests.Session:
    session = getattr(THREAD_STATE, "session", None)
    if session is None:
        session = requests.Session()
        adapter = requests.adapters.HTTPAdapter(pool_connections=1, pool_maxsize=1, max_retries=0)
        session.mount("http://", adapter)
        THREAD_STATE.session = session
    return session


def percentile(values: list[float], fraction: float) -> float:
    values = sorted(values)
    if not values:
        return 0.0
    index = min(len(values) - 1, round(fraction * (len(values) - 1)))
    return values[index]


def call(method: str, url: str, **kwargs: Any) -> dict[str, Any]:
    response = requests.request(method, url, timeout=30, **kwargs)
    response.raise_for_status()
    return response.json()


def mysql(args: argparse.Namespace, query: str) -> str:
    if not BOOKING_DB_USERNAME or not BOOKING_DB_PASSWORD:
        raise RuntimeError("BOOKING_DB_USERNAME and BOOKING_DB_PASSWORD must be set")
    command = ["docker", "exec", args.mysql_container, "mysql", "-N", "-B", f"-u{BOOKING_DB_USERNAME}", f"-p{BOOKING_DB_PASSWORD}", "-D", args.mysql_database, "-e", query]
    result = subprocess.run(command, capture_output=True, text=True, timeout=30, check=True)
    return result.stdout.strip()


def redis(args: argparse.Namespace, command: str, *values: str) -> str:
    result = subprocess.run(["docker", "exec", "ai-booking-redis", "redis-cli", command, *values], capture_output=True, text=True, timeout=30, check=True)
    return result.stdout.strip()


def prepare_fixture(args: argparse.Namespace) -> None:
    first = datetime.now().replace(minute=0, second=0, microsecond=0) + timedelta(hours=1)
    start_time = first.strftime("%Y-%m-%d %H:%M:%S")
    end_time = (first + timedelta(hours=1)).strftime("%Y-%m-%d %H:%M:%S")
    query = f"""
INSERT IGNORE INTO resource_machine(id, machine_name, resource_type, gpu_model, status)
VALUES ({TEST_MACHINE_ID}, 'AUTOMATED-BASE-CHAIN-TEST', 'GPU', 'TEST', 'ACTIVE');
INSERT INTO resource_slot(id, machine_id, resource_id, resource_name, resource_type,
                          start_time, end_time, total_count, available_count, status)
VALUES ({args.slot_id}, {TEST_MACHINE_ID}, 1, 'BASE-CHAIN-TEST', 'GPU', '{start_time}',
        '{end_time}', {args.capacity}, {args.capacity}, 'AVAILABLE')
ON DUPLICATE KEY UPDATE total_count={args.capacity}, available_count={args.capacity}, status='AVAILABLE';
DELETE c FROM consume_log c
JOIN message_log m ON m.message_key = c.message_key
JOIN booking_record b ON b.id = m.booking_id
WHERE b.slot_id = {args.slot_id};
DELETE m FROM message_log m JOIN booking_record b ON b.id = m.booking_id WHERE b.slot_id = {args.slot_id};
DELETE a FROM booking_event_audit a
JOIN booking_record b ON b.id = a.booking_id
WHERE b.slot_id = {args.slot_id};
DELETE p FROM booking_event_projection p
JOIN booking_record b ON b.id = p.booking_id
WHERE b.slot_id = {args.slot_id};
DELETE d FROM dead_letter_log d
WHERE JSON_VALID(d.payload) = 1
  AND JSON_UNQUOTE(JSON_EXTRACT(d.payload, '$.slotId')) = '{args.slot_id}';
DELETE FROM booking_record WHERE slot_id = {args.slot_id};
UPDATE resource_slot SET total_count={args.capacity}, available_count={args.capacity}, status='AVAILABLE' WHERE id={args.slot_id};
"""
    mysql(args, query)
    call("POST", f"{args.base_url}/api/slots/{args.slot_id}/warmup")


def cleanup_fixture(args: argparse.Namespace) -> None:
    query = f"""
DELETE c FROM consume_log c
JOIN message_log m ON m.message_key = c.message_key
JOIN booking_record b ON b.id = m.booking_id
WHERE b.slot_id = {args.slot_id};
DELETE m FROM message_log m JOIN booking_record b ON b.id = m.booking_id WHERE b.slot_id = {args.slot_id};
DELETE a FROM booking_event_audit a
JOIN booking_record b ON b.id = a.booking_id
WHERE b.slot_id = {args.slot_id};
DELETE p FROM booking_event_projection p
JOIN booking_record b ON b.id = p.booking_id
WHERE b.slot_id = {args.slot_id};
DELETE d FROM dead_letter_log d
WHERE JSON_VALID(d.payload) = 1
  AND JSON_UNQUOTE(JSON_EXTRACT(d.payload, '$.slotId')) = '{args.slot_id}';
DELETE FROM booking_record WHERE slot_id = {args.slot_id};
DELETE FROM resource_slot WHERE id = {args.slot_id};
DELETE FROM resource_machine WHERE id = {TEST_MACHINE_ID};
"""
    mysql(args, query)


def state(args: argparse.Namespace) -> dict[str, Any]:
    slot = call("GET", f"{args.base_url}/api/slots/{args.slot_id}")
    verify = call("GET", f"{args.base_url}/api/dev/verify/{args.slot_id}")
    redis_available = redis(args, "GET", f"slot:{args.slot_id}:available")
    redis_users = redis(args, "SCARD", f"slot:{args.slot_id}:booked-users")
    return {"slot": slot, "verify": verify, "mysql_available": slot.get("mysqlAvailableCount"),
            "redis_available": int(redis_available) if redis_available else None,
            "redis_users": int(redis_users or 0)}


def booking(args: argparse.Namespace, user_id: int) -> dict[str, Any]:
    started = time.perf_counter()
    try:
        response = http_session().post(
            f"{args.base_url}/api/bookings",
            headers={"X-Test-User-Id": str(user_id)},
            json={"slotId": args.slot_id},
            timeout=30,
        )
        body = response.json()
        return {"user_id": user_id, "status": response.status_code, "code": body.get("code"),
                "booking_id": body.get("bookingId"), "latency_ms": (time.perf_counter() - started) * 1000, "error": ""}
    except (requests.RequestException, ValueError) as exc:
        return {"user_id": user_id, "status": 0, "code": "SYSTEM_ERROR", "booking_id": None,
                "latency_ms": (time.perf_counter() - started) * 1000, "error": str(exc)}


def run(args: argparse.Namespace) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    prepare_fixture(args)
    before = state(args)
    started_at = datetime.now(timezone.utc).isoformat()
    started = time.perf_counter()
    results: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [pool.submit(booking, args, args.first_user_id + i) for i in range(args.requests)]
        for future in as_completed(futures):
            results.append(future.result())
    elapsed = time.perf_counter() - started
    results.sort(key=lambda item: item["user_id"])
    deadline = time.time() + args.message_wait_seconds
    after = state(args)
    while time.time() < deadline and (
        after["verify"].get("messageConsistent") is not True
        or after["verify"].get("consumedMessageCount") != after["verify"].get("messageLogCount")
    ):
        time.sleep(0.5)
        after = state(args)
    counts: dict[str, int] = {}
    for item in results:
        counts[item["code"]] = counts.get(item["code"], 0) + 1
    latencies = [float(item["latency_ms"]) for item in results]
    booked = counts.get("SUCCESS", 0)
    expected_booked = min(args.capacity, args.requests)
    slot = after["slot"]
    verify = after["verify"]
    metrics: dict[str, Any] = {
        "started_at": started_at, "slot_id": args.slot_id, "capacity": args.capacity,
        "requests": args.requests, "concurrency": args.concurrency, "elapsed_seconds": elapsed,
        "tps": len(results) / elapsed if elapsed else 0.0, "p50_ms": percentile(latencies, 0.50),
        "p95_ms": percentile(latencies, 0.95), "p99_ms": percentile(latencies, 0.99),
        "business_counts": counts, "system_errors": sum(1 for r in results if r["code"] == "SYSTEM_ERROR"),
        "mysql_available": slot.get("mysqlAvailableCount"), "redis_available": after.get("redis_available"),
        "redis_users": after.get("redis_users"),
        "reserved_count": verify.get("successBookingCount"), "stock_consistent": verify.get("stockConsistent"),
        "expected_booked": expected_booked,
        "pass": booked == expected_booked and verify.get("stockConsistent") is True
        and verify.get("messageConsistent") is True
        and verify.get("consumedMessageCount") == verify.get("messageLogCount")
        and metrics_safe(slot, verify, after),
        "before": before, "after": after,
    }
    return metrics, results


def metrics_safe(slot: dict[str, Any], verify: dict[str, Any], after: dict[str, Any]) -> bool:
    available = slot.get("mysqlAvailableCount")
    total = slot.get("totalCount")
    return (isinstance(available, int) and isinstance(total, int) and 0 <= available <= total
            and verify.get("successBookingCount") == total - available
            and after.get("redis_available") == available
            and after.get("redis_users") == verify.get("successBookingCount"))


def write_outputs(args: argparse.Namespace, metrics: dict[str, Any], results: list[dict[str, Any]]) -> Path:
    output = Path(args.output_root) / datetime.now().strftime("%Y%m%d-%H%M%S")
    output.mkdir(parents=True, exist_ok=True)
    (output / "metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")
    with (output / "requests.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=results[0].keys())
        writer.writeheader()
        writer.writerows(results)
    try:
        import matplotlib.pyplot as plt
        plt.figure(figsize=(10, 4.5))
        plt.plot([r["user_id"] for r in results], [r["latency_ms"] for r in results], linewidth=1)
        plt.xlabel("Test user id")
        plt.ylabel("Latency (ms)")
        plt.title("Base-chain booking latency")
        plt.grid(alpha=0.25)
        plt.tight_layout()
        plt.savefig(output / "latency.png", dpi=160)
        plt.close()
    except ImportError:
        pass
    lines = ["# 基础链路验收与压测报告", "", f"- 结论: {'PASS' if metrics['pass'] else 'FAIL'}", f"- 时间: {metrics['started_at']}", f"- 测试 slot: {metrics['slot_id']}", f"- 请求数/并发数: {metrics['requests']} / {metrics['concurrency']}", "", "## 性能", "", f"- TPS: {metrics['tps']:.2f}", f"- P50: {metrics['p50_ms']:.2f} ms", f"- P95: {metrics['p95_ms']:.2f} ms", f"- P99: {metrics['p99_ms']:.2f} ms", "", "## 正确性", "", f"- 业务结果: {json.dumps(metrics['business_counts'], ensure_ascii=False)}", f"- MySQL available: {metrics['mysql_available']}", f"- Redis available: {metrics['redis_available']}", f"- RESERVED booking: {metrics['reserved_count']}", f"- 库存一致性: {metrics['stock_consistent']}", "", "原始数据见 `metrics.json` 和 `requests.csv`；图表见 `latency.png`。"]
    (output / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--slot-id", type=int, default=990001)
    parser.add_argument("--capacity", type=int, default=100)
    parser.add_argument("--requests", type=int, default=500)
    parser.add_argument("--concurrency", type=int, default=100)
    parser.add_argument("--first-user-id", type=int, default=100001)
    parser.add_argument("--mysql-container", default="ai-booking-mysql")
    parser.add_argument("--mysql-database", default="wrx_booking")
    parser.add_argument("--output-root", default="test-results/base-chain")
    parser.add_argument("--message-wait-seconds", type=int, default=30)
    args = parser.parse_args()
    try:
        try:
            metrics, results = run(args)
            output = write_outputs(args, metrics, results)
            print(json.dumps({"output": str(output), **metrics}, ensure_ascii=False, indent=2))
            return 0 if metrics["pass"] else 1
        finally:
            cleanup_fixture(args)
    except (requests.RequestException, subprocess.SubprocessError, AssertionError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
