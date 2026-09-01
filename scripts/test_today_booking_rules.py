"""运行态验收：当天范围、下一整点、连续时长和越界保护。"""
from __future__ import annotations

import subprocess
import sys
from datetime import datetime, timedelta

import requests

BASE = "http://localhost:8080"
IDS = list(range(910001, 910006))


def mysql(sql: str) -> None:
    subprocess.run(["docker", "exec", "ai-booking-mysql", "mysql", "-uroot", "-proot", "-D", "wrx_booking", "-e", sql], check=True, capture_output=True, text=True)


def main() -> int:
    now = datetime.now().replace(minute=0, second=0, microsecond=0)
    first = now + timedelta(hours=1)
    today_slots = ",".join(
        f"({slot_id},1,1,'RULE-TEST','GPU','{first + timedelta(hours=i):%Y-%m-%d %H:%M:%S}','{first + timedelta(hours=i+1):%Y-%m-%d %H:%M:%S}',1,1,'AVAILABLE')"
        for i, slot_id in enumerate(IDS[:4])
    )
    tomorrow = first + timedelta(days=1)
    sql = f"""
    DELETE FROM booking_record WHERE slot_id IN ({','.join(map(str, IDS))});
    DELETE FROM resource_slot WHERE id IN ({','.join(map(str, IDS))});
    INSERT INTO resource_slot(id,machine_id,resource_id,resource_name,resource_type,start_time,end_time,total_count,available_count,status)
    VALUES {today_slots};
    INSERT INTO resource_slot(id,machine_id,resource_id,resource_name,resource_type,start_time,end_time,total_count,available_count,status)
    VALUES (910005,1,1,'RULE-TEST','GPU','{tomorrow:%Y-%m-%d %H:%M:%S}','{tomorrow + timedelta(hours=1):%Y-%m-%d %H:%M:%S}',1,1,'AVAILABLE');
    """
    mysql(sql)
    session = requests.Session()
    login = session.post(f"{BASE}/api/auth/login", json={"username": "admin", "password": "admin123"}, timeout=15)
    login.raise_for_status()
    headers = {}

    try:
        slots = session.get(f"{BASE}/api/slots", headers=headers, timeout=15).json()
        visible = [s for s in slots if s.get("resourceName") == "RULE-TEST"]
        assert visible and all(datetime.fromisoformat(s["startTime"]).date() == now.date() for s in visible)
        assert all(datetime.fromisoformat(s["startTime"]) >= first for s in visible)
        assert all(datetime.fromisoformat(s["endTime"]) <= datetime.combine(now.date() + timedelta(days=1), datetime.min.time()) for s in visible)

        first_id = IDS[0]
        booked = session.post(f"{BASE}/api/bookings", headers=headers, json={"slotId": first_id, "durationHours": 2}, timeout=15).json()
        assert booked["code"] == "SUCCESS" and len(booked["slotIds"]) == 2
        duplicate = session.post(f"{BASE}/api/bookings", headers=headers, json={"slotId": first_id, "durationHours": 1}, timeout=15).json()
        assert duplicate["code"] == "DUPLICATE_BOOKING"
        invalid = session.post(f"{BASE}/api/bookings", headers=headers, json={"slotId": IDS[2], "durationHours": 3}, timeout=15).json()
        assert invalid["code"] == "INVALID_DURATION"
        tomorrow_result = session.post(f"{BASE}/api/bookings", headers=headers, json={"slotId": 910005, "durationHours": 1}, timeout=15).json()
        assert tomorrow_result["code"] == "SLOT_NOT_BOOKABLE"
        print("PASS today-only, next-hour, duration 1/2/4 validation and tomorrow guard")
        return 0
    finally:
        mysql(f"DELETE FROM booking_record WHERE slot_id IN ({','.join(map(str, IDS))}); DELETE FROM resource_slot WHERE id IN ({','.join(map(str, IDS))});")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, requests.RequestException, subprocess.SubprocessError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
