STATUS: AGREED

# Sprint 03 Contract — `BaselineBalanceFigures` data class

## Sprint goal

Replace the positional `withdrawalBalance: Long, depositBalance: Long`
parameter pair on `AccountMapper.mapToDomainEntity` with a single
`BaselineBalanceFigures` `data class` carrying `Money` values, so the mapper
can no longer be miscalled with swapped arguments. Build the figures in
`AccountPersistenceAdapter` where the JPA aggregates land.

## Deliverable

Working code such that:

1. New file
   `src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt`:
   - `data class BaselineBalanceFigures(val deposit: Money, val withdrawal: Money)`
   - Package `io.reflectoring.buckpal.account.domain`.
   - `fun toBaselineBalance(): Money = deposit - withdrawal` helper.
2. `AccountMapper.mapToDomainEntity` signature is exactly:
   ```
   fun mapToDomainEntity(
       account: AccountJpaEntity,
       activities: List<ActivityJpaEntity>,
       figures: BaselineBalanceFigures,
   ): Account
   ```
   Body uses `figures.toBaselineBalance()` where the previous code computed
   `Money.of(depositBalance).minus(Money.of(withdrawalBalance))`.
3. `AccountPersistenceAdapter` constructs
   `BaselineBalanceFigures(deposit = Money.of(depositBalance), withdrawal = Money.of(withdrawalBalance))`
   before calling the mapper. The raw `Long?` aggregates from
   `ActivityRepository.getDepositBalanceUntil` /
   `getWithdrawalBalanceUntil` still default to `0L` exactly as today
   (no change to the null-coalescing rule).
4. `AccountMapperTest` call sites pass `BaselineBalanceFigures`.
5. **Unchanged**: `ActivityRepository.kt` (interface + HQL), `AccountJpaEntity.kt`
   (empty marker), `ActivityJpaEntity.kt`, `SendMoneyController.kt`,
   `BaselineDate.kt`, `ActivityTimestamp.kt`, `LoadAccountPort.kt`,
   `SendMoneySystemTest.sql`, `build.gradle`, `gradle.properties`,
   `Account.baselineBalance` (stored as `Money` on the domain entity — spec
   §4 row REJECT applies, do not introduce a separate VO).

## Inputs

- `.claude/harness/workspace/spec/product-spec.md` — sprint-03 section.
- `.claude/harness/workspace/handoffs/sprint-00-vo-candidates.md` — ADR row #3.
- Files in scope listed in spec sprint-03.

## Acceptance checks (Evaluator runs these directly)

The Evaluator MUST execute each command and quote the result.

1. `BaselineBalanceFigures.kt` exists:
   ```
   test -f src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt
   ```
   Expected: exit 0.

2. `BaselineBalanceFigures` is a `data class` with `deposit: Money` and
   `withdrawal: Money`:
   ```
   grep -Eq 'data[[:space:]]+class[[:space:]]+BaselineBalanceFigures' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt \
     && grep -Eq 'val[[:space:]]+deposit[[:space:]]*:[[:space:]]*Money' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt \
     && grep -Eq 'val[[:space:]]+withdrawal[[:space:]]*:[[:space:]]*Money' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt
   ```
   Expected: exit 0.

3. `BaselineBalanceFigures` is **not** a `value class` (the spec requires a
   `data class` because the type has two fields):
   ```
   grep -Eq '@JvmInline|value[[:space:]]+class[[:space:]]+BaselineBalanceFigures' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt
   ```
   Expected: exit 1 (no match).

4. `BaselineBalanceFigures.toBaselineBalance()` returns `Money`:
   ```
   grep -Eq 'fun[[:space:]]+toBaselineBalance\s*\(\s*\)[[:space:]]*:[[:space:]]*Money' \
     src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt
   ```
   Expected: exit 0.

5. `AccountMapper.mapToDomainEntity` accepts a `BaselineBalanceFigures`
   parameter:
   ```
   grep -Eq 'figures[[:space:]]*:[[:space:]]*BaselineBalanceFigures' \
     src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt
   ```
   Expected: exit 0.

6. `AccountMapper.mapToDomainEntity` no longer carries the old positional
   `withdrawalBalance: Long, depositBalance: Long` pair:
   ```
   grep -Eq 'withdrawalBalance[[:space:]]*:[[:space:]]*Long' \
     src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt
   ```
   Expected: exit 1 (no match).

7. `AccountPersistenceAdapter` constructs `BaselineBalanceFigures` and passes
   it to the mapper:
   ```
   grep -Eq 'BaselineBalanceFigures\s*\(' \
     src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.kt
   ```
   Expected: exit 0.

8. External-contract / out-of-scope files unchanged in working tree:
   ```
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt
   git diff --quiet -- src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt
   git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt
   ```
   Each MUST exit 0.

9. Persistence-adapter test suite passes:
   ```
   ./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.*"
   ```
   Expected: BUILD SUCCESSFUL, exit 0.

10. Full clean build + check is green:
    ```
    ./gradlew clean build check
    ```
    Expected: BUILD SUCCESSFUL, exit 0. ArchUnit `DependencyRuleTests` green.

11. `SendMoneySystemTest` still passes end-to-end:
    ```
    ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"
    ```
    Expected: BUILD SUCCESSFUL, exit 0.

12. No Lombok regressions:
    ```
    grep -R "import lombok" src/
    ```
    Expected: no matches (exit 1).

## Out of scope

- Reworking `ActivityRepository`'s aggregate queries.
- Touching `AccountJpaEntity` (empty marker entity by design).
- Changing how `Account.baselineBalance` is stored on the domain entity
  (spec inventory row #4 REJECT — see ADR).
- Editing `SendMoneyController.kt`, `BaselineDate.kt`, `ActivityTimestamp.kt`,
  `LoadAccountPort.kt`, build infrastructure, or `SendMoneySystemTest.sql`.

## Risks

- **Money arithmetic.** `Money` overloads `minus`/`plus`. Confirm
  `deposit - withdrawal` is `Money` (not `BigInteger`). The Generator should
  use the existing operator overload, not unwrap to `amount`.
- **Mapper Money construction site.** `AccountPersistenceAdapter` currently
  computes `Money.of(depositBalance ?: 0L)` and `Money.of(withdrawalBalance ?: 0L)`.
  Wrap into `BaselineBalanceFigures(deposit = ..., withdrawal = ...)` — keep
  the null-coalescing for the JPA aggregates. Risk: silent change of
  `0L`-default behaviour. Do not introduce a `requireNotNull` here.
- **`AccountMapperTest` call sites.** The test currently invokes
  `mapToDomainEntity(account, activities, withdrawalBalance = X,
  depositBalance = Y)`. After the change it becomes
  `mapToDomainEntity(account, activities, BaselineBalanceFigures(deposit =
  Money.of(Y), withdrawal = Money.of(X)))`. Note the parameter-name swap when
  going from "positional Long" to "named Money" — write both lines side by
  side and double-check.
- **`AccountMapperTest` may have an "argument-order swap" guard test.** If
  not, the new type provides the safety mechanically.
- **`ArchUnit DependencyRuleTests`.** `BaselineBalanceFigures` lives in
  `account.domain`; the mapper (adapter) imports it — allowed direction.
  No new domain → adapter dependency.
- **No mockk port mocks involve `BaselineBalanceFigures`.** The type only
  appears in mapper / adapter / mapper-test. Sprint-01's mockk workaround
  pattern does not need to repeat.

## Revision history

- v1 — Initial draft covering every hard exit criterion + external contract
  verification from spec sprint-03. Adds mechanical "no `value class`"
  (check 3) and "no positional `Long` pair" (check 6) guards.
