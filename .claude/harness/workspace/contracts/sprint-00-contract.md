STATUS: AGREED

# Sprint 00 Contract — Analysis + ADR (read-only)

## Sprint goal

Lock down which Value Object candidates from the spec's inventory will be
extracted in subsequent sprints, by producing a reviewable ADR-style decision
table. No production or test code is modified in this sprint.

## Deliverable

A single new file:

- `.claude/harness/workspace/handoffs/sprint-00-vo-candidates.md`

The file MUST contain a markdown table with at least these columns, in this
order:

| Candidate | Current type | Decision (ACCEPT/REJECT) | Target VO type name | Implementing sprint | Rationale |

Rules for the table:

- Every row from the spec's "Candidate inventory" (rows 1–7) MUST appear,
  with the same ACCEPT/REJECT decision the spec states, OR with an explicit
  written justification under the table if the Generator chooses to flip a
  decision (which then must list which sprints are affected).
- ACCEPT rows MUST cite the sprint number that will implement the VO
  (`sprint-01` for `BaselineDate`, `sprint-02` for `ActivityTimestamp`,
  `sprint-03` for `BaselineBalanceFigures`).
- REJECT rows leave the "Implementing sprint" cell as `—`.
- The "Target VO type name" column is the proposed new Kotlin type name (or
  `—` for REJECT rows).

The handoff file should also include a short "Read-verified files" section
listing the production files the Generator actually opened to confirm the
inventory matches current code.

## Inputs (read-only)

The Generator may Read these files. It MUST NOT edit any of them:

- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Account.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Activity.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindow.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Money.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/port/in/SendMoneyCommand.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/port/in/GetAccountBalanceQuery.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/port/out/LoadAccountPort.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/port/out/UpdateAccountStatePort.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/port/out/AccountLock.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyService.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/service/GetAccountBalanceService.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/service/MoneyTransferProperties.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt`
- `src/main/kotlin/io/reflectoring/buckpal/BuckPalConfiguration.kt`
- `src/main/kotlin/io/reflectoring/buckpal/BuckPalConfigurationProperties.kt`

## Acceptance checks (Evaluator runs these directly)

The Evaluator MUST execute each command and record the result.

1. Deliverable exists:
   ```
   test -f .claude/harness/workspace/handoffs/sprint-00-vo-candidates.md
   ```
   Expected: exit 0.

2. Deliverable has the required columns. Grep for the header row:
   ```
   grep -F "| Candidate | Current type | Decision (ACCEPT/REJECT) | Target VO type name | Implementing sprint | Rationale |" \
     .claude/harness/workspace/handoffs/sprint-00-vo-candidates.md
   ```
   Expected: exit 0 (one matching line).

3. All 7 inventory candidates are listed. The handoff must contain at least
   one row per row of the spec inventory. As a lower bound, the file must
   include all of these strings:
   - `LocalDateTime` (rows 1, 2)
   - `withdrawalBalance` (row 3)
   - `baselineBalance` (row 4)
   - `transferThreshold` (row 5)
   - `SendMoneyController` (row 6)
   - `BigInteger` (row 7)

4. No production or test code touched:
   ```
   git diff --quiet -- src/main src/test
   ```
   Expected: exit 0 (clean).

5. External contracts unchanged (redundant with #4 but explicit):
   ```
   git diff --quiet -- \
     src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt \
     src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt \
     src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt
   ```
   Expected: exit 0.

6. Build is trivially green (nothing changed):
   ```
   ./gradlew test
   ```
   Expected: exit 0 (BUILD SUCCESSFUL, all test suites pass).

## Out of scope

- Editing any file under `src/main` or `src/test`.
- Editing `build.gradle.kts`.
- Creating the actual VO Kotlin files (those belong to sprint-01/02/03).
- Documentation in `README.md` or other production docs.
- Changing the spec itself (Planner already produced it).

## Risks

- **Generator flips a REJECT to ACCEPT (or vice versa) silently.** Any
  decision change MUST appear in writing under the table with rationale AND
  must call out which downstream sprints are impacted. If the change requires
  a new sprint, the Generator instead emits `// EVALUATOR:` style commentary
  and asks for spec revision rather than acting on it.
- **Accidental code edits while exploring.** The Generator uses Read only.
  Any file save under `src/` invalidates the sprint and forces a redo.
- **Deliverable filename typo.** The downstream sprints reference
  `sprint-00-vo-candidates.md` by exact path; any deviation breaks the chain.
- **Spec-vs-code drift.** If the actual code disagrees with the spec's row
  (e.g. a parameter has already been renamed), the handoff must note the
  discrepancy in a "Notes" sub-section beneath the table so a follow-up
  decision is visible.
- **New candidate discovered.** If the Generator finds a primitive leak the
  spec missed, it adds an 8th row with decision = `DEFER` and rationale =
  "out of current spec scope; consider a follow-up sprint." It does NOT
  silently implement the new VO.
