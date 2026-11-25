"""Build only the corporate website. Requires Python 3.9+."""
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src"
DIST = ROOT / "dist"


def build():
    # Only replace the directory previously created by this builder.
    if DIST.exists():
        if DIST.is_symlink() or not (DIST / "website-version.json").is_file():
            raise SystemExit("dist is not an existing website build")
        shutil.rmtree(DIST)
    shutil.copytree(SRC, DIST)
    digest = hashlib.sha256()
    for path in sorted(DIST.rglob("*")):
        if path.is_file():
            digest.update(path.relative_to(DIST).as_posix().encode() + b"\0")
            digest.update(hashlib.sha256(path.read_bytes()).digest())
    version = digest.hexdigest()
    (DIST / "website-version.json").write_text(
        json.dumps({"site": "jiugeng-corporate", "sha256": version}) + "\n"
    )
    print(f"website {version[:12]}: {DIST}")


if __name__ == "__main__":
    build()
