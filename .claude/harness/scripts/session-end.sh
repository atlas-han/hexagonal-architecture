#!/usr/bin/env bash
# Stop hook: brief harness status on session end.
# Warns if the worktree has uncommitted changes that need attention.
set -u

cd "${CLAUDE_PROJECT_DIR:-$(pwd)}" 2>/dev/null || exit 0

WS=".claude/harness/workspace"
SPEC="$WS/spec/product-spec.md"

[ -f "$SPEC" ] || exit 0

total=$(grep -cE '^- sprint-[0-9]{2}: ' "$SPEC" 2>/dev/null || echo 0)
passed=0
if [ -d "$WS/reviews" ]; then
  shopt -s nullglob 2>/dev/null || true
  files=( "$WS/reviews"/sprint-*-review.md )
  if [ "${#files[@]}" -gt 0 ]; then
    passed=$(grep -lE '^STATUS: PASS$' "${files[@]}" 2>/dev/null | wc -l | tr -d ' ')
  fi
fi

echo "## Harness — session end"
echo "Sprints PASS: $passed / $total"

wt=$(git worktree list --porcelain 2>/dev/null | \
  awk '/^worktree / {p=$2} /^branch refs\/heads\/harness\/kotlin-migration$/ {print p}')
if [ -n "$wt" ] && [ -d "$wt" ]; then
  dirty=$(git -C "$wt" status --porcelain 2>/dev/null | wc -l | tr -d ' ')
  if [ "$dirty" -gt 0 ]; then
    echo "WARNING: worktree has $dirty uncommitted change(s) at $wt"
    echo "Run \`/harness\` (no args) to resume and commit."
  else
    echo "Worktree: clean at $wt"
  fi
fi

exit 0
