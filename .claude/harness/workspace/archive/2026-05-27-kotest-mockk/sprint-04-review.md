STATUS: PASS

# Sprint 04 Review — `AccountPersistenceAdapterTest` Kotest migration

WEIGHTED SCORE: 9.4

Sprint goal: convert the `@DataJpaTest` slice test to a Kotest `DescribeSpec`
that registers the Spring extension, drops AssertJ/JUnit, and loads the SQL
fixture inside the `loads account` leaf so the `@Sql` annotation's
container-scoping work is preserved.

Outcome: PASSES on every mechanically-verifiable acceptance check. The
Generator deviated from the contract's literal `dataSource.connection.use {…}`
snippet, swapping in `DataSourceUtils.getConnection / releaseConnection`.
That deviation is **load-bearing for correctness** and **does not break any
contract grep gate**; details below in "Notes on the DataSourceUtils
deviation".

## Mandatory commands

| # | Command | Exit | Evidence |
|---|---------|------|----------|
| 1 | `git status` | 0 | Only `src/test/kotlin/.../AccountPersistenceAdapterTest.kt` (modified) + harness workspace files (untracked) — clean scope. |
| 2 | `./gradlew clean` | 0 | `BUILD SUCCESSFUL in 325ms`. |
| 3 | `./gradlew compileKotlin compileTestKotlin` | 0 | `BUILD SUCCESSFUL in 1s`, no warnings on the converted file. |
| 4 | `./gradlew test` | 0 | `BUILD SUCCESSFUL in 7s` first pass; re-run with `--rerun-tasks` also green. |
| 5 | `./gradlew check` | 0 | `BUILD SUCCESSFUL`; ArchUnit `DependencyRuleTests` green. |
| 6 | `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest"` | 0 | `BUILD SUCCESSFUL in 4s`. Both `loads account` and `updates activities` leaves green. |

Aggregate leaf-test counts from `build/test-results/test/TEST-*.xml`:

| Suite | tests | failures | errors | skipped |
|-------|-------|----------|--------|---------|
| `account.adapter.in.web.SendMoneyControllerTest` | 1 | 0 | 0 | 0 |
| `account.adapter.out.persistence.AccountPersistenceAdapterTest` | **2** | 0 | 0 | 0 |
| `account.application.service.SendMoneyServiceTest` | 2 | 0 | 0 | 0 |
| `account.domain.AccountTest` | 4 | 0 | 0 | 0 |
| `account.domain.ActivityWindowTest` | 3 | 0 | 0 | 0 |
| `BuckPalApplicationTests` | 1 | 0 | 0 | 0 |
| `DependencyRuleTests` | **2** | 0 | 0 | 0 |
| `SendMoneySystemTest` | 1 | 0 | 0 | 0 |
| **Total** | **16** | **0** | **0** | **0** |

Total 16 matches the Sprint 03 baseline. `AccountPersistenceAdapterTest`
contributes exactly **2** leaves. `DependencyRuleTests` reports
`tests="2" failures="0"` — ArchUnit stays green; the XML artefact is the
concrete evidence the contract demands.

## Criteria

### Behavioral Correctness — 10/10 [threshold 9]

- `./gradlew test` and `./gradlew test --rerun-tasks` both exit 0; full
  16-leaf suite green.
- Per-suite XML at
  `build/test-results/test/TEST-io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest.xml`
  → `tests="2" failures="0" errors="0" skipped="0"`.
- `loads account` leaf reads 2 activities and balance `Money.of(500L)`
  (proves the SQL preload landed and was visible inside the slice
  transaction — see DataSourceUtils note below).
- `updates activities` leaf observes `activityRepository.count() == 1L`
  (proves no cross-leaf SQL bleed: `@DataJpaTest`'s transactional rollback
  remained intact, and the SQL load from the previous leaf was rolled
  back, not committed).
- ArchUnit `DependencyRuleTests` XML reports `tests="2" failures="0"`.
- `./gradlew check` exit 0.

No regressions; no test was modified to mask a bug.

### Idiomatic Kotlin — 9/10 [threshold 7]

Sampled the entire file (80 lines) plus the diff against `HEAD`:

Strong points:
- `class AccountPersistenceAdapterTest : DescribeSpec()` with `init { describe { … } }` — class-body form, correctly chosen because `@Autowired lateinit var` properties cannot live inside the constructor-arg lambda form.
- `override fun extensions() = listOf(SpringExtension)` (line 25) — expression body, no redundant return type.
- Infix `shouldBe` and `shouldHaveSize` everywhere; zero `.shouldBe(` or `.shouldHaveSize(` method-call form.
- Zero `!!` operators.
- All three `lateinit var` declarations are `@Autowired` (lines 27→28, 30→31, 33→34); count is exactly 3 as the contract requires. Each `lateinit var` is documented by Spring's TestContextManager pattern.
- `loadSql(resource: String)` is a private member function — appropriate scoping; no global state.
- Kotlin string template `"io/reflectoring/buckpal/account/adapter/out/persistence/$resource"` instead of `String.format`.
- `try { … } finally { DataSourceUtils.releaseConnection(…) }` is the idiomatic Spring-style pattern for `DataSourceUtils`; the connection cannot use Kotlin's `.use { … }` extension because `releaseConnection` is the API contract, not `close()` (see deviation note).

Minor nits (deducting 1 point but not failing):
- Line 53: `LocalDateTime.of(2018, 8, 10, 0, 0)` — magic numbers retained verbatim from the original test. Acceptable since the contract scope is mechanical conversion; flagging for the next iteration only if the team wants a named constant.
- Line 75: `activityRepository.findAll().get(0)` is Java-style; Kotlin convention prefers `findAll().first()` or `findAll()[0]`. The contract did not require this and behavior is identical, but a Kotlin reviewer would call it out.

### Architectural Integrity — 10/10 [threshold 9]

- ArchUnit `DependencyRuleTests` XML reports `tests="2" failures="0"`. The
  hexagonal layout under `src/main/kotlin/io/reflectoring/buckpal/**` is
  untouched (no `src/main/` files changed).
- `git diff --name-only HEAD -- src/main/` → empty.
- `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/common/` → empty (fixtures untouched).
- `git diff --name-only HEAD -- src/test/resources/` → empty (SQL files untouched, spec out-of-scope item respected).
- `git diff --name-only HEAD -- build.gradle` → empty.
- `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/domain/` → empty.
- `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/application/service/` → empty.
- `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/` → empty.
- `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/archunit/` → empty.
- `find src/main/java src/test/java -name '*.java'` → no such directories (post-Sprint-9 state preserved).
- `grep -R "import lombok" src/main/kotlin src/test/kotlin` → no hits.
- Package path of the test file unchanged: `io.reflectoring.buckpal.account.adapter.out.persistence`.

Only one source file changed, exactly the one declared in the contract:
`src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt`.

### Code Quality — 8/10 [threshold 7]

Negative grep gates (all exit 1 — no matches, as required):

| Gate | Result |
|------|--------|
| `^import org\.assertj\.core` | no hits |
| `assertThat(` | no hits |
| `^import org\.junit\.jupiter` | no hits |
| `@Test\b` | no hits |
| `!!` | no hits |
| `\.shouldBe\(` | no hits |
| `\.shouldHaveSize\(` | no hits |

Positive grep gates:

| Gate | Required | Actual |
|------|----------|--------|
| `^class AccountPersistenceAdapterTest\s*:\s*DescribeSpec` | ≥ 1 | 1 (line 23) |
| `^import io\.kotest\.core\.spec\.style\.DescribeSpec` | ≥ 1 | 1 (line 3) |
| `^import io\.kotest\.extensions\.spring\.SpringExtension` | ≥ 1 | 1 (line 4) |
| `override fun extensions\(\)` | ≥ 1 | 1 (line 25) |
| `@DataJpaTest` | ≥ 1 | 2 (line 21 class-level + line 39 comment) |
| `@Import\(AccountPersistenceAdapter::class,\s*AccountMapper::class\)` | ≥ 1 | 1 (line 22) |
| `AccountPersistenceAdapterTest\.sql` | ≥ 1 | 1 (line 51) |
| `ScriptUtils` | ≥ 1 | 2 (import line 17 + call line 42) |
| `shouldHaveSize` count | exactly 2 | 2 (import line 5 + line 55) |
| `\bshouldBe\b` count | exactly 4 | 4 (import line 6 + lines 56, 73, 76) |
| `lateinit var` count | exactly 3 | 3 (lines 28, 31, 34) |

All exact-count gates match exactly. No deviation in counts.

Minor observations:
- The literal `@DataJpaTest` appears a second time inside the comment on
  line 39, which is fine (grep gate is "≥ 1"); the comment is informative
  rather than load-bearing.
- The string literal `"io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.sql"` is split into a base path constant + interpolated resource name in `loadSql`. The literal `AccountPersistenceAdapterTest.sql` is preserved on line 51 (positive gate satisfied).
- Helper function `loadSql` is private; could in principle become a top-level helper or a Kotest extension, but the current scoping is the simplest and the contract explicitly wrote it this way.

## Notes on the DataSourceUtils deviation (handoff-flagged)

**What the contract said:**

```kotlin
private fun loadSql(resource: String) {
    val classpath = "io/reflectoring/buckpal/account/adapter/out/persistence/$resource"
    dataSource.connection.use { connection ->
        ScriptUtils.executeSqlScript(connection, ClassPathResource(classpath))
    }
}
```

**What the Generator wrote:**

```kotlin
private fun loadSql(resource: String) {
    val classpath = "io/reflectoring/buckpal/account/adapter/out/persistence/$resource"
    val connection = DataSourceUtils.getConnection(dataSource)
    try {
        ScriptUtils.executeSqlScript(connection, ClassPathResource(classpath))
    } finally {
        DataSourceUtils.releaseConnection(connection, dataSource)
    }
}
```

**Why the deviation is correct, not a regression:**

`@DataJpaTest` activates Spring's transactional test infrastructure — there is
an active `TransactionStatus` bound to the test thread for the entire leaf,
and Hibernate's `EntityManager` uses the connection from that transaction.
If the SQL load grabs a *fresh* connection from the pool via
`dataSource.connection` (which calls `HikariDataSource.getConnection()`
directly), the script runs against a different connection in a different
transaction. Two possible outcomes, both bad:

1. Default H2/Hikari isolation is `READ_COMMITTED`. The fresh connection's
   transaction commits the INSERTs into the underlying DB. Hibernate's
   transaction (via `EntityManager`) then sees them — but at the end of the
   test, `@DataJpaTest`'s rollback only rolls back Hibernate's transaction,
   not the fresh-connection one. The data **persists across tests**,
   leaking into `updates activities` and breaking the `count() == 1L`
   assertion.
2. Worse, on a stricter isolation level, Hibernate's transaction wouldn't
   see the fresh-connection INSERTs at all, and `loadAccount` would return
   an empty account → the `shouldHaveSize 2` assertion fails.

`DataSourceUtils.getConnection(dataSource)` is exactly the helper Spring's
own `@Sql` implementation
(`org.springframework.test.context.jdbc.SqlScriptsTestExecutionListener`)
uses to obtain a connection participating in the current transaction. It
returns the transaction-bound connection if one exists, otherwise a fresh
one; `releaseConnection` is the matched cleanup. This is **the canonical
Spring pattern** for joining a test transaction.

Without this deviation, the test would either fail outright or leak rows
across leaves. With it, behavior matches the original JUnit `@Sql` path
exactly: SQL loads into the test transaction → leaf assertions see the
data → rollback at leaf end removes it → next leaf starts clean.

**Impact on contract grep gates:** none. The relevant gates are:

- `ScriptUtils` ≥ 1 → satisfied (import + call, count 2).
- `AccountPersistenceAdapterTest.sql` ≥ 1 → satisfied (line 51).
- No gate references `dataSource.connection.use` or `DataSourceUtils`
  literally. The Idiomatic-Kotlin gate "no `!!`" is satisfied (no `!!`).
- The exact-count gates (`shouldBe`=4, `shouldHaveSize`=2, `lateinit var`=3)
  are unaffected by the helper-function body.

The handoff transparently declared the deviation. Per evaluator.md the rule
"git diff showing files the Generator didn't mention in the handoff =
automatically a FAIL" does not apply — the file IS the in-scope file, and
the deviation is explicitly called out for review.

**Verdict on the deviation:** ACCEPTED. Deviation improves correctness and
is the Spring-idiomatic form. Generator should still note in future
contracts that `dataSource.connection.use { … }` is **not** equivalent to
`DataSourceUtils.getConnection(...)` under a `@Transactional` test
context, so future planners write the latter form upfront.

## Bugs found

| File:Line | Defect | Suggested fix |
|-----------|--------|---------------|
| (none) | n/a | n/a |

No defects. Style nits surfaced in the Idiomatic Kotlin and Code Quality
sections are explicitly out of contract scope; recording them here for
the next iteration's awareness.

Style nits (informational, not bugs):

| File:Line | Observation | Optional improvement |
|-----------|-------------|----------------------|
| `AccountPersistenceAdapterTest.kt:75` | Java-style `findAll().get(0)` | `findAll().first()` reads more Kotlin-natively. |
| `AccountPersistenceAdapterTest.kt:53` | `LocalDateTime.of(2018, 8, 10, 0, 0)` magic numbers carried over verbatim | Could extract a `private val baselineDate = LocalDateTime.of(...)` constant; only worth doing if other tests reuse the value. |

## Contract checklist

### Behavioral correctness

- [x] `./gradlew test --tests "*AccountPersistenceAdapterTest"` → exits 0. Verified above.
- [x] `./gradlew test` exits 0; 0 failures; aggregate 16 leaves preserved; `AccountPersistenceAdapterTest` contributes 2 leaves. XML evidence above.
- [x] `TEST-AccountPersistenceAdapterTest.xml` → `tests="2" failures="0" errors="0" skipped="0"`. Confirmed via `grep -oE` on the XML.
- [x] `TEST-DependencyRuleTests.xml` → `tests="2" failures="0"`. Confirmed.
- [x] `loads account` leaf reads 2 activities and `Money.of(500L)`. Test passes — assertion would fail otherwise.
- [x] `updates activities` leaf observes `activityRepository.count() == 1L`. Test passes — assertion would fail otherwise.

### Architectural integrity

- [x] `./gradlew check` exits 0; ArchUnit green; XML evidence above.

### Code quality — AssertJ / JUnit residue gone

- [x] `grep "^import org\.assertj\.core" <file>` → no matches.
- [x] `grep "assertThat(" <file>` → no matches.
- [x] `grep "^import org\.junit\.jupiter" <file>` → no matches.
- [x] `grep "@Test\b" <file>` → no matches.

### Code quality — Kotest / Spring extension / SQL fixture wiring present

- [x] `class AccountPersistenceAdapterTest : DescribeSpec` matches line 23.
- [x] `import io.kotest.core.spec.style.DescribeSpec` matches line 3.
- [x] `import io.kotest.extensions.spring.SpringExtension` matches line 4.
- [x] `override fun extensions()` matches line 25.
- [x] `@DataJpaTest` present at line 21.
- [x] `@Import(AccountPersistenceAdapter::class, AccountMapper::class)` present at line 22.
- [x] `AccountPersistenceAdapterTest.sql` literal present at line 51.
- [x] `ScriptUtils` present (line 17 import, line 42 call).
- [x] `shouldHaveSize` count = 2 (1 import + 1 call site).
- [x] `\bshouldBe\b` count = 4 (1 import + 3 call sites).

### Idiomatic Kotlin — no banned patterns

- [x] `grep "!!" <file>` → no matches.
- [x] `lateinit var` count = 3 (lines 28, 31, 34).
- [x] Each `lateinit var` is `@Autowired` (lines 27→28, 30→31, 33→34). Manually verified.
- [x] `grep "\.shouldBe(" <file>` → no matches (infix only).
- [x] `grep "\.shouldHaveSize(" <file>` → no matches (infix only).

### Scope — only one file changed

- [x] `git diff --name-only HEAD -- src/` = single line `src/test/kotlin/.../AccountPersistenceAdapterTest.kt`.
- [x] `src/main/` diff empty.
- [x] `src/test/kotlin/.../common/` diff empty.
- [x] `src/test/resources/` diff empty.
- [x] `build.gradle` diff empty.
- [x] `src/test/kotlin/.../account/domain/` diff empty.
- [x] `src/test/kotlin/.../account/application/service/` diff empty.
- [x] `src/test/kotlin/.../account/adapter/in/web/` diff empty.
- [x] `src/test/kotlin/.../archunit/` diff empty.

All 30 acceptance checks pass.

## Verdict

Sprint 04 PASSES. The `AccountPersistenceAdapterTest` is now a Kotest
`DescribeSpec` registering `SpringExtension`, the `@DataJpaTest` +
`@Import(AccountPersistenceAdapter::class, AccountMapper::class)` slice
annotations are preserved verbatim, the SQL fixture loads inside the
`loads account` leaf via `ScriptUtils`, and `@DataJpaTest`'s transactional
rollback semantics are intact (verified by the `count() == 1L` assertion
in the second leaf). All 30 acceptance checks pass; the full 16-leaf
suite is green; ArchUnit is green; no production code, fixtures, build
script, or out-of-scope test classes were touched.

The Generator's `DataSourceUtils.getConnection(...)` deviation from the
contract's literal `dataSource.connection.use { … }` snippet is accepted
as a correctness fix: under `@DataJpaTest`, a fresh pool connection
would have either leaked rows across leaves or run in a separate
transaction invisible to Hibernate; `DataSourceUtils` is the canonical
Spring helper for joining the active test transaction, matching `@Sql`'s
internal behavior exactly. The deviation affects zero contract grep
gates and is transparently declared in the handoff.
