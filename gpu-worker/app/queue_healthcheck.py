"""Health check for the outbound Railway GPU queue (no HTTP server here)."""
from pathlib import Path
import sys
import time

HEARTBEAT=Path("/tmp/hot-hat-gpu-queue.last-ok")

def healthy(max_age_seconds=75):
    try:
        age=time.time()-HEARTBEAT.stat().st_mtime
        return 0 <= age < max_age_seconds
    except OSError:
        return False

if __name__=="__main__":
    sys.exit(0 if healthy() else 1)
