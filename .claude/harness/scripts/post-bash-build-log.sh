#!/usr/bin/env bash
# PostToolUse hook: logs ./gradlew failures to the harness run log.
# Silent when the command succeeds or is not a gradlew command.
set -u

payload="$(cat)"

command_str=""
exit_code="0"
if command -v jq >/dev/null 2>&1; then
  command_str="$(echo "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null)"
  exit_code="$(echo "$payload" | jq -r '.tool_response.exit_code // "0"' 2>/dev/null)"
fi
if [ -z "$command_str" ]; then
  command_str="$(echo "$payload" | sed -nE 's/.*"command"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)"
fi

# Only act on gradlew commands that failed
echo "$command_str" | grep -qE '\./gradlew' || exit 0
[ "${exit_code:-0}" = "0" ] && exit 0

cd "${CLAUDE_PROJECT_DIR:-$(pwd)}" 2>/dev/null || exit 0
log_dir=".claude/harness/workspace/logs"
log_file="$log_dir/run-log.md"
[ -d "$log_dir" ] || exit 0

ts="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
short_cmd="$(echo "$command_str" | tr -s ' ' | cut -c1-80)"
printf -- "- %s | build-failure | %s | exit=%s\n" "$ts" "$short_cmd" "${exit_code:-?}" >> "$log_file"

exit 0
