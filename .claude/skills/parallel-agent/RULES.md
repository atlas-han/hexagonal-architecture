# parallel-agent — Detailed Rules

## Decision tree: should I parallelize?

```
Is there a data dependency between tasks?
  YES → Sequential only. Stop.
  NO  ↓
Do tasks write to the same file or git index?
  YES → Sequential only. Stop.
  NO  ↓
Is each task substantial (>= 3 tool calls or > ~10s runtime)?
  NO  → Inline parallel tool calls (Read/Bash in one message). No Agent needed.
  YES → Spawn Agent team.
```

## Inline parallel calls (no sub-agent overhead)

Use within a single message when tasks are fast (reads, greps, status commands).

```
# Read 3 files at once
Read(".claude/harness/workspace/spec/product-spec.md")
Read(".claude/harness/workspace/reviews/sprint-05-review.md")
Read(".claude/harness/workspace/handoffs/sprint-05-handoff.md")

# Run 3 independent grep checks at once
Bash("grep -Rn 'import lombok' src/main/kotlin")
Bash("grep -Rn '@Autowired'    src/main/kotlin")
Bash("grep -Rn 'Optional<'     src/main/kotlin")
```

## Agent team spawn (parallel sub-agents)

Use when each task is a multi-step investigation or heavy build operation.

```python
# Template
Agent(
    description="<short label — appears in job list>",
    run_in_background=True,   # fire and continue
    prompt="""
    Context: <what you know so far>
    Task: <exactly what to do>
    Output: <what to write / return>
    Do NOT: <anything out of scope>
    """
)
```

After firing all agents, collect results (the main session is notified when
each completes). Never fire agents in sequence when they could run in parallel.

## Harness-specific parallel opportunities

### A. Evaluator acceptance checks (sprint-NN)

Each contract section can be verified by a separate agent:

| Agent | Checks |
|-------|--------|
| `eval-build` | `./gradlew compileKotlin compileTestKotlin` |
| `eval-tests` | `./gradlew test` — unit tests for the sprint's scope |
| `eval-archunit` | `./gradlew check` — ArchUnit DependencyRuleTests |
| `eval-lombok` | `grep -R "import lombok"` — must return empty |
| `eval-idioms` | `@Autowired`, `Optional<`, `!!` scans |

Spawn eval-build first (compile must pass before tests can run). Once it
returns PASS, spawn eval-tests + eval-archunit + eval-lombok + eval-idioms
in parallel.

### B. Final verification (§4 of orchestrator)

```
Sequential: ./gradlew clean
Parallel after clean:
  Agent: ./gradlew build
  Agent: grep -R "import lombok" ... (all sources)
  Agent: diff package trees
```

### C. Retrospective Phase L (learnings)

The Retrospective agent reads all sprint reviews. These reads can be done in
a single parallel batch before synthesizing.

### D. Session startup analysis

When `/harness` resumes, the orchestrator can read multiple artifacts in
parallel before deciding the entry point:
- `spec/product-spec.md`
- All `reviews/sprint-NN-review.md` files
- Worktree git log

## Coordination rules

1. **Write separation**: Parallel agents must write to different files.
   Use naming convention: `sprint-NN-<agent-name>.tmp` for intermediate
   output; orchestrator merges into final artifact.
2. **PASS/FAIL aggregation**: If any parallel eval agent returns FAIL, the
   sprint as a whole is FAIL. Do not declare PASS until all agents return.
3. **Timeout handling**: If a background agent has not returned after 10
   minutes, treat it as FAIL and surface `needs input:` rather than blocking
   indefinitely.
4. **Log ordering**: When multiple agents append to `run-log.md`, entries
   may arrive out of order. Sort by timestamp when reading the log.

## Anti-patterns to avoid

- Spawning N agents for N trivial one-line Bash calls — overhead exceeds
  benefit. Use inline parallel Bash instead.
- Parallel agents that both call `git commit` — git index is not
  concurrency-safe.
- Parallel Generator agents for different sprints — sprint N+1 may depend on
  N's class files; always wait for PASS before starting the next sprint.
