"""Resumable SSH upload client; SSH identity/known-hosts stay in environment paths."""
import hashlib
import os
import re
from pathlib import Path
import subprocess
import sys
import time

from staged_upload import CHUNK_SIZE, digest_file, validate


def upload(path, revision, invoke, *, sleep=time.sleep):
    size, digest = path.stat().st_size, digest_file(path)
    base = [revision, digest, str(size)]
    validate(['stage-status', *base])
    expected = invoke(['stage-current'], None).strip()
    if not re.fullmatch('[0-9a-f]{40}', expected):
        raise ValueError('Invalid current server revision')
    failures = 0
    with path.open('rb') as source:
        while True:
            try:
                status = invoke(['stage-status', *base], None).strip()
                if not status.isdecimal() or not 0 <= int(status) <= size:
                    raise ValueError('Invalid remote upload offset')
                offset = int(status)
                if offset == size:
                    break
                source.seek(offset)
                chunk = source.read(CHUNK_SIZE)
                result = invoke(['stage-chunk', *base, str(offset), str(len(chunk)),
                                 hashlib.sha256(chunk).hexdigest()], chunk).strip()
                if result != str(offset + len(chunk)):
                    raise ValueError('Invalid remote chunk acknowledgement')
                print(f'Upload committed: {offset + len(chunk)}/{size}', flush=True)
                failures = 0
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
                failures += 1
                if failures >= 5:
                    raise
                print('Transfer interrupted; resuming verified prefix', flush=True)
                sleep(min(failures * 3, 15))
    # Never automatically replay activation: a lost acknowledgement needs version inspection.
    print(invoke(['stage-activate', *base, expected], None, activation=True), end='', flush=True)


def main():
    ssh = ['ssh', '-T', '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes',
           '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=15',
           '-o', 'ServerAliveInterval=20', '-o', 'ServerAliveCountMax=3',
           '-o', 'UserKnownHostsFile=' + os.environ['DEPLOY_KNOWN_HOSTS_FILE'],
           '-i', os.environ['DEPLOY_KEY_FILE'], 'erp-deploy@' + os.environ['DEPLOY_HOST']]
    def invoke(args, data, activation=False):
        result = subprocess.run([*ssh, ' '.join(args)], input=data or b'',
                                stdout=subprocess.PIPE, check=True,
                                timeout=900 if activation else 240)
        return result.stdout.decode()
    upload(Path(sys.argv[1]), sys.argv[2], invoke)


if __name__ == '__main__':
    main()
