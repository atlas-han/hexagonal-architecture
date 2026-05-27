# Sprint 03 Handoff — `BaselineBalanceFigures` data class

## Summary

Replaced the positional `withdrawalBalance: Long, depositBalance: Long`
parameter pair on `AccountMapper.mapToDomainEntity` with a single
`BaselineBalanceFigures` data class carrying `Money` values. The pair is now
constructed once in `AccountPersistenceAdapter` (where the JPA aggregates
land, still with `?: 0L` null-coalescing) and threaded into the mapper as a
single named parameter. The mapper body uses `figures.toBaselineBalance()`,
which collapses to `deposit - withdrawal` via the existing `Money` operator
overload. No external contract surface (HTTP, JPA columns, HQL parameter
names) changed.

## Files changed

| File | Change |
|------|--------|
| `src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt` | **New.** `data class BaselineBalanceFigures(val deposit: Money, val withdrawal: Money)` with `fun toBaselineBalance(): Money = deposit - withdrawal`. |
| `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt` | Replaced the positional `withdrawalBalance: Long, depositBalance: Long` pair on `mapToDomainEntity` with a single `figures: BaselineBalanceFigures` parameter. Body uses `figures.toBaselineBalance()` instead of `Money.subtract(Money.of(depositBalance), Money.of(withdrawalBalance))`. Added `BaselineBalanceFigures` import. |
| `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.kt` | Still calls `activityRepository.getDepositBalanceUntil(...)` / `getWithdrawalBalanceUntil(...)` with `?: 0L`, but now wraps the two `Long`s into `BaselineBalanceFigures(deposit = Money.of(depositBalance), withdrawal = Money.of(withdrawalBalance))` before invoking the mapper. Added `BaselineBalanceFigures` and `Money` imports. |
| `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapperTest.kt` | Three `mapToDomainEntity(... withdrawalBalance = X, depositBalance = Y)` call sites rewritten as `mapToDomainEntity(... figures = BaselineBalanceFigures(deposit = Money.of(Y), withdrawal = Money.of(X)))`. The positional-to-named swap (old `depositBalance = Y` → new `deposit = Money.of(Y)`; old `withdrawalBalance = X` → new `withdrawal = Money.of(X)`) was applied carefully — the working case keeps `deposit = 700`, `withdrawal = 200`, asserting `result.baselineBalance shouldBe Money.of(500L)`. Added `BaselineBalanceFigures` import. |

Out-of-scope files diffed clean (see check 8 below).

## Self-check results

All 12 acceptance checks executed; every check matched its expected outcome.

| # | Check | Actual exit | Expected | Verdict |
|---|-------|-------------|----------|---------|
| 1 | `test -f .../BaselineBalanceFigures.kt` | `exit=0` | 0 | PASS |
| 2 | `grep` for `data class BaselineBalanceFigures` + `val deposit: Money` + `val withdrawal: Money` (chained `&&`) | `exit=0` | 0 | PASS |
| 3 | `grep -Eq '@JvmInline\|value class BaselineBalanceFigures'` | `exit=1` | 1 (no match) | PASS |
| 4 | `grep` for `fun toBaselineBalance(): Money` | `exit=0` | 0 | PASS |
| 5 | `grep` for `figures: BaselineBalanceFigures` in `AccountMapper.kt` | `exit=0` | 0 | PASS |
| 6 | `grep` for `withdrawalBalance: Long` in `AccountMapper.kt` | `exit=1` | 1 (no match) | PASS |
| 7 | `grep` for `BaselineBalanceFigures(` in `AccountPersistenceAdapter.kt` | `exit=0` | 0 | PASS |
| 8 | `git diff --quiet --` on the 7 external-contract files | all 7 `exit=0` | each 0 | PASS |
| 9 | `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.*"` | `BUILD SUCCESSFUL in 46s` | BUILD SUCCESSFUL | PASS |
| 10 | `./gradlew clean build check` | `BUILD SUCCESSFUL in 30s` | BUILD SUCCESSFUL | PASS |
| 11 | `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` | `BUILD SUCCESSFUL in 14s` | BUILD SUCCESSFUL | PASS |
| 12 | `grep -R "import lombok" src/` | `exit=1` | 1 (no match) | PASS |

Check 8 detail (each `git diff --quiet --` exited 0):
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt` — exit 0
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt` — exit 0
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt` — exit 0
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt` — exit 0
- `src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql` — exit 0
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt` — exit 0
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt` — exit 0

All checks PASS. Working tree is ready for the orchestrator to stage + commit.

## Commit

```
feat(domain): sprint-03 — extract BaselineBalanceFigures data class for mapper deposit/withdrawal pair
```

## Notes for Evaluator

- **Argument-order swap was the main hazard.** The old positional call had
  `withdrawalBalance` first, `depositBalance` second; the new
  `BaselineBalanceFigures` is constructed with `deposit` first, `withdrawal`
  second. The `AccountMapperTest` "baseline = deposit - withdrawal" case
  asserts `result.baselineBalance shouldBe Money.of(500L)` with inputs
  `deposit = 700, withdrawal = 200`, which would fail loudly if the swap had
  been muffed. Build is green, so the mapping is correct.
- **`Money.minus` is the existing operator overload.** `toBaselineBalance()`
  returns `deposit - withdrawal`, which is `Money` (not `BigInteger`) — the
  operator returns `Money(amount.subtract(...))`. The previous mapper body
  used `Money.subtract(Money.of(deposit), Money.of(withdrawal))` (static
  helper); both forms are semantically identical, but the operator form is
  more idiomatic now that both sides are already `Money`.
- **Null-coalescing preserved.** `AccountPersistenceAdapter` still does
  `... ?: 0L` on the `Long?` aggregates from `ActivityRepository` *before*
  wrapping into `Money.of(...)`. There is no `requireNotNull` introduced,
  per contract risk note.
- **Adapter → domain dependency direction.** `BaselineBalanceFigures` lives
  in `io.reflectoring.buckpal.account.domain`; `AccountMapper` (adapter)
  imports it. This is the allowed direction; `DependencyRuleTests` (ArchUnit)
  is run as part of `./gradlew check` and passed (check 10).
- **No `value class`.** Check 3 is explicit: `BaselineBalanceFigures` has two
  fields, so `data class` is the spec-mandated shape. The file contains
  neither `@JvmInline` nor `value class BaselineBalanceFigures`.
- **No mockk port mocks touched.** `BaselineBalanceFigures` only appears in
  mapper + adapter + mapper test. The sprint-01 mockk-with-VO workaround
  pattern was not needed here.
- **Pre-existing `BigInteger → Long` narrowing in `mapToJpaEntity`** is
  unchanged. Spec risk register row notes this is pre-existing, not a
  regression introduced by sprint-03.
