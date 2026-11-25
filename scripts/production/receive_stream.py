"""Bounded SSH stdin reception; never touches services, locks or releases."""
import os
import select
import sys
import time


def receive(fd, destination, *, limit=536870912, idle=180, total=600,
            interval=15, log=print):
    started = last_data = last_report = time.monotonic()
    size = 0
    log("Upload receiving: bytes=0", flush=True)
    with open(destination, "xb") as output:
        while True:
            now = time.monotonic()
            if now - started >= total:
                raise TimeoutError(f"Upload total timeout: bytes={size}")
            if now - last_data >= idle:
                raise TimeoutError(f"Upload idle timeout: bytes={size}")
            if now - last_report >= interval:
                log(f"Upload progress: bytes={size} elapsed={int(now-started)}s", flush=True)
                last_report = now
            delay = min(total - (now - started), idle - (now - last_data),
                        interval - (now - last_report))
            if not select.select([fd], [], [], max(0, delay))[0]:
                continue
            data = os.read(fd, min(65536, limit - size + 1))
            if not data:
                log(f"Upload received: bytes={size} elapsed={int(time.monotonic()-started)}s", flush=True)
                return size
            size += len(data)
            if size > limit:
                raise ValueError(f"Upload size limit exceeded: bytes={size}")
            output.write(data)
            last_data = time.monotonic()


if __name__ == "__main__":
    try:
        receive(sys.stdin.fileno(), sys.argv[1])
    except (TimeoutError, ValueError, OSError) as error:
        print(str(error), file=sys.stderr, flush=True)
        sys.exit(1)
