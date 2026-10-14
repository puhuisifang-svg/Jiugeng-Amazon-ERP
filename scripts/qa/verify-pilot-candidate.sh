#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)

echo "[1/7] backend clean verify"
(cd "$repo_root/deliverables/backend/sprint-0/jiugeng-erp-backend" && ./mvnw clean verify)

echo "[2/7] frontend quality"
(cd "$repo_root/frontend" && pnpm quality)

echo "[3/7] import contract"
(cd "$repo_root/deliverables/backend/sprint-0/import-contract/tests" && npm ci --ignore-scripts && npm test && python3 validate_contract.py)

echo "[4/7] active scope contract"
python3 "$repo_root/scripts/quality/validate-mvp-scope.py"

echo "[5/7] real MySQL migration, restart, and mock isolation"
"$repo_root/scripts/qa/verify-mysql-profile.sh"

echo "[6/7] actual MySQL backup and restore drill"
"$repo_root/scripts/qa/verify-mysql-backup-restore.sh"

echo "[7/7] lightweight performance baseline"
"$repo_root/scripts/qa/verify-performance-baseline.sh"

echo "PASS: ERP-020 pilot-candidate quality gate completed."
