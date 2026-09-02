"""运行态验收：当天范围、下一整点、连续时长和越界保护。"""
from __future__ import annotations

import subprocess
import sys
import argparse
import os
from datetime import datetime, timedelta

import requests

BASE = "http://localhost:8080"
IDS = list(range(910001, 910006))
MACHINE_ID = 999003
DB_USERNAME = os.environ.get("BOOKING_DB_USERNAME")
DB_PASSWORD = os.environ.get("BOOKING_DB_PASSWORD")
ADMIN_USERNAME = os.environ.get("APP_BOOTSTRAP_ADMIN_USERNAME")
ADMIN_PASSWORD = os.environ.get("APP_BOOTSTRAP_ADMIN_PASSWORD")


def mysql(sql: str) -> None:
    if not DB_USERNAME or not DB_PASSWORD:
        raise RuntimeError("BOOKING_DB_USERNAME and BOOKING_DB_PASSWORD must be set")
    result = subprocess.run(
        ["docker", "exec", "ai-booking-mysql", "mysql", f"-u{DB_USERNAME}", f"-p{DB_PASSWORD}", "-D", "wrx_booking", "-e", sql],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "mysql command failed")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=BASE)
    parser.add_argument("--perf", action="store_true")
    parser.add_argument("--user-id", type=int, default=86053001)
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    now = datetime.now().replace(minute=0, second=0, microsecond=0)
    first = now + timedelta(hours=1)
    today_slots = ",".join(
        f"({slot_id},{MACHINE_ID},1,'RULE-TEST','GPU','{first + timedelta(hours=i):%Y-%m-%d %H:%M:%S}','{first + timedelta(hours=i+1):%Y-%m-%d %H:%M:%S}',1,1,'AVAILABLE')"
        for i, slot_id in enumerate(IDS[:4])
    )
    tomorrow = first + timedelta(days=1)
    sql = f"""
    INSERT IGNORE INTO resource_machine(id,machine_name,resource_type,gpu_model,status)
    VALUES ({MACHINE_ID},'AUTOMATED-RULE-TEST','GPU','TEST','ACTIVE');
    DELETE FROM booking_record WHERE slot_id IN ({','.join(map(str, IDS))});
    DELETE FROM resource_slot WHERE id IN ({','.join(map(str, IDS))});
    INSERT INTO resource_slot(id,machine_id,resource_id,resource_name,resource_type,start_time,end_time,total_count,available_count,status)
    VALUES {today_slots};
    INSERT INTO resource_slot(id,machine_id,resource_id,resource_name,resource_type,start_time,end_time,total_count,available_count,status)
    VALUES (910005,{MACHINE_ID},1,'RULE-TEST','GPU','{tomorrow:%Y-%m-%d %H:%M:%S}','{tomorrow + timedelta(hours=1):%Y-%m-%d %H:%M:%S}',1,1,'AVAILABLE');
    """
    mysql(sql)
    session = requests.Session()
    headers = {"X-Test-User-Id": str(args.user_id)} if args.perf else {}
    if not args.perf:
        if not ADMIN_USERNAME or not ADMIN_PASSWORD:
            raise RuntimeError("APP_BOOTSTRAP_ADMIN_USERNAME and APP_BOOTSTRAP_ADMIN_PASSWORD must be set")
        login = session.post(f"{base_url}/api/auth/login", json={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD}, timeout=15)
        login.raise_for_status()

    for slot_id in IDS:
        warmup = session.post(f"{base_url}/api/slots/{slot_id}/warmup", headers=headers, timeout=15)
        warmup.raise_for_status()
        assert warmup.json().get("code") == "SUCCESS", f"warmup failed for slot {slot_id}: {warmup.text}"

    try:
        slots = session.get(f"{base_url}/api/slots", headers=headers, timeout=15).json()
        visible = [s for s in slots if s.get("resourceName") == "RULE-TEST"]
        assert visible, "RULE-TEST slots are not visible in catalog"
        assert all(datetime.fromisoformat(s["startTime"]).date() == now.date() for s in visible), f"visible slots: {visible}"
        assert all(datetime.fromisoformat(s["startTime"]) >= first for s in visible), f"visible slots: {visible}"
        assert all(datetime.fromisoformat(s["endTime"]) <= datetime.combine(now.date() + timedelta(days=1), datetime.min.time()) for s in visible), f"visible slots: {visible}"

        first_id = IDS[0]
        booked = session.post(f"{base_url}/api/bookings", headers=headers, json={"slotId": first_id, "durationHours": 2}, timeout=15).json()
        assert booked["code"] == "SUCCESS" and len(booked["slotIds"]) == 2, booked
        duplicate = session.post(f"{base_url}/api/bookings", headers=headers, json={"slotId": first_id, "durationHours": 1}, timeout=15).json()
        assert duplicate["code"] == "DUPLICATE_BOOKING", duplicate
        invalid = session.post(f"{base_url}/api/bookings", headers=headers, json={"slotId": IDS[2], "durationHours": 3}, timeout=15).json()
        assert invalid["code"] == "INVALID_DURATION", invalid
        tomorrow_result = session.post(f"{base_url}/api/bookings", headers=headers, json={"slotId": 910005, "durationHours": 1}, timeout=15).json()
        assert tomorrow_result["code"] == "SLOT_NOT_BOOKABLE", tomorrow_result
        print("PASS today-only, next-hour, duration 1/2/4 validation and tomorrow guard")
        return 0
    finally:
        mysql(f"DELETE FROM booking_record WHERE slot_id IN ({','.join(map(str, IDS))}); DELETE FROM resource_slot WHERE id IN ({','.join(map(str, IDS))}); DELETE FROM resource_machine WHERE id = {MACHINE_ID};")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, requests.RequestException, subprocess.SubprocessError, RuntimeError) as exc:
        print(f"FAIL {type(exc).__name__}: {exc!r}", file=sys.stderr)
        raise SystemExit(1)
