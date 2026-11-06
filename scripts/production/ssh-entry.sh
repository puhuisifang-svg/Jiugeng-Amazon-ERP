#!/usr/bin/env bash
set -euo pipefail
# Forced SSH command: no shell, forwarding, file browsing or arbitrary sudo.
if [[ ${SSH_ORIGINAL_COMMAND:-} =~ ^deploy\ ([0-9a-f]{40})\ ([0-9a-f]{64})$ ]]; then
 exec sudo -n /usr/local/lib/jiugeng/receive.sh "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}"
fi
echo 'Only a versioned deploy command is allowed' >&2
exit 2
