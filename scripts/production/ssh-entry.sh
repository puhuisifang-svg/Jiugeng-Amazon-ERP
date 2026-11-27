#!/usr/bin/env bash
set -euo pipefail
# Forced SSH command: no shell, forwarding, file browsing or arbitrary sudo.
if [[ ${SSH_ORIGINAL_COMMAND:-} =~ ^deploy\ ([0-9a-f]{40})\ ([0-9a-f]{64})$ ]]; then
 exec sudo -n /usr/local/lib/jiugeng/receive.sh "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}"
fi
if [[ ${SSH_ORIGINAL_COMMAND:-} == stage-current || ${SSH_ORIGINAL_COMMAND:-} =~ ^stage-status\ [0-9a-f]{40}\ [0-9a-f]{64}\ [0-9]{1,9}$ || ${SSH_ORIGINAL_COMMAND:-} =~ ^stage-activate\ [0-9a-f]{40}\ [0-9a-f]{64}\ [0-9]{1,9}\ [0-9a-f]{40}$ || ${SSH_ORIGINAL_COMMAND:-} =~ ^stage-chunk\ [0-9a-f]{40}\ [0-9a-f]{64}\ [0-9]{1,9}\ [0-9]{1,9}\ [0-9]{1,7}\ [0-9a-f]{64}$ ]]; then
 read -r -a arguments <<< "$SSH_ORIGINAL_COMMAND"
 exec sudo -n /usr/local/lib/jiugeng/receive.sh "${arguments[@]}"
fi
echo 'Only a versioned deploy command is allowed' >&2
exit 2
