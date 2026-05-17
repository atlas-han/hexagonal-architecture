STATUS: AGREED

# Sprint 04 Contract — Migrate `AccountPersistenceAdapterTest` (`@DataJpaTest`)

## Sprint goal (verbatim from spec)

> Convert the JPA-slice test to a Kotest spec hosting the existing
> `@DataJpaTest` + `@Import` + `@Sql` annotations through the Spring extension.
> No mocks involved; this validates that Kotest + `@DataJpaTest` + H2 + `@Sql`
> interact cleanly.

## Files in scope

Only this **one** test file may be edited in Sprint 04. Anything outside this
list — including production code, SQL resources, fixtures, the build script,
or any other test class — is off-limits.

- `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt`

Files explicitly **not** in scope (read-only, Generator must not touch them):

- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountRepository.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/**` (Account, Money, ActivityWindow, …)
- `src/test/kotlin/io/reflectoring/buckpal/common/AccountTestData.kt`
- `src/test/kotlin/io/reflectoring/buckpal/common/ActivityTestData.kt`
- `src/test/resources/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.sql`
  (spec out-of-scope: do not edit)
- every other test class in the suite (Sprints 01, 02, 03 already migrated;
  Sprints 05–07 still pending)
- every file under `src/test/kotlin/io/reflectoring/buckpal/archunit/**`
- `build.gradle` (Sprint 00 already wired Kotest + MockK + springmockk;
  Sprint 07 removes the legacy stack)

## Hard exit criteria (verbatim from spec)

- Class extends `DescribeSpec` (preferred) or `FunSpec`.
- `@DataJpaTest`, `@Import(AccountPersistenceAdapter::class, AccountMapper::class)`,
  and the per-method `@Sql("AccountPersistenceAdapterTest.sql")` annotation
  are preserved. `@Sql` is allowed on the method even inside a spec block as
  long as the Spring extension picks it up; if Kotest's Spring extension
  cannot honor method-level `@Sql` inside a lambda, move the SQL load to a
  `beforeTest` block that runs the same SQL (verify by reading the SQL
  file).
- Kotest's `SpringExtension` is registered.
- `assertThat(...).hasSize(2)` and `.isEqualTo(Money.of(500L))` become
  `shouldHaveSize(2)` and `shouldBe Money.of(500L)`.
- `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest"`
  exits 0; both `loadsAccount` and `updatesActivities` pass and the H2 row
  count remains `1` for the update path.
- `./gradlew test` (full suite) exits 0.

## Out of scope (verbatim from spec, plus additions)

From spec:

- `AccountPersistenceAdapter` / `AccountMapper` / `ActivityRepository`
  production code.
- `src/test/resources/.../AccountPersistenceAdapterTest.sql` (do not edit).

Generator-added:

- All production code (any file under `src/main/kotlin/**`). The non-negotiable
  invariant of the whole migration is that production code is not modified.
- `build.gradle` — Sprint 00 wired Kotest + MockK + springmockk; Sprint 07
  removes the legacy stack. This sprint is test-source-only.
- ArchUnit code under `src/test/kotlin/io/reflectoring/buckpal/archunit/**`.
- Domain tests (`AccountTest`, `ActivityWindowTest`) — Sprint 01.
- Application-service test (`SendMoneyServiceTest`) — Sprint 02.
- Controller test (`SendMoneyControllerTest`) — Sprint 03.
- Smoke + ArchUnit-hosting tests (`BuckPalApplicationTests`, `DependencyRuleTests`) — Sprint 05.
- System test (`SendMoneySystemTest`) — Sprint 06.
- Coroutine APIs (`coEvery`, `coVerify`, `runBlocking`). No production method
  on this slice is `suspend`.
- Reintroducing any Mockito artifact (none was used in this file anyway — the
  test is mock-free).

---

## Spring wiring decision

### Chosen approach: **`@DataJpaTest` + `@Import` retained; class becomes a Kotest spec that registers Kotest's `SpringExtension`; SQL fixture is loaded directly inside the `loads account` leaf via `ScriptUtils`.**

The spec's Risk register #2 says `@Sql` resolution inside Kotest test lambdas
is the principal hazard for this sprint (JUnit's `@Sql` is discovered on
`Method` objects; Kotest leaf tests are lambdas, not reflective methods).
Sprint 03 (`@WebMvcTest`) validated the basic Spring-extension wiring — no
`@Sql` was involved there, so this is the first sprint that exercises the
SQL-fixture path under Kotest. The spec explicitly authorises moving the SQL
load out of the `@Sql` annotation and into an in-spec call when the
extension cannot honor a method-level `@Sql` on a lambda leaf (*"if Kotest's
Spring extension cannot honor method-level `@Sql` inside a lambda, **move**
the SQL load to a `beforeTest` block that runs the same SQL"* — the word
**move** authorises dropping the annotation when the fallback is taken).

Concretely:

1. The class declaration becomes
   `class AccountPersistenceAdapterTest : DescribeSpec(...)`.
2. The annotations `@DataJpaTest` and
   `@Import(AccountPersistenceAdapter::class, AccountMapper::class)` stay on
   the class **unchanged**. Spring Boot's test slice infrastructure reads
   these via reflection; whether the class is a JUnit class or a Kotest spec
   is irrelevant — what matters is that **a `TestContextManager` is created
   and bound to the test instance**, which is exactly what Kotest's
   `SpringExtension` does (it is a `TestListener` that inspects the spec
   instance, runs the standard `TestContextManager` lifecycle, and performs
   `@Autowired` injection on the spec instance properties).
3. `@Autowired` injection stays as `@Autowired private lateinit var
   adapterUnderTest: AccountPersistenceAdapter` and `@Autowired private
   lateinit var activityRepository: ActivityRepository` (Kotest spec
   properties). A third `@Autowired private lateinit var dataSource:
   DataSource` is added so the SQL fixture loader can open a JDBC
   connection. `lateinit var` is unavoidable for `@Autowired`-injected
   properties: Spring needs a mutable property to assign into, and there is
   no constructor-arg form available because the test slice creates the
   beans and Kotest creates the spec. The Idiomatic-Kotlin check below
   allows `@Autowired lateinit var` exactly because of this Spring pattern.
4. Kotest's `SpringExtension` is registered via
   `override fun extensions() = listOf(SpringExtension)`. Same form as
   Sprint 03 (PASS).

### `@Sql` resolution — container-path SQL load (no test-name string-match, no marker function)

`kotest-extensions-spring:1.1.3` documents that the Spring extension runs the
JUnit-Jupiter `TestContextManager` lifecycle around each Kotest test. Whether
that lifecycle resolves a `@Sql` annotation attached to a **lambda receiver
method** — i.e., the synthetic method that hosts the `it(...) { … }` body — is
not explicitly guaranteed in the 1.1.3 docs, and the spec's Risk register #2
calls this out as **not guaranteed**.

The Generator therefore does **not** rely on Kotest auto-invoking `@Sql`, and
does **not** rely on any version-sensitive Kotest 5.5.x `testCase.name` /
`testCase.descriptor.id` API to gate a `beforeTest` block on the test-name
string. Instead, the SQL fixture is loaded by **container-path scoping**: the
SQL load runs as a direct call inside the `it("loads account") { … }` leaf,
before any production-code invocation. The `it("updates activities") { … }`
leaf does not call it. This is robust against Kotest API drift between minor
versions (the Kotest 5.x line has shifted between `testName`, `name`, and
`descriptor.id.value`) because no Kotest test-name field is consulted at all.

Concretely:

- Define a private helper inside the class:
  ```kotlin
  private fun loadSql(resource: String) {
      val classpath = "io/reflectoring/buckpal/account/adapter/out/persistence/$resource"
      dataSource.connection.use { connection ->
          ScriptUtils.executeSqlScript(connection, ClassPathResource(classpath))
      }
  }
  ```
- In the `loads account` leaf, call it as the **first** statement:
  ```kotlin
  it("loads account") {
      loadSql("AccountPersistenceAdapterTest.sql")
      val account = adapterUnderTest.loadAccount(...)
      account.activityWindow.getActivities() shouldHaveSize 2
      account.calculateBalance() shouldBe Money.of(500L)
  }
  ```
- The `updates activities` leaf does **not** call `loadSql`. The
  `@DataJpaTest` transactional rollback resets H2 between leaves, so the
  table starts empty for that leaf — preserving today's
  `activityRepository.count() == 1L` assertion.

This is the **container-path** form: SQL is scoped to a single leaf by being
written inside that leaf's body. No `beforeTest { testCase -> if
(testCase.name.testName == "loads account") ... }` pattern is used; no
Kotest 5.5.x test-name API surface is depended on.

`ScriptUtils.executeSqlScript` is the same call Spring's `@Sql` machinery
uses under the hood — behavior is semantically identical to today's JUnit
`@Sql` path.

The classpath resource path is
`io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.sql`
(relative to test resources root); equivalent to today's
`@Sql("AccountPersistenceAdapterTest.sql")` which is relative to the test
class's package.

### Annotation policy: `@Sql` annotation is dropped from the file

The spec explicitly authorises this when the SQL load is moved out of `@Sql`:
*"if Kotest's Spring extension cannot honor method-level `@Sql` inside a
lambda, **move** the SQL load to a `beforeTest` block that runs the same
SQL (verify by reading the SQL file)."* The Generator takes the
container-path form (above) rather than `beforeTest`, but the principle is
the same: the SQL load is moved out of the annotation, so the annotation
serves no purpose and is removed. A dead-code "marker function" carrying
`@Sql` with an empty body that nobody calls would satisfy a naive grep but
not a reader — it is a smell, not preservation, and is **not** used here.

The Generator therefore:

- **Removes** the `@Sql("AccountPersistenceAdapterTest.sql")` annotation
  from the file.
- **Removes** the `import org.springframework.test.context.jdbc.Sql` line.
- **Keeps** the SQL fixture's resource name as a literal string in the
  source — `"AccountPersistenceAdapterTest.sql"` is passed to
  `loadSql(...)` inside the `loads account` leaf. The acceptance grep
  below verifies the resource name is still referenced in the source
  (positive gate; replaces the prior `@Sql(...)` annotation gate).

### Spec-style choice: **`DescribeSpec`** (one `describe` + two `it`)

The spec's Spec-style selection paragraph names `DescribeSpec` for the
persistence test (verbatim: *"Persistence test
(`AccountPersistenceAdapterTest`) → `DescribeSpec` to group `loadsAccount`
and `updatesActivities`."*). `FunSpec` is also permitted; the Generator picks
`DescribeSpec` for consistency with Sprint 03 (also a Spring slice using
`DescribeSpec`) and the upcoming Sprint 06.

Rationale to pick `DescribeSpec` class-body form:

- The class needs property declarations (`@Autowired lateinit var` for
  `adapterUnderTest`, `activityRepository`, and `dataSource`).
  Constructor-arg lambda form (`: DescribeSpec({ … })`) cannot host property
  declarations.
- Two leaves naturally group under one `describe("AccountPersistenceAdapter") { … }`
  container with `it("loads account") { … }` and `it("updates activities") { … }`
  children — Kotest's container/leaf model maps onto the two `@Test` methods
  1:1.

### Kotest `SpringExtension` registration form

Kotest 5.5.x exposes the Spring extension as
`io.kotest.extensions.spring.SpringExtension` (object). Registration is via:

```kotlin
override fun extensions() = listOf(SpringExtension)
```

Same form as Sprint 03 (PASS-reviewed). The override is placed inside the
spec class body alongside the `init { describe(...) { ... } }` block.

### Why no mocks

The persistence test exercises real JPA against H2 via `@DataJpaTest`. There
are no `@MockBean` / `@MockkBean` declarations today, and none should be
added. springmockk is irrelevant to this sprint (the underscore-free
`com.ninjasquad.springmockk.MockkBean` package note from the Sprint 03 review
is **still** the correct one — Sprint 03 follow-up — but unused here).

## Sprint-03 review follow-ups absorbed

The Sprint 03 review surfaced two follow-ups; both are recorded here so this
contract does not repeat them.

1. **springmockk class FQN is `com.ninjasquad.springmockk.MockkBean`** (no
   underscore). The Maven group is `com.ninja-squad` (hyphen), but the JAR
   collapses the hyphen rather than substituting an underscore. **This sprint
   does not import `MockkBean`** because no mocks are used here. Recorded for
   Sprint 06.
2. **`.header("Content-Type", "application/json")` baggage** observation —
   not applicable to this sprint (no `MockMvc`).

## Inventory of legacy APIs used today

Grepped from the live file
(`src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt`,
56 lines):

| Today's import / annotation                                                | Where it appears                                                    |
|----------------------------------------------------------------------------|---------------------------------------------------------------------|
| `org.assertj.core.api.Assertions.assertThat` (import + `assertThat(...)`)  | line 8 (import), lines 31, 32, 50, 53 (four assertion call sites)   |
| `org.junit.jupiter.api.Test` (import + annotation)                         | line 9 (import), lines 26, 35 (two annotations)                     |
| `@Sql("AccountPersistenceAdapterTest.sql")` (annotation)                   | line 27 (above `loadsAccount`)                                      |
| `@DataJpaTest` (annotation)                                                | line 16 (class-level)                                               |
| `@Import(AccountPersistenceAdapter::class, AccountMapper::class)`          | line 17 (class-level)                                               |
| `@Autowired private lateinit var adapterUnderTest: AccountPersistenceAdapter` | lines 20–21                                                       |
| `@Autowired private lateinit var activityRepository: ActivityRepository`   | lines 23–24                                                         |

Imports to **delete**:

- `org.assertj.core.api.Assertions.assertThat`
- `org.junit.jupiter.api.Test`

Imports to **retain** (unchanged):

- `io.reflectoring.buckpal.account.domain.Account.AccountId`
- `io.reflectoring.buckpal.account.domain.ActivityWindow`
- `io.reflectoring.buckpal.account.domain.Money`
- `io.reflectoring.buckpal.common.AccountTestData.defaultAccount`
- `io.reflectoring.buckpal.common.ActivityTestData.defaultActivity`
- `org.springframework.beans.factory.annotation.Autowired`
- `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest`
- `org.springframework.context.annotation.Import`
- `org.springframework.test.context.jdbc.Sql`
- `java.time.LocalDateTime`

Imports to **add**:

- `io.kotest.core.spec.style.DescribeSpec`
- `io.kotest.extensions.spring.SpringExtension`
- `io.kotest.matchers.collections.shouldHaveSize`
- `io.kotest.matchers.shouldBe`
- `org.springframework.core.io.ClassPathResource`
- `org.springframework.jdbc.datasource.init.ScriptUtils`
- `javax.sql.DataSource`

All three Spring/JDBC imports are required because the SQL fixture is loaded
in-leaf via `ScriptUtils.executeSqlScript(...)` against the `@Autowired`
`DataSource`. No conditional path; no marker-function alternative.

## AssertJ → Kotest assertion mapping

| Today (line)                                                            | Tomorrow                                                                        |
|--------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| `assertThat(account.activityWindow.getActivities()).hasSize(2)` (line 31) | `account.activityWindow.activities shouldHaveSize 2` (Kotlin property accessor) or `account.activityWindow.getActivities() shouldHaveSize 2` (Java-style — either compiles) |
| `assertThat(account.calculateBalance()).isEqualTo(Money.of(500L))` (line 32) | `account.calculateBalance() shouldBe Money.of(500L)`                            |
| `assertThat(activityRepository.count()).isEqualTo(1)` (line 50)          | `activityRepository.count() shouldBe 1L`                                        |
| `assertThat(savedActivity.amount).isEqualTo(1L)` (line 53)               | `savedActivity.amount shouldBe 1L`                                              |

Notes:

- `shouldBe` is **infix-only** in this file (consistent with Sprint 01 and
  Sprint 02 conventions); method-call form `actual.shouldBe(expected)` is
  banned by grep.
- `shouldHaveSize` is also infix-only.
- `Money` is a `data class`, so structural `equals` is value-based —
  `Money.of(500L) shouldBe Money.of(500L)` succeeds.
- `activityRepository.count()` returns `Long`; the literal `1L` makes the
  type match explicit (today's AssertJ `.isEqualTo(1)` accepts the auto-widen
  from `Int` to `Long`, but Kotlin's `shouldBe` is type-strict in some
  matcher implementations — `1L` is the safer literal).

## Conversion targets

| `.kt` file in scope                  | Class type                                                                                 |
|--------------------------------------|---------------------------------------------------------------------------------------------|
| `AccountPersistenceAdapterTest.kt`   | `class AccountPersistenceAdapterTest : DescribeSpec()` with `init { describe { it; it } }` |

Class shape facts:

- Stays in package `io.reflectoring.buckpal.account.adapter.out.persistence`.
- Keeps class name `AccountPersistenceAdapterTest` so test filtering
  (`--tests "io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest"`)
  continues to work unchanged.
- Class-level annotations preserved verbatim:
  - `@DataJpaTest`
  - `@Import(AccountPersistenceAdapter::class, AccountMapper::class)`
- Class-level `override fun extensions() = listOf(SpringExtension)` registers
  the Kotest Spring extension.
- Class-level `@Autowired private lateinit var adapterUnderTest:
  AccountPersistenceAdapter` — populated by Spring's `TestContextManager`.
- Class-level `@Autowired private lateinit var activityRepository:
  ActivityRepository` — populated by Spring's `TestContextManager`.
- Class-level `@Autowired private lateinit var dataSource: DataSource` —
  populated by Spring's `TestContextManager`; used by the private
  `loadSql(...)` helper to execute the SQL fixture against H2 via
  `ScriptUtils.executeSqlScript`.
- Private helper `loadSql(resource: String)` that opens a connection from
  `dataSource` and calls `ScriptUtils.executeSqlScript` with a
  `ClassPathResource` whose path is
  `io/reflectoring/buckpal/account/adapter/out/persistence/<resource>`.
- `init { describe("AccountPersistenceAdapter") { it("loads account") {
  loadSql("AccountPersistenceAdapterTest.sql"); ... } it("updates
  activities") { ... } } }` — one container, two leaves; the SQL fixture is
  loaded as the first statement of the `loads account` leaf.
- The literal string `"AccountPersistenceAdapterTest.sql"` is present in the
  source (passed as the argument to `loadSql`). The grep gate below pins
  this; the `@Sql` annotation itself is dropped (per the spec-authorised
  "move the SQL load" path).

## Idiomatic Kotlin commitments

1. **`DescribeSpec()` class-body form** — `class AccountPersistenceAdapterTest
   : DescribeSpec()` with `init { describe { it; it } }`. Constructor-arg
   lambda form is **not** used because the class needs `@Autowired lateinit
   var` property declarations.
2. **`@Autowired lateinit var` declarations are permitted exceptions to the
   "prefer `val`" guideline.** Rationale: Spring's `TestContextManager`
   populates these properties via reflection **after** spec instantiation —
   the constructor cannot supply them. Kotest's Spring extension explicitly
   documents this pattern.

   The count of `lateinit var` lines in the file is **exactly 3**:
   `adapterUnderTest`, `activityRepository`, and `dataSource`. Every
   `lateinit var` must carry `@Autowired` on the same line or on the
   immediately preceding line. No other `lateinit var` is permitted.

   **Grep gate:** `wc -l` count of `lateinit var` lines is exactly **3**.
   The Evaluator must manually confirm that each `lateinit var` is
   `@Autowired`.
3. **No `!!` non-null assertions** — the migrated file contains zero `!!`.
4. **No `org.assertj.core.*` imports.** AssertJ is fully replaced by Kotest
   matchers. The grep check below enforces this absolutely.
5. **No `@Test` annotation; no `org.junit.jupiter.api` imports.**
6. **Infix `shouldBe`, no method-call form** — `.shouldBe(` is banned.
7. **`@DataJpaTest` and `@Import(AccountPersistenceAdapter::class,
   AccountMapper::class)` are preserved verbatim** — each must appear at
   least once in the file. The `@Sql` annotation is **dropped** in favor of
   the in-leaf SQL load via `ScriptUtils.executeSqlScript` (authorised by
   the spec's *"move the SQL load"* clause); the SQL fixture's resource
   name remains in the source as a string-literal argument to `loadSql`.
8. **`@DataJpaTest` semantics (transactional rollback per test) are unchanged**
   — no `@Transactional` override, no `@Rollback(false)`. The fact that the
   leaf for `updatesActivities` ends with `activityRepository.count() == 1L`
   means the transaction rollback at leaf end resets the row to zero before
   the next leaf, exactly like today's JUnit behavior.

## Risk handling specific to this sprint

### Risk: `@Sql` resolution inside Kotest test lambdas (spec Risk register #2)

Detection: if the `loads account` leaf throws
`EmptyResultDataAccessException` or returns an `Account` with zero
activities, the SQL did not load. Mitigation:

- The Generator's path is the **in-leaf `loadSql(...)` call** using
  `ScriptUtils.executeSqlScript` against the `@Autowired` `DataSource`.
  This is deterministic and equivalent to today's JUnit `@Sql` semantics —
  it uses the same Spring helper under the hood, runs synchronously before
  the leaf body, and is scoped to a single leaf by its location in the
  source (no test-name string match, no Kotest API-drift risk).
- The `@Sql` annotation is **dropped** from the file (authorised by the
  spec's *"move the SQL load"* clause). No marker function is introduced.
- The SQL file itself is **never edited** (spec out-of-scope item).
  `ScriptUtils.executeSqlScript(connection, ClassPathResource("io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.sql"))`
  reads the file verbatim from `src/test/resources/...`.

### Risk: `@DataJpaTest` transactional rollback

`@DataJpaTest` rolls back each test in a transaction by default. Today's
JUnit test depends on this for the `updatesActivities` leaf — it asserts
`activityRepository.count() == 1` (one inserted activity), implying the
table started empty for that test (no SQL preload). Under Kotest, the same
rollback semantics apply because the `TestContextManager` lifecycle is
identical — the `@Transactional` annotation that `@DataJpaTest` composes is
honored. **No mitigation needed**, but the Evaluator should re-verify that
the `updatesActivities` leaf still observes `count() == 1L` (not `2L` from
cross-test bleed).

### Risk: `ActivityWindow.getActivities()` vs `activities` property accessor

`ActivityWindow` is a Kotlin class with a backing `activities` collection.
The Java/AssertJ form was `getActivities()`; in Kotlin this can be accessed
as `activityWindow.activities` (property accessor) **only if** the production
class exposes it as a `val activities` or a getter named `getActivities()`.
Production code is out of scope; the Generator must not modify it. Either
form (`activityWindow.activities shouldHaveSize 2` or
`activityWindow.getActivities() shouldHaveSize 2`) is acceptable; the grep
check below does not enforce which form is used — only that AssertJ is gone.

### Risk: aggregate leaf count

The Sprint 03 baseline is 16 leaves. `AccountPersistenceAdapterTest` had **2**
leaves before (`loadsAccount`, `updatesActivities`) and must have exactly **2**
leaves after this sprint (`it("loads account") { … }` and `it("updates
activities") { … }`). Aggregate stays at **16**.

### Risk: TEST-*.xml leaf count under Kotest

Today the JUnit engine emits
`TEST-io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest.xml`
with `tests="2"`. Under Kotest's JUnit Platform engine the same FQCN file is
emitted with `tests="2"`. Acceptance check below formalizes this.

### Risk: full-suite cross-talk

ArchUnit (`DependencyRuleTests`) scans **compiled production classes**, not
test classes. Reshaping `AccountPersistenceAdapterTest` cannot affect
ArchUnit rules. The other Spring slices live in separate classes with
independent `TestContextManager` lifecycles. Verified by running the full
suite in self-check.

## Acceptance checks (mechanically verifiable by Evaluator)

Each box is one shell command or one observable artifact. Categories mirror
Sprint 03 — Behavioral, Architectural, Code Quality (negative + positive),
Idiomatic Kotlin, and Scope.

### Behavioral correctness

- [ ] `./gradlew test --tests "*AccountPersistenceAdapterTest"` → exits 0.
- [ ] `./gradlew test` (full suite) → exits 0; **0 failures**; aggregate
  leaf-test count is unchanged versus the Sprint 03 baseline (16 leaves).
  `AccountPersistenceAdapterTest` contributes exactly **2** leaves (same as
  before this sprint).
- [ ] Parsed
  `build/test-results/test/TEST-io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest.xml`
  reports `tests="2"` and `failures="0"` and `errors="0"` and `skipped="0"`.
- [ ] Parsed
  `build/test-results/test/TEST-io.reflectoring.buckpal.DependencyRuleTests.xml`
  reports `tests="2"` and `failures="0"`. ArchUnit rules remain green; the
  XML artefact is the concrete check (rather than relying on
  `BUILD SUCCESSFUL` text alone).
- [ ] The `loads account` leaf reads two activities and balance `Money.of(500L)`
  (proves SQL preload worked).
- [ ] The `updates activities` leaf observes `activityRepository.count() == 1L`
  (proves no cross-leaf SQL bleed; transactional rollback is intact).

### Architectural integrity

- [ ] `./gradlew check` → exits 0 (full test task + ArchUnit;
  `DependencyRuleTests` must stay green; the XML artefact above is the
  evidence).

### Code quality — AssertJ / JUnit residue is gone

- [ ] `grep -nE "^import org\\.assertj\\.core" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → no matches (exit 1).
- [ ] `grep -n "assertThat(" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → no matches (exit 1). Catches stray AssertJ calls.
- [ ] `grep -nE "^import org\\.junit\\.jupiter" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → no matches (exit 1).
- [ ] `grep -nE "@Test\\b" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → no matches (exit 1). (`\b` so a future `@TestConfiguration` would not trigger.)

### Code quality — Kotest, Spring extension, and `@Sql` are present

- [ ] `grep -nE "^class AccountPersistenceAdapterTest\\s*:\\s*DescribeSpec" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → matches exactly one line.
- [ ] `grep -nE "^import io\\.kotest\\.core\\.spec\\.style\\.DescribeSpec" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → matches at least one line.
- [ ] `grep -nE "^import io\\.kotest\\.extensions\\.spring\\.SpringExtension" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → matches at least one line.
- [ ] `grep -nE "override fun extensions\\(\\)" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → matches at least one line. Kotest's Spring extension is registered.
- [ ] `grep -nE "@DataJpaTest" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → matches at least one line. The slice annotation is preserved per spec.
- [ ] `grep -nE "@Import\\(AccountPersistenceAdapter::class,\\s*AccountMapper::class\\)" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → matches at least one line. The `@Import` is preserved verbatim per spec.
- [ ] `grep -nE "AccountPersistenceAdapterTest\\.sql" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → matches at least one line. The SQL fixture's resource name is referenced in the source (positive gate; replaces the prior `@Sql(...)` annotation gate now that the spec-authorised "move the SQL load" path drops the annotation).
- [ ] `grep -nE "ScriptUtils" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → matches at least one line. The in-leaf SQL load uses Spring's `ScriptUtils` (same helper `@Sql` uses internally).
- [ ] `grep -cE "shouldHaveSize" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → exactly **2** lines (one import, one assertion call site at the `loads account` leaf).
- [ ] `grep -cE "\\bshouldBe\\b" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → exactly **4** lines (one import `io.kotest.matchers.shouldBe`, plus the three assertion call sites named in the mapping table: `Money.of(500L)`, `count() shouldBe 1L`, `savedActivity.amount shouldBe 1L`). Word-boundary form catches infix `x shouldBe y` and explicitly excludes `shouldBeXxx` variants. Exact count — "at least" is rejected because it would let accidental over-asserting slip in unnoticed.

### Idiomatic Kotlin — no banned patterns

- [ ] `grep -nE "!!" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → no matches (exit 1). Literal `!!`; `!=` is not flagged because the regex requires two `!` adjacent.
- [ ] `grep -n "lateinit var" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt | wc -l` → exactly **3**: `adapterUnderTest` (Spring-injected), `activityRepository` (Spring-injected), `dataSource` (Spring-injected, used by `loadSql`). Any other `lateinit var` is a defect.
- [ ] For each `lateinit var` line: the same line OR the line directly above it must contain `@Autowired`. (Manual reading by Evaluator — qualitative side of the count check.)
- [ ] `grep -nE "\\.shouldBe\\(" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → no matches (exit 1). Only infix `actual shouldBe expected` is permitted.
- [ ] `grep -nE "\\.shouldHaveSize\\(" src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → no matches (exit 1). Only infix `actual shouldHaveSize expected` is permitted.

### Scope — only one file changed

- [ ] `git diff --name-only HEAD -- src/` → exactly the single line
  `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt`.
  No other source file is modified.
- [ ] `git diff --name-only HEAD -- src/main/` → empty (no production-code
  edits — protects the non-negotiable invariant).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/common/` → empty (fixtures untouched).
- [ ] `git diff --name-only HEAD -- src/test/resources/` → empty (SQL files
  untouched — Sprint 04 spec out-of-scope item).
- [ ] `git diff --name-only HEAD -- build.gradle` → empty (build-script
  untouched; Sprint 00/07 territory).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/domain/` → empty (Sprint 01 files untouched).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/application/service/` → empty (Sprint 02 files untouched).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/` → empty (Sprint 03 files untouched).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/archunit/` → empty (ArchUnit infrastructure untouched).

## Verification commands the Generator will run before handoff

In order, from the worktree root, with
`JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home`:

1. `./gradlew --no-daemon compileKotlin compileTestKotlin` → expect
   `BUILD SUCCESSFUL`. (Sanity: no production code edits, so `compileKotlin`
   is essentially a no-op; `compileTestKotlin` proves the rewrite parses and
   that `io.kotest.extensions.spring.SpringExtension`,
   `io.kotest.matchers.shouldBe`,
   `io.kotest.matchers.collections.shouldHaveSize`,
   `org.springframework.core.io.ClassPathResource`,
   `org.springframework.jdbc.datasource.init.ScriptUtils`, and
   `javax.sql.DataSource` all resolve.)
2. `./gradlew --no-daemon test --tests "*AccountPersistenceAdapterTest"`
   → expect `BUILD SUCCESSFUL` and the TEST-*.xml file with `tests="2"`,
   `failures="0"`, `errors="0"`, `skipped="0"`.
3. `./gradlew --no-daemon test` → expect `BUILD SUCCESSFUL`, aggregate 16
   leaf tests (same as Sprint 03 baseline), 0 failures. Additionally,
   `build/test-results/test/TEST-io.reflectoring.buckpal.DependencyRuleTests.xml`
   must report `tests="2" failures="0"` — concrete ArchUnit-green artefact
   for the Evaluator to point at, rather than relying on `BUILD SUCCESSFUL`
   text alone (mirrors Sprint 03's review-section pattern).
4. `./gradlew --no-daemon check` → expect `BUILD SUCCESSFUL` (ArchUnit
   `DependencyRuleTests` exercised here; XML artefact from step 3 is the
   pointer).
5. `git diff --name-only HEAD -- src/` → expect exactly one path:
   `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt`.
6. Negative greps (all expect "no output"):
   - `grep -nE "^import org\\.assertj\\.core" <file>`
   - `grep -n "assertThat(" <file>`
   - `grep -nE "^import org\\.junit\\.jupiter" <file>`
   - `grep -nE "@Test\\b" <file>`
   - `grep -nE "!!" <file>`
   - `grep -nE "\\.shouldBe\\(" <file>`
   - `grep -nE "\\.shouldHaveSize\\(" <file>`
7. Positive greps (counts must match exactly where specified):
   - `grep -cE "\\bshouldBe\\b" <file>` → **4** (1 import + 3 call sites).
   - `grep -cE "shouldHaveSize" <file>` → **2** (1 import + 1 call site).
   - `grep -n "lateinit var" <file> | wc -l` → **3**.
   - `grep -nE "AccountPersistenceAdapterTest\\.sql" <file>` → at least 1
     (the literal SQL resource name passed to `loadSql(...)`).
   - `grep -nE "ScriptUtils" <file>` → at least 1 (import + call site).

If any step fails, the Generator will diagnose and rerun. No red handoff.
