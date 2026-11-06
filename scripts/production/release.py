#!/usr/bin/env python3
"""Create and safely unpack versioned ERP bundles; never include runtime secrets."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import tarfile
import tempfile

MAX_BYTES = 512 * 1024 * 1024

def sha(path):
    with path.open('rb') as stream:
        digest = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
        return digest.hexdigest()

def build(repo, revision, output):
    if not re.fullmatch(r'[0-9a-f]{40}', revision):
        raise ValueError('A full commit SHA is required')
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        shutil.copy2(repo/'deliverables/backend/sprint-0/jiugeng-erp-backend/target/jiugeng-erp-backend-0.0.1-SNAPSHOT.jar', root/'backend.jar')
        shutil.copytree(repo/'frontend/dist', root/'frontend')
        shutil.copytree(repo/'deliverables/pm/excel_templates', root/'import-templates')
        (root/'frontend/version.json').write_text(json.dumps({'revision': revision})+'\n')
        files = {str(p.relative_to(root)): sha(p) for p in sorted(root.rglob('*')) if p.is_file()}
        (root/'manifest.json').write_text(json.dumps({'revision': revision, 'files': files}, indent=2)+'\n')
        output.parent.mkdir(parents=True, exist_ok=True)
        with tarfile.open(output, 'w:gz') as archive:
            for p in sorted(root.rglob('*')):
                if p.is_file():
                    archive.add(p, arcname=str(p.relative_to(root)), recursive=False)

def unpack(archive_path, destination, revision):
    if destination.exists():
        raise ValueError('Release destination already exists')
    with tarfile.open(archive_path, 'r:gz') as archive:
        members = archive.getmembers()
        names = set()
        total = 0
        for member in members:
            path = PurePosixPath(member.name)
            if (not member.isfile() or path.is_absolute() or '..' in path.parts
                    or str(path) != member.name or member.name in names
                    or not (member.name in ('backend.jar', 'manifest.json')
                            or member.name.startswith(('frontend/', 'import-templates/')))):
                raise ValueError('Unsafe or duplicate archive member')
            names.add(member.name)
            total += member.size
        if total > MAX_BYTES or len(members) > 10000:
            raise ValueError('Release exceeds limits')
        if not {'backend.jar', 'manifest.json', 'frontend/index.html', 'frontend/version.json'} <= names:
            raise ValueError('Incomplete release')
        manifest = json.load(archive.extractfile('manifest.json'))
        if manifest['revision'] != revision or set(manifest['files']) != names - {'manifest.json'}:
            raise ValueError('Manifest mismatch')
        destination.mkdir(mode=0o755)
        try:
            for member in members:
                target = destination/member.name
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open('xb') as out, archive.extractfile(member) as stream:
                    shutil.copyfileobj(stream, out)
                target.chmod(0o644)
                if member.name != 'manifest.json' and sha(target) != manifest['files'][member.name]:
                    raise ValueError('File checksum mismatch')
            if json.loads((destination/'frontend/version.json').read_text())['revision'] != revision:
                raise ValueError('Frontend version mismatch')
        except Exception:
            shutil.rmtree(destination)
            raise

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['build', 'unpack'])
    parser.add_argument('source', type=Path)
    parser.add_argument('destination', type=Path)
    parser.add_argument('revision')
    args = parser.parse_args()
    if args.action == 'build':
        build(args.source, args.revision, args.destination)
    else:
        unpack(args.source, args.destination, args.revision)
