#!/usr/bin/env bash
# PostToolUse hook for the Kotlin Migration Harness.
#
# When the Write or Edit tool touches a file under
# .claude/harness/workspace/{spec,contracts,handoffs,reviews}/, append a
# timestamped audit line to .claude/harness/workspace/logs/run-log.md.
#
# Stays silent for any other path so unrelated edits don't trigger noise.
# Reads the tool-call JSON from stdin.
set -u

# Read the JSON payload Claude Code passes on stdin.
payload="$(cat)"

# Extract the file path. Prefer jq when available; fall back to a grep.
file_path=""
if command -v jq >/dev/null 2>&1; then
  file_path="$(echo "$payload" | jq -r '.tool_input.file_path // empty' 2>/dev/null)"
fi
if [ -z "$file_path" ]; then
  file_path="$(echo "$payload" | sed -nE 's/.*"file_path"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)"
fi

[ -n "$file_path" ] || exit 0

# Only act on harness workspace artifacts we care about.
case "$file_path" in
  *.claude/harness/workspace/spec/*.md) kind="spec" ;;
  *.claude/harness/workspace/contracts/*.md) kind="contract" ;;
  *.claude/harness/workspace/handoffs/*.md) kind="handoff" ;;
  *.claude/harness/workspace/reviews/*.md) kind="review" ;;
  *) exit 0 ;;
esac

cd "${CLAUDE_PROJECT_DIR:-$(pwd)}" 2>/dev/null || exit 0
log_dir=".claude/harness/workspace/logs"
log_file="$log_dir/run-log.md"
mkdir -p "$log_dir"
[ -f "$log_file" ] || printf "# Harness Run Log\n\n" > "$log_file"

ts="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
basename_only="$(basename "$file_path")"

# Try to capture the first-line STATUS (if any) for contract / review files.
status=""
if [ -f "$file_path" ]; then
  first_line="$(head -n1 "$file_path" 2>/dev/null)"
  case "$first_line" in
    STATUS:*) status="${first_line#STATUS: }" ;;
  esac
fi

if [ -n "$status" ]; then
  printf -- "- %s | %s | %s | STATUS=%s\n" "$ts" "$kind" "$basename_only" "$status" >> "$log_file"
else
  printf -- "- %s | %s | %s\n" "$ts" "$kind" "$basename_only" >> "$log_file"
fi

exit 0
