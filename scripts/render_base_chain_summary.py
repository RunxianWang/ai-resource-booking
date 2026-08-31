#!/usr/bin/env python3
"""Render a compact image for the base-chain acceptance report."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("metrics", type=Path)
    args = parser.parse_args()
    data = json.loads(args.metrics.read_text(encoding="utf-8"))
    import matplotlib.pyplot as plt

    counts = data["business_counts"]
    labels = ["SUCCESS", "SOLD_OUT", "SYSTEM_ERROR"]
    values = [counts.get(label, 0) for label in labels]
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.5))
    axes[0].bar(labels, values, color=["#1f9d55", "#e0a11a", "#d64545"])
    axes[0].set_title("Business outcomes")
    axes[0].set_ylabel("Requests")
    axes[0].grid(axis="y", alpha=0.25)
    axes[0].text(0, values[0], str(values[0]), ha="center", va="bottom")
    axes[0].text(1, values[1], str(values[1]), ha="center", va="bottom")
    axes[0].text(2, values[2], str(values[2]), ha="center", va="bottom")

    inventory_labels = ["MySQL stock", "Redis stock", "Redis users", "RESERVED"]
    inventory_values = [data["mysql_available"], data["redis_available"], data["redis_users"], data["reserved_count"]]
    axes[1].bar(inventory_labels, inventory_values, color="#3778c2")
    axes[1].set_title("Final inventory state")
    axes[1].set_ylabel("Count")
    axes[1].grid(axis="y", alpha=0.25)
    for index, value in enumerate(inventory_values):
        axes[1].text(index, value, str(value), ha="center", va="bottom")

    fig.suptitle(f"Base-chain acceptance | TPS {data['tps']:.2f} | P95 {data['p95_ms']:.2f} ms")
    fig.tight_layout()
    output = args.metrics.with_name("acceptance-summary.png")
    fig.savefig(output, dpi=160)
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
