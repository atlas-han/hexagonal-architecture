# Product Spec — Domain Value Object Extraction

## Migration goal

Inspect the Kotlin domain layer of BuckPal (`io.reflectoring.buckpal.account.domain.**`
plus its immediate collaborators in `application/**` and `adapter/**`) for
primitive-typed fields, parameters, and method signatures that carry domain
meaning today but are not represented by a Value Object. For each well-founded
candidate, extract a Kotlin `@JvmInline value class` (or `data class`, when
multiple fields belong together) that makes the meaning explicit and the API
type-safe. Do **not** invent VOs for primitives that already have one
(`Money`, `Account.AccountId`, `Activity.ActivityId`); do **not** modify the
HTTP path-variable contract (`/accounts/send/{Long}/{Long}/{Long}`), the JSON
body shape, or the JPA column types in `AccountJpaEntity` / `ActivityJpaEntity`.

The work proceeds as a small read-only analysis sprint followed by a focused
extraction sprint per VO, and concludes with a verification + convention sprint.

## Non-negotiable invariants

These must hold true at every sprint boundary (i.e. after every Generator
batch the Evaluator accepts):

- **External contracts unchanged.** The HTTP endpoint
  `POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}` still
  binds three `Long` path variables and returns the same status codes. JSON
  request/response shapes (if any) are byte-for-byte identical.
- **JPA schema unchanged.** `AccountJpaEntity` and `ActivityJpaEntity` keep
  their column names, column types, and `@GeneratedValue` semantics. The
  `account` and `activity` tables are untouched. `ActivityRepository`'s
  `@Query` HQL strings keep their `:ownerAccountId`, `:since`, `:until`,
  `:accountId` parameter names with their current Java types.
- **Hexagonal package boundaries unchanged.** All public package paths under
  `io.reflectoring.buckpal.**` remain stable. New VO types live in
  `io.reflectoring.buckpal.account.domain` (the domain owns its vocabulary)
  unless explicitly noted otherwise.
- **ArchUnit rules pass.** `DependencyRuleTests` (Kotest `FunSpec`) continues
  to import `io.reflectoring.buckpal..` and validate the hexagonal layering.
  No new dependency from `domain` outward.
- **Existing tests stay green without weakening assertions.** All Kotest
  specs (`AccountTest`, `ActivityTest`, `ActivityWindowTest`, `MoneyTest`,
  `AccountFactoriesTest`, `SendMoneyServiceTest`, `GetAccountBalanceServiceTest`,
  `MoneyTransferPropertiesTest`, `ThresholdExceededExceptionTest`,
  `AccountMapperTest`, `AccountPersistenceAdapterTest`, `SendMoneyControllerTest`,
  `SendMoneySystemTest`, `BuckPalApplicationTests`, `SelfValidatingTest`) keep
  passing.
- **`./gradlew clean build check` is green** at the end of every sprint.
- **No production-code changes in sprint-00.** Sprint-00 is analysis-only; its
  deliverable is a table inside the spec workspace (not in production source)
  documenting which candidates were accepted and which were rejected.
- **No new VOs for primitives already wrapped.** Do not introduce a second
  type for `Money`, `Account.AccountId`, or `Activity.ActivityId`.

## Target Kotlin conventions

These idioms guide the Generator. They are guidance, not law — deviate when
the code reads more clearly without them.

- **Prefer `@JvmInline value class` for single-field VOs** that wrap a primitive
  or a single domain object. This keeps allocation cost near-zero on the JVM
  while giving us a distinct type.
- **Prefer `data class` for multi-field VOs** (when two or more values belong
  together as one concept, e.g. a deposit/withdrawal pair).
- **VO constructors validate eagerly** via `init { require(...) }` for any
  invariant (non-negative, not-future, etc.). If the field has no meaningful
  invariant, omit the `init` block.
- **Companion-object factory `of(...)` is acceptable** when the call site
  reads better (mirrors `Money.of(Long)`), but is not required.
- **Operator overloads only when they read naturally.** Do not add `plus`/
  `minus` to a date-like VO unless a sprint actually needs it.
- **Java interop.** VOs that may be referenced from `internal` JPA mappers,
  Spring-managed beans, or Kotest specs do not need `@JvmStatic` / `@JvmField`
  unless an existing caller relies on a static form. Where they do (e.g.
  test data builders), preserve the call shape.
- **No reflection-only constructors.** VOs are plain Kotlin types; Jackson and
  JPA never see them (we keep adapters mapping to/from primitives at the
  edges).
- **One VO = one file** under `account/domain/`, named after the VO.

## Candidate inventory (informational; final list decided in sprint-00)

Read-only inspection of the current code surfaced the following primitive
leaks. Sprint-00 produces the binding decision table; later sprints implement
each accepted candidate.

| # | Location | Current type | Domain meaning | Recommendation |
|---|----------|--------------|----------------|----------------|
| 1 | `LoadAccountPort.loadAccount(_, baselineDate: LocalDateTime)`, `SendMoneyService` (`LocalDateTime.now().minusDays(10)`), `GetAccountBalanceService` (`LocalDateTime.now()`), `ActivityRepository.findByOwnerSince(_, since)`, `getDepositBalanceUntil(_, until)`, `getWithdrawalBalanceUntil(_, until)` | `LocalDateTime` | Cutoff for the activity window — "consider activities at-or-after this instant for the window; everything strictly before contributes to the baseline." | **Extract `BaselineDate` `@JvmInline value class`** (sprint-01). Strong: this concept is distinct from "when an activity happened" and is currently re-derived ad-hoc in three places. |
| 2 | `Activity.timestamp: LocalDateTime`, `ActivityWindow.getStartTimestamp()/getEndTimestamp()`, `ActivityWindowTest`, `ActivityJpaEntity.timestamp`, `ActivityTestData` | `LocalDateTime` | The instant a money-movement Activity occurred. Today it shares a type with the unrelated baseline cutoff. | **Extract `ActivityTimestamp` `@JvmInline value class`** (sprint-02). Justifies type-distinction from `BaselineDate`. JPA column stays `LocalDateTime` — mapper converts at the edge. |
| 3 | `AccountMapper.mapToDomainEntity(_, _, withdrawalBalance: Long, depositBalance: Long)` and its single caller `AccountPersistenceAdapter` (computing `withdrawalBalance` / `depositBalance` as `Long?` from JPA aggregates) | two positional `Long`s | A pair of partial balances (sum of withdrawals before baseline, sum of deposits before baseline). Two values that belong together; argument-order bugs are easy and silent. | **Extract `BaselineBalanceFigures(deposit: Money, withdrawal: Money)` `data class`** (sprint-03) with a `toBaselineBalance(): Money` helper that returns `deposit - withdrawal`. Mapper accepts one parameter instead of two raw `Long`s. |
| 4 | `Account.baselineBalance: Money` | `Money` | "Balance valid before the first activity in the window." Already a Money, but undistinguished from any other Money in the codebase. | **REJECT** — would add ceremony with no observable safety win; arithmetic with other `Money` instances would force constant unwrapping. Documented as rejected in sprint-00 ADR. |
| 5 | `BuckPalConfigurationProperties.transferThreshold: Long` | `Long` | Configured maximum transfer amount (minor units). | **REJECT for VO** — Spring `@ConfigurationProperties` binds primitives from YAML/properties; introducing a VO here forces a `Converter`. Already wrapped exactly once in `BuckPalConfiguration#moneyTransferProperties` into `Money.of(...)`. Documented as rejected in sprint-00 ADR. |
| 6 | `SendMoneyController` path variables (`Long`, `Long`, `Long`) | `Long` | External HTTP contract for account ids and amount. | **REJECT for VO** — the HTTP path is part of the public contract. Wrapping is already done inside the controller body (`Account.AccountId(...)`, `Money.of(...)`) which is the correct boundary. Documented in sprint-00. |
| 7 | `Money.amount: BigInteger` ↔ JPA `Long amount` (in `AccountMapper.mapToJpaEntity` via `activity.money.amount.toLong()`) | silent narrowing | Possible overflow when a Money exceeds `Long.MAX_VALUE`. | **Out of scope** — not a missing VO, but recorded in the risk register. |

If sprint-00 review uncovers other candidates we missed, that sprint may add
rows to its ADR table; later sprints adjust accordingly.

## Risk register

- **JPA `@Query` HQL parameter binding.** `ActivityRepository` HQL strings
  reference primitive-typed parameters (`:ownerAccountId` as `Long`,
  `:since`/`:until` as `LocalDateTime`). Any new VO must unwrap to the
  primitive at the call site (in `AccountPersistenceAdapter`). HQL is **not**
  to be edited.
- **`open class Account` for Mockito-style mocks.** `Account.id`, `withdraw`,
  `deposit`, `calculateBalance` are `open` because `SendMoneyServiceTest`
  mocks them. New VO parameters must not change the openness or the method
  signatures' externally observable shape (parameter names used by Mockk
  matchers can stay, since we use `any()` for VOs too).
- **`SelfValidating<SendMoneyCommand>` + javax-validation.** `SendMoneyCommand`
  uses `@field:NotNull`. A new VO field stays nullable-safe by Kotlin types;
  do not add `@NotNull` to value-class fields without confirming Hibernate
  Validator can traverse them.
- **Kotest `shouldBe` equality on dates.** `ActivityWindowTest` and
  `SendMoneySystemTest` compare timestamps with `shouldBe`. A `value class
  ActivityTimestamp(val value: LocalDateTime)` is `equals`-by-component, so
  test comparisons must compare *the same wrapper type* on both sides — the
  Generator must update both production and test call sites in the same
  sprint, not split across two sprints.
- **`@JvmInline value class` and `data class` companions.** `value class` does
  not auto-generate `copy()`; sprints that introduce one should not assume
  `.copy(...)` is available. Plain `data class` is fine for multi-field VOs.
- **Spring `@ConfigurationProperties` binders.** Do not migrate
  `transferThreshold` to a custom VO without writing a Spring `Converter`;
  sprint-00 records this as REJECT and out of scope.
- **Silent `BigInteger → Long` narrowing in `AccountMapper.mapToJpaEntity`.**
  Pre-existing; not introduced by this work. Noted so the Evaluator does not
  flag it as a regression.

---

## Sprint 0 — Analysis and ADR (read-only)

**User-visible goal.** Produce a written, reviewable decision record that
nails down which VOs will be extracted (and which were considered and
rejected), so later sprints execute against a fixed scope.

**Files in scope (read-only).** No production code is modified. The Generator
writes one analysis artifact:

- `.claude/harness/workspace/handoffs/sprint-00-vo-candidates.md` — an ADR-style
  table listing every candidate from the inventory above, marked
  `ACCEPT` / `REJECT`, with a one-sentence rationale, the target type name
  (`BaselineDate`, `ActivityTimestamp`, `BaselineBalanceFigures`, ...), and the
  sprint number that will implement it.

The Generator reads (only) these files to verify the inventory matches the
current code:

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

**Hard exit criteria.**
- The file `.claude/harness/workspace/handoffs/sprint-00-vo-candidates.md`
  exists.
- It contains a markdown table with at least these columns: `Candidate`,
  `Current type`, `Decision (ACCEPT/REJECT)`, `Target VO type name`,
  `Implementing sprint`, `Rationale`.
- Every candidate in this spec's inventory (rows 1–7) appears as a row,
  with the same ACCEPT/REJECT decisions called out here, or with an explicit
  written justification if the Generator wishes to change a decision.
- `git diff -- src/main/kotlin src/test/kotlin` is empty (no production or
  test changes this sprint).
- `./gradlew test` exits 0 (trivially, since nothing changed).

**External contract verification (must appear in Evaluator notes).**
- `git diff -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt`
  is empty.
- `git diff -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt`
  is empty.

**Out of scope.**
- Any change to production source.
- Any change to tests.
- Any change to `build.gradle.kts`.

---

## Sprint 1 — `BaselineDate` value class (cutoff date for activity windows)

**User-visible goal.** Replace the `LocalDateTime` parameter that means
"baseline cutoff for the activity window" with a dedicated
`BaselineDate` value class everywhere it flows: incoming application service
boundaries, outgoing port (`LoadAccountPort`), and the persistence adapter.
Unwrap to `LocalDateTime` only at the JPA repository boundary.

**Files in scope.**
- Create `src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt`.
- Edit `src/main/kotlin/io/reflectoring/buckpal/account/application/port/out/LoadAccountPort.kt`
  (parameter type only).
- Edit `src/main/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyService.kt`
  (construct `BaselineDate` from `LocalDateTime.now().minusDays(10)`).
- Edit `src/main/kotlin/io/reflectoring/buckpal/account/application/service/GetAccountBalanceService.kt`
  (construct `BaselineDate.now()` or equivalent).
- Edit `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.kt`
  (accept `BaselineDate`, pass `.value` to `ActivityRepository`).
- Edit `src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
  (mock setups and verifies must use `BaselineDate` matchers).
- Edit `src/test/kotlin/io/reflectoring/buckpal/account/application/service/GetAccountBalanceServiceTest.kt`
  (same).
- Edit `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt`
  (call sites use `BaselineDate`).
- Edit `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` *only if*
  it calls `loadAccountPort.loadAccount(_, LocalDateTime.now())` — wrap with
  `BaselineDate(...)`.

**Hard exit criteria.**
- `BaselineDate.kt` exists, is a `@JvmInline value class` wrapping a single
  `LocalDateTime` field named `value`, lives in package
  `io.reflectoring.buckpal.account.domain`, has at minimum a companion
  `now(): BaselineDate` factory.
- `LoadAccountPort.loadAccount` signature reads
  `fun loadAccount(accountId: Account.AccountId, baselineDate: BaselineDate): Account`.
- `ActivityRepository`'s HQL `:since` / `:until` parameters and Java types
  are unchanged (verify by diff: `git diff -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt`
  is empty).
- `./gradlew clean build check` exits 0.
- `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` exits 0
  (the round-trip HTTP path is unaffected).

**External contract verification.**
- `git diff -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt`
  is empty (HTTP path variables unchanged).
- `git diff -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt`
  is empty.

**Out of scope.**
- Activity occurrence timestamp (that's sprint-02).
- Mapper baseline-balance pair (that's sprint-03).
- Any HQL or schema change.

---

## Sprint 2 — `ActivityTimestamp` value class (when an Activity occurred)

**User-visible goal.** Replace the `LocalDateTime timestamp` field on
`Activity`, the return types of `ActivityWindow.getStartTimestamp()` /
`getEndTimestamp()`, and the matching test data with a dedicated
`ActivityTimestamp` value class — so the type system can no longer confuse
"when an activity happened" with `BaselineDate` (from sprint-01) or any
other clock value. Persistence and HTTP keep `LocalDateTime` at the edge;
conversion happens in `AccountMapper`.

**Files in scope.**
- Create `src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt`.
- Edit `src/main/kotlin/io/reflectoring/buckpal/account/domain/Activity.kt`
  (`timestamp: ActivityTimestamp`; both constructors updated).
- Edit `src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindow.kt`
  (`getStartTimestamp(): ActivityTimestamp`, `getEndTimestamp(): ActivityTimestamp`,
  `minByOrNull`/`maxByOrNull` selector unchanged in semantics).
- Edit `src/main/kotlin/io/reflectoring/buckpal/account/domain/Account.kt`
  (`Activity(... LocalDateTime.now() ...)` callers become
  `Activity(... ActivityTimestamp.now() ...)`).
- Edit `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt`
  (convert `ActivityJpaEntity.timestamp: LocalDateTime` ↔
  `Activity.timestamp: ActivityTimestamp` at the boundary; the JPA column
  type stays `LocalDateTime`).
- Edit `src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt`
  (assertions use `ActivityTimestamp`).
- Edit `src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityTest.kt`
  if it constructs `Activity` with a raw `LocalDateTime`.
- Edit `src/test/kotlin/io/reflectoring/buckpal/common/ActivityTestData.kt`
  (`withTimestamp(...)` overloads accept `ActivityTimestamp`; convenience
  overload accepting `LocalDateTime` is *optional* for test readability).
- Edit `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapperTest.kt`
  if it constructs/asserts on activity timestamps.

**Hard exit criteria.**
- `ActivityTimestamp.kt` is a `@JvmInline value class` wrapping a single
  `LocalDateTime` field named `value`, with at minimum a companion
  `now(): ActivityTimestamp` factory.
- `Activity.timestamp` is typed `ActivityTimestamp`; `ActivityJpaEntity.timestamp`
  stays typed `LocalDateTime?` (JPA-mapped column intact).
- `ActivityWindow.getStartTimestamp()` and `getEndTimestamp()` both return
  `ActivityTimestamp`.
- `./gradlew clean build check` exits 0.
- `./gradlew test --tests "io.reflectoring.buckpal.account.domain.*"` exits 0.

**External contract verification.**
- `git diff -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt`
  is empty.
- `git diff -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt`
  is empty (only `AccountMapper.kt` may change in the persistence adapter
  package besides files explicitly listed above).
- DB script `src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql`
  is unchanged.

**Out of scope.**
- `BaselineDate` (already done in sprint-01).
- The baseline-balance pair (sprint-03).
- Renaming `Activity.timestamp` to anything else; only its type changes.

---

## Sprint 3 — `BaselineBalanceFigures` data class (deposit/withdrawal pair)

**User-visible goal.** Replace the positional `withdrawalBalance: Long,
depositBalance: Long` parameter pair on `AccountMapper.mapToDomainEntity` with
a single `BaselineBalanceFigures` data class carrying `Money` values, so the
mapper can no longer be miscalled with swapped arguments. Build the figures
in `AccountPersistenceAdapter` where the JPA aggregates land.

**Files in scope.**
- Create `src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt`
  (data class with `deposit: Money`, `withdrawal: Money`; helper
  `fun toBaselineBalance(): Money = deposit - withdrawal`).
- Edit `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt`
  (signature becomes
  `fun mapToDomainEntity(account: AccountJpaEntity, activities: List<ActivityJpaEntity>, figures: BaselineBalanceFigures): Account`;
  internals use `figures.toBaselineBalance()`).
- Edit `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.kt`
  (build `BaselineBalanceFigures(deposit = Money.of(depositBalance), withdrawal = Money.of(withdrawalBalance))`
  before calling the mapper; raw `Long?` aggregates from
  `ActivityRepository.getDepositBalanceUntil` / `getWithdrawalBalanceUntil`
  are still defaulted to `0L` exactly as today).
- Edit `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapperTest.kt`
  (call sites updated to pass `BaselineBalanceFigures`).

**Hard exit criteria.**
- `BaselineBalanceFigures.kt` is a `data class` (NOT a `value class`, because
  it has two fields), located in `io.reflectoring.buckpal.account.domain`,
  with named parameters `deposit: Money, withdrawal: Money`.
- `AccountMapper.mapToDomainEntity` has exactly 3 parameters, and the
  baseline-related parameter is typed `BaselineBalanceFigures`.
- `ActivityRepository` interface is unchanged (its `Long?` returns are still
  consumed by `AccountPersistenceAdapter`, not by the mapper).
- `./gradlew clean build check` exits 0.
- `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.*"`
  exits 0.

**External contract verification.**
- `git diff -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt`
  is empty.
- `git diff -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt`
  is empty.
- `SendMoneySystemTest` round-trip still passes.

**Out of scope.**
- Reworking `ActivityRepository`'s aggregate queries.
- Touching `AccountJpaEntity` (this is an empty marker entity by design).
- Changing how `Account.baselineBalance` is stored on the domain entity.

---

## Sprint 4 — Final verification and VO convention notes

**User-visible goal.** Prove the system is fully green end-to-end with the
three new VOs in place, document the convention so future contributors keep
the boundary discipline, and leave nothing half-converted.

**Files in scope.**
- Verification only (no new production source). The Generator may make
  *micro*-edits to call sites it missed (e.g. an overlooked Kotest builder
  call), but no new VOs are introduced this sprint.
- Write `.claude/harness/workspace/handoffs/sprint-04-vo-convention.md`
  reproducing:
  - Where VOs live (`account/domain/`).
  - Which primitive leaks remain INTENTIONAL (HTTP path variables, Spring
    `@ConfigurationProperties`, JPA columns).
  - The accepted-vs-rejected table from sprint-00, copied for posterity.
  (Do not edit `README.md` or any production doc.)

**Hard exit criteria.**
- `./gradlew clean build check` exits 0 — full build, all tests.
- `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"`
  exits 0 (ArchUnit hexagonal check still passes; no new
  domain → application/adapter dependency was introduced).
- `./gradlew test --tests "io.reflectoring.buckpal.BuckPalApplicationTests"`
  exits 0 (Spring context still boots).
- `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"`
  exits 0 (end-to-end HTTP transfer still works).
- A grep proves no production file outside `account/domain/` constructs
  `BaselineDate(...)`, `ActivityTimestamp(...)`, or `BaselineBalanceFigures(...)`
  from raw primitives in a way that violates layer boundaries:
  ```
  rg --type kt -n 'BaselineDate\(|ActivityTimestamp\(|BaselineBalanceFigures\(' src/main/kotlin
  ```
  All hits are in `account/domain/`, `account/application/**`, or
  `account/adapter/**` (i.e. inside the bounded context).
- A grep proves no leftover raw `LocalDateTime` parameter in the
  application/port surface:
  ```
  rg --type kt -n 'LocalDateTime' src/main/kotlin/io/reflectoring/buckpal/account/application/port
  ```
  returns no matches (after sprint-01).
- The handoff file
  `.claude/harness/workspace/handoffs/sprint-04-vo-convention.md` exists.

**External contract verification.**
- `SendMoneySystemTest` still passes, proving
  `POST /accounts/send/{Long}/{Long}/{Long}` is byte-identical.
- Diff of `AccountJpaEntity.kt`, `ActivityJpaEntity.kt`, `ActivityRepository.kt`,
  and the SQL fixtures across the whole branch shows zero changes from the
  pre-sprint-00 baseline:
  ```
  git diff <baseline>..HEAD -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt src/test/resources
  ```
  must be empty.

**Out of scope.**
- Introducing additional VOs not on the sprint-00 ACCEPT list.
- Reworking the `Money` API.
- Editing `build.gradle.kts`.
- Renaming any existing class or package.
- Editing `README.md` or other production documentation files.

---

## Sprint Index

- sprint-00: analysis — read-only VO candidate inventory + ADR decision table
- sprint-01: BaselineDate — value class for the activity-window cutoff date
- sprint-02: ActivityTimestamp — value class for when an Activity occurred
- sprint-03: BaselineBalanceFigures — data class for the deposit/withdrawal pair in AccountMapper
- sprint-04: verification — full build, ArchUnit, system test, and VO convention notes
