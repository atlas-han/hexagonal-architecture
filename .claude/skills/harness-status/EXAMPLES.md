# harness-status — Examples

## Example 1: 모든 sprint PASS된 상태

```
Harness: done
Worktree: .claude/worktrees/harness/kotlin-migration
Branch:   harness/kotlin-migration (9 sprint commits)

| Sprint | Title                                          | State | Last note       |
|--------|------------------------------------------------|-------|-----------------|
| 00     | build config — Kotlin Gradle plugin            | DONE  | commit a1b2c3d  |
| 01     | common — annotations and SelfValidating        | DONE  | commit e4f5g6h  |
| 02     | account/domain — Money, Activity, Account      | DONE  | commit i7j8k9l  |
| 03     | port/in, port/out                              | DONE  | commit m0n1o2p  |
| 04     | application/service                            | DONE  | commit q3r4s5t  |
| 05     | adapter/in/web                                 | DONE  | commit u6v7w8x  |
| 06     | adapter/out/persistence — JPA entities         | DONE  | commit y9z0a1b  |
| 07     | BuckPalApplication + Configuration             | DONE  | commit c2d3e4f  |
| 08     | test sources — JUnit + ArchUnit                | DONE  | commit g5h6i7j  |

Next action: none — retrospective pending. Run /harness (no args).
```

## Example 2: sprint-02 FAIL 상태 (재시도 중)

```
Harness: running
Worktree: .claude/worktrees/harness/kotlin-migration
Branch:   harness/kotlin-migration (2 sprint commits)

| Sprint | Title                                     | State            | Last note              |
|--------|-------------------------------------------|------------------|------------------------|
| 00     | build config                              | DONE             | commit a1b2c3d         |
| 01     | common                                    | DONE             | commit e4f5g6h         |
| 02     | account/domain — Money, Activity          | FAILED (try 2/3) | Money equals scale     |
| 03     | port/in, port/out                         | NOT STARTED      | —                      |
| ...    | ...                                       | NOT STARTED      | —                      |

Bugs found (sprint-02-review.md):
- Money.equals uses BigDecimal.equals — scale-sensitive: 10.0 != 10.00
- Activity data class missing @JvmField on id field for JPA compatibility

Next action: re-invoke Generator on sprint-02 with reviews/sprint-02-review.md.
```

## Example 3: 계약 협상 단계

```
Harness: running
Worktree: .claude/worktrees/harness/kotlin-migration

| Sprint | Title                   | State                | Last note                      |
|--------|-------------------------|----------------------|--------------------------------|
| 00     | build config            | DONE                 | commit a1b2c3d                 |
| 01     | common                  | CONTRACT NEGOTIATION | STATUS: NEEDS_REVISION (try 1) |
| ...    | ...                     | NOT STARTED          | —                              |

Next action: Generator must re-draft sprint-01-contract.md with Evaluator feedback.
```

## Invocation triggers

- 사용자: "어디까지 됐어?" / "현재 진행 상황은?" / "sprint 몇 개 남았어?"
- `/harness` 가 `needs input:` 로 멈춘 직후
- 오랜 시간이 지나고 세션을 재개했을 때 (SessionStart hook 이 이미 보여주는 것 보다 상세한 내용이 필요할 때)
