#!/usr/bin/env python3
"""Repeatable API verification and performance report."""

from __future__ import annotations

import argparse
import csv
import json
import subprocess
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import requests


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(round(fraction * (len(ordered) - 1))))
    return ordered[index]


def request_once(base_url: str, path: str, sequence: int) -> dict[str, Any]:
    started = time.perf_counter()
    try:
        response = requests.get(base_url.rstrip("/") + path, timeout=15)
        return {
            "sequence": sequence,
            "status": response.status_code,
            "success": 200 <= response.status_code < 300,
            "latency_ms": round((time.perf_counter() - started) * 1000, 3),
            "error": "",
        }
    except requests.RequestException as exc:
        return {
            "sequence": sequence,
            "status": 0,
            "success": False,
            "latency_ms": round((time.perf_counter() - started) * 1000, 3),
            "error": str(exc),
        }


def fetch_state(base_url: str, slot_id: int) -> dict[str, int]:
    response = requests.get(base_url.rstrip("/") + f"/api/dev/state/{slot_id}", timeout=15)
    response.raise_for_status()
    data = response.json()
    message = data.get("messageSummary") or {}
    consume = data.get("consumeSummary") or {}
    return {
        "message_log_count": int(message.get("messageLogCount") or 0),
        "sent_message_count": int(message.get("sentMessageCount") or 0),
        "consume_log_count": int(consume.get("consumeLogCount") or 0),
    }


def fetch_message_metrics(container: str, database: str, user: str, password: str) -> dict[str, float]:
    query = (
        "SELECT "
        "(SELECT COUNT(*) FROM message_log WHERE status='SENT'),"
        "(SELECT COUNT(*) FROM consume_log WHERE status='SUCCESS' AND consumer_group='booking-success-consumer'),"
        "(SELECT COUNT(*) FROM dead_letter_log),"
        "COALESCE((SELECT metric_value FROM consumer_metric WHERE metric_key='consumer.duplicate_consumption'),0),"
        "(SELECT COALESCE(AVG(TIMESTAMPDIFF(MICROSECOND,created_at,replayed_at))/1000,0) FROM dead_letter_log WHERE status='REPLAYED' AND replayed_at IS NOT NULL)"
    )
    command = ["docker", "exec", container, "mysql", "-N", "-B", f"-u{user}", f"-p{password}", "-D", database, "-e", query]
    completed = subprocess.run(command, capture_output=True, text=True, timeout=15, check=True)
    output_lines = [line for line in completed.stdout.splitlines() if line.strip()]
    values = output_lines[-1].split("\t")
    if len(values) != 5:
        raise RuntimeError("unexpected MySQL metrics output")
    return {
        "sent_message_count": float(values[0]),
        "consumed_message_count": float(values[1]),
        "dlt_count": float(values[2]),
        "duplicate_consumption_count": float(values[3]),
        "replay_recovery_ms": float(values[4]),
    }


def write_chart(results: list[dict[str, Any]], output: Path) -> None:
    try:
        import matplotlib.pyplot as plt
    except ImportError:
        return
    values = [float(item["latency_ms"]) for item in results]
    plt.figure(figsize=(10, 4.5))
    plt.plot(range(1, len(values) + 1), values, linewidth=1)
    plt.title("Request latency")
    plt.xlabel("Request")
    plt.ylabel("Milliseconds")
    plt.grid(alpha=0.25)
    plt.tight_layout()
    plt.savefig(output, dpi=140)
    plt.close()


def write_report(output: Path, config: dict[str, Any], metrics: dict[str, Any]) -> None:
    lines = [
        "# 可重复验收与性能报告",
        "",
        "- 运行时间: " + metrics["started_at"],
        "- 请求地址: " + config["base_url"] + config["path"],
        "- 请求数: " + str(config["requests"]),
        "- 并发数: " + str(config["concurrency"]),
        "",
        "## 性能指标",
        "",
        "| 指标 | 结果 |",
        "|---|---:|",
        "| TPS | %.2f |" % metrics["tps"],
        "| P50 | %.2f ms |" % metrics["p50_ms"],
        "| P95 | %.2f ms |" % metrics["p95_ms"],
        "| P99 | %.2f ms |" % metrics["p99_ms"],
        "| 成功率 | %.2f%% |" % metrics["success_rate"],
        "| 错误数 | %d |" % metrics["error_count"],
        "",
        "## 消息指标",
        "",
        "| 指标 | 结果 |",
        "|---|---:|",
        "| 消息成功率（当前总览） | " + str(metrics["message_success_rate"]) + " |",
        "| 重复消费次数 | " + str(metrics["duplicate_consumption_count"]) + " |",
        "| DLT 数量（本轮增量） | " + str(metrics["dlt_count"]) + " |",
        "| Replay 平均恢复耗时 | " + str(metrics["replay_recovery_ms"]) + " ms |",
        "",
        "原始数据: metrics.json、requests.csv；图表: latency.png。",
        "消息成功率和 Replay 耗时为数据库当前总览；DLT 和重复消费次数同时记录本轮增量。",
    ]
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run a repeatable API verification report.")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--path", default="/api/slots")
    parser.add_argument("--requests", type=int, default=100)
    parser.add_argument("--concurrency", type=int, default=20)
    parser.add_argument("--slot-id", type=int)
    parser.add_argument("--output-root", default="test-results")
    parser.add_argument("--mysql-container", default="ai-booking-mysql")
    parser.add_argument("--mysql-database", default="wrx_booking")
    parser.add_argument("--mysql-user", default="root")
    parser.add_argument("--mysql-password", default="root")
    args = parser.parse_args()

    if args.requests < 1 or args.concurrency < 1:
        parser.error("requests and concurrency must be positive")

    output_dir = Path(args.output_root) / datetime.now().strftime("%Y%m%d-%H%M%S")
    output_dir.mkdir(parents=True, exist_ok=True)
    before = fetch_state(args.base_url, args.slot_id) if args.slot_id else {}
    before_messages = fetch_message_metrics(args.mysql_container, args.mysql_database, args.mysql_user, args.mysql_password)

    started_at = datetime.now(timezone.utc).isoformat()
    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [
            executor.submit(request_once, args.base_url, args.path, sequence)
            for sequence in range(args.requests)
        ]
        results = [future.result() for future in as_completed(futures)]
    elapsed = time.perf_counter() - started
    results.sort(key=lambda item: item["sequence"])

    after = fetch_state(args.base_url, args.slot_id) if args.slot_id else {}
    after_messages = fetch_message_metrics(args.mysql_container, args.mysql_database, args.mysql_user, args.mysql_password)
    latencies = [float(item["latency_ms"]) for item in results]
    success_count = sum(1 for item in results if item["success"])
    metrics: dict[str, Any] = {
        "started_at": started_at,
        "elapsed_seconds": elapsed,
        "requests": len(results),
        "concurrency": args.concurrency,
        "tps": len(results) / elapsed if elapsed else 0.0,
        "success_count": success_count,
        "error_count": len(results) - success_count,
        "success_rate": success_count * 100 / len(results),
        "p50_ms": percentile(latencies, 0.50),
        "p95_ms": percentile(latencies, 0.95),
        "p99_ms": percentile(latencies, 0.99),
        "message_success_rate": "not-collected",
        "duplicate_consumption_count": max(0, after_messages["duplicate_consumption_count"] - before_messages["duplicate_consumption_count"]),
        "dlt_count": max(0, after_messages["dlt_count"] - before_messages["dlt_count"]),
        "replay_recovery_ms": after_messages["replay_recovery_ms"],
        "before_messages": before_messages,
        "after_messages": after_messages,
        "before_state": before,
        "after_state": after,
    }
    sent = after_messages["sent_message_count"] - before_messages["sent_message_count"]
    consumed = after_messages["consumed_message_count"] - before_messages["consumed_message_count"]
    total_sent = after_messages["sent_message_count"]
    total_consumed = after_messages["consumed_message_count"]
    metrics["message_success_rate"] = "%.2f%%" % (total_consumed * 100 / total_sent) if total_sent else "not-collected"

    (output_dir / "metrics.json").write_text(
        json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    with (output_dir / "requests.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=["sequence", "status", "success", "latency_ms", "error"])
        writer.writeheader()
        writer.writerows(results)
    write_chart(results, output_dir / "latency.png")
    write_report(output_dir / "report.md", {
        "base_url": args.base_url.rstrip("/"),
        "path": args.path,
        "requests": args.requests,
        "concurrency": args.concurrency,
    }, metrics)
    print(json.dumps({"output_dir": str(output_dir), **metrics}, ensure_ascii=False, indent=2))
    return 0 if metrics["error_count"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
