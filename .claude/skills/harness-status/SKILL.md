---
name: harness-status
description: Summarize the current Kotlin Migration Harness state — which sprint is in progress, last PASS/FAIL, latest review excerpt, and worktree status. Use whenever the user asks about migration progress, "where are we", "어디까지 됐어", "현재 sprint", or after `/harness` paused with `needs input:`. Reads only files under .claude/harness/workspace/ and runs read-only git commands; never edits state.
---

# harness-status

You are reporting the **current state of the Kotlin Migration Harness**. Be
precise: every claim must be backed by a file or command. Do not guess.

## Steps

1. Check if `.claude/harness/workspace/spec/product-spec.md` exists.
   - Not found → report `harness state: not started` and stop.
2. Parse the `## Sprint Index` section at the end of the spec to get the
   full sprint list. Use regex `^- sprint-(\d{2}): (.+)$`.
3. For each sprint NN in order, classify its state:
   - `reviews/sprint-NN-review.md` first line == `STATUS: PASS` → **DONE**.
   - `reviews/sprint-NN-review.md` first line == `STATUS: FAIL` → **FAILED**
     (count how many handoff files exist for this sprint to infer retry count).
   - `handoffs/sprint-NN-handoff.md` exists but no review yet →
     **AWAITING EVALUATOR**.
   - `contracts/sprint-NN-contract.md` first line == `STATUS: AGREED`, no
     handoff yet → **IMPLEMENTING**.
   - `contracts/sprint-NN-contract.md` exists with `STATUS: NEEDS_REVISION`
     or no STATUS yet → **CONTRACT NEGOTIATION**.
   - Otherwise → **NOT STARTED**.
4. Check worktree state:
   - `git worktree list --porcelain` — does `harness/kotlin-migration`
     exist?
   - If yes, show its path and `git -C <path> log --oneline harness/kotlin-migration ^main`
     for the per-sprint commits.
5. Print a compact summary table (one row per sprint) plus a short narrative:
   - The current active sprint (first non-DONE row).
   - The next concrete action (e.g., "re-invoke Generator with last review").
   - If the most recent review is FAIL, quote the first 2–3 bullets of its
     *Bugs found* section so the user sees the blocker without opening files.

## Output shape (target)

```
Harness: <not started | running | paused | done>
Worktree: <path or "not present">
Branch:   harness/kotlin-migration (<N> sprint commits)

| Sprint | Title                | State              | Last note            |
|--------|----------------------|--------------------|----------------------|
| 00     | build config         | DONE               | commit abc1234       |
| 01     | common               | DONE               | commit def5678       |
| 02     | account/domain       | FAILED (try 2/3)   | Money equals scale   |
| 03     | port/in, port/out    | NOT STARTED        | —                    |
...

Next action: re-invoke Generator on sprint-02 with reviews/sprint-02-review.md.
```

For more detailed example outputs and scenario descriptions, see
`.claude/skills/harness-status/EXAMPLES.md`.

## Rules

- Do not edit any harness state file. This skill is **read-only**.
- Do not invoke the Planner / Generator / Evaluator yourself. If the user
  wants to resume, tell them to run `/harness` (no args = resume mode).
- Prefer one tight summary over per-file dumps — paths and line numbers, not
  giant excerpts.
