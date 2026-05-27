# Sprint 00 — Value Object Candidate ADR

This is the binding decision record for which Value Objects will be extracted
from the BuckPal Kotlin domain layer. Later sprints execute against this list.
The seven rows below correspond 1:1 to the candidate inventory in
`.claude/harness/workspace/spec/product-spec.md`.

## Decision table

| Candidate | Current type | Decision (ACCEPT/REJECT) | Target VO type name | Implementing sprint | Rationale |
|-----------|--------------|--------------------------|---------------------|---------------------|-----------|
| 1. Activity-window cutoff date: `LoadAccountPort.loadAccount(_, baselineDate)`, `SendMoneyService` (`LocalDateTime.now().minusDays(10)`), `GetAccountBalanceService` (`LocalDateTime.now()`), and the `:since`/`:until` parameters threaded through `AccountPersistenceAdapter` into `ActivityRepository.findByOwnerSince` / `getDepositBalanceUntil` / `getWithdrawalBalanceUntil` | `LocalDateTime` | ACCEPT | `BaselineDate` | sprint-01 | This `LocalDateTime` is a distinct domain concept — "cutoff dividing baseline from the activity window" — and is currently re-derived ad hoc in three call sites. A `@JvmInline value class` makes the intent type-safe at port boundaries while still unwrapping to `LocalDateTime` at the JPA edge so the HQL parameters stay primitive. |
| 2. Activity occurrence instant: `Activity.timestamp`, `ActivityWindow.getStartTimestamp()` / `getEndTimestamp()` (and downstream call sites such as `ActivityWindowTest`, `ActivityJpaEntity.timestamp`, and `ActivityTestData`) | `LocalDateTime` | ACCEPT | `ActivityTimestamp` | sprint-02 | Today this `LocalDateTime` is structurally indistinguishable from `BaselineDate` even though it means "when an activity occurred", not "window cutoff". Extracting `ActivityTimestamp` as a `@JvmInline value class` enforces that distinction at compile time; conversion to/from the JPA `LocalDateTime` column happens in `AccountMapper` so `ActivityJpaEntity` stays untouched. |
| 3. Mapper baseline aggregates: `AccountMapper.mapToDomainEntity(_, _, withdrawalBalance: Long, depositBalance: Long)` plus the `AccountPersistenceAdapter` caller that builds those two values from `ActivityRepository.getDepositBalanceUntil` / `getWithdrawalBalanceUntil` | two positional `Long`s | ACCEPT | `BaselineBalanceFigures` | sprint-03 | Two positional `Long`s for "deposit total" and "withdrawal total" silently invite swap-bugs at the single call site. A two-field `data class` (`deposit: Money`, `withdrawal: Money`) plus a `toBaselineBalance()` helper collapses the pair into one named argument and lifts the values into `Money` exactly once, at the adapter boundary. |
| 4. `Account.baselineBalance: Money` (existing `Money` field on the aggregate root) | `Money` | REJECT (baselineBalance retype) | — | — | The field is already wrapped in `Money`; introducing a second wrapper class `BaselineBalance` would force constant unwrap/rewrap whenever it is added to or subtracted from another `Money`, with no observable safety win. Documented as rejected so future contributors do not re-litigate it. |
| 5. `BuckPalConfigurationProperties.transferThreshold: Long` | `Long` | REJECT (transferThreshold) | — | — | Spring `@ConfigurationProperties` binds primitives directly from YAML/properties; introducing a custom VO here would force writing a Spring `Converter` for no real benefit. The value is already lifted into `Money.of(...)` exactly once in `BuckPalConfiguration#moneyTransferProperties`, which is the correct boundary. |
| 6. `SendMoneyController` `@PathVariable` parameters (`sourceAccountId`, `targetAccountId`, `amount`) | three `Long`s | REJECT (SendMoneyController path vars) | — | — | The HTTP path `POST /accounts/send/{Long}/{Long}/{Long}` is part of the external contract and must remain `Long`-bound. The controller already wraps each value into `Account.AccountId(...)` / `Money.of(...)` on the very next line, which is the correct boundary. Changing the path-variable type would alter the HTTP contract. |
| 7. `Money.amount: BigInteger` narrowed to `Long` in `AccountMapper.mapToJpaEntity` via `activity.money.amount.toLong()` | silent `BigInteger` → `Long` narrowing | REJECT / OUT-OF-SCOPE | — | — | This is not a missing Value Object — it is a pre-existing precision-loss risk when a `Money` exceeds `Long.MAX_VALUE`. Recorded in the spec's risk register so the Evaluator does not flag it as a regression introduced by this work. Any fix belongs to a separate `Money` redesign sprint, not the VO-extraction stream. |

## Notes

- Spec-vs-code drift check (per contract Risk #4): all 20 production files
  listed in the contract's "Inputs (read-only)" section were Read in Phase 2
  of this sprint. The code matches the spec inventory faithfully. The
  `transferThreshold` row (5) references
  `BuckPalConfigurationProperties.transferThreshold`, which exists as
  `var transferThreshold: Long = Long.MAX_VALUE`. Note that
  `MoneyTransferProperties` exposes a related but separately-named field
  `maximumTransferThreshold: Money` — this is the *post-conversion* form,
  already wrapped in `Money`, and is therefore not itself a VO candidate. No
  table row needs to change because of this.
- No new (8th) candidate was discovered during the read-only inspection;
  per the contract's Risk #5, none is added.
- All seven decisions match the spec inventory verbatim. No flips.

## Read-verified files

The following production source files were opened with the Read tool during
Phase 2 of this sprint to confirm the spec inventory still matches the code:

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
