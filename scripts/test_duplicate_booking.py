#!/usr/bin/env python3
"""
Smoke test for the booking flow:
one demo user can book a slot once, and the second booking is rejected.

Run after the local backend is available:
    python scripts/test_duplicate_booking.py
    python scripts/test_duplicate_booking.py --base-url http://localhost:8080 --slot-id 1
"""

from __future__ import annotations

import argparse
import sys
from typing import Any

import requests


def request_json(method: str, url: str, **kwargs: Any) -> dict[str, Any]:
    response = requests.request(method, url, timeout=10, **kwargs)
    response.raise_for_status()
    return response.json()


def assert_equal(actual: Any, expected: Any, message: str) -> None:
    if actual != expected:
        raise AssertionError(f"{message}: expected {expected!r}, got {actual!r}")


def assert_true(value: Any, message: str) -> None:
    if value is not True:
        raise AssertionError(f"{message}: expected True, got {value!r}")


def run_duplicate_booking_test(base_url: str, slot_id: int) -> None:
    base_url = base_url.rstrip("/")

    print(f"Testing duplicate booking guard: base_url={base_url}, slot_id={slot_id}")

    reset = request_json("POST", f"{base_url}/api/dev/reset/{slot_id}")
    assert_equal(reset.get("code"), "SUCCESS", "reset slot failed")

    warmup = request_json("POST", f"{base_url}/api/slots/{slot_id}/warmup")
    assert_equal(warmup.get("code"), "SUCCESS", "warm up slot failed")

    before = request_json("GET", f"{base_url}/api/slots/{slot_id}")
    before_available = before.get("availableCount")

    first = request_json("POST", f"{base_url}/api/bookings", json={"slotId": slot_id})
    assert_equal(first.get("code"), "SUCCESS", "first booking should succeed")
    if first.get("bookingId") is None:
        raise AssertionError("first booking should return a bookingId")

    second = request_json("POST", f"{base_url}/api/bookings", json={"slotId": slot_id})
    assert_equal(second.get("code"), "DUPLICATE_BOOKING", "second booking should be rejected")
    if second.get("bookingId") is not None:
        raise AssertionError("duplicate booking should not return a bookingId")

    after = request_json("GET", f"{base_url}/api/slots/{slot_id}")
    verify = request_json("GET", f"{base_url}/api/dev/verify/{slot_id}")

    if isinstance(before_available, int):
        assert_equal(
            after.get("availableCount"),
            before_available - 1,
            "slot inventory should decrease only once",
        )

    assert_equal(verify.get("successBookingCount"), 1, "only one successful booking should exist")
    assert_true(verify.get("stockConsistent"), "stock should remain consistent")

    print("PASS duplicate booking is rejected and inventory remains consistent")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Smoke test duplicate booking behavior.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="backend base URL")
    parser.add_argument("--slot-id", type=int, default=1, help="resource slot id to test")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        run_duplicate_booking_test(args.base_url, args.slot_id)
    except requests.RequestException as exc:
        print(f"FAIL request error: {exc}", file=sys.stderr)
        return 1
    except AssertionError as exc:
        print(f"FAIL assertion error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
