"""Root-only resumable release transport. Activation uses the existing receiver."""
import contextlib
import fcntl
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

from receive_stream import receive

MAX_SIZE = 512 * 1024 * 1024
CHUNK_SIZE = 1024 * 1024
ROOT = Path('/srv/jiugeng/uploads')


def validate(args):
    if len(args) not in (4, 5, 7):
        raise ValueError('Invalid transport arguments')
    action, revision, digest, size = args[:4]
    if not re.fullmatch('[0-9a-f]{40}', revision) or not re.fullmatch('[0-9a-f]{64}', digest):
        raise ValueError('Invalid revision or digest')
    if not size.isascii() or not size.isdecimal() or not 0 < int(size) <= MAX_SIZE:
        raise ValueError('Invalid archive size')
    if action not in ('stage-status', 'stage-chunk', 'stage-activate'):
        raise ValueError('Unknown transport action')
    if len(args) != {'stage-status': 4, 'stage-chunk': 7, 'stage-activate': 5}[action]:
        raise ValueError('Invalid chunk arguments')
    if action == 'stage-activate' and not re.fullmatch('[0-9a-f]{40}', args[4]):
        raise ValueError('Invalid expected current revision')
    if action == 'stage-chunk':
        offset, length, chunk_digest = args[4:]
        if not all(s.isascii() and s.isdecimal() for s in (offset, length)):
            raise ValueError('Invalid chunk position')
        if not 0 < int(length) <= CHUNK_SIZE or int(offset) + int(length) > int(size):
            raise ValueError('Chunk exceeds archive bounds')
        if not re.fullmatch('[0-9a-f]{64}', chunk_digest):
            raise ValueError('Invalid chunk digest')
    return action, revision, digest, int(size)


def digest_file(path):
    with path.open('rb') as stream:
        digest = hashlib.sha256()
        for chunk in iter(lambda: stream.read(CHUNK_SIZE), b''):
            digest.update(chunk)
        return digest.hexdigest()


@contextlib.contextmanager
def locked(path):
    with path.open('a+b') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield


def run(args, *, root=ROOT, fd=0, activate=None):
    if args == ['stage-current']:
        current = Path('/srv/jiugeng/current').resolve().name
        if not re.fullmatch('[0-9a-f]{40}', current):
            raise ValueError('Current revision unavailable; inspect server')
        print(current, flush=True)
        return
    action, revision, digest, size = validate(args)
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    archive = root / (revision + '-' + digest + '.part')
    # A single staging lock bounds disk use and prevents append/activation races.
    # It is separate from the deployment lock: a slow upload never blocks service work.
    with locked(root / '.lock'):
        if not archive.exists():
            if action != 'stage-status':
                raise ValueError('Initialize upload with stage-status first')
            if len(list(root.glob('*.part'))) >= 4:
                raise ValueError('Staging quota reached; operator must review old uploads')
            if shutil.disk_usage(root).free < MAX_SIZE + 2 * 1024**3:
                raise ValueError('Insufficient staging space')
            archive.touch(mode=0o600, exist_ok=False)
        current = archive.stat().st_size
        if current > size:
            raise ValueError('Staged size exceeds requested archive')
        if action == 'stage-status':
            print(current, flush=True)
        elif action == 'stage-chunk':
            offset, length, chunk_digest = int(args[4]), int(args[5]), args[6]
            if offset != current:
                raise ValueError('Offset changed; query stage-status and resume')
            if shutil.disk_usage(root).free < length + 2 * 1024**3:
                raise ValueError('Insufficient staging space')
            # Never append unverified partial chunks. A disconnect preserves the prefix.
            with tempfile.TemporaryDirectory(prefix='.chunk-', dir=root) as work:
                chunk = Path(work) / 'data'
                count = receive(fd, chunk, limit=length, idle=60, total=180,
                                log=lambda *a, **k: None)
                if count != length or digest_file(chunk) != chunk_digest:
                    raise ValueError('Incomplete or corrupt chunk')
                try:
                    with archive.open('ab') as output, chunk.open('rb') as source:
                        shutil.copyfileobj(source, output)
                        output.flush()
                        os.fsync(output.fileno())
                except BaseException:
                    with archive.open('r+b') as output:
                        output.truncate(current)
                    raise
            print(current + length, flush=True)
        else:
            if current != size or digest_file(archive) != digest:
                raise ValueError('Staged archive failed size/SHA-256 verification')
            if activate is None:
                def activate(path, sha, checksum, expected):
                    with path.open('rb') as stream:
                        subprocess.run(['/usr/local/lib/jiugeng/receive.sh', sha, checksum, expected],
                                       stdin=stream, check=True)
            activate(archive, revision, digest, args[4])
            # Only the verified, successfully deployed cache is removed, never releases/data.
            archive.unlink()


if __name__ == '__main__':
    try:
        if os.geteuid() != 0 or not Path('/etc/jiugeng/deploy-enabled').is_file():
            raise ValueError('Root and enabled deployment gate required')
        run(sys.argv[1:])
    except (ValueError, OSError, TimeoutError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr, flush=True)
        sys.exit(1)
