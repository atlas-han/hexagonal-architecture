STATUS: PASS

GITLEAKS_VIOLATIONS: SKIPPED
SOLID_VIOLATIONS: NO

# Sprint 03 Review — `BaselineBalanceFigures` data class

WEIGHTED SCORE: 9.4/10

## Security Scan

GITLEAKS_VIOLATIONS: SKIPPED

`gitleaks` binary not available in the sandbox; tool absent → per evaluator
playbook this is a SKIPPED, not a FAIL. No new secrets were introduced —
the only new file (`BaselineBalanceFigures.kt`) is a 9-line pure-domain data
class.

## Mandatory commands

All 12 contract acceptance checks executed independently by the Evaluator.

### Check 1 — `BaselineBalanceFigures.kt` exists

```
test -f src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt
```
exit code: `0`. Expected `0`. **PASS**.

### Check 2 — `data class` + `val deposit: Money` + `val withdrawal: Money`

Three-step chained grep, each independently re-run:
```
grep -Eq 'data[[:space:]]+class[[:space:]]+BaselineBalanceFigures' .../BaselineBalanceFigures.kt   → exit 0
grep -Eq 'val[[:space:]]+deposit[[:space:]]*:[[:space:]]*Money'    .../BaselineBalanceFigures.kt   → exit 0
grep -Eq 'val[[:space:]]+withdrawal[[:space:]]*:[[:space:]]*Money' .../BaselineBalanceFigures.kt   → exit 0
```
Expected all 0. **PASS**.

### Check 3 — Not a `value class` / no `@JvmInline`

```
grep -Eq '@JvmInline|value[[:space:]]+class[[:space:]]+BaselineBalanceFigures' \
  src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt
```
Exit code: `1` (no match). Expected `1`. **PASS**.

### Check 4 — `fun toBaselineBalance(): Money`

```
grep -Eq 'fun[[:space:]]+toBaselineBalance\s*\(\s*\)[[:space:]]*:[[:space:]]*Money' \
  src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt
```
Exit code: `0`. Body matches at line 8:
```
fun toBaselineBalance(): Money = deposit - withdrawal
```
**PASS**.

### Check 5 — `figures: BaselineBalanceFigures` in `AccountMapper.kt`

```
grep -Eq 'figures[[:space:]]*:[[:space:]]*BaselineBalanceFigures' \
  src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt
```
Exit code: `0`. Match at line 17 of `AccountMapper.kt`:
```
figures: BaselineBalanceFigures,
```
**PASS**.

### Check 6 — No leftover `withdrawalBalance: Long` in `AccountMapper.kt`

```
grep -Eq 'withdrawalBalance[[:space:]]*:[[:space:]]*Long' \
  src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt
```
Exit code: `1` (no match). Expected `1`. **PASS**.

### Check 7 — `BaselineBalanceFigures(` in `AccountPersistenceAdapter.kt`

```
grep -Eq 'BaselineBalanceFigures\s*\(' \
  src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.kt
```
Exit code: `0`. Construction at line 41:
```
val figures = BaselineBalanceFigures(
    deposit = Money.of(depositBalance),
    withdrawal = Money.of(withdrawalBalance),
)
```
**PASS**.

### Check 8 — 7 external-contract files all `git diff --quiet`

```
git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt          → exit 0
git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt    → exit 0
git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt   → exit 0
git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt  → exit 0
git diff --quiet -- src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql                              → exit 0
git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt                          → exit 0
git diff --quiet -- src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt                     → exit 0
```
All 7 commands exit 0. **PASS**.

### Check 9 — Persistence adapter test suite

```
./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.*"
```
Output tail:
```
> Task :jacocoTestReport
BUILD SUCCESSFUL in 13s
6 actionable tasks: 2 executed, 4 up-to-date
```
Exit code: `0`. **PASS**.

### Check 10 — Full clean + build + check (includes ArchUnit)

```
./gradlew clean build check
```
Output tail:
```
> Task :jacocoTestReport
> Task :check
> Task :build
BUILD SUCCESSFUL in 27s
10 actionable tasks: 10 executed
```
Exit code: `0`. **PASS** (`./gradlew check` aggregates the ArchUnit
`DependencyRuleTests`; see explicit re-run below).

### Check 11 — `SendMoneySystemTest` round-trip

```
./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"
```
Output tail:
```
> Task :jacocoTestReport
BUILD SUCCESSFUL in 14s
6 actionable tasks: 2 executed, 4 up-to-date
```
Exit code: `0`. **PASS**. Confirms
`POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}` is
byte-identical to the pre-sprint behaviour.

### Check 12 — No Lombok imports anywhere in `src/`

```
grep -R "import lombok" src/
```
Exit code: `1` (no match). Expected `1`. **PASS**.

## File scope audit

```
git diff --stat HEAD
 .claude/harness/workspace/logs/run-log.md             |  3 +++
 .../account/adapter/out/persistence/AccountMapper.kt  |  9 +++------
 .../out/persistence/AccountPersistenceAdapter.kt      | 10 ++++++++--
 .../adapter/out/persistence/AccountMapperTest.kt      | 19 +++++++++++++------
 4 files changed, 27 insertions(+), 14 deletions(-)
```

Untracked (new) file:
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineBalanceFigures.kt`

Mapping vs spec sprint-03 "Files in scope":

| Spec entry | Actual change | Verdict |
|------------|---------------|---------|
| Create `BaselineBalanceFigures.kt` (domain) | NEW (untracked) | match |
| Edit `AccountMapper.kt` | modified | match |
| Edit `AccountPersistenceAdapter.kt` | modified | match |
| Edit `AccountMapperTest.kt` | modified | match |

The only non-production change is
`.claude/harness/workspace/logs/run-log.md`, which is harness telemetry
outside the source tree — accepted as in-process bookkeeping. No drive-by
edits leaked into other domain, application, or adapter files. **PASS**.

## Argument-order swap verification

The spec change is from the old positional pair
`mapToDomainEntity(_, _, withdrawalBalance: Long, depositBalance: Long)` to
a new named-figures struct
`BaselineBalanceFigures(deposit: Money, withdrawal: Money)`. This means the
*first* positional value used to be `withdrawal`, but the *first* named field
is now `deposit` — exactly the kind of swap that silently passes type-checking
but inverts arithmetic.

Direct Read of `AccountMapperTest.kt`:

The "baseline = deposit − withdrawal" case constructs
```
figures = BaselineBalanceFigures(
    deposit = Money.of(700L),
    withdrawal = Money.of(200L),
),
```
and asserts `result.baselineBalance shouldBe Money.of(500L)`. 700 − 200 = 500
→ the assertion holds **iff** the mapping is semantically correct. The build
is green (check 9, 10), so this asserts mechanically.

Direct Read of `AccountPersistenceAdapter.kt`:
```
val figures = BaselineBalanceFigures(
    deposit = Money.of(depositBalance),
    withdrawal = Money.of(withdrawalBalance),
)
```
`depositBalance` is sourced from `getDepositBalanceUntil(...)`,
`withdrawalBalance` from `getWithdrawalBalanceUntil(...)`. The
named-argument pairing matches the semantic origin. **PASS** — the swap
was applied correctly in both production and test sites.

## Money operator overload

Read of `src/main/kotlin/io/reflectoring/buckpal/account/domain/Money.kt`:

```
operator fun minus(money: Money): Money = Money(amount.subtract(money.amount))
```

Confirmed: `Money.minus` exists, is an `operator fun`, takes a `Money`, and
returns `Money` (not `BigInteger`). `BaselineBalanceFigures.toBaselineBalance()`
relies on this via `deposit - withdrawal`, which therefore evaluates to
`Money`. The Generator correctly used the existing operator overload instead
of unwrapping to `amount`. **PASS**.

## ArchUnit / hexagonal layering

`./gradlew check` (check 10) aggregates `DependencyRuleTests`. Additionally
re-ran in isolation:

```
./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"
> Task :jacocoTestReport
BUILD SUCCESSFUL in 10s
exit=0
```

Direction audit:
- `BaselineBalanceFigures` lives in `io.reflectoring.buckpal.account.domain`.
- `AccountMapper` (adapter) imports it.
- `AccountPersistenceAdapter` (adapter) imports it.
- Domain → adapter: none (no new outward dependency from domain).
- adapter → domain: present and allowed.

ArchUnit `DependencyRuleTests` still passes. **PASS**.

## Sprint-02 invariant preservation

```
grep -n "timestamp" src/main/kotlin/io/reflectoring/buckpal/account/domain/Activity.kt
 11:    val timestamp: ActivityTimestamp,
 19:        timestamp: ActivityTimestamp,

grep -n "timestamp" src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt
 19:    var timestamp: LocalDateTime? = null,
```

Spec invariant "`Activity.timestamp: ActivityTimestamp`,
`ActivityJpaEntity.timestamp: LocalDateTime?`" remains intact after
sprint-03. **PASS**.

## SOLID Analysis

SOLID_VIOLATIONS: NO

### S — Single Responsibility

`BaselineBalanceFigures` has exactly one job: hold a deposit/withdrawal pair
and expose the derived baseline (`toBaselineBalance()`). It does not
validate, persist, or format. The mapper still only translates JPA ↔ domain.
The persistence adapter still only orchestrates repositories and the mapper.
No violation.

### O — Open/Closed

No `when (x) { is Foo -> … }` switches added. The helper
`toBaselineBalance()` is implementation detail of the data class — no
extension axis to over-fit. No violation.

### L — Liskov Substitution

No new inheritance. `BaselineBalanceFigures` is a `data class` (final by
default). No subclasses, no narrowing of contracts. No violation.

### I — Interface Segregation

No port interfaces were added or modified — `LoadAccountPort` /
`UpdateAccountStatePort` are untouched in this sprint. No
`UnsupportedOperationException` patterns. No violation.

### D — Dependency Inversion

The new type lives in `account.domain`. Adapters (`AccountMapper`,
`AccountPersistenceAdapter`) depend on it; domain does not depend back on
either adapter. `AccountMapper` still receives `BaselineBalanceFigures` as a
parameter (not instantiated inside). No violation.

## Criteria

### Behavioral Correctness — 10/10 [threshold 9]

All 12 acceptance checks PASS. `./gradlew clean build check`, the
persistence adapter test suite, the system test, and `DependencyRuleTests`
all exit 0. The "deposit = 700, withdrawal = 200 → baseline = 500" assertion
in `AccountMapperTest` mechanically guards the positional-to-named swap.

### Idiomatic Kotlin — 9/10 [threshold 7]

- `data class` for a 2-field VO (correct vs `value class`, per spec).
- Trailing commas in the parameter list — idiomatic.
- Named-argument construction in adapter + tests — eliminates the original
  argument-order hazard at the call site too.
- Single-expression function body for `toBaselineBalance()` — concise.
- Reuses existing `operator fun minus(Money): Money` — no unwrap to BigInteger.

Minor nit (not a defect): `BaselineBalanceFigures.kt` could carry a short
KDoc explaining the asymmetric `deposit - withdrawal` direction, since the
sign convention is non-obvious to a reader without the spec. Not failing
over a missing doc comment.

### Architectural Integrity — 10/10 [threshold 9]

ArchUnit `DependencyRuleTests` re-run independently — passes. No new
domain → adapter dependency. The new VO lives where the spec says it must
(`account.domain`). No HQL, JPA column, controller, or SQL fixture touched
(check 8). Hexagonal layering intact.

### Code Quality — 8/10 [threshold 7]

- Null-coalescing `?: 0L` preserved on both JPA aggregates exactly as the
  contract required.
- Adapter still computes both `Long`s into named locals (`withdrawalBalance`,
  `depositBalance`) before wrapping. This is arguably one level of
  indirection more than necessary — the temporaries could be inlined into
  the `BaselineBalanceFigures(...)` constructor call. But keeping the named
  locals helps readability and makes the `?: 0L` rule obvious; acceptable.
- Test file uses repeated `BaselineBalanceFigures(deposit = ..., withdrawal
  = ...)` blocks across three describe spec blocks. A small `private fun`
  helper inside the test class could DRY this, but it would be a style nit,
  not a defect.

## Bugs found

| File:Line | Defect | Suggested fix |
|-----------|--------|---------------|
| _(none)_  | No defects found. | — |

## Contract checklist

| # | Contract acceptance check | Verdict | Evidence |
|---|---------------------------|---------|----------|
| 1 | `BaselineBalanceFigures.kt` exists | PASS | `test -f` exit 0 |
| 2 | `data class` + `val deposit: Money` + `val withdrawal: Money` | PASS | three greps each exit 0 |
| 3 | NOT a `@JvmInline value class` | PASS | grep exit 1 (no match) |
| 4 | `fun toBaselineBalance(): Money` | PASS | grep exit 0 |
| 5 | `figures: BaselineBalanceFigures` in mapper | PASS | grep exit 0 |
| 6 | No `withdrawalBalance: Long` in mapper | PASS | grep exit 1 (no match) |
| 7 | `BaselineBalanceFigures(` in adapter | PASS | grep exit 0 |
| 8 | 7 external-contract files untouched | PASS | seven `git diff --quiet` exit 0 |
| 9 | Persistence adapter tests green | PASS | BUILD SUCCESSFUL in 13s |
| 10 | `./gradlew clean build check` green | PASS | BUILD SUCCESSFUL in 27s |
| 11 | `SendMoneySystemTest` green | PASS | BUILD SUCCESSFUL in 14s |
| 12 | No `import lombok` in src/ | PASS | grep exit 1 (no match) |

## Notes

- The handoff's self-check matches the Evaluator's independent re-run on
  every one of the 12 acceptance commands. No discrepancy.
- File scope is exactly the spec-declared set (one new domain file, three
  edits in mapper + adapter + mapper-test). Only the harness log file was
  modified outside source — accepted as orchestrator telemetry, not
  production drift.
- The argument-order swap (old positional `withdrawalBalance` first → new
  named `deposit` first) is the highest-risk part of this sprint. The
  "700/200 → 500" assertion in `AccountMapperTest` would fail loudly under
  any swap mistake; the green build is concrete evidence that the mapping
  is semantically right.
- `gitleaks` unavailable in the sandbox — SKIPPED per the evaluator
  playbook, not a FAIL.
- ArchUnit `DependencyRuleTests` re-run in isolation also passes; the
  `account.domain` → `account.adapter` direction remains clean.

## Verdict

Sprint-03 lands the `BaselineBalanceFigures` data class exactly as the spec
and the AGREED contract demanded: a `data class` (not a `value class`) in
`account.domain`, an asymmetric `toBaselineBalance(): Money = deposit -
withdrawal` helper, a mapper that takes a single named `figures` parameter,
an adapter that constructs it once at the JPA aggregate boundary with the
`?: 0L` null-coalescing preserved, and tests that use named arguments to
lock the swap-safety in. The full build, the persistence adapter suite, the
system test, the ArchUnit dependency rules, and the Lombok-free grep all
pass. The external HTTP and JPA contract is byte-identical. **STATUS: PASS.**
