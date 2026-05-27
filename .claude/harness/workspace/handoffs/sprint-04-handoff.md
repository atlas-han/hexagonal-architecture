# Sprint 04 Handoff — Final verification + VO convention notes

## Summary

Sprint-04 is the verification + documentation sprint. The three VOs from
sprints 01–03 (`BaselineDate`, `ActivityTimestamp`, `BaselineBalanceFigures`)
are in place; this sprint proves the system is fully green end-to-end, the
boundary discipline holds, and the convention is captured in writing so
future contributors do not re-derive the decisions.

No production source changes were required this sprint — every grep-based
boundary check (VO construction containment, application/port LocalDateTime
leaks, JPA/HTTP/HQL boundary diff vs. the pre-sprint-00 baseline, Lombok
purity) already passes. The single new artifact is
`sprint-04-vo-convention.md`, which copies the sprint-00 ADR table verbatim
and explains the three INTENTIONAL primitive leaks (HTTP path vars,
`@ConfigurationProperties.transferThreshold`, JPA columns + HQL params).

## Files changed

No production source changes; 1 new workspace file:
- `.claude/harness/workspace/handoffs/sprint-04-vo-convention.md` —
  the standing VO convention record. Contains: "Where VOs live",
  "INTENTIONAL primitive leaks" (3 boundaries), "Quick reference" table for
  the 3 VOs, "Why three primitives stay raw" (the 4 REJECT decisions),
  the verbatim sprint-00 ADR 7-row decision table, and a "How to apply this
  convention" cheat sheet.

## Self-check results

All 11 acceptance checks from `sprint-04-contract.md` were executed. All
PASS.

### Check 1 — `./gradlew clean build check`

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

BUILD SUCCESSFUL in 26s
10 actionable tasks: 10 executed
```
PASS. Full build, all tests, ArchUnit `DependencyRuleTests` (part of
`:test`) included.

### Check 2 — `DependencyRuleTests` in isolation

```
$ ./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"
> Task :jacocoTestReport
BUILD SUCCESSFUL in 10s
6 actionable tasks: 2 executed, 4 up-to-date
```
PASS. Hexagonal ArchUnit check still green; no new
`domain → application/adapter` dependency was introduced.

### Check 3 — `BuckPalApplicationTests` (Spring context boots)

```
$ ./gradlew test --tests "io.reflectoring.buckpal.BuckPalApplicationTests"
> Task :jacocoTestReport
BUILD SUCCESSFUL in 14s
6 actionable tasks: 2 executed, 4 up-to-date
```
PASS. Spring context boots; `@ConfigurationProperties` binding for
`transferThreshold: Long` still works (one of the INTENTIONAL leaks).

### Check 4 — `SendMoneySystemTest` (end-to-end HTTP)

```
$ ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"
> Task :jacocoTestReport
BUILD SUCCESSFUL in 14s
6 actionable tasks: 2 executed, 4 up-to-date
```
PASS. Round-trip `POST /accounts/send/{Long}/{Long}/{Long}` works
byte-for-byte against the baseline.

### Check 5 — VO construction is bounded to the context

```
$ grep -rEn 'BaselineDate\(|ActivityTimestamp\(|BaselineBalanceFigures\(' \
    src/main/kotlin
src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt:56:                ActivityTimestamp(timestamp),
src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.kt:41:        val figures = BaselineBalanceFigures(
src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt:3:data class BaselineBalanceFigures(
src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt:6:value class BaselineDate(val value: LocalDateTime) {
src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt:8:    fun minusDays(days: Long): BaselineDate = BaselineDate(value.minusDays(days))
src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt:11:        fun now(): BaselineDate = BaselineDate(LocalDateTime.now())
src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt:13:value class ActivityTimestamp(val value: LocalDateTime) {
src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt:16:        fun now(): ActivityTimestamp = ActivityTimestamp(LocalDateTime.now())
```
PASS. Every hit is under `account/domain/` or `account/adapter/`. No hit
in `common/`, no hit in `BuckPalConfiguration*`, no hit at the package
root.

### Check 6 — no raw `LocalDateTime` in port surface

```
$ grep -rn 'LocalDateTime' src/main/kotlin/io/reflectoring/buckpal/account/application/port
$ echo $?
1
```
PASS (exit 1, no matches). Sprint-01 removed the raw `LocalDateTime`
parameter from `LoadAccountPort.loadAccount`; no leaks added since.

### Check 7 — convention handoff exists

```
$ test -f .claude/harness/workspace/handoffs/sprint-04-vo-convention.md
$ echo $?
0
```
PASS.

### Check 8 — convention covers 3 VOs + 3 INTENTIONAL leak boundaries

```
$ grep -q 'BaselineDate' .../sprint-04-vo-convention.md \
   && grep -q 'ActivityTimestamp' .../sprint-04-vo-convention.md \
   && grep -q 'BaselineBalanceFigures' .../sprint-04-vo-convention.md \
   && grep -q 'SendMoneyController' .../sprint-04-vo-convention.md \
   && grep -q 'transferThreshold' .../sprint-04-vo-convention.md \
   && grep -q 'ActivityJpaEntity' .../sprint-04-vo-convention.md
$ echo $?
0
```
PASS. All six required keywords present.

### Check 9 — JPA/HQL/SQL/HTTP boundary unchanged vs pre-sprint-00 baseline

```
$ git diff 5948495..HEAD -- \
    src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt \
    src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt \
    src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt \
    src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt \
    src/test/resources
$ # (no output)
```
PASS. Empty diff. The external HTTP contract, both JPA entities, the
HQL-bearing repository, and the SQL fixtures are byte-identical to the
pre-sprint-00 baseline (`5948495`, the JDK17 hot-fix; immediate parent of
`b3ddc61` = sprint-00).

### Check 10 — no Lombok imports

```
$ grep -R "import lombok" src/
$ echo $?
1
```
PASS (exit 1, no matches).

### Check 11 — sprint-00 ADR keyword coverage in convention

```
$ for kw in LocalDateTime withdrawalBalance baselineBalance transferThreshold SendMoneyController BigInteger; do
    grep -q "$kw" .claude/harness/workspace/handoffs/sprint-04-vo-convention.md \
      || echo "MISSING: $kw"
  done
$ # (no MISSING output)
```
PASS. All six keywords present in `sprint-04-vo-convention.md`.

## Commit

```
chore(harness): sprint-04 — final verification + VO convention notes
```

## Notes for Evaluator

1. **No production code touched this sprint.** The contract explicitly
   permits micro-edits for missed call sites, but checks 5, 6, and 9
   already passed cleanly against the sprint-03 tree, so no micro-edit was
   needed. The only new file is the convention handoff.

2. **Check 5 hits in `account/domain/` are construction lines inside the
   VO declarations themselves** (`value class BaselineDate(...)`,
   `data class BaselineBalanceFigures(...)`, and the `companion object`
   factories). These are the canonical, expected locations. The check's
   intent — "VO constructors are not called from outside the bounded
   context" — is met: every other hit is in
   `account/adapter/out/persistence/`, which is inside the bounded
   context.

3. **`account/application/` has no `BaselineDate(...)` construction line**
   because services exclusively use the `BaselineDate.now()` /
   `BaselineDate.now().minusDays(10)` factory chain. This is by design
   (sprint-01) and does not affect the contract's grep semantics.

4. **The convention file's "Why three primitives stay raw" section
   summarizes all 4 REJECT decisions** (rows 4–7 of the sprint-00 ADR),
   not just 3 — REJECT row 4 (`Account.baselineBalance` retype) is a
   bonus rejection that doesn't correspond to a runtime "primitive leak"
   (the field is already `Money`-wrapped), but is included for posterity
   so future contributors don't re-propose a `BaselineBalance` wrapper.

5. **Baseline ref for check 9 (`5948495`) is correct.** This is the
   immediate parent of `b3ddc61` (sprint-00), and is the JDK17 launcher
   hot-fix — the cleanest pre-VO baseline.
