STATUS: AGREED

# Sprint 02 Contract — `ActivityTimestamp` value class

## Sprint goal

Introduce `ActivityTimestamp`, a `@JvmInline value class` wrapping
`LocalDateTime`, to model "when an Activity occurred". Retype `Activity.timestamp`,
`ActivityWindow.getStartTimestamp()` / `getEndTimestamp()`, and the matching
test data. Keep persistence (`ActivityJpaEntity.timestamp: LocalDateTime?`)
and HTTP (`SendMoneyController`) at the raw `LocalDateTime`; conversion
happens at the `AccountMapper` boundary.

## Deliverable

Working code such that:

1. New file `src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt`
   exists with this shape:
   - `@JvmInline value class ActivityTimestamp(val value: LocalDateTime)`
   - Package `io.reflectoring.buckpal.account.domain`.
   - `companion object { fun now(): ActivityTimestamp }` factory.
2. `Activity.timestamp` is typed `ActivityTimestamp` on both
   constructors / primary properties.
3. `ActivityWindow.getStartTimestamp()` and `getEndTimestamp()` return
   `ActivityTimestamp`.
4. `Account.kt` `Activity(... LocalDateTime.now() ...)` call sites use
   `ActivityTimestamp.now()`.
5. `AccountMapper` converts at the JPA boundary:
   `ActivityJpaEntity.timestamp: LocalDateTime?` ↔
   `Activity.timestamp: ActivityTimestamp`.
6. Tests under `account/domain/` and `common/ActivityTestData.kt`,
   `AccountMapperTest.kt` updated to use `ActivityTimestamp`.
7. **Unchanged**: `ActivityJpaEntity.kt` (JPA column type `LocalDateTime?`),
   `ActivityRepository.kt` (HQL params), `SendMoneyController.kt` (HTTP),
   `SendMoneySystemTest.sql` (DB fixture), `BaselineDate.kt` (sprint-01),
   `LoadAccountPort.kt` (sprint-01 territory),
   `AccountPersistenceAdapter.kt` (sprint-01 territory; mapper is the seam).

## Inputs

- `.claude/harness/workspace/spec/product-spec.md` — sprint-02 section.
- `.claude/harness/workspace/handoffs/sprint-00-vo-candidates.md` — ADR row #2.
- Files in scope listed in spec sprint-02.

## Acceptance checks (Evaluator runs these directly)

The Evaluator MUST execute each command and quote the result.

1. `ActivityTimestamp.kt` exists:
   ```
   test -f src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt
   ```
   Expected: exit 0.

2. `ActivityTimestamp` is a `@JvmInline value class` wrapping
   `LocalDateTime`:
   ```
   grep -Eq '@JvmInline' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt \
     && grep -Eq 'value[[:space:]]+class[[:space:]]+ActivityTimestamp' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt \
     && grep -Eq 'val[[:space:]]+value[[:space:]]*:[[:space:]]*LocalDateTime' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt
   ```
   Expected: exit 0.

2b. `ActivityTimestamp` has `companion object` + `fun now(): ActivityTimestamp`:
   ```
   grep -q 'companion[[:space:]]*object' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt \
     && grep -Eq 'fun[[:space:]]+now\s*\(\s*\)[[:space:]]*:[[:space:]]*ActivityTimestamp' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt
   ```
   Expected: exit 0.

3. `Activity.timestamp` is typed `ActivityTimestamp`:
   ```
   grep -Eq 'timestamp[[:space:]]*:[[:space:]]*ActivityTimestamp' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/Activity.kt
   ```
   Expected: exit 0.

4. `ActivityWindow.getStartTimestamp()` and `getEndTimestamp()` both return
   `ActivityTimestamp`:
   ```
   grep -E 'fun (getStartTimestamp|getEndTimestamp)\s*\([^)]*\)\s*:\s*ActivityTimestamp' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindow.kt
   ```
   Expected: at least 2 matching lines.

5. No raw `LocalDateTime.now()` left in the `account/domain` source
   (the cutoff factory lives on the VOs):
   ```
   grep -RnE 'LocalDateTime\.now\s*\(\s*\)' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/
   ```
   Expected: no matches (exit 1).

6. `ActivityJpaEntity.timestamp` stays typed `LocalDateTime?` (JPA column
   intact):
   ```
   grep -Eq 'var[[:space:]]+timestamp[[:space:]]*:[[:space:]]*LocalDateTime\?' \
     src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt
   ```
   Expected: exit 0.

7. External-contract files unchanged in working tree (and in the sprint
   commit, when applied):
   ```
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt
   git diff --quiet -- src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql
   ```
   Each MUST exit 0.

8. Domain test suite passes:
   ```
   ./gradlew test --tests "io.reflectoring.buckpal.account.domain.*"
   ```
   Expected: BUILD SUCCESSFUL, exit 0.

9. Full clean build + check is green:
   ```
   ./gradlew clean build check
   ```
   Expected: BUILD SUCCESSFUL, exit 0. ArchUnit `DependencyRuleTests` green.

10. `SendMoneySystemTest` still passes:
    ```
    ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"
    ```
    Expected: BUILD SUCCESSFUL, exit 0.

11. No Lombok regressions:
    ```
    grep -R "import lombok" src/
    ```
    Expected: no matches (exit 1).

## Out of scope

- `BaselineDate` (sprint-01, already done).
- `BaselineBalanceFigures` mapper pair (sprint-03).
- Renaming `Activity.timestamp` (only its type changes).
- Any HQL / SQL / JPA column-type change.
- Editing `SendMoneyController.kt`.
- Editing `build.gradle`, `gradle.properties`, wrapper.

## Risks

- **Kotest `shouldBe` on the VO.** `ActivityWindowTest` and similar specs
  compare timestamps via `shouldBe`. After the retype, both sides must be
  `ActivityTimestamp` (auto-equality via the wrapped `LocalDateTime`).
  Spec risk register §4.
- **`ActivityWindow.minByOrNull` / `maxByOrNull` selectors.** Selectors
  currently return `LocalDateTime`; with the retype they must select on
  `Activity.timestamp.value` (or accept that `ActivityTimestamp` itself
  implements `Comparable` — easier path: select `.value`). Either is fine
  as long as the test ordering is preserved.
- **`AccountMapper.mapToJpaEntity` / `mapToDomainEntity` boundary.** Mapper
  must convert `LocalDateTime?` ↔ `ActivityTimestamp`. Null handling: the
  JPA column is nullable; map `null` to a sentinel or assert non-null on
  read (mapper currently treats it as non-null on read; preserve that).
- **`ActivityTestData.withTimestamp(...)`.** Spec allows an optional
  `LocalDateTime` convenience overload alongside the `ActivityTimestamp`
  one. Generator's choice — Evaluator does not require it.
- **Sprint-01 mockk workaround does NOT recur here.** Sprint-02 does not
  add `ActivityTimestamp` to any mockk-mocked port signature; the type
  appears only in domain classes, the mapper, and tests that construct
  values directly.

## Revision history

- v1 — Initial draft, addressing every hard exit criterion and external
  contract verification from spec sprint-02. Includes a mechanical
  `LocalDateTime.now()` leak grep (check 5) and a JPA column-type
  invariant (check 6).
