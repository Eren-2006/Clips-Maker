from __future__ import annotations

import shutil
import time
from pathlib import Path


def purge_expired_outputs(output_dir: Path, retention_hours: int = 24, now_ts: float | None = None) -> int:
    if not output_dir.exists():
        return 0

    current_time = now_ts if now_ts is not None else time.time()
    cutoff = current_time - (retention_hours * 3600)
    removed = 0

    for entry in output_dir.iterdir():
        try:
            mtime = entry.stat().st_mtime
            if mtime < cutoff:
                if entry.is_dir():
                    shutil.rmtree(entry, ignore_errors=True)
                else:
                    entry.unlink(missing_ok=True)
                removed += 1
        except OSError:
            continue
    return removed
