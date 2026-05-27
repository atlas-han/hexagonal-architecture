STATUS: AGREED

# Sprint 01 Contract — `BaselineDate` value class

## Sprint goal

Introduce `BaselineDate`, a `@JvmInline value class` wrapping `LocalDateTime`,
to represent the activity-window cutoff. Replace every raw `LocalDateTime`
that today carries this meaning across the application services, outgoing
port, and persistence adapter. Unwrap to `LocalDateTime` only at the
`ActivityRepository` JPA boundary.

## Deliverable

Working code such that:

1. New file `src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt`
   exists with this shape (or equivalent):
   - `@JvmInline value class BaselineDate(val value: LocalDateTime)`
   - Lives in package `io.reflectoring.buckpal.account.domain`.
   - Has a companion-object factory `now(): BaselineDate` returning
     `BaselineDate(LocalDateTime.now())`.
2. `LoadAccountPort.loadAccount` signature is
   `fun loadAccount(accountId: Account.AccountId, baselineDate: BaselineDate): Account`.
3. Every caller (services, persistence adapter) and every test mock/verify
   uses `BaselineDate`. The persistence adapter unwraps to `LocalDateTime`
   only when calling into `ActivityRepository`.
4. `ActivityRepository.kt`, `AccountJpaEntity.kt`, `ActivityJpaEntity.kt`,
   and `SendMoneyController.kt` are byte-identical to their pre-sprint
   versions.

## Inputs

- `.claude/harness/workspace/spec/product-spec.md` — sprint-01 section.
- `.claude/harness/workspace/handoffs/sprint-00-vo-candidates.md` — ADR
  row #1.
- Files in scope listed in spec sprint-01 (production + test).

## Acceptance checks (Evaluator runs these directly)

The Evaluator MUST execute each command and quote its result in the review.

1. `BaselineDate.kt` exists:
   ```
   test -f src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt
   ```
   Expected: exit 0.

2. `BaselineDate` is a `@JvmInline value class` wrapping `LocalDateTime`:
   ```
   grep -Eq '@JvmInline\s*$|@JvmInline[[:space:]]+value[[:space:]]+class' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt \
     && grep -Eq 'value[[:space:]]+class[[:space:]]+BaselineDate' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt \
     && grep -Eq 'val[[:space:]]+value[[:space:]]*:[[:space:]]*LocalDateTime' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt
   ```
   Expected: exit 0.

2b. `BaselineDate` exposes a `companion object` factory `now(): BaselineDate`:
   ```
   grep -q 'companion[[:space:]]*object' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt \
     && grep -Eq 'fun[[:space:]]+now\s*\(\s*\)[[:space:]]*:[[:space:]]*BaselineDate' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt
   ```
   Expected: exit 0 (both `companion object` and `fun now(): BaselineDate`
   appear in the same file).

3. `LoadAccountPort.loadAccount` uses `BaselineDate`:
   ```
   grep -E 'fun loadAccount.*baselineDate[[:space:]]*:[[:space:]]*BaselineDate' \
     src/main/kotlin/io/reflectoring/buckpal/account/application/port/out/LoadAccountPort.kt
   ```
   Expected: exit 0 (one matching line).

4. No raw `LocalDateTime.now()` leaks remain in the two service files that
   build the baseline cutoff (mechanical post-filter that proves the
   replacement is complete):
   ```
   grep -nE 'LocalDateTime\.now\s*\(\s*\)' \
     src/main/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyService.kt \
     src/main/kotlin/io/reflectoring/buckpal/account/application/service/GetAccountBalanceService.kt
   ```
   Expected: no matches (exit 1). Every former `LocalDateTime.now()` call site
   that fed `loadAccountPort.loadAccount` must now use `BaselineDate.now()`
   (or an explicit `BaselineDate(...)`).

4b. Every `loadAccountPort.loadAccount` call site in the two services is
   still present (positive existence check, paired with #4's negative leak
   check):
   ```
   grep -nE 'loadAccountPort\.loadAccount\b' \
     src/main/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyService.kt \
     src/main/kotlin/io/reflectoring/buckpal/account/application/service/GetAccountBalanceService.kt
   ```
   Expected: at least one matching line per file. (Used to confirm the
   replacement happened in-place rather than by deleting the call.)

5. External contract files unchanged in this sprint (working-tree diff
   against HEAD must be clean for each listed file):
   ```
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt
   ```
   Each MUST exit 0 (no uncommitted changes to these files at the time of
   review). After the sprint commit lands, the same check phrased as
   `git diff --quiet HEAD~1 HEAD -- <file>` MUST also exit 0 — i.e. the
   commit itself did not touch these files.

6. Full build + check is green:
   ```
   ./gradlew clean build check
   ```
   Expected: BUILD SUCCESSFUL, exit 0. All tests pass. ArchUnit
   `DependencyRuleTests` passes.

7. System test still passes:
   ```
   ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"
   ```
   Expected: BUILD SUCCESSFUL, exit 0.

8. No Lombok regressions:
   ```
   grep -R "import lombok" src/
   ```
   Expected: no matches (exit 1).

## Out of scope

- `Activity.timestamp` (that is sprint-02 `ActivityTimestamp`).
- Mapper baseline-balance pair (that is sprint-03 `BaselineBalanceFigures`).
- Any HQL / SQL / JPA column change.
- Editing `SendMoneyController.kt`.
- Editing `build.gradle`, `gradle.properties`, wrapper, or other build
  infrastructure.

## Risks

- **Forgetting a call site.** If a `loadAccountPort.loadAccount(...,
  LocalDateTime.now()...)` is missed (e.g. in a test fixture), the build
  will fail with a type mismatch. That is acceptable — the compiler is the
  enforcement mechanism. Check #4 mechanically guards the two services.
- **Test mocks/matchers.** Mockk `every { loadAccountPort.loadAccount(any(),
  any()) }` continues to work because `any()` is generic; explicit
  arguments need to be wrapped in `BaselineDate(...)`.
- **`open class Account` interaction.** `Account` is `open` for Mockito-style
  mocks. `BaselineDate` is an inline value class. Mockk's mocking of `Account`
  is unaffected by VO parameter types.
- **`ActivityRepository` HQL parameters stay primitive.** The adapter must
  call `repository.findByOwnerSince(accountId.value, baselineDate.value)` —
  pass `LocalDateTime`, not `BaselineDate`. Risk #1 in spec risk register.
- **`SendMoneySystemTest`.** Likely does not call `loadAccountPort.loadAccount`
  directly (HTTP-driven). Only wrap if a `LocalDateTime.now()` is passed to
  the port; otherwise leave the test untouched (per spec "Files in scope"
  conditional clause).
- **`AccountFactoriesTest` / `AccountTestData`.** If they reference the
  port, they may need updates; otherwise leave them alone.

## Revision history

- v2 (this version) — Addressed Evaluator Phase A round-1 feedback:
  - Added check 2b verifying the `companion object` `now(): BaselineDate`
    factory required by spec hard exit criterion #1.
  - Replaced check #4's prose-only "Reviewer inspects" tail with a
    mechanical `grep -nE 'LocalDateTime\.now\(\)'` negative check on the
    two service files. Added 4b as a positive existence check.
  - Removed the no-op `git diff --quiet HEAD~1 HEAD~1 -- ...` typo from
    check #5 and clarified the working-tree vs commit-diff intent.
