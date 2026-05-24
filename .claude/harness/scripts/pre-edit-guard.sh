#!/usr/bin/env bash
# PreToolUse hook: warns when main session edits src/ outside a worktree.
# Warning only — exits 0 (allow). Does not block the edit.
set -u

payload="$(cat)"

file_path=""
if command -v jq >/dev/null 2>&1; then
  file_path="$(echo "$payload" | jq -r '.tool_input.file_path // empty' 2>/dev/null)"
fi
if [ -z "$file_path" ]; then
  file_path="$(echo "$payload" | sed -nE 's/.*"file_path"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' | head -n1)"
fi

[ -n "$file_path" ] || exit 0

case "$file_path" in
  */src/main/*|*/src/test/*)
    project_dir="${CLAUDE_PROJECT_DIR:-}"
    worktree_prefix="${project_dir}/.claude/worktrees/"
    case "$file_path" in
      "${worktree_prefix}"*)
        exit 0  # Inside worktree — allowed
        ;;
      *)
        echo "HARNESS WARNING: Editing production source outside a worktree."
        echo "  File: $file_path"
        echo "  All src/ edits should happen inside the Generator agent's worktree:"
        echo "  .claude/worktrees/harness/kotlin-migration/"
        echo "  Proceeding — verify this is intentional."
        exit 0
        ;;
    esac
    ;;
esac

exit 0
