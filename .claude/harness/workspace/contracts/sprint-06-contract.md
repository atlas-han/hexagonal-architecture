STATUS: AGREED

# Sprint 06 Contract — Migrate `SendMoneySystemTest` (full Spring Boot system test) to Kotest

## Sprint goal (verbatim from spec)

> Convert the end-to-end Spring Boot system test to a Kotest spec
> (`DescribeSpec` or `FunSpec`) while keeping
> `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` and the
> `@Sql("SendMoneySystemTest.sql")` data load. Replace
> `org.assertj.core.api.BDDAssertions.then` with Kotest's `shouldBe`.

## Files in scope

Only this **one** test file may be edited in Sprint 06. Anything outside
this list — production code, ArchUnit infrastructure, the build script, the
SQL resource, or any other test class — is off-limits.

- `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`

Files explicitly **not** in scope (read-only, Generator must not touch them):

- `src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql` —
  spec out-of-scope: "do not edit". The SQL classpath resource is loaded
  at runtime (either via `@Sql` annotation or via the `loadSql` helper
  pattern from Sprint 04); its contents must not change.
- All production code under `src/main/kotlin/**` — non-negotiable migration
  invariant ("the production source tree under
  `src/main/kotlin/io/reflectoring/buckpal/**` is not modified by any
  sprint in this migration").
- Every other test class already migrated in Sprints 01–05
  (`AccountTest`, `ActivityWindowTest`, `SendMoneyServiceTest`,
  `SendMoneyControllerTest`, `AccountPersistenceAdapterTest`,
  `DependencyRuleTests`, `BuckPalApplicationTests`).
- `src/test/kotlin/io/reflectoring/buckpal/common/AccountTestData.kt` and
  `ActivityTestData.kt` — fixtures, untouched per spec.
- Every file under `src/test/kotlin/io/reflectoring/buckpal/archunit/**`
  (`HexagonalArchitecture`, `Adapters`, `ApplicationLayer`,
  `ArchitectureElement`) — ArchUnit support types, not tests.
- `build.gradle` — Sprint 00 wired Kotest + MockK + springmockk; Sprint 07
  removes the legacy stack. This sprint is test-source-only.

## Hard exit criteria (verbatim from spec)

- Class extends a Kotest spec and registers Kotest's `SpringExtension`.
- The `@SpringBootTest(... RANDOM_PORT)` annotation is preserved.
- `@Sql("SendMoneySystemTest.sql")` is preserved on the leaf test (same
  `@Sql` fallback strategy as Sprint 04 if needed).
- The HTTP exchange via `TestRestTemplate` produces a 200 and balance
  deltas of `-500` (source) / `+500` (target), asserted via Kotest
  matchers.
- No imports from `org.assertj.core.*` remain in this file.
- `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"`
  exits 0; the test launches a random port and round-trips through the
  controller.
- `./gradlew test` (full suite) exits 0.

## Out of scope (verbatim from spec, plus additions)

From spec:

- `src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql`.
- Any production code.
- Any other test file.

Generator-added:

- All Sprint 01–05 test files (already migrated): `AccountTest`,
  `ActivityWindowTest`, `SendMoneyServiceTest`, `SendMoneyControllerTest`,
  `AccountPersistenceAdapterTest`, `DependencyRuleTests`,
  `BuckPalApplicationTests`. Untouched.
- `build.gradle` — Sprint 00 / Sprint 07 territory.
- ArchUnit infrastructure under `src/test/kotlin/.../archunit/**`.
- Test fixtures under `src/test/kotlin/.../common/**`.
- Coroutine APIs (`coEvery`, `coVerify`, `runBlocking`) — the system test
  is a synchronous HTTP round-trip; the controller is not a suspend
  function. No coroutines.
- Mocking — this is a full-stack test; everything (controller, service,
  persistence) is wired through the real Spring context. No `mockk`, no
  `@MockkBean`, no `@MockBean`. Negative greps below guard this.

---

## Spec-style decision — `DescribeSpec` (class-body form)

The spec offers `DescribeSpec` or `FunSpec`. The Generator picks
`DescribeSpec` for these reasons:

1. **Consistency with the prior Spring-flavored sprints.** Sprints 03
   (`SendMoneyControllerTest`), 04 (`AccountPersistenceAdapterTest`), and
   05's `BuckPalApplicationTests` all use the `DescribeSpec`
   class-body form. Adopting the same shape minimizes review cognitive
   load and keeps the Spring-extension wiring pattern uniform across
   every Spring spec in the suite.
2. **`override fun extensions()` requires class-body form.** Kotest
   5.5.x registers the Spring extension via
   `override fun extensions() = listOf(SpringExtension)`, which is a
   member function and cannot live inside a `FunSpec({ ... })`
   constructor-arg lambda. The class-body / `init { }` pattern is the
   only viable shape when an `extensions()` override is required.
3. **`@Autowired` `lateinit var` properties are class-level.** This file
   has two `@Autowired` properties (`restTemplate`, `loadAccountPort`)
   that must be Spring-injected before any leaf body runs. Class-level
   `lateinit var` properties are wired by Kotest's `SpringExtension`
   exactly the same way Sprints 03 and 04 wired theirs. Lambda-form
   `FunSpec` cannot carry class-level injected properties.
4. **One leaf, one `describe` container.** The original
   `SendMoneySystemTest` has exactly one `@Test fun sendMoney()`. The
   spec accepts `FunSpec` for flat single-block files, but since rules
   1–3 force the class-body form anyway, `DescribeSpec` with one
   `describe("...") { it("sends money") { ... } }` block reads more
   naturally than a `FunSpec` with one `test(...)` block, and matches
   `BuckPalApplicationTests`'s Sprint-05 shape exactly.

**Final decision:**

```kotlin
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SendMoneySystemTest : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var loadAccountPort: LoadAccountPort

    init {
        describe("POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}") {
            it("sends money between two accounts") {
                // body: load SQL, capture initial balances, POST, assert
            }
        }
    }
    // private helpers preserved as class-body methods
}
```

The `describe` container name mirrors the HTTP route under test (same
convention as Sprint 03's `SendMoneyControllerTest`). The single `it`
leaf is named to clearly correspond to the original `@Test fun sendMoney()`.

---

## Conversion targets

| `.kt` file in scope                                          | Class type                                                                                                                                                                                                  |
|---------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` | `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT) class SendMoneySystemTest : DescribeSpec()` with class-body `override fun extensions()`, two `@Autowired lateinit var` properties, and `init { describe(...) { it(...) { } } }` |

### Class shape facts — `SendMoneySystemTest`

- Stays in package `io.reflectoring.buckpal` (root test package, same as today).
- Class name stays `SendMoneySystemTest` so test filtering
  (`--tests "io.reflectoring.buckpal.SendMoneySystemTest"` and
  `--tests "*SendMoneySystemTest"`) continues to work unchanged.
- `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` is
  **preserved** verbatim on the new class (hard exit criterion).
- Class extends `DescribeSpec()` (class-body form).
- `override fun extensions() = listOf(SpringExtension)` registers Kotest's
  Spring extension.
- Two `@Autowired private lateinit var` properties, preserved verbatim
  in name and type:
  - `restTemplate: TestRestTemplate`
  - `loadAccountPort: LoadAccountPort`
  (Total `lateinit var` count for this file: **exactly 2**.)
- One `describe("POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}")` container
  wrapping one `it("sends money between two accounts") { ... }` leaf.
- The four private helper functions (`sourceAccount()`, `targetAccount()`,
  `loadAccount(...)`, `whenSendMoney(...)`) plus the three private
  constants-as-funs (`transferredAmount()`, `sourceAccountId()`,
  `targetAccountId()`) are **kept as class-body member functions** —
  they are called from the leaf and from each other. Member-function
  form is necessary because they reference `loadAccountPort` /
  `restTemplate`, which are class-level `lateinit var`s.
- No `!!` non-null assertions. The original has none; the migration
  preserves that.

### `@Sql` resolution strategy — same approach as Sprint 04

Spec Risk #2: *"@Sql resolution inside Kotest test lambdas — JUnit's @Sql
is discovered on Method objects; Kotest leaf tests are lambdas, not
reflective methods. If kotest-extensions-spring does not honor class- +
method-level @Sql on lambda leaves, Sprints 04 and 06 must fall back to
executing the same SQL in a `beforeTest` block."*

Sprint 04 already exercised this fallback for `@DataJpaTest` and chose
to drop the `@Sql` annotation entirely and load the SQL programmatically
via `ScriptUtils.executeSqlScript` against the auto-wired `DataSource`.
Sprint 06 adopts the **same pattern, same justification**:

- The hard exit criterion says *"`@Sql(...)` is preserved on the leaf test
  (same `@Sql` fallback strategy as Sprint 04 if needed)"*. Sprint 04's
  realized strategy is to **drop** the `@Sql` annotation and call an
  in-spec `loadSql("SendMoneySystemTest.sql")` helper at the top of the
  leaf body. This matches the spec's "*if needed*" allowance: Kotest
  5.5.x's `kotest-extensions-spring:1.1.3` does not reflect method-level
  `@Sql` annotations on lambda leaves (validated empirically by Sprint
  04). Therefore the annotation is removed and replaced by an explicit
  call inside the leaf body — the SQL load still happens before any
  assertion runs.
- The Generator wires the SQL load via an auto-wired `javax.sql.DataSource`,
  same as Sprint 04's `AccountPersistenceAdapterTest`. A third
  `@Autowired private lateinit var dataSource: DataSource` property is
  added (total `lateinit var` count then becomes **3**). A private
  `loadSql(resource: String)` helper joins the current transaction (via
  `DataSourceUtils.getConnection(dataSource)`) and executes the
  classpath script at
  `io/reflectoring/buckpal/SendMoneySystemTest.sql`. `@SpringBootTest`
  does not roll back transactions by default (unlike `@DataJpaTest`), so
  the SQL inserts persist for the duration of this random-port server;
  the test is isolated because it runs in its own Spring context with a
  fresh H2 database.
- The classpath path is
  `io/reflectoring/buckpal/SendMoneySystemTest.sql` — matches the
  resource location seen via `find`: `src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql`.
- Negative-grep acceptance for the `@Sql` annotation removal is
  formalised below.

### Annotation policy summary

- `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` —
  **preserved**.
- `@Sql("SendMoneySystemTest.sql")` — **dropped**; replaced by an
  in-leaf `loadSql("SendMoneySystemTest.sql")` call. (Spec sanctions
  this fallback verbatim.)
- `@Autowired` on `restTemplate`, `loadAccountPort`, `dataSource` —
  applied to the three `lateinit var` properties.

---

## Idiomatic Kotlin commitments

1. **`DescribeSpec()` class-body form** — required because
   `override fun extensions()` cannot live inside a constructor-arg
   lambda. Consistent with Sprints 03, 04, and 05's
   `BuckPalApplicationTests`.
2. **Exactly 3 `lateinit var` properties** — `restTemplate`,
   `loadAccountPort`, and the new `dataSource`. The first two are
   verbatim from today; the third is the Sprint-04-pattern addition
   needed to run the SQL programmatically (since `@Sql` cannot survive
   on a lambda leaf under Kotest 5.5.x).
3. **No `!!` non-null assertions.** Today's file has none; the
   migration preserves that.
4. **No `Mockito.` prefix, no `org.mockito` / `BDDMockito` imports, no
   `@MockBean` / `@MockkBean`, no `every` / `verify` / `mockk` calls.**
   This is a full-stack system test; nothing is mocked.
5. **No `@Test` annotation; no `org.junit.jupiter.api.*` imports.**
6. **No `org.assertj.core.*` imports** — the lone `BDDAssertions.then`
   today is replaced by Kotest's infix `shouldBe`.
7. **Kotest matchers in infix form only.** `actual shouldBe expected`
   (no `.shouldBe(...)` method-call form, no helper-wrapping). The
   three assertions become:
   - `response.statusCode shouldBe HttpStatus.OK`
   - `sourceAccount().calculateBalance() shouldBe initialSourceBalance.minus(transferredAmount())`
   - `targetAccount().calculateBalance() shouldBe initialTargetBalance.plus(transferredAmount())`
8. **`SpringExtension` is the Kotest object
   `io.kotest.extensions.spring.SpringExtension`**, not the JUnit
   variant `org.springframework.test.context.junit.jupiter.SpringExtension`.
   Registered via `override fun extensions() = listOf(SpringExtension)`.
9. **Helper functions stay as private class-body members** (`sourceAccount`,
   `targetAccount`, `loadAccount`, `whenSendMoney`, `transferredAmount`,
   `sourceAccountId`, `targetAccountId`). They reference
   `loadAccountPort` and `restTemplate`, so they cannot move to top
   level without losing access to the injected properties. Today's
   shape is preserved.
10. **HTTP call shape preserved verbatim.** `restTemplate.exchange(...)`
    with the same path template, `HttpMethod.POST`, `HttpEntity<Void>`
    + `HttpHeaders` carrying `Content-Type: application/json`, and the
    same path-variable args (`sourceAccountId.value`,
    `targetAccountId.value`, `amount.amount`). The Generator does not
    refactor the HTTP shape.

---

## Risk handling specific to this sprint

### Risk: `@Sql` annotation on lambda leaf (spec Risk register #2)

Already addressed above: the annotation is dropped and replaced by an
in-leaf `loadSql("SendMoneySystemTest.sql")` call, mirroring Sprint 04's
proven approach. The hard exit criterion permits "*the same `@Sql`
fallback strategy as Sprint 04*"; Sprint 04 dropped the annotation in
favour of a programmatic SQL load. Sprint 06 adopts the same.

### Risk: `@SpringBootTest` + Kotest `SpringExtension` wiring (spec Risk #1)

Sprints 03 (`@WebMvcTest`), 04 (`@DataJpaTest`), and 05's
`BuckPalApplicationTests` (`@SpringBootTest`) have already validated the
`kotest-extensions-spring:1.1.3` wiring for all three Spring slice
shapes. `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)`
uses the same `TestContextManager` lifecycle as the plain
`@SpringBootTest` exercised in Sprint 05 — the random-port aspect only
changes how the embedded servlet container is wired, not how Kotest's
extension hooks `TestContextManager.beforeTestClass()` /
`prepareTestInstance()` calls. Mitigation:

- Use `override fun extensions() = listOf(SpringExtension)` exactly as in
  Sprints 03, 04, and 05.
- The fallback `override fun listeners() = listOf(SpringExtension)` is
  documented in Sprint 03's contract; only swap to it if `extensions()`
  self-check fails.

### Risk: `TestRestTemplate` injection under Kotest

`@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` registers
a `TestRestTemplate` bean preconfigured with the random port. Kotest's
Spring extension delegates to `TestContextManager`, which performs the
`@Autowired` injection during `prepareTestInstance`. The class-body
`@Autowired private lateinit var restTemplate: TestRestTemplate`
declaration is identical to the original (lines 24–25 of today's file)
and will be populated by Spring before any `init { describe { it { } } }`
leaf runs. Same wiring as `MockMvc` in Sprint 03 (which already passed).

### Risk: HTTP round-trip semantics

The original test issues `POST /accounts/send/1/2/500` and expects:

- HTTP 200 in the response.
- `sourceAccount.calculateBalance()` decreased by 500.
- `targetAccount.calculateBalance()` increased by 500.

The migration preserves every one of these assertions, expressed in
Kotest infix `shouldBe` form. The HTTP path, method, headers, body, and
path-variable order are copied verbatim. Behavior cannot drift because
the production code under `src/main/kotlin/**` is non-negotiably
untouched.

### Risk: `@Sql` SQL load timing in random-port test

Today, `@Sql("SendMoneySystemTest.sql")` runs before the `@Test` method
fires, populating two accounts and eight activities. After migration,
`loadSql("SendMoneySystemTest.sql")` is the first call inside the leaf
body, so the SQL still runs before any `restTemplate.exchange(...)` and
before the `sourceAccount()` / `targetAccount()` helpers are invoked.
The random-port embedded server is started by Spring during
`@SpringBootTest` bootstrap (before any leaf), so by the time the SQL
script inserts rows, the controller endpoint is already live and ready
to accept the POST.

Note: unlike Sprint 04's `@DataJpaTest` (which rolls back transactions
at leaf end), `@SpringBootTest` does **not** auto-rollback. The inserted
rows persist for the lifetime of this test class's Spring context. Since
this file has exactly one leaf and the spec keeps it at one leaf, no
data-isolation issue arises. (If the file ever grows a second leaf, a
`@DirtiesContext` or per-leaf `delete from activity; delete from account;`
pre-clean would be needed — but that is out of scope for Sprint 06.)

### Risk: leaf-count mismatch on `SendMoneySystemTest` XML

Today the JUnit engine emits
`TEST-io.reflectoring.buckpal.SendMoneySystemTest.xml` with `tests="1"`
(one `@Test` method `sendMoney`). After migration to `DescribeSpec` with
one `describe { it { } }`, Kotest emits a single leaf —
`tests="1"` is preserved. Acceptance check below formalizes this.

### Risk: aggregate leaf count

The Sprint 05 PASS-review baseline is **16 leaves total**:

| Suite | tests |
|-------|-------|
| `account.adapter.in.web.SendMoneyControllerTest` | 1 |
| `account.adapter.out.persistence.AccountPersistenceAdapterTest` | 2 |
| `account.application.service.SendMoneyServiceTest` | 2 |
| `account.domain.AccountTest` | 4 |
| `account.domain.ActivityWindowTest` | 3 |
| `BuckPalApplicationTests` | 1 |
| `DependencyRuleTests` | 2 |
| `SendMoneySystemTest` | 1 |
| **Total** | **16** |

Sprint 06 touches only the last row (`SendMoneySystemTest`) and keeps
its count at 1. Aggregate must remain **16 leaves**.

### Risk: Kotest spec class import paths

Kotest 5.5.x exposes:

- `io.kotest.core.spec.style.DescribeSpec`
- `io.kotest.extensions.spring.SpringExtension`
- `io.kotest.matchers.shouldBe` (infix matcher)

These are the imports added. The artifacts are already on
`testRuntimeClasspath` per Sprint 00 / Sprints 03–05, so resolution
succeeds.

### Risk: Spring `DataSource` import collision

The Sprint 04 file imports `javax.sql.DataSource` (not
`jakarta.sql.DataSource`). Spring Boot 2.4.3 targets Java EE 8, so
`javax.sql.DataSource` is the correct package. Sprint 06 uses the
same import path.

---

## Inventory of JUnit / AssertJ / Spring API used today

Grepped from the live file (Read above) — `SendMoneySystemTest.kt` is
**85 lines** with **1 `@Test`**, **2 `@Autowired`**, **1 `@Sql`**.

| Today's API                                                                        | Where it appears                                  | Disposition           |
|-------------------------------------------------------------------------------------|----------------------------------------------------|------------------------|
| `org.assertj.core.api.BDDAssertions.then` (import + 3 call sites)                  | line 7 (import); lines 42, 45, 48 (calls)          | **Deleted** (replaced by `shouldBe`) |
| `org.junit.jupiter.api.Test` (import + annotation)                                  | line 8 (import); line 30 (annotation)              | **Deleted**            |
| `org.springframework.test.context.jdbc.Sql` (import + annotation)                   | line 18 (import); line 31 (annotation)             | **Deleted** (replaced by `loadSql(...)` helper, see Sprint-04-pattern justification above) |
| `io.reflectoring.buckpal.account.application.port.out.LoadAccountPort` (import)    | line 3                                             | Retained               |
| `io.reflectoring.buckpal.account.domain.Account` (import)                          | line 4                                             | Retained               |
| `io.reflectoring.buckpal.account.domain.Account.AccountId` (import)                | line 5                                             | Retained               |
| `io.reflectoring.buckpal.account.domain.Money` (import)                            | line 6                                             | Retained               |
| `org.springframework.beans.factory.annotation.Autowired` (import + 2 annotations)  | line 9 (import); lines 24, 27 (annotations)        | Retained               |
| `org.springframework.boot.test.context.SpringBootTest` (import + annotation)       | line 10 (import); line 21 (annotation)             | Retained               |
| `org.springframework.boot.test.context.SpringBootTest.WebEnvironment` (import)     | line 11                                            | Retained               |
| `org.springframework.boot.test.web.client.TestRestTemplate` (import)               | line 12                                            | Retained               |
| `org.springframework.http.HttpEntity` (import)                                     | line 13                                            | Retained               |
| `org.springframework.http.HttpHeaders` (import)                                    | line 14                                            | Retained               |
| `org.springframework.http.HttpMethod` (import)                                     | line 15                                            | Retained               |
| `org.springframework.http.HttpStatus` (import)                                     | line 16                                            | Retained               |
| `org.springframework.http.ResponseEntity` (import)                                 | line 17                                            | Retained               |
| `java.time.LocalDateTime` (import)                                                 | line 19                                            | Retained               |

### Imports to **delete**

- `org.assertj.core.api.BDDAssertions.then`
- `org.junit.jupiter.api.Test`
- `org.springframework.test.context.jdbc.Sql`

### Imports to **add**

- `io.kotest.core.spec.style.DescribeSpec`
- `io.kotest.extensions.spring.SpringExtension`
- `io.kotest.matchers.shouldBe`
- `javax.sql.DataSource` (for the `dataSource` `@Autowired` property)
- `org.springframework.core.io.ClassPathResource` (for `ScriptUtils.executeSqlScript`)
- `org.springframework.jdbc.datasource.DataSourceUtils` (joins the SQL load to any active transaction; Sprint-04 pattern)
- `org.springframework.jdbc.datasource.init.ScriptUtils` (executes the SQL classpath script — same call Spring's `@Sql` machinery uses)

---

## JUnit + AssertJ → Kotest 1:1 mapping

| Today (line)                                                            | Tomorrow                                                                                                |
|--------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `class SendMoneySystemTest {` (line 22)                                  | `class SendMoneySystemTest : DescribeSpec() {`                                                          |
| `@Test @Sql("SendMoneySystemTest.sql") fun sendMoney() { ... }` (lines 30–50) | `init { describe("POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}") { it("sends money between two accounts") { loadSql("SendMoneySystemTest.sql"); ... body ... } } }` |
| `then(response.statusCode).isEqualTo(HttpStatus.OK)` (line 42–43)        | `response.statusCode shouldBe HttpStatus.OK`                                                            |
| `then(sourceAccount().calculateBalance()).isEqualTo(initialSourceBalance.minus(transferredAmount()))` (line 45–46) | `sourceAccount().calculateBalance() shouldBe initialSourceBalance.minus(transferredAmount())`         |
| `then(targetAccount().calculateBalance()).isEqualTo(initialTargetBalance.plus(transferredAmount()))` (line 48–49) | `targetAccount().calculateBalance() shouldBe initialTargetBalance.plus(transferredAmount())`         |
| `import org.assertj.core.api.BDDAssertions.then` (line 7)                | Deleted                                                                                                 |
| `import org.junit.jupiter.api.Test` (line 8)                             | Deleted                                                                                                 |
| `import org.springframework.test.context.jdbc.Sql` (line 18)             | Deleted; replaced by in-leaf `loadSql(...)` helper call                                                 |
| (no `lateinit var dataSource` today)                                     | Added: `@Autowired private lateinit var dataSource: DataSource`                                         |
| (no `loadSql` helper today)                                              | Added: private `loadSql(resource: String)` member function — Sprint-04 pattern (`DataSourceUtils` + `ScriptUtils.executeSqlScript(ClassPathResource(...))`) |
| (no `override fun extensions()` today)                                   | Added: `override fun extensions() = listOf(SpringExtension)`                                            |
| Helper methods `sourceAccount`, `targetAccount`, `loadAccount`, `whenSendMoney`, `transferredAmount`, `sourceAccountId`, `targetAccountId` (lines 52–83) | Preserved verbatim as private class-body member functions                                               |

---

## Acceptance checks (mechanically verifiable by Evaluator)

Each box is one shell command or one observable artifact. Categories
mirror Sprints 03–05: Behavioral, Idiomatic, Architectural, Code Quality,
plus a Scope gate.

### Behavioral correctness

- [ ] `./gradlew test --tests "*SendMoneySystemTest"` → exits 0.
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` → exits 0 (FQCN form; same suite as above but explicit).
- [ ] `./gradlew test` (full suite) → exits 0; 0 failures across the suite.
- [ ] Aggregate leaf-test count remains **16** (unchanged from the Sprint 05 baseline). `SendMoneySystemTest` had **1** leaf before and has **1** leaf after this sprint.
- [ ] Parsed `build/test-results/test/TEST-io.reflectoring.buckpal.SendMoneySystemTest.xml` reports `tests="1"` and `failures="0"` and `errors="0"` and `skipped="0"`. The single round-trip leaf passes (HTTP 200 + correct balance deltas after the SQL load and POST).
- [ ] The leaf body still asserts:
  - HTTP `200 OK` on the response from `POST /accounts/send/1/2/500`.
  - Source-account balance after = source-account balance before `- 500`.
  - Target-account balance after = target-account balance before `+ 500`.

### Architectural integrity

- [ ] `./gradlew check` → exits 0 (full test task + ArchUnit
  `DependencyRuleTests` continues to pass; Sprint 06 does not touch
  ArchUnit infrastructure or production code).

### Code quality — JUnit / AssertJ / Mockito residue is gone

- [ ] `grep -nE "^import org\\.junit\\.jupiter" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → no matches (exit 1).
- [ ] `grep -nE "@Test\\b" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → no matches (exit 1).
- [ ] `grep -nE "^import org\\.assertj\\.core" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → no matches (exit 1). The lone `BDDAssertions.then` import is gone; spec hard exit criterion: "No imports from `org.assertj.core.*` remain in this file."
- [ ] `grep -nE "BDDAssertions" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → no matches (exit 1).
- [ ] `grep -nE "^import org\\.mockito" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → no matches (exit 1).
- [ ] `grep -nE "BDDMockito" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → no matches (exit 1).
- [ ] `grep -nE "@MockBean\\b|@MockkBean\\b" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → no matches (exit 1). This is a full-stack system test; nothing is mocked.
- [ ] `grep -nE "\\bmockk\\b|\\bevery\\s*\\{|\\bverify\\s*\\{" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → no matches (exit 1). No MockK primitives belong in a system test.
- [ ] `grep -nE "^import org\\.springframework\\.test\\.context\\.jdbc\\.Sql" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → no matches (exit 1). `@Sql` annotation and import removed per Sprint-04 fallback strategy.
- [ ] `grep -nE "@Sql\\b" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → no matches (exit 1). The `@Sql` annotation is replaced by the in-leaf `loadSql(...)` helper call.

### Code quality — Kotest spec wrappers are present (positive gates)

- [ ] `grep -nE "^class SendMoneySystemTest\\s*:\\s*DescribeSpec" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches exactly one line. Kotest spec wrapper.
- [ ] `grep -nE "^import io\\.kotest\\.core\\.spec\\.style\\.DescribeSpec" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line.
- [ ] `grep -nE "^import io\\.kotest\\.extensions\\.spring\\.SpringExtension" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line.
- [ ] `grep -nE "^import io\\.kotest\\.matchers\\.shouldBe" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line.
- [ ] `grep -nE "override fun extensions\\(\\)" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line. Kotest Spring extension is registered.
- [ ] `grep -nE "@SpringBootTest" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line. Spec hard exit criterion: "The `@SpringBootTest(... RANDOM_PORT)` annotation is preserved."
- [ ] `grep -nE "WebEnvironment\\.RANDOM_PORT" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line. `RANDOM_PORT` web environment preserved verbatim.
- [ ] `grep -nE "\\bdescribe\\(\\\"" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt | wc -l` → exactly **1**. One `describe(...)` container.
- [ ] `grep -nE "\\bit\\(\\\"" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt | wc -l` → exactly **1**. One `it(...)` leaf (matches the spec's "the HTTP exchange via TestRestTemplate produces a 200 and balance deltas of −500 / +500" — a single leaf, same count as today's one `@Test`).
- [ ] `grep -nE "\\bshouldBe\\b" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt | wc -l` → at least **3**. The three `BDDAssertions.then(...).isEqualTo(...)` sites are replaced by three Kotest `shouldBe` assertions (HTTP status + two balance deltas).
- [ ] `grep -nE "loadSql\\(\\\"SendMoneySystemTest\\.sql\\\"\\)" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line. The `@Sql("SendMoneySystemTest.sql")` annotation is replaced by an in-leaf `loadSql(...)` call referencing the same SQL classpath resource.
- [ ] `grep -nE "ScriptUtils\\.executeSqlScript" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line. SQL is loaded via the same Spring API `@Sql` would use internally.
- [ ] `grep -nE "ClassPathResource" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line. SQL resource is resolved by classpath (same resolution semantics as `@Sql("SendMoneySystemTest.sql")`).

### Code quality — HTTP shape and assertion semantics preserved

- [ ] `grep -nE "TestRestTemplate" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line. The `TestRestTemplate` `@Autowired` property is preserved.
- [ ] `grep -nE "restTemplate\\.exchange" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line. The HTTP round-trip is preserved.
- [ ] `grep -nE "HttpMethod\\.POST" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line. The HTTP method is preserved.
- [ ] `grep -nE "/accounts/send/" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line. The URL path is preserved.
- [ ] `grep -nE "HttpStatus\\.OK" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line. The 200-status assertion target is preserved.
- [ ] `grep -nE "LoadAccountPort" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → matches at least one line. The outgoing port used to read balances is preserved.

### Idiomatic Kotlin — no banned patterns, mandatory positive shape

- [ ] `grep -nE "!!" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → no matches (exit 1). Literal double-bang non-null assertion. (`!=` does not match because the regex requires two adjacent `!`.)
- [ ] `grep -nE "\\.shouldBe\\(" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → no matches (exit 1). Only infix `actual shouldBe expected` is permitted — the method-call form `.shouldBe(...)` is banned.
- [ ] `grep -n "lateinit var" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt | wc -l` → exactly **3**. The three `@Autowired` properties are `restTemplate`, `loadAccountPort`, and the Sprint-04-pattern `dataSource` (added to support programmatic SQL loading, since `@Sql` cannot survive on a Kotest lambda leaf).
- [ ] `grep -nE "@Autowired\\b" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt | wc -l` → exactly **3**. One `@Autowired` per `lateinit var` above.

### Scope — only the one file in scope changed

- [ ] `git diff --name-only HEAD -- src/` → exactly one line:
  - `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`

  No other source file is modified.
- [ ] `git diff --name-only HEAD -- src/main/` → empty (no production-code edits — protects the non-negotiable invariant).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/common/` → empty (fixtures untouched).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/archunit/` → empty (ArchUnit infrastructure untouched).
- [ ] `git diff --name-only HEAD -- build.gradle` → empty (build-script untouched; Sprint 00 / Sprint 07 territory).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/` → empty (Sprints 01–04 files untouched).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → empty (Sprint 05 files untouched).
- [ ] `git diff --name-only HEAD -- src/test/resources/` → empty (no SQL or resource edits — spec out-of-scope:
  `src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql` must not change).

---

## Verification commands the Generator will run before handoff

In order, from the worktree root, with
`JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home`:

1. `./gradlew --no-daemon compileKotlin compileTestKotlin` → expect
   `BUILD SUCCESSFUL`. (Sanity: no production-code edits, so
   `compileKotlin` is a no-op; `compileTestKotlin` proves the rewrites
   parse and that `io.kotest.core.spec.style.DescribeSpec`,
   `io.kotest.extensions.spring.SpringExtension`,
   `io.kotest.matchers.shouldBe`, `javax.sql.DataSource`,
   `org.springframework.core.io.ClassPathResource`,
   `org.springframework.jdbc.datasource.DataSourceUtils`, and
   `org.springframework.jdbc.datasource.init.ScriptUtils` all resolve.)
2. `./gradlew --no-daemon test --tests "*SendMoneySystemTest"` → expect
   `BUILD SUCCESSFUL` and the TEST-*.xml file with `tests="1"`,
   `failures="0"`, `errors="0"`, `skipped="0"`. The single round-trip
   leaf passes: random-port server starts, SQL load inserts the eight
   activity rows + two accounts, POST returns 200, source/target
   balance deltas equal −500/+500.
3. `./gradlew --no-daemon test` → expect `BUILD SUCCESSFUL`, aggregate
   **16** leaf tests (same as Sprint 05 baseline), 0 failures.
4. `./gradlew --no-daemon check` → expect `BUILD SUCCESSFUL` (full
   test task + ArchUnit `DependencyRuleTests`).
5. `git diff --name-only HEAD -- src/` → expect exactly one path:
   - `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
6. Negative greps (all expect "no output"):
   - `grep -nE "^import org\\.junit\\.jupiter" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "@Test\\b" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "^import org\\.assertj\\.core" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "BDDAssertions" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "^import org\\.mockito" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "@MockBean\\b|@MockkBean\\b" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "\\bmockk\\b|\\bevery\\s*\\{|\\bverify\\s*\\{" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "^import org\\.springframework\\.test\\.context\\.jdbc\\.Sql" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "@Sql\\b" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "!!" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "\\.shouldBe\\(" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
7. Positive greps (all expect at least one match):
   - `grep -nE "^class SendMoneySystemTest\\s*:\\s*DescribeSpec" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "override fun extensions\\(\\)" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "@SpringBootTest" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "WebEnvironment\\.RANDOM_PORT" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "loadSql\\(\\\"SendMoneySystemTest\\.sql\\\"\\)" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
   - `grep -nE "ScriptUtils\\.executeSqlScript" src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`

If any step fails, the Generator will diagnose and rerun. No red handoff.
