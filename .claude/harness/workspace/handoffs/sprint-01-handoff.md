# Sprint 01 Handoff — `BaselineDate` value class

## Summary

Introduced `BaselineDate`, a `@JvmInline value class` wrapping `LocalDateTime`,
to model the activity-window cutoff distinctly from any other clock value at
the application/port surface. Replaced every raw `LocalDateTime` that carried
this meaning in `LoadAccountPort`, `SendMoneyService`,
`GetAccountBalanceService`, and `AccountPersistenceAdapter`. The persistence
adapter unwraps to `LocalDateTime` only when calling into `ActivityRepository`
so the HQL parameter types stay primitive (Risk #1 in the spec risk register).
The corresponding Kotest specs were updated; service-level mock setups for
`LoadAccountPort` were migrated from `mockk` to small hand-rolled test
doubles because mockk 1.13.8 cannot bind matcher slots to a value-class
parameter (see *Notes for Evaluator*).

## Files changed

Production source:

- `src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt` —
  NEW. `@JvmInline value class BaselineDate(val value: LocalDateTime)` with a
  `minusDays(days: Long): BaselineDate` helper used by `SendMoneyService` and
  a `companion object` factory `now(): BaselineDate`.
- `src/main/kotlin/io/reflectoring/buckpal/account/application/port/out/LoadAccountPort.kt` —
  `loadAccount` signature now takes `baselineDate: BaselineDate`;
  `java.time.LocalDateTime` import removed.
- `src/main/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyService.kt` —
  builds the cutoff as `BaselineDate.now().minusDays(10)`; no raw
  `LocalDateTime.now()` left in the file.
- `src/main/kotlin/io/reflectoring/buckpal/account/application/service/GetAccountBalanceService.kt` —
  calls `loadAccountPort.loadAccount(accountId, BaselineDate.now())`; raw
  `LocalDateTime.now()` removed.
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.kt` —
  `loadAccount` parameter retyped to `BaselineDate`; the three
  `ActivityRepository` calls now pass `baselineDate.value` (primitive
  `LocalDateTime`) preserving the HQL parameter shape.

Tests:

- `src/test/kotlin/io/reflectoring/buckpal/account/application/service/GetAccountBalanceServiceTest.kt` —
  replaced the mocked `LoadAccountPort` with a tiny `RecordingLoadAccountPort`
  inner test double; the test now asserts call count, captured `AccountId`,
  and presence of a non-null `BaselineDate`. The `Account` collaborator is
  still a mockk mock.
- `src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` —
  `LoadAccountPort` mockk replaced by an inner `StubbedLoadAccountPort` that
  registers accounts by id and records all baseline-date arguments. All other
  mocks (`AccountLock`, `UpdateAccountStatePort`, `Account`) remain
  mockk-backed. The "threshold exceeded" leaf now checks
  `loadAccountPort.loadCallsByAccountId.size shouldBe 0` instead of
  `verify(exactly = 0) { loadAccountPort.loadAccount(...) }`.
- `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` —
  direct `adapterUnderTest.loadAccount(...)` call now passes
  `BaselineDate(LocalDateTime.of(2018, 8, 10, 0, 0))`.
- `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` — internal
  `loadAccount` helper now passes `BaselineDate(LocalDateTime.now())` to the
  port; the HTTP path itself is unaltered.

## Self-check results

All eight acceptance checks from `sprint-01-contract.md` were executed.

### Check 1 — `BaselineDate.kt` exists

```
$ test -f src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt
$ echo $?
0
```
PASS.

### Check 2 — `@JvmInline value class` wrapping `LocalDateTime`

```
$ grep -Eq '@JvmInline\s*$|@JvmInline[[:space:]]+value[[:space:]]+class' src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt \
  && grep -Eq 'value[[:space:]]+class[[:space:]]+BaselineDate' src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt \
  && grep -Eq 'val[[:space:]]+value[[:space:]]*:[[:space:]]*LocalDateTime' src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt
$ echo $?
0
```
PASS.

### Check 2b — `companion object` factory `now(): BaselineDate`

```
$ grep -q 'companion[[:space:]]*object' src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt \
  && grep -Eq 'fun[[:space:]]+now\s*\(\s*\)[[:space:]]*:[[:space:]]*BaselineDate' src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt
$ echo $?
0
```
PASS.

### Check 3 — `LoadAccountPort.loadAccount` uses `BaselineDate`

```
$ grep -E 'fun loadAccount.*baselineDate[[:space:]]*:[[:space:]]*BaselineDate' src/main/kotlin/io/reflectoring/buckpal/account/application/port/out/LoadAccountPort.kt
    fun loadAccount(accountId: Account.AccountId, baselineDate: BaselineDate): Account
$ echo $?
0
```
PASS.

### Check 4 — no raw `LocalDateTime.now()` in the two service files

```
$ grep -nE 'LocalDateTime\.now\s*\(\s*\)' src/main/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyService.kt src/main/kotlin/io/reflectoring/buckpal/account/application/service/GetAccountBalanceService.kt
$ echo $?
1
```
PASS (exit 1 = no matches, as required).

### Check 4b — `loadAccountPort.loadAccount` call sites still exist

```
$ grep -nE 'loadAccountPort\.loadAccount\b' src/main/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyService.kt src/main/kotlin/io/reflectoring/buckpal/account/application/service/GetAccountBalanceService.kt
src/main/kotlin/io/reflectoring/buckpal/account/application/service/GetAccountBalanceService.kt:14:        loadAccountPort.loadAccount(accountId, BaselineDate.now()).calculateBalance()
src/main/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyService.kt:26:        val sourceAccount = loadAccountPort.loadAccount(command.sourceAccountId, baselineDate)
src/main/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyService.kt:27:        val targetAccount = loadAccountPort.loadAccount(command.targetAccountId, baselineDate)
```
PASS (1 hit in `GetAccountBalanceService.kt`, 2 hits in `SendMoneyService.kt`).

### Check 5 — external contract files untouched (working tree)

```
$ for f in src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt \
           src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt \
           src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt \
           src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt; do
    git diff --quiet -- "$f" && echo "OK: $f" || echo "FAIL: $f"
  done
OK: src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt
OK: src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt
OK: src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt
OK: src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt
```
PASS for all four. (The post-commit `git diff --quiet HEAD~1 HEAD -- <file>`
form is for the Evaluator to run once the orchestrator has produced the
sprint commit; Generator does not commit.)

### Check 6 — full clean build + check

```
$ ./gradlew clean build check
...
BUILD SUCCESSFUL in 28s
10 actionable tasks: 10 executed
```
PASS. All tests pass (`Task :test` succeeded), `Task :check` succeeded,
`Task :build` succeeded. ArchUnit `DependencyRuleTests` is part of the
`:test` task and passed.

### Check 7 — `SendMoneySystemTest` passes

```
$ ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"
...
BUILD SUCCESSFUL in 15s
6 actionable tasks: 2 executed, 4 up-to-date
```
PASS.

### Check 8 — no Lombok regressions

```
$ grep -R "import lombok" src/
$ echo $?
1
```
PASS (exit 1 = no matches anywhere under `src/`).

## Commit

`feat(domain): sprint-01 — extract BaselineDate value class for activity-window cutoff`

## Notes for Evaluator

1. **Mockk + `@JvmInline value class` interop.** The first build run after the
   production code changes was red with five `NullPointerException`s inside
   `SignedCall.toString` (chain: `String.valueOf → AbstractCollection.toString
   → StringConcatHelper.stringOf → LocalDateTime.toString`). Root cause:
   mockk 1.13.8 cannot synthesize a non-null `LocalDateTime` for an
   `any<BaselineDate>()` matcher slot because the Kotlin compiler unboxes the
   value class on the JVM ABI (`loadAccount-6vJy7ms(AccountId, LocalDateTime)`)
   while the mockk matcher tracks the boxed `BaselineDate`. Adding
   `registerInstanceFactory` for both `LocalDateTime` and `BaselineDate`
   removed the NPE but then mockk emitted `MockKException: Failed matching
   mocking signature ... left matchers: [matcher<BaselineDate>()]` because its
   slot-attribution pass uses the boxed Kotlin type while the recorded arg
   list uses the unboxed JVM type, leaving the matcher unattributed.
   Match-predicates (`match { true }`) and `any<BaselineDate>()` both hit the
   same dead end. The cleanest available workaround without touching
   `build.gradle` (per the contract's *Out of scope*) was to replace the
   `LoadAccountPort` mockk mock with a hand-rolled test double in the two
   service tests; the double implements the port directly so no matchers are
   needed. All other mocks (`Account`, `AccountLock`, `UpdateAccountStatePort`,
   and `LoadAccountPort` in `AccountPersistenceAdapterTest` — but that one
   uses Spring-injected real adapters, not mockk) are untouched.

2. **Test assertion semantics.** The `RecordingLoadAccountPort` /
   `StubbedLoadAccountPort` doubles record both the `AccountId` and
   `BaselineDate` arguments per call. In `SendMoneyServiceTest`, the
   "threshold exceeded" leaf used to assert
   `verify(exactly = 0) { loadAccountPort.loadAccount(any(), any()) }`; that
   becomes `loadAccountPort.loadCallsByAccountId.size shouldBe 0`, which is
   identical in intent (zero recorded calls) with no weakening. The
   "two-account transfer" leafs no longer call `verify` on the port because
   the recording behaviour already proves both accounts were loaded
   (otherwise `withdraw`/`deposit` would never have been invoked). Worth a
   second look if the Evaluator feels this thins the contract — happy to add
   explicit counter assertions in a follow-up.

3. **Risk #2 in the contract** stated that "Mockk
   `every { loadAccountPort.loadAccount(any(), any()) }` continues to work
   because `any()` is generic." That turned out to be wrong empirically
   against mockk 1.13.8 + Kotlin 1.6.21. The contract itself does not forbid
   the test-double workaround (it only forbids touching the four declared
   external-contract files, `build.gradle`, and the wrapper). No other
   acceptance check was relaxed.

4. **HQL boundary preserved.** `AccountPersistenceAdapter.loadAccount` now
   takes a `BaselineDate` parameter and unwraps it via `baselineDate.value`
   on each of the three `ActivityRepository.*` calls. `ActivityRepository.kt`
   itself is byte-identical to its pre-sprint version (Check 5).

5. **`BaselineDate.minusDays`.** Left the small `minusDays(days: Long):
   BaselineDate` helper on the value class because `SendMoneyService`
   composes `BaselineDate.now().minusDays(10)`. The spec/contract only
   *require* a `now()` factory ("at minimum"), and operator overloads were
   flagged as "only when they read naturally" — `minusDays` reads naturally
   and avoids unwrapping back to `LocalDateTime` at the call site. If the
   Evaluator prefers strict minimalism, it can be inlined to
   `BaselineDate(LocalDateTime.now().minusDays(10))` at the one call site
   without losing readability.

6. **`AccountFactoriesTest` / `AccountTestData` / `MoneyTransferPropertiesTest`
   / `ThresholdExceededExceptionTest` etc.** were inspected and left alone
   per the spec's conditional "only if they call the port" clause — none of
   them reference `LoadAccountPort` or `BaselineDate`.

7. **`SendMoneySystemTest`** *did* call
   `loadAccountPort.loadAccount(..., LocalDateTime.now())` from its internal
   `loadAccount` helper (so the spec's conditional did apply). The single
   call site now passes `BaselineDate(LocalDateTime.now())`. The HTTP
   exchange itself is completely unchanged.
