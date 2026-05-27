# Value Object Convention — BuckPal Domain Layer

This note is the standing record of the Value Object (VO) discipline applied
to the BuckPal Kotlin domain layer across sprints 00–03. It exists so that
future contributors can keep the boundary discipline without re-deriving
the decisions from scratch. The decision table in
`sprint-00-vo-candidates.md` is reproduced verbatim below for posterity in
case that sprint-00 artifact is ever archived elsewhere.

---

## Where VOs live

All new VOs introduced by this work — `BaselineDate`, `ActivityTimestamp`,
and `BaselineBalanceFigures` — live in the package
`io.reflectoring.buckpal.account.domain`. One VO per file, named after the
type. The application layer (`account.application.port.*`,
`account.application.service.*`) and the adapter layer
(`account.adapter.in.web`, `account.adapter.out.persistence`) import them.
The domain layer never imports outward — `DependencyRuleTests` (the
ArchUnit hexagonal check) is part of `./gradlew check` and enforces this
direction on every build.

---

## INTENTIONAL primitive leaks

Three boundaries deliberately keep raw primitive types. Each of them is a
boundary the codebase does not own — wrapping at the boundary would buy no
type-safety while costing either an explicit converter, schema churn, or
external-contract breakage.

### 1. HTTP path variables in `SendMoneyController`

The endpoint `POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}`
binds three `Long` `@PathVariable`s. These `Long`s are the external HTTP
contract — changing them would alter the URL contract that clients depend
on. `SendMoneyController` wraps each path variable into `Account.AccountId(...)`
and `Money.of(...)` *on the very next line*, which is the correct
boundary. Do not introduce VO-typed `@PathVariable` parameters here.

### 2. Spring `@ConfigurationProperties.transferThreshold: Long`

`BuckPalConfigurationProperties.transferThreshold: Long` is bound by Spring
from YAML/property files. Spring's `@ConfigurationProperties` machinery
binds primitives directly; introducing a custom VO at this seam would
require writing a Spring `Converter` for zero observable benefit. The
value is already lifted into `Money` exactly once in
`BuckPalConfiguration#moneyTransferProperties` (where it becomes
`Money.of(transferThreshold)`), which is the right boundary. Leave
`transferThreshold` typed `Long` on the configuration-properties class.

### 3. JPA column types and HQL parameters

`ActivityJpaEntity.timestamp: LocalDateTime?` and other JPA column types
are part of the database schema and are never replaced with VOs. The JPA
column type is what gets serialized to the `activity` and `account`
tables; changing it would be a schema migration, not a refactoring. The
mapping layer (`AccountMapper`) converts at the edge:

- Read path: `ActivityTimestamp(activity.timestamp!!)` wraps the raw
  `LocalDateTime` after the existing `requireNotNull` check.
- Write path: `activity.timestamp.value` unwraps back to `LocalDateTime`.

Similarly, `ActivityRepository`'s `@Query` HQL strings reference
`:ownerAccountId` (`Long`), `:since` and `:until` (`LocalDateTime`),
`:accountId` (`Long`). HQL has no awareness of Kotlin value classes, so
the repository interface stays typed in raw primitives. The unwrap
happens in `AccountPersistenceAdapter` — e.g. `baselineDate.value` is
passed to `activityRepository.findByOwnerSince(...)`. Do not modify the
HQL parameter shapes.

---

## Quick reference

| VO | Wraps | Class shape | Factory / helper |
|----|-------|-------------|------------------|
| `BaselineDate` | `LocalDateTime` | `@JvmInline value class BaselineDate(val value: LocalDateTime)` | `companion object { fun now(): BaselineDate }`; `fun minusDays(days: Long): BaselineDate` |
| `ActivityTimestamp` | `LocalDateTime` | `@JvmInline value class ActivityTimestamp(val value: LocalDateTime)` | `companion object { fun now(): ActivityTimestamp }` |
| `BaselineBalanceFigures` | a pair of `Money` values | `data class BaselineBalanceFigures(val deposit: Money, val withdrawal: Money)` (two fields, not value class) | `fun toBaselineBalance(): Money = deposit - withdrawal` |

- **`BaselineDate`** — the activity-window cutoff. Use at every port and
  service surface that previously took a raw `LocalDateTime` for "consider
  activities at-or-after this instant; everything strictly before
  contributes to the baseline." Unwrap to `LocalDateTime` only at the
  `ActivityRepository` HQL boundary.
- **`ActivityTimestamp`** — the instant a single Activity occurred. Use on
  `Activity.timestamp` and on the `ActivityWindow.getStartTimestamp()` /
  `getEndTimestamp()` return types. Convert to/from `LocalDateTime` only in
  `AccountMapper`.
- **`BaselineBalanceFigures`** — the deposit/withdrawal pair fed into
  `AccountMapper.mapToDomainEntity(...)`. Build it once in
  `AccountPersistenceAdapter` from the `Long?` aggregates returned by
  `ActivityRepository.getDepositBalanceUntil` / `getWithdrawalBalanceUntil`
  (still `?: 0L`-defaulted). Call `figures.toBaselineBalance()` inside the
  mapper to collapse the pair into a single `Money`.

---

## Why three primitives stay raw (the four REJECT decisions)

Four candidates from the sprint-00 inventory were considered and
rejected, three of which correspond to the INTENTIONAL leaks above.
They are summarized here so the rejection rationale survives future
spec re-archiving.

- **`Account.baselineBalance: Money` (REJECT — baselineBalance retype).**
  The field is already wrapped in `Money`. A second wrapper class
  `BaselineBalance` would force constant unwrap/rewrap whenever
  `baselineBalance` is added to or subtracted from another `Money`,
  with no observable safety win. Already-wrapped primitives do not get a
  second wrapper.
- **`BuckPalConfigurationProperties.transferThreshold: Long` (REJECT —
  transferThreshold).** Spring binds primitives from YAML/properties
  directly. A custom VO would force a `Converter` for zero benefit; the
  value is already lifted into `Money.of(transferThreshold)` exactly once
  in `BuckPalConfiguration#moneyTransferProperties`, which is the correct
  boundary.
- **`SendMoneyController` path variables (REJECT — SendMoneyController
  path vars).** The HTTP path `POST /accounts/send/{Long}/{Long}/{Long}`
  is the external contract. `SendMoneyController` wraps each value into
  `Account.AccountId(...)` / `Money.of(...)` on the very next line.
  Changing the `@PathVariable` type would alter the HTTP contract.
- **`Money.amount: BigInteger` narrowed to `Long` in
  `AccountMapper.mapToJpaEntity` (REJECT — out of scope).** Not a missing
  VO at all; it is a pre-existing precision-loss risk when a `Money`
  exceeds `Long.MAX_VALUE`. Recorded in the spec risk register, not
  introduced by this VO-extraction work. Any fix belongs to a separate
  `Money`-redesign effort.

---

## ADR table from sprint-00 (verbatim)

This is the 7-row decision table from
`.claude/harness/workspace/handoffs/sprint-00-vo-candidates.md`,
reproduced verbatim for posterity.

| Candidate | Current type | Decision (ACCEPT/REJECT) | Target VO type name | Implementing sprint | Rationale |
|-----------|--------------|--------------------------|---------------------|---------------------|-----------|
| 1. Activity-window cutoff date: `LoadAccountPort.loadAccount(_, baselineDate)`, `SendMoneyService` (`LocalDateTime.now().minusDays(10)`), `GetAccountBalanceService` (`LocalDateTime.now()`), and the `:since`/`:until` parameters threaded through `AccountPersistenceAdapter` into `ActivityRepository.findByOwnerSince` / `getDepositBalanceUntil` / `getWithdrawalBalanceUntil` | `LocalDateTime` | ACCEPT | `BaselineDate` | sprint-01 | This `LocalDateTime` is a distinct domain concept — "cutoff dividing baseline from the activity window" — and is currently re-derived ad hoc in three call sites. A `@JvmInline value class` makes the intent type-safe at port boundaries while still unwrapping to `LocalDateTime` at the JPA edge so the HQL parameters stay primitive. |
| 2. Activity occurrence instant: `Activity.timestamp`, `ActivityWindow.getStartTimestamp()` / `getEndTimestamp()` (and downstream call sites such as `ActivityWindowTest`, `ActivityJpaEntity.timestamp`, and `ActivityTestData`) | `LocalDateTime` | ACCEPT | `ActivityTimestamp` | sprint-02 | Today this `LocalDateTime` is structurally indistinguishable from `BaselineDate` even though it means "when an activity occurred", not "window cutoff". Extracting `ActivityTimestamp` as a `@JvmInline value class` enforces that distinction at compile time; conversion to/from the JPA `LocalDateTime` column happens in `AccountMapper` so `ActivityJpaEntity` stays untouched. |
| 3. Mapper baseline aggregates: `AccountMapper.mapToDomainEntity(_, _, withdrawalBalance: Long, depositBalance: Long)` plus the `AccountPersistenceAdapter` caller that builds those two values from `ActivityRepository.getDepositBalanceUntil` / `getWithdrawalBalanceUntil` | two positional `Long`s | ACCEPT | `BaselineBalanceFigures` | sprint-03 | Two positional `Long`s for "deposit total" and "withdrawal total" silently invite swap-bugs at the single call site. A two-field `data class` (`deposit: Money`, `withdrawal: Money`) plus a `toBaselineBalance()` helper collapses the pair into one named argument and lifts the values into `Money` exactly once, at the adapter boundary. |
| 4. `Account.baselineBalance: Money` (existing `Money` field on the aggregate root) | `Money` | REJECT (baselineBalance retype) | — | — | The field is already wrapped in `Money`; introducing a second wrapper class `BaselineBalance` would force constant unwrap/rewrap whenever it is added to or subtracted from another `Money`, with no observable safety win. Documented as rejected so future contributors do not re-litigate it. |
| 5. `BuckPalConfigurationProperties.transferThreshold: Long` | `Long` | REJECT (transferThreshold) | — | — | Spring `@ConfigurationProperties` binds primitives directly from YAML/properties; introducing a custom VO here would force writing a Spring `Converter` for no real benefit. The value is already lifted into `Money.of(...)` exactly once in `BuckPalConfiguration#moneyTransferProperties`, which is the correct boundary. |
| 6. `SendMoneyController` `@PathVariable` parameters (`sourceAccountId`, `targetAccountId`, `amount`) | three `Long`s | REJECT (SendMoneyController path vars) | — | — | The HTTP path `POST /accounts/send/{Long}/{Long}/{Long}` is part of the external contract and must remain `Long`-bound. The controller already wraps each value into `Account.AccountId(...)` / `Money.of(...)` on the very next line, which is the correct boundary. Changing the path-variable type would alter the HTTP contract. |
| 7. `Money.amount: BigInteger` narrowed to `Long` in `AccountMapper.mapToJpaEntity` via `activity.money.amount.toLong()` | silent `BigInteger` → `Long` narrowing | REJECT / OUT-OF-SCOPE | — | — | This is not a missing Value Object — it is a pre-existing precision-loss risk when a `Money` exceeds `Long.MAX_VALUE`. Recorded in the spec's risk register so the Evaluator does not flag it as a regression introduced by this work. Any fix belongs to a separate `Money` redesign sprint, not the VO-extraction stream. |

---

## How to apply this convention to future changes

- New domain concept that currently leaks as a primitive at a port or
  service signature? Add a `@JvmInline value class` under
  `account/domain/` with a single `val value: <PrimitiveType>` field and
  a `companion object { fun now(): ... }` (or analogous) factory.
- Two values that travel together and can be silently swapped? Use a
  `data class` (not a value class — value classes can't have two fields).
  Add a small helper method if the consumer always combines the two in
  the same way.
- Already-wrapped value (already a `Money`, `AccountId`, etc.)? Do not
  add a second wrapper.
- External binder (HTTP path, Spring config properties, JPA column, HQL
  parameter)? Keep the primitive at the binder; wrap on the very next
  line in the adapter.
- VO touched at a port surface? Update the matching tests in the same
  sprint, not in a follow-up — Kotest `shouldBe` equality across VO
  types only works when both sides carry the same wrapper.
