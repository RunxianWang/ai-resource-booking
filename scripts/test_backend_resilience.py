#!/usr/bin/env python3
"""后端可靠性专项：Redis/Kafka 故障、Outbox、DLT、Replay 和幂等。"""

from __future__ import annotations

import json
import os
import socket
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Callable

import requests


ROOT = Path(__file__).resolve().parents[1]
BASE_URL = "http://localhost:8083"
MACHINE_ID = 999010
SLOTS = list(range(990010, 990016))
MYSQL_CONTAINER = "ai-booking-mysql"
REDIS_CONTAINER = "ai-booking-redis"
KAFKA_CONTAINER = "ai-booking-kafka"
DATABASE = "wrx_booking"
DB_USERNAME = os.environ.get("BOOKING_DB_USERNAME")
DB_PASSWORD = os.environ.get("BOOKING_DB_PASSWORD")


def mysql(query: str) -> list[list[str]]:
    if not DB_USERNAME or not DB_PASSWORD:
        raise RuntimeError("BOOKING_DB_USERNAME and BOOKING_DB_PASSWORD must be set")
    result = subprocess.run(
        ["docker", "exec", MYSQL_CONTAINER, "mysql", "-N", "-B", f"-u{DB_USERNAME}", f"-p{DB_PASSWORD}", "-D", DATABASE, "-e", query],
        capture_output=True, text=True, timeout=30, check=True,
    )
    return [line.split("\t") for line in result.stdout.splitlines() if line.strip()]


def scalar(query: str) -> str:
    rows = mysql(query)
    return rows[-1][0] if rows else ""


def docker(*args: str, input_text: str | None = None) -> str:
    result = subprocess.run(
        ["docker", *args], input=input_text, capture_output=True, text=True, timeout=60, check=True,
    )
    return result.stdout.strip()


def port_open(port: int) -> bool:
    with socket.socket() as sock:
        sock.settimeout(0.5)
        return sock.connect_ex(("127.0.0.1", port)) == 0


def wait_for(predicate: Callable[[], bool], timeout: float = 40, interval: float = 0.5) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if predicate():
            return True
        time.sleep(interval)
    return predicate()


def run_backend(action: str, fault_point: str = "none") -> None:
    subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
         str(ROOT / "scripts" / "start-backend-test-env.ps1"), "-Action", action,
         "-Port", "8083", "-Profile", "perf", "-FaultPoint", fault_point],
        cwd=ROOT, check=True, timeout=240,
    )


def request(method: str, path: str, **kwargs: Any) -> requests.Response:
    kwargs.setdefault("timeout", 30)
    return requests.request(method, BASE_URL + path, **kwargs)


def book(slot_id: int, user_id: int, duration: int = 1, timeout: int = 30) -> dict[str, Any]:
    response = request("POST", "/api/bookings", headers={"X-Test-User-Id": str(user_id)},
                       json={"slotId": slot_id, "durationHours": duration}, timeout=timeout)
    try:
        body = response.json()
    except ValueError:
        body = {"code": "NON_JSON", "body": response.text}
    body["httpStatus"] = response.status_code
    return body


def prepare_fixture() -> None:
    first = datetime.now().replace(minute=0, second=0, microsecond=0) + timedelta(hours=1)
    values = []
    for index, slot_id in enumerate(SLOTS):
        start = first + timedelta(hours=index)
        end = start + timedelta(hours=1)
        values.append(
            f"({slot_id},{MACHINE_ID},1,'RELIABILITY-TEST','GPU','{start:%Y-%m-%d %H:%M:%S}',"
            f"'{end:%Y-%m-%d %H:%M:%S}',2,2,'AVAILABLE')"
        )
    slot_list = ",".join(map(str, SLOTS))
    mysql(f"""
INSERT IGNORE INTO resource_machine(id,machine_name,resource_type,gpu_model,status)
VALUES ({MACHINE_ID},'AUTOMATED-RELIABILITY-TEST','GPU','TEST','ACTIVE');
DELETE c FROM consume_log c JOIN message_log m ON m.message_key=c.message_key
JOIN booking_record b ON b.id=m.booking_id WHERE b.slot_id IN ({slot_list});
DELETE a FROM booking_event_audit a JOIN booking_record b ON b.id=a.booking_id WHERE b.slot_id IN ({slot_list});
DELETE p FROM booking_event_projection p JOIN booking_record b ON b.id=p.booking_id WHERE b.slot_id IN ({slot_list});
DELETE m FROM message_log m JOIN booking_record b ON b.id=m.booking_id WHERE b.slot_id IN ({slot_list});
DELETE FROM booking_record WHERE slot_id IN ({slot_list});
DELETE FROM resource_slot WHERE id IN ({slot_list});
INSERT INTO resource_slot(id,machine_id,resource_id,resource_name,resource_type,start_time,end_time,total_count,available_count,status)
VALUES {','.join(values)};
""")
    for slot_id in SLOTS:
        response = request("POST", f"/api/slots/{slot_id}/warmup")
        response.raise_for_status()


def cleanup_fixture() -> None:
    slot_list = ",".join(map(str, SLOTS))
    mysql(f"""
DELETE c FROM consume_log c JOIN message_log m ON m.message_key=c.message_key
JOIN booking_record b ON b.id=m.booking_id
JOIN resource_slot s ON s.id=b.slot_id WHERE s.machine_id={MACHINE_ID};
DELETE a FROM booking_event_audit a JOIN booking_record b ON b.id=a.booking_id
JOIN resource_slot s ON s.id=b.slot_id WHERE s.machine_id={MACHINE_ID};
DELETE p FROM booking_event_projection p JOIN booking_record b ON b.id=p.booking_id
JOIN resource_slot s ON s.id=b.slot_id WHERE s.machine_id={MACHINE_ID};
DELETE m FROM message_log m JOIN booking_record b ON b.id=m.booking_id
JOIN resource_slot s ON s.id=b.slot_id WHERE s.machine_id={MACHINE_ID};
DELETE FROM dead_letter_log WHERE JSON_VALID(payload)=1
  AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.slotId')) IN
      (SELECT CAST(id AS CHAR) FROM resource_slot WHERE machine_id={MACHINE_ID});
DELETE FROM booking_record WHERE slot_id IN (SELECT id FROM resource_slot WHERE machine_id={MACHINE_ID});
DELETE FROM resource_slot WHERE machine_id={MACHINE_ID};
DELETE FROM resource_machine WHERE id={MACHINE_ID};
""")
    for slot_id in SLOTS:
        docker("exec", REDIS_CONTAINER, "redis-cli", "DEL", f"slot:{slot_id}:available", f"slot:{slot_id}:booked-users")


def wait_message(message_key: str, status: str = "SENT", timeout: float = 40) -> bool:
    return wait_for(lambda: scalar(
        f"SELECT COUNT(*) FROM message_log WHERE message_key='{message_key}' AND status='{status}'"
    ) == "1", timeout)


def test_transaction_and_unique() -> dict[str, Any]:
    slot_a, slot_b = SLOTS[4], SLOTS[5]
    for slot_id in (slot_a, slot_b):
        mysql(f"UPDATE resource_slot SET total_count=1,available_count=1,status='AVAILABLE' WHERE id={slot_id}")
        request("POST", f"/api/slots/{slot_id}/warmup").raise_for_status()
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda user: book(slot_a, user, 2), (950001, 950002)))
    success = [item for item in results if item.get("code") == "SUCCESS"]
    total = int(scalar(f"SELECT COUNT(*) FROM booking_record WHERE slot_id IN ({slot_a},{slot_b}) AND status='RESERVED'"))
    unique_index = scalar("""SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='booking_record' AND index_name='uk_active_user_slot'""")
    return {"results": [item.get("code") for item in results], "success_count": len(success),
            "reserved_rows": total, "unique_index_count": int(unique_index),
            "pass": len(success) == 1 and total == 2 and int(unique_index) == 1}


def test_redis_failure() -> dict[str, Any]:
    slot_id = SLOTS[0]
    before = scalar(f"SELECT available_count FROM resource_slot WHERE id={slot_id}")
    run_backend("stop")
    run_backend("start", "redis-after-reserve")
    try:
        response = book(slot_id, 951001)
    finally:
        run_backend("stop")
        run_backend("start")
        request("POST", f"/api/slots/{slot_id}/warmup").raise_for_status()
    bookings = scalar(f"SELECT COUNT(*) FROM booking_record WHERE slot_id={slot_id} AND user_id=951001")
    slot_bookings = scalar(f"SELECT COUNT(*) FROM booking_record WHERE slot_id={slot_id}")
    after = scalar(f"SELECT available_count FROM resource_slot WHERE id={slot_id}")
    return {"response_code": response.get("code"), "http_status": response.get("httpStatus"),
            "before_available": int(before), "after_available": int(after), "booking_rows": int(bookings),
            "slot_booking_rows": int(slot_bookings),
            "pass": response.get("code") != "SUCCESS" and int(bookings) == 0
            and int(slot_bookings) == 0 and before == after}


def test_kafka_pause_and_outbox_restart() -> dict[str, Any]:
    slot_id = SLOTS[1]
    docker("stop", KAFKA_CONTAINER)
    try:
        response = book(slot_id, 952001)
        booking_id = response.get("bookingId")
        key = f"booking:{booking_id}:reserved"
        time.sleep(2)
        failed_or_pending = scalar(f"SELECT COUNT(*) FROM message_log WHERE message_key='{key}' AND status IN ('INIT','FAILED')")
        before_restart_status = scalar(f"SELECT status FROM message_log WHERE message_key='{key}'")
    finally:
        run_backend("stop")
        docker("start", KAFKA_CONTAINER)
        wait_for(lambda: port_open(9092), 60)
        run_backend("start")
    recovered = wait_message(key, "SENT", 50)
    consumed = wait_for(lambda: scalar(f"SELECT COUNT(*) FROM consume_log WHERE message_key='{key}'") == "1", 40)
    retry_count = int(scalar(f"SELECT retry_count FROM message_log WHERE message_key='{key}'"))
    capacity_after_restart = int(scalar(f"SELECT total_count FROM resource_slot WHERE id={slot_id}"))
    return {"booking_code": response.get("code"), "before_restart_status": before_restart_status,
            "failed_or_pending": int(failed_or_pending), "retry_count": retry_count,
            "capacity_after_restart": capacity_after_restart, "recovered_sent": recovered, "consumed": consumed,
            "pass": response.get("code") == "SUCCESS" and int(failed_or_pending) == 1
            and capacity_after_restart == 2 and recovered and consumed}


def test_dlt_and_replay() -> dict[str, Any]:
    slot_id = SLOTS[2]
    run_backend("stop")
    run_backend("start", "projection-update")
    response = book(slot_id, 953001)
    booking_id = response.get("bookingId")
    key = f"booking:{booking_id}:reserved"
    dlt_ready = wait_for(lambda: scalar(f"SELECT COUNT(*) FROM dead_letter_log WHERE message_key='{key}'") == "1", 45)
    dlt_id = int(scalar(f"SELECT id FROM dead_letter_log WHERE message_key='{key}' ORDER BY id DESC LIMIT 1")) if dlt_ready else 0
    retry_count = int(scalar(f"SELECT retry_count FROM dead_letter_log WHERE id={dlt_id}")) if dlt_id else 0
    run_backend("stop")
    run_backend("start")
    with ThreadPoolExecutor(max_workers=20) as pool:
        replay_responses = list(pool.map(lambda _: request("POST", f"/api/dead-letters/{dlt_id}/replay"), range(20)))
    replay_http_success = sum(1 for item in replay_responses if item.status_code == 200)
    replayed = wait_for(lambda: scalar(f"SELECT status FROM dead_letter_log WHERE id={dlt_id}") == "REPLAYED", 30)
    consumed = wait_for(lambda: scalar(f"SELECT COUNT(*) FROM consume_log WHERE message_key='{key}'") == "1", 30)
    projection_count = int(scalar(f"SELECT COUNT(*) FROM booking_event_projection WHERE booking_id={booking_id}"))
    replay_count = int(scalar(f"SELECT replay_count FROM dead_letter_log WHERE id={dlt_id}")) if dlt_id else 0
    return {"booking_code": response.get("code"), "dlt_ready": dlt_ready, "dlt_id": dlt_id,
            "dlt_retry_count": retry_count, "replay_http_success": replay_http_success,
            "replayed": replayed, "replay_count": replay_count, "consumed": consumed,
            "projection_count": projection_count,
            "pass": response.get("code") == "SUCCESS" and dlt_ready and retry_count >= 3
            and replay_http_success == 1 and replayed and replay_count == 1 and consumed and projection_count == 1}


def test_duplicate_consumption() -> dict[str, Any]:
    slot_id = SLOTS[3]
    response = book(slot_id, 954001)
    booking_id = response.get("bookingId")
    key = f"booking:{booking_id}:reserved"
    wait_message(key, "SENT", 30)
    wait_for(lambda: scalar(f"SELECT COUNT(*) FROM consume_log WHERE message_key='{key}'") == "1", 30)
    payload = mysql(f"SELECT payload FROM message_log WHERE message_key='{key}'")[0][0]
    before_metric = int(scalar("SELECT COALESCE(metric_value,0) FROM consumer_metric WHERE metric_key='consumer.duplicate_consumption'"))
    producer = ["exec", "-i", KAFKA_CONTAINER, "/opt/kafka/bin/kafka-console-producer.sh",
                "--bootstrap-server", "localhost:9092", "--topic", "booking-success-topic",
                "--property", "parse.key=true", "--property", "key.separator=|"]
    docker(*producer, input_text=f"{key}|{payload}\n{key}|{payload}\n")
    metric_changed = wait_for(lambda: int(scalar("SELECT COALESCE(metric_value,0) FROM consumer_metric WHERE metric_key='consumer.duplicate_consumption'")) >= before_metric + 1, 30)
    consume_count = int(scalar(f"SELECT COUNT(*) FROM consume_log WHERE message_key='{key}'"))
    projection_count = int(scalar(f"SELECT COUNT(*) FROM booking_event_projection WHERE booking_id={booking_id}"))
    return {"booking_code": response.get("code"), "duplicate_metric_before": before_metric,
            "duplicate_metric_changed": metric_changed, "consume_count": consume_count,
            "projection_count": projection_count,
            "pass": response.get("code") == "SUCCESS" and metric_changed and consume_count == 1 and projection_count == 1}


def write_outputs(results: dict[str, Any]) -> Path:
    output = ROOT / "test-results" / "backend" / "resilience" / datetime.now().strftime("%Y%m%d-%H%M%S")
    output.mkdir(parents=True, exist_ok=True)
    (output / "metrics.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = ["# 后端可靠性专项报告", "", f"- 结论: {'PASS' if results.get('pass') else 'FAIL'}", f"- 时间: {results.get('started_at')}", "", "| 场景 | 结果 |", "|---|---|"]
    for name, value in results.items():
        if isinstance(value, dict) and "pass" in value:
            lines.append(f"| {name} | {'PASS' if value['pass'] else 'FAIL'} |")
    if results.get("error"):
        lines.extend(["", f"- 错误: {results['error']}"])
    lines.extend(["", "原始结果见 `metrics.json`。"])
    (output / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return output


def main() -> int:
    if port_open(8083):
        print("FAIL port 8083 is already in use", file=sys.stderr)
        return 1
    conflicting_ports = [port for port in (8080, 8081, 8082) if port_open(port)]
    if conflicting_ports:
        print(f"FAIL other backend consumers are listening on ports: {conflicting_ports}", file=sys.stderr)
        return 1
    results: dict[str, Any] = {"started_at": datetime.now().isoformat()}
    server_started = False
    try:
        run_backend("start")
        server_started = True
        prepare_fixture()
        results["T05_database_unique_and_rollback"] = test_transaction_and_unique()
        results["T07_redis_failure_compensation"] = test_redis_failure()
        results["T08_kafka_pause_resume_and_T09_outbox_restart"] = test_kafka_pause_and_outbox_restart()
        results["T10_dlt_retry_and_dead_letter"] = test_dlt_and_replay()
        results["T06_duplicate_consumption_idempotency"] = test_duplicate_consumption()
        results["pass"] = all(item.get("pass") for item in results.values() if isinstance(item, dict))
        results["output"] = str(write_outputs(results))
        print(json.dumps(results, ensure_ascii=False, indent=2))
        return 0 if results["pass"] else 1
    except (AssertionError, OSError, requests.RequestException, subprocess.SubprocessError, ValueError) as exc:
        results["pass"] = False
        results["error"] = f"{type(exc).__name__}: {exc}"
        results["output"] = str(write_outputs(results))
        print(json.dumps(results, ensure_ascii=False, indent=2), file=sys.stderr)
        return 1
    finally:
        try:
            if server_started:
                run_backend("stop")
        finally:
            try:
                docker("start", REDIS_CONTAINER)
                docker("start", KAFKA_CONTAINER)
            except subprocess.SubprocessError:
                pass
            try:
                cleanup_fixture()
            except subprocess.SubprocessError:
                pass


if __name__ == "__main__":
    raise SystemExit(main())
