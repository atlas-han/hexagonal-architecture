#!/usr/bin/env bash
# SessionStart hook for the Kotlin Migration Harness.
#
# Emits a short status summary on stdout when a harness run is in progress.
# Stays silent (exit 0) when there is nothing to report, so users who are
# not running the harness don't see noise.
#
# Output is fed back into Claude's context as additional SessionStart info.
set -u

cd "${CLAUDE_PROJECT_DIR:-$(pwd)}" 2>/dev/null || exit 0

WS=".claude/harness/workspace"
SPEC="$WS/spec/product-spec.md"

[ -f "$SPEC" ] || exit 0

echo "## Kotlin Migration Harness — current state"
echo

# Count sprints and how many have PASSed.
total=$(grep -cE '^- sprint-[0-9]{2}: ' "$SPEC" 2>/dev/null)
total=${total:-0}
passed=0
if [ -d "$WS/reviews" ]; then
  shopt -s nullglob 2>/dev/null || true
  files=( "$WS/reviews"/sprint-*-review.md )
  if [ "${#files[@]}" -gt 0 ]; then
    passed=$(grep -lE '^STATUS: PASS$' "${files[@]}" 2>/dev/null | wc -l | tr -d ' ')
  fi
fi

echo "- spec:            $SPEC ($total sprints)"
echo "- reviews PASS:    $passed / $total"

# Find the smallest sprint number that is NOT yet PASS.
next=""
while IFS= read -r line; do
  nn=$(echo "$line" | sed -nE 's/^- sprint-([0-9]{2}): .*/\1/p')
  [ -z "$nn" ] && continue
  review="$WS/reviews/sprint-$nn-review.md"
  if [ -f "$review" ] && head -1 "$review" | grep -qE '^STATUS: PASS$'; then
    continue
  fi
  next="$nn"
  break
done < "$SPEC"

if [ -n "$next" ]; then
  echo "- next sprint:     sprint-$next"
  contract="$WS/contracts/sprint-$next-contract.md"
  handoff="$WS/handoffs/sprint-$next-handoff.md"
  review="$WS/reviews/sprint-$next-review.md"
  if [ -f "$review" ]; then
    s=$(head -1 "$review")
    echo "- last review:     $s ($review)"
  elif [ -f "$handoff" ]; then
    echo "- phase:           AWAITING EVALUATOR ($handoff exists, no review yet)"
  elif [ -f "$contract" ]; then
    s=$(head -1 "$contract")
    echo "- phase:           ${s:-CONTRACT IN PROGRESS} ($contract)"
  else
    echo "- phase:           NOT STARTED"
  fi
else
  echo "- next sprint:     none — all sprints PASS"
fi

# Worktree info, if present.
wt=$(git worktree list --porcelain 2>/dev/null | awk '/^worktree / {p=$2} /^branch refs\/heads\/harness\/kotlin-migration$/ {print p}')
if [ -n "$wt" ]; then
  echo "- worktree:        $wt"
  commits=$(git -C "$wt" log --oneline main..HEAD 2>/dev/null | wc -l | tr -d ' ')
  echo "- sprint commits:  $commits on harness/kotlin-migration"
fi

echo
echo "Run \`/harness\` (no args) to resume from sprint-${next:-XX}, or invoke the"
echo "harness-status skill for a full per-sprint table."
