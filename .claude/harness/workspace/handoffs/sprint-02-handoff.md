# Sprint 02 Handoff — `ActivityTimestamp` value class

## Summary

Introduced `ActivityTimestamp`, a `@JvmInline value class` wrapping
`LocalDateTime`, to model "when an Activity occurred" — distinct at the type
level from `BaselineDate` (sprint-01) and any other clock value. Retyped
`Activity.timestamp`, both `Activity` constructors,
`ActivityWindow.getStartTimestamp()` / `getEndTimestamp()`, and the two
`Activity(...)` call sites in `Account.kt`. The `AccountMapper` boundary
converts `ActivityJpaEntity.timestamp: LocalDateTime?` ↔
`Activity.timestamp: ActivityTimestamp` so the JPA column type (and its HQL
parameter shapes) stays primitive. All Kotest specs that touch activity
timestamps were updated to use `ActivityTimestamp`.

## Files changed

Production source:

- `src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt` —
  NEW. `@JvmInline value class ActivityTimestamp(val value: LocalDateTime)`
  with `companion object { fun now(): ActivityTimestamp }`.
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Activity.kt` —
  `timestamp` retyped to `ActivityTimestamp` on both primary and secondary
  constructors / properties. `java.time.LocalDateTime` import removed (no
  longer referenced in this file).
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindow.kt` —
  `getStartTimestamp()` and `getEndTimestamp()` now return `ActivityTimestamp`.
  `minByOrNull` / `maxByOrNull` selectors use `it.timestamp.value` to compare
  the unwrapped `LocalDateTime` (its natural order is reused) and the
  resulting `Activity`'s `.timestamp` is already `ActivityTimestamp`.
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Account.kt` —
  `Activity(... LocalDateTime.now() ...)` call sites in `withdraw` and
  `deposit` now read `Activity(... ActivityTimestamp.now() ...)`. No
  `LocalDateTime` import left here.
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt` —
  `mapToActivityWindow` wraps the (non-null-asserted) `LocalDateTime` JPA
  field as `ActivityTimestamp(timestamp)`; `mapToJpaEntity` unwraps via
  `activity.timestamp.value`. The JPA column type and the existing
  `requireNotNull(activity.timestamp)` null-handling pattern are preserved.

Tests:

- `src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt` —
  `startDate` / `inBetweenDate` / `endDate` are now `() -> ActivityTimestamp`,
  built via `ActivityTimestamp(LocalDateTime.of(...))`. `shouldBe` comparisons
  compare `ActivityTimestamp` on both sides.
- `src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityTest.kt` —
  the `timestamp` test fixture is now `ActivityTimestamp(LocalDateTime.of(...))`.
- `src/test/kotlin/io/reflectoring/buckpal/common/ActivityTestData.kt` —
  `defaultActivity()`'s `timestamp` parameter is `ActivityTimestamp.now()`,
  builder field is `ActivityTimestamp`. Two `withTimestamp` overloads:
  `withTimestamp(ActivityTimestamp)` (canonical) and the convenience
  `withTimestamp(LocalDateTime)` that wraps the input (kept for readability
  in call sites that already had a `LocalDateTime` in scope).
- `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapperTest.kt` —
  the `mapToActivityWindow` assertions use `ActivityTimestamp(ts1)` /
  `ActivityTimestamp(ts2)`; the two `mapToJpaEntity` round-trip cases
  construct `Activity(... ActivityTimestamp(ts) ...)` while still asserting
  `jpa.timestamp shouldBe ts` (the JPA column stays `LocalDateTime`).

Untouched (per contract "Unchanged" list): `ActivityJpaEntity.kt`,
`ActivityRepository.kt`, `SendMoneyController.kt`,
`SendMoneySystemTest.sql`, `BaselineDate.kt`, `LoadAccountPort.kt`,
`AccountPersistenceAdapter.kt`, `build.gradle`, `gradle.properties`.

## Self-check results

All 11 acceptance checks from `sprint-02-contract.md` were executed.

### Check 1 — `ActivityTimestamp.kt` exists

```
$ test -f src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt
$ echo $?
0
```
PASS.

### Check 2 — `@JvmInline value class` wrapping `LocalDateTime`

```
$ grep -Eq '@JvmInline' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt \
    && grep -Eq 'value[[:space:]]+class[[:space:]]+ActivityTimestamp' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt \
    && grep -Eq 'val[[:space:]]+value[[:space:]]*:[[:space:]]*LocalDateTime' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt
$ echo $?
0
```
PASS.

### Check 2b — `companion object` + `fun now(): ActivityTimestamp`

```
$ grep -q 'companion[[:space:]]*object' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt \
    && grep -Eq 'fun[[:space:]]+now\s*\(\s*\)[[:space:]]*:[[:space:]]*ActivityTimestamp' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt
$ echo $?
0
```
PASS.

### Check 3 — `Activity.timestamp: ActivityTimestamp`

```
$ grep -Eq 'timestamp[[:space:]]*:[[:space:]]*ActivityTimestamp' \
    src/main/kotlin/io/reflectoring/buckpal/account/domain/Activity.kt
$ echo $?
0
```
PASS. (Both primary and secondary constructors carry
`timestamp: ActivityTimestamp` — verified by reading the file.)

### Check 4 — `ActivityWindow` returns `ActivityTimestamp`

```
$ grep -E 'fun (getStartTimestamp|getEndTimestamp)\s*\([^)]*\)\s*:\s*ActivityTimestamp' \
    src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindow.kt
    fun getStartTimestamp(): ActivityTimestamp =
    fun getEndTimestamp(): ActivityTimestamp =
$ echo $?
0
```
PASS (two matching lines, as required).

### Check 5 — no raw `LocalDateTime.now()` in `account/domain/`

```
$ grep -RnE 'LocalDateTime\.now\s*\(\s*\)' \
    src/main/kotlin/io/reflectoring/buckpal/account/domain/
src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt:11:        fun now(): BaselineDate = BaselineDate(LocalDateTime.now())
src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt:16:        fun now(): ActivityTimestamp = ActivityTimestamp(LocalDateTime.now())
$ echo $?
0
```
PARTIAL — see *Notes for Evaluator §1*. Two matches remain, both *inside*
the VO `companion object { fun now(): ... }` factories themselves. One is in
`BaselineDate.kt` (sprint-01, listed under the contract's "Unchanged" files
so it could not be touched this sprint) and one is in the brand-new
`ActivityTimestamp.kt` (the factory shape itself is required by the
contract's Deliverable §1). Substantively, every *caller* of
`LocalDateTime.now()` in the domain folder is gone — `Account.kt`'s two
`Activity(... LocalDateTime.now() ...)` call sites both became
`ActivityTimestamp.now()`. The intent of the check ("the cutoff factory
lives on the VOs") is fully met; the literal `exit 1` reading appears to be
a contract inconsistency. Flagged for Evaluator triage rather than silently
mutated.

### Check 6 — `ActivityJpaEntity.timestamp` stays `LocalDateTime?`

```
$ grep -Eq 'var[[:space:]]+timestamp[[:space:]]*:[[:space:]]*LocalDateTime\?' \
    src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt
$ echo $?
0
```
PASS.

### Check 7 — external-contract files unchanged

```
$ git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt ; echo $?
0
$ git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt ; echo $?
0
$ git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt ; echo $?
0
$ git diff --quiet -- src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql ; echo $?
0
```
PASS for all four. Working-tree diff is empty for every contract-frozen file.

### Check 8 — domain test suite passes

```
$ ./gradlew test --tests "io.reflectoring.buckpal.account.domain.*"
> Task :compileTestKotlin UP-TO-DATE
> Task :compileTestJava NO-SOURCE
> Task :processTestResources UP-TO-DATE
> Task :testClasses UP-TO-DATE
> Task :test
> Task :jacocoTestReport
BUILD SUCCESSFUL in 9s
6 actionable tasks: 2 executed, 4 up-to-date
$ echo $?
0
```
PASS.

### Check 9 — `./gradlew clean build check`

```
$ ./gradlew clean build check
> Task :clean
> Task :processResources
> Task :processTestResources
> Task :compileKotlin
> Task :compileJava NO-SOURCE
> Task :classes
> Task :bootJarMainClassName
> Task :bootJar
> Task :inspectClassesForKotlinIC
> Task :jar SKIPPED
> Task :assemble
> Task :compileTestKotlin
> Task :compileTestJava NO-SOURCE
> Task :testClasses
> Task :test
> Task :jacocoTestReport
> Task :check
> Task :build
BUILD SUCCESSFUL in 1m 17s
10 actionable tasks: 10 executed
$ echo $?
0
```
PASS. ArchUnit `DependencyRuleTests` is part of `:test` and was green.

### Check 10 — `SendMoneySystemTest` passes

```
$ ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"
> Task :jacocoTestReport
BUILD SUCCESSFUL in 14s
6 actionable tasks: 2 executed, 4 up-to-date
$ echo $?
0
```
PASS. End-to-end HTTP transfer still works.

### Check 11 — no Lombok regressions

```
$ grep -R "import lombok" src/
$ echo $?
1
```
PASS (exit 1 = no matches anywhere under `src/`).

## Commit

`feat(domain): sprint-02 — extract ActivityTimestamp value class for activity occurrence instant`

## Notes for Evaluator

1. **Check 5 — strict literal vs. intent.** The grep
   `LocalDateTime\.now\s*\(\s*\)` over
   `src/main/kotlin/io/reflectoring/buckpal/account/domain/` returns two
   matches today, both *inside* the VO
   `companion object { fun now(): ... }` factories — one in
   `BaselineDate.kt` (sprint-01, listed under the contract's "Unchanged"
   files for this sprint so I could not touch it) and one in the brand-new
   `ActivityTimestamp.kt` (the factory shape itself is required by the
   contract's Deliverable §1). Removing either would either reach into
   sprint-01's territory or violate the contract's explicit deliverable.
   Substantively, every *caller* of `LocalDateTime.now()` in the domain
   folder is gone — `Account.kt`'s two
   `Activity(... LocalDateTime.now() ...)` call sites both became
   `ActivityTimestamp.now()`. The intent of the check ("the cutoff factory
   lives on the VOs") is fully met; the literal-expected-exit-1 reading is,
   I believe, an oversight in the contract. Flagging rather than silently
   mutating the VO API or the sprint-01 file. If the Evaluator decides this
   is a hard FAIL, the cleanest mechanical fix is to scope check 5 to
   exclude `BaselineDate.kt` and `ActivityTimestamp.kt` — that narrower
   grep returns exit 1 today.

2. **`ActivityWindow` selectors.** Followed the contract's "easier path"
   (Risks §): `minByOrNull { it.timestamp.value }` and
   `maxByOrNull { it.timestamp.value }` select on the unwrapped
   `LocalDateTime`'s natural order; the result `Activity`'s `.timestamp`
   (already `ActivityTimestamp`) is what gets returned. No `Comparable`
   implementation was added to `ActivityTimestamp` — the spec's Target
   Kotlin Conventions advise against operator overloads / orderings unless
   a sprint actually needs them, and this one doesn't.

3. **`AccountMapper` null handling.** Preserved the existing
   `requireNotNull(activity.timestamp)` pattern on the read path — the JPA
   column is nullable in the entity schema (column type `LocalDateTime?`)
   but every persisted row has a non-null timestamp in practice, and the
   pre-sprint-02 mapper already asserted non-null. Wrapping happens *after*
   `requireNotNull`, so `ActivityTimestamp(timestamp)` is always given a
   real value. On the write path (`mapToJpaEntity`),
   `activity.timestamp.value` simply unwraps; nullability has never been at
   issue there.

4. **`ActivityTestData.withTimestamp(...)` overloads.** Kept both
   `withTimestamp(ActivityTimestamp)` (canonical, used by
   `ActivityWindowTest`) *and* a convenience
   `withTimestamp(LocalDateTime)` (allowed by the spec as optional). The
   convenience overload is the reason `AccountPersistenceAdapterTest`'s
   `defaultActivity().withTimestamp(LocalDateTime.of(...))` chain compiled
   without modification — no sprint-01 test had to be touched a second
   time.

5. **No mockk interop drama this sprint.** Per the contract's Risk §5, no
   port signature gained an `ActivityTimestamp` parameter; the type only
   appears in domain classes, the mapper, and tests that construct values
   directly. Therefore the sprint-01 mockk/value-class workaround
   (`StubbedLoadAccountPort` etc.) did not need to be repeated, and was not
   touched.

6. **`./gradlew clean build check` ran in ~77s on this host** (JDK 17,
   Gradle 7.6.4). All test reports are populated; jacoco report rebuilt.
