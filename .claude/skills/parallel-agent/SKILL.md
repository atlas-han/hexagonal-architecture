---
name: parallel-agent
description: Rules and patterns for forming Agent teams to run independent work in parallel. Use whenever multiple independent tasks can be done simultaneously — file reads, test executions, code searches, or multi-module verification. Applies to orchestrators, sub-agents, and any session doing exploratory or verification work.
---

# parallel-agent

Use this skill when you need to run multiple independent tasks and want to maximize
throughput by spawning Agent teams instead of working sequentially.

## When to parallelize

Spawn parallel agents when ALL of the following hold:
1. Tasks have **no data dependency** (Task B does not need Task A's output).
2. Tasks write to **different files** (no write conflicts).
3. Each task is substantial enough to justify agent overhead (>= 3 tool calls).

## When NOT to parallelize

- Sprint loop in `/harness` — each sprint's build depends on the previous.
- Any git sequence (add → commit → push must be sequential).
- Evaluator reviewing a handoff that Generator just wrote (sequential by design).
- Tasks that read then write the same file.

## Parallel patterns in this project

### Pattern 1: Parallel file reads (orchestrator pre-flight)

When you need to read multiple independent files at session start, read them
all in one message as parallel tool calls:

```
# Good — one message, multiple parallel Read calls
Read(spec/product-spec.md)
Read(reviews/sprint-02-review.md)
Read(contracts/sprint-02-contract.md)

# Bad — sequential
Read spec → then Read review → then Read contract
```

### Pattern 2: Parallel acceptance checks (Evaluator)

When the Evaluator runs acceptance checks from a contract, spawn one agent
per independent check group:

```
Agent(description="Evaluator: compile + unit tests",
      prompt="Run ./gradlew compileKotlin compileTestKotlin test ...")
Agent(description="Evaluator: ArchUnit + Lombok scan",
      prompt="Run ./gradlew check && grep -R 'import lombok' ...")
Agent(description="Evaluator: package layout diff",
      prompt="diff package trees java vs kotlin ...")
```

Collect results, then synthesize into sprint-NN-review.md.

### Pattern 3: Parallel search (Explore / research)

When searching for multiple unrelated symbols or patterns:

```
# One message — all searches fire simultaneously
Bash("grep -Rn 'AccountId' src/main/kotlin")
Bash("grep -Rn 'Optional<' src/main/kotlin")
Bash("grep -Rn 'import lombok' src/main/kotlin")
```

### Pattern 4: Parallel final verification (§4 of orchestrator)

After all sprints PASS, run the three build commands that can be staged
in parallel groups:

- Group A (independent): `./gradlew clean`, ArchUnit scan, Lombok scan
- Group B (depends on A): `./gradlew build`, `./gradlew check`

See `.claude/skills/parallel-agent/RULES.md` for the complete decision tree
and harness-specific guidance.
