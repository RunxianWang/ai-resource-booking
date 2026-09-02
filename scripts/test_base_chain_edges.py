#!/usr/bin/env python3
"""基础链路边界场景：同用户重复预约、并发取消。"""

from __future__ import annotations

import argparse
import sys
import time
from concurrent.futures import ThreadPoolExecutor

from test_base_chain import call, cleanup_fixture, prepare_fixture, state


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--slot-id", type=int, default=990001)
    parser.add_argument("--capacity", type=int, default=100)
    args = parser.parse_args()
    args.mysql_container = "ai-booking-mysql"
    args.mysql_database = "wrx_booking"

    prepare_fixture(args)
    try:
        user_id = 200001
        with ThreadPoolExecutor(max_workers=20) as pool:
            duplicate_results = list(pool.map(
                lambda _: call("POST", f"{args.base_url}/api/bookings", headers={"X-Test-User-Id": str(user_id)}, json={"slotId": args.slot_id}),
                range(20),
            ))
        duplicate_counts = {}
        booking_id = None
        for result in duplicate_results:
            duplicate_counts[result.get("code")] = duplicate_counts.get(result.get("code"), 0) + 1
            booking_id = booking_id or result.get("bookingId")

        if duplicate_counts != {"SUCCESS": 1, "DUPLICATE_BOOKING": 19}:
            print(f"FAIL duplicate result: {duplicate_counts}", file=sys.stderr)
            return 1

        with ThreadPoolExecutor(max_workers=20) as pool:
            cancel_results = list(pool.map(
                lambda _: call("POST", f"{args.base_url}/api/bookings/{booking_id}/cancel", headers={"X-Test-User-Id": str(user_id)}),
                range(20),
            ))
        cancel_counts = {}
        for result in cancel_results:
            cancel_counts[result.get("code")] = cancel_counts.get(result.get("code"), 0) + 1
        after = state(args)
        deadline = time.time() + 30
        while time.time() < deadline and after["verify"].get("messageConsistent") is not True:
            time.sleep(0.5)
            after = state(args)
        passed = (cancel_counts.get("SUCCESS") == 1 and cancel_counts.get("BOOKING_CANCEL_SKIPPED") == 19
                  and after["slot"].get("mysqlAvailableCount") == args.capacity
                  and after.get("redis_available") == args.capacity
                  and after.get("redis_users") == 0
                  and after["verify"].get("messageConsistent") is True
                  and after["verify"].get("consumedMessageCount") == after["verify"].get("messageLogCount"))
        print({"duplicate": duplicate_counts, "cancel": cancel_counts, "after": after, "pass": passed})
        return 0 if passed else 1
    finally:
        cleanup_fixture(args)


if __name__ == "__main__":
    raise SystemExit(main())
