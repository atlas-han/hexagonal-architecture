# Migration Wrap-up — Domain Value Object Extraction (BuckPal Kotlin)

**Branch:** `claude/harness-domain-value-objects-5PRde`
**Generated:** 2026-05-27
**Status:** complete — 5/5 sprints PASS, final build green (`BUILD SUCCESSFUL in 25s`).

## 1. What shipped

This migration replaced three primitive leaks in the BuckPal Kotlin domain layer
with explicit Value Objects: `BaselineDate` (activity-window cutoff,
`@JvmInline value class` over `LocalDateTime`), `ActivityTimestamp` (instant an
Activity occurred, also a `@JvmInline value class` over `LocalDateTime`), and
`BaselineBalanceFigures` (a deposit/withdrawal `Money` pair as a `data class`
with a `toBaselineBalance()` helper). The application/port surface, services,
and persistence adapter now carry these VOs; primitives are unwrapped only at
the HQL boundary inside `AccountPersistenceAdapter`. Four candidates were
explicitly REJECTed in the sprint-00 ADR (`Account.baselineBalance` retype,
`transferThreshold` VO, `SendMoneyController` path variables, `Money` BigInteger
narrowing) — the rejection rationale is preserved in
`handoffs/sprint-04-vo-convention.md`. External contracts are byte-identical:
the HTTP path `POST /accounts/send/{Long}/{Long}/{Long}` is unchanged, JPA
column types and HQL parameter names are untouched, and ArchUnit hexagonal
layering still holds.

## 2. Sprint ledger

| # | Title | Commit | Status |
|---|-------|--------|--------|
| 00 | Analysis and ADR (read-only) — VO candidate inventory + decision table | `b3ddc61` | PASS |
| 01 | `BaselineDate` value class for activity-window cutoff | `81ad214` | PASS |
| 02 | `ActivityTimestamp` value class for activity occurrence instant | `49da855` | PASS |
| 03 | `BaselineBalanceFigures` data class for mapper deposit/withdrawal pair | `34813fa` | PASS |
| 04 | Final verification + VO convention notes (zero production source touched) | `966f04a` | PASS |

Pre-sprint commits (separate from the 5-sprint sequence, not counted above):
`51a0f50` planner output (product-spec.md), `7f78473` archive of previous run,
`5948495` JDK 21 → JDK 17 toolchain hot-fix in `build.gradle` /
`gradle.properties` (no `src/` touched).

## 3. Branch state

- **Branch:** `claude/harness-domain-value-objects-5PRde`
- **Sprint commits since baseline (`5948495`):** 5 (`b3ddc61` → `966f04a`).
- **`git diff 5948495..HEAD --stat -- src/`:** 19 files changed,
  169 insertions, 66 deletions. Net +103 lines for 3 VOs and call-site
  rewrites.
- **New files (3):**
  - `src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt`
  - `src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt`
  - `src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt`
- **External contract files verified untouched** since baseline `5948495`:
  - `account/adapter/in/web/SendMoneyController.kt` — HTTP path variables intact.
  - `account/adapter/out/persistence/AccountJpaEntity.kt` and
    `ActivityJpaEntity.kt` — JPA columns intact.
  - `account/adapter/out/persistence/ActivityRepository.kt` — HQL `:since`,
    `:until`, `:ownerAccountId`, `:accountId` parameters intact (byte-identical
    diff).
  - `src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql` —
    fixture unchanged.

## 4. Build status

```
./gradlew clean build check
BUILD SUCCESSFUL in 25s
```

Final verification recorded in `logs/run-log.md` at sprint-04 review
(`2026-05-27T14:15:46Z | STATUS=PASS`). All Kotest specs green; ArchUnit
`DependencyRuleTests` reports `tests=2 failures=0 errors=0`;
`BuckPalApplicationTests` (Spring context boot) and `SendMoneySystemTest`
(end-to-end HTTP transfer) both pass. Gitleaks reported `SKIPPED` every sprint
(no binary in the container — documented escape-hatch behaviour, not a FAIL).

## 5. Key learnings (highest-signal surprises — see `learnings.md` §2 for full detail)

- **Mockk 1.13.8 cannot mock methods whose parameter is a `@JvmInline value class`** — the boxed-vs-unboxed JVM ABI breaks matcher slot attribution. Sprint-01 replaced two `LoadAccountPort` mockk mocks with hand-rolled test doubles (`RecordingLoadAccountPort`, `StubbedLoadAccountPort`).
- **`ActivityWindow.minByOrNull(Activity::timestamp)` broke once `timestamp` became `ActivityTimestamp`** (no `Comparable`); fixed with the trailing-lambda selector `minByOrNull { it.timestamp.value }`.
- **Sprint-02 contract grep check 5 was self-contradictory** (forbade `LocalDateTime.now()` in `account/domain/` while the VO factories themselves were in that folder) — Evaluator ruled PASS by intent; recorded as a candidate for grep-scope pre-flight tightening.
- **Named-argument construction of `BaselineBalanceFigures(deposit, withdrawal)` rescued a silent positional-swap risk** — the old `mapToDomainEntity(_, _, withdrawalBalance, depositBalance)` signature had the *opposite* positional order from the new VO field order.
- **JDK 21 in the container vs Gradle 7.6.4 launcher** forced a separate hot-fix commit (`5948495`) pinning the Java toolchain to 17; any future container that unpins re-triggers `Unsupported class file major version 65`.

## 6. Next steps for the human

This work is complete and self-contained; the steps below require human
judgment and are not auto-performed.

- [ ] Review the diff: `git log --oneline 5948495..HEAD` and
      `git diff 5948495..HEAD -- src/` (19 files, +169/-66).
- [ ] Decide whether to open a PR against `main` (`gh pr create ...`) — this is
      a user decision; the orchestrator did not push or create a PR.
- [ ] Decide merge strategy if the PR is opened: squash merge (collapses the
      5 sprint commits into one) vs. merge commit (preserves the per-sprint
      history, which mirrors the harness ledger above).
- [ ] Optionally remove the worktree if you are done with it:
      `git worktree remove <path>`.
- [ ] Decide which deferred work from `learnings.md` §5 to schedule next —
      the most consequential candidates are listed in §7 below.

## 7. Risk register / follow-up candidates

The four highest-signal items from `learnings.md` §5, in suggested priority
order:

1. **`Money.amount: BigInteger` → `Long` silent narrowing in
   `AccountMapper.mapToJpaEntity`** (sprint-00 ADR row 7, REJECT /
   OUT-OF-SCOPE). Pre-existing precision-loss risk when a `Money` exceeds
   `Long.MAX_VALUE`. Needs its own multi-sprint stream — touches `Money`
   storage type, `ActivityJpaEntity.amount` column, the SQL fixture in
   `SendMoneySystemTest.sql`, and every arithmetic call site. Highest impact,
   highest scope.
2. **Mockk → newer version (or migration to a value-class-aware mocking
   library)**. Sprint-01 forced hand-rolled test doubles for `LoadAccountPort`
   because mockk 1.13.8 cannot bind matchers to a `@JvmInline value class`
   parameter slot. Check mockk's tracker for a fix; if available, restore the
   `verify { loadAccountPort.loadAccount(any(), any()) }` form in
   `SendMoneyServiceTest` / `GetAccountBalanceServiceTest`.
3. **Gradle upgrade (7.6.4 → 8.x)**. Deprecation warnings surface on every
   `./gradlew check`; an upgrade would also let the JDK 17 toolchain pin
   relax. Dedicated single-sprint stream, separate from VO work.
4. **Harness pre-flight: contract-grep scope tightening**. Recurring
   Evaluator finding (sprint-02, sprint-04): a grep over `account/domain/`
   that forbids primitive leaks at call sites will also match the VO
   declarations themselves. Add a pre-flight rule to `agents/planner.md` /
   `agents/evaluator.md` that flags any `rg|grep -RnE` over `account/domain/`
   for VO-file exclusion.

Three more deferred items live in `learnings.md` §5 and are lower priority:
Spring `Converter<String, TransferThreshold>` (likely never worth doing),
`SendMoneyController` path-variable retype (permanently out of scope — HTTP
contract), and `Account.baselineBalance` retype (rejected on the
"already-wrapped primitives do not get a second wrapper" rule).

---

## Artifacts produced

- `spec/product-spec.md` — the plan
- `contracts/sprint-NN-contract.md` × 5 — agreed sprint contracts
  (sprint-01 needed 4 NEEDS_REVISION rounds; the other 4 reached AGREED in 1)
- `handoffs/sprint-NN-handoff.md` × 4 (plus `sprint-00-vo-candidates.md` ADR
  and `sprint-04-vo-convention.md`)
- `reviews/sprint-NN-review.md` × 5 — Evaluator verdicts (all PASS on first
  submission; zero FAIL → retry cycles)
- `logs/run-log.md` — phase-by-phase transitions
- `learnings.md` — cross-sprint patterns + gotchas
- `wrap-up.md` — this file
