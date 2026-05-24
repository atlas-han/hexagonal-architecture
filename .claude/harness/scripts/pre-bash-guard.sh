#!/usr/bin/env bash
# PreToolUse hook: blocks dangerous git commands that would destroy work.
# Exit code 2 = block the tool call. Exit code 0 = allow.
set -u

payload="$(cat)"

command_str=""
if command -v jq >/dev/null 2>&1; then
  command_str="$(echo "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null)"
fi
if [ -z "$command_str" ]; then
  command_str="$(echo "$payload" | sed -nE 's/.*"command"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)"
fi

[ -n "$command_str" ] || exit 0

# Block: force-push (may overwrite remote history)
# --force-with-lease is allowed: aborts safely if remote has unexpected changes.
if echo "$command_str" | grep -qE 'git\s+push\s+.*(-f\b|--force($|[^-]))'; then
  echo "BLOCKED: force-push detected — this can destroy remote history."
  echo "Command: $command_str"
  echo "Use --force-with-lease for safe force pushes, or ask the user explicitly."
  exit 2
fi

# Block: hard reset (destroys uncommitted work)
if echo "$command_str" | grep -qE 'git\s+reset\s+--hard'; then
  echo "BLOCKED: git reset --hard detected — this discards uncommitted changes."
  echo "Command: $command_str"
  echo "Confirm with the user before destructive reset."
  exit 2
fi

# Block: --no-verify (skips pre-commit hooks)
if echo "$command_str" | grep -qE '\-\-no\-verify'; then
  echo "BLOCKED: --no-verify detected — skipping git hooks is not allowed in the harness."
  echo "Command: $command_str"
  echo "Fix the underlying hook failure instead of bypassing it."
  exit 2
fi

# Block: git clean -f/-fd/-fx (deletes untracked files)
if echo "$command_str" | grep -qE 'git\s+clean\s+.*-[fdx]+'; then
  echo "BLOCKED: git clean -f detected — this permanently deletes untracked files."
  echo "Command: $command_str"
  echo "Confirm with the user before running git clean."
  exit 2
fi

exit 0
