#!/usr/bin/env python3
import os
import sys


group = sys.argv[sys.argv.index("--group") + 1]
mode = os.environ.get("ASSIGNMENT_MODE", "ready")
topics = (
    "bet.resolution.revised.v1",
    "bet.settled.v1",
    "bet.voided.v1",
)
rows = [(topic, partition) for topic in topics for partition in range(3)]
if mode == "missing":
    rows.pop()
elif mode == "extra":
    rows.append(("unexpected.topic", 0))

print("GROUP TOPIC PARTITION CURRENT-OFFSET LOG-END-OFFSET LAG CONSUMER-ID HOST CLIENT-ID")
for topic, partition in rows:
    consumer = "-" if mode == "inactive" and partition == 2 else "consumer-1"
    print(f"{group} {topic} {partition} 0 0 0 {consumer} /host client-1")
