#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
log_file=$(mktemp "${TMPDIR:-/tmp}/jiugeng-erp-performance.XXXXXX")
trap 'rm -f "$log_file"' EXIT HUP INT TERM

(cd "$repo_root/deliverables/backend/sprint-0/jiugeng-erp-backend" && \
  ./mvnw -Dtest=PerformanceBaselineTest,ImportControllerTest#acceptsAndCommitsOneThousandProductRowsWithinGate test) | tee "$log_file"
grep '^PERF product-list ' "$log_file"
grep '^PERF import-products-1000 ' "$log_file"
echo "PASS: lightweight test-hardware performance thresholds were met; this is not a production capacity claim."
