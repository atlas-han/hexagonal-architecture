# Sprint 04 Handoff

## What changed

- **Modified:** `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt`
  - Class now extends `DescribeSpec()` (class-body form, since `@Autowired lateinit var` properties are required).
  - Registers `io.kotest.extensions.spring.SpringExtension` via `override fun extensions() = listOf(SpringExtension)`.
  - `@DataJpaTest` and `@Import(AccountPersistenceAdapter::class, AccountMapper::class)` are preserved verbatim.
  - `@Sql("AccountPersistenceAdapterTest.sql")` annotation is **removed** (per the spec-authorised *"move the SQL load"* clause); the SQL fixture is loaded in-leaf via `ScriptUtils.executeSqlScript` against the `@Autowired` `DataSource`, using `DataSourceUtils.getConnection(...)` to join the active test transaction (see Idiomatic Kotlin section).
  - JUnit `@Test` and AssertJ `assertThat(...)` are gone. Two leaves: `it("loads account") { … }` and `it("updates activities") { … }` under one `describe("AccountPersistenceAdapter")` container.
  - AssertJ matchers replaced with Kotest infix matchers (`shouldHaveSize 2`, `shouldBe Money.of(500L)`, `shouldBe 1L` × 2).

No other source file was edited. No production code, fixture, SQL file, or `build.gradle` change.

## Contract checklist

### Behavioral correctness

- [x] `./gradlew test --tests "*AccountPersistenceAdapterTest"` → `BUILD SUCCESSFUL` (run 2 of 2; first run failed due to a non-transactional SQL load, fixed by routing through `DataSourceUtils` — see "Anything the Evaluator should pay extra attention to" below).
- [x] `./gradlew test` (full suite) → `BUILD SUCCESSFUL`. Aggregate leaf count from `build/test-results/test/TEST-*.xml`:
  - `AccountTest`: 4
  - `ActivityWindowTest`: 3
  - `SendMoneyServiceTest`: 2
  - `SendMoneyControllerTest`: 1
  - `AccountPersistenceAdapterTest`: 2
  - `BuckPalApplicationTests`: 1
  - `DependencyRuleTests`: 2
  - `SendMoneySystemTest`: 1
  - **Total: 16 leaves** (Sprint 03 baseline preserved). 0 failures.
- [x] `build/test-results/test/TEST-io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest.xml` reports `tests="2" skipped="0" failures="0" errors="0"`. Leaves `loads account` and `updates activities` both pass.
- [x] `build/test-results/test/TEST-io.reflectoring.buckpal.DependencyRuleTests.xml` reports `tests="2" failures="0" errors="0" skipped="0"`. ArchUnit remains green.
- [x] The `loads account` leaf observes 2 activities and balance `Money.of(500L)` (SQL preload landed before assertions).
- [x] The `updates activities` leaf observes `activityRepository.count() == 1L` (no SQL bleed; transactional rollback intact).

### Architectural integrity

- [x] `./gradlew check` → `BUILD SUCCESSFUL` (ArchUnit `DependencyRuleTests` green; XML artefact above is the evidence).

### Code quality — AssertJ / JUnit residue gone

- [x] `grep -nE "^import org\\.assertj\\.core" <file>` → no matches.
- [x] `grep -n "assertThat(" <file>` → no matches.
- [x] `grep -nE "^import org\\.junit\\.jupiter" <file>` → no matches.
- [x] `grep -nE "@Test\\b" <file>` → no matches.

### Code quality — Kotest, Spring extension, SQL fixture present

- [x] `grep -nE "^class AccountPersistenceAdapterTest\\s*:\\s*DescribeSpec" <file>` → `23:class AccountPersistenceAdapterTest : DescribeSpec() {`.
- [x] `grep -nE "^import io\\.kotest\\.core\\.spec\\.style\\.DescribeSpec" <file>` → `3:`.
- [x] `grep -nE "^import io\\.kotest\\.extensions\\.spring\\.SpringExtension" <file>` → `4:`.
- [x] `grep -nE "override fun extensions\\(\\)" <file>` → `25:    override fun extensions() = listOf(SpringExtension)`.
- [x] `grep -nE "@DataJpaTest" <file>` → preserved at line 21.
- [x] `grep -nE "@Import\\(AccountPersistenceAdapter::class,\\s*AccountMapper::class\\)" <file>` → preserved at line 22.
- [x] `grep -nE "AccountPersistenceAdapterTest\\.sql" <file>` → `51: loadSql("AccountPersistenceAdapterTest.sql")`.
- [x] `grep -nE "ScriptUtils" <file>` → matches at the import (line 17) and call site (line 42).
- [x] `grep -cE "shouldHaveSize" <file>` → **2** (import + one call site).
- [x] `grep -cE "\\bshouldBe\\b" <file>` → **4** (import + three call sites: `Money.of(500L)`, `1L`, `1L`).

### Idiomatic Kotlin — no banned patterns

- [x] `grep -nE "!!" <file>` → no matches.
- [x] `grep -n "lateinit var" <file> | wc -l` → exactly **3** (`adapterUnderTest`, `activityRepository`, `dataSource`).
- [x] Each `lateinit var` line carries `@Autowired` on the line directly above (manual confirmation: lines 27-28, 30-31, 33-34).
- [x] `grep -nE "\\.shouldBe\\(" <file>` → no matches (infix only).
- [x] `grep -nE "\\.shouldHaveSize\\(" <file>` → no matches (infix only).

### Scope — only one file changed

- [x] `git diff --name-only HEAD -- src/` → exactly `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt`.
- [x] `git diff --name-only HEAD -- src/main/` → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/common/` → empty.
- [x] `git diff --name-only HEAD -- src/test/resources/` → empty.
- [x] `git diff --name-only HEAD -- build.gradle` → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/domain/` → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/application/service/` → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/` → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/archunit/` → empty.

## Idiomatic Kotlin choices worth flagging

1. **`DescribeSpec()` class-body form.** Constructor-arg lambda form (`: DescribeSpec({ … })`) cannot host property declarations, and the spec needs three `@Autowired lateinit var` properties (`adapterUnderTest`, `activityRepository`, `dataSource`), plus an `override fun extensions()`. Class-body + `init { describe { … } }` is the only viable form. Matches Sprint 03.

2. **`@Autowired lateinit var` exception.** Kotlin idiom prefers `val`, but Spring's `TestContextManager` populates these properties via reflection *after* spec construction — no constructor-arg form is available because the test slice owns bean creation and Kotest owns spec instantiation. Exactly the same pattern Sprint 03 used and the Sprint 03 review accepted.

3. **In-leaf SQL load via `ScriptUtils` + `DataSourceUtils.getConnection`.** The contract's "container-path SQL load" approach correctly avoids the Kotest 5.5.x test-name API drift (no `testCase.name.testName` matching). However, the *first* implementation called `dataSource.connection.use { … }`, which acquires a fresh, auto-commit JDBC connection that is **not** part of the active test transaction. This caused `updates activities` to fail with a `PRIMARY KEY` violation on `ACTIVITY.ID = 1` because the SQL preload rows from `loads account` were committed independently of the test transaction and never rolled back. The fix is to use `org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource)` — Spring's own helper for joining the current transaction's connection — and `DataSourceUtils.releaseConnection` for cleanup. This is also exactly what Spring's `@Sql` machinery does under the hood (`SqlScriptsTestExecutionListener` calls `DataSourceUtils.getConnection`), so behavior is semantically identical to today's JUnit `@Sql` path. Net effect: the contract's "one extra import" (`ScriptUtils`/`ClassPathResource`/`DataSource`) became two extra imports (added `DataSourceUtils`). Flagged below for the Evaluator.

4. **Infix Kotest matchers everywhere.** `shouldHaveSize`, `shouldBe` — no method-call form. Consistent with Sprints 01–03.

5. **`val` for the local `account` and `savedActivity` references inside leaves.** No `var` introduced.

6. **No coroutine APIs.** No `suspend`, no `coEvery`, no `runBlocking`. Kotest 5.5.x test scopes are coroutine-based internally but the test bodies remain straight-line.

## Anything the Evaluator should pay extra attention to

1. **Deviation from the contract: `DataSourceUtils` was added.** The contract's "Imports to add" list named `ScriptUtils`, `ClassPathResource`, and `javax.sql.DataSource`, and the `loadSql` body shown in the contract used `dataSource.connection.use { connection -> ScriptUtils.executeSqlScript(connection, ...) }`. That form acquires a **fresh** auto-commit connection that bypasses the `@DataJpaTest` transaction — the SQL inserts commit immediately and persist across leaves, causing `updates activities` to fail with PK collision at `ACTIVITY.ID = 1`. The fix is to acquire the connection via `org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource)` (which returns the transaction-bound connection if one is active) and release it via `DataSourceUtils.releaseConnection`. Final import list:

   - `io.kotest.core.spec.style.DescribeSpec`
   - `io.kotest.extensions.spring.SpringExtension`
   - `io.kotest.matchers.collections.shouldHaveSize`
   - `io.kotest.matchers.shouldBe`
   - `org.springframework.core.io.ClassPathResource`
   - **`org.springframework.jdbc.datasource.DataSourceUtils`** (new vs contract)
   - `org.springframework.jdbc.datasource.init.ScriptUtils`
   - `javax.sql.DataSource`

   The contract's grep gates are unaffected: none of them keys on `DataSourceUtils`, the `ScriptUtils` positive grep still matches, and no new `lateinit var`s were introduced. The contract's "Risk: `@DataJpaTest` transactional rollback" section asserts "**no mitigation needed**, but the Evaluator should re-verify that the `updatesActivities` leaf still observes `count() == 1L`" — that's exactly the regression the `DataSourceUtils` fix prevents, and the green `tests="2" failures="0"` XML confirms `count() == 1L` is observed.

2. **`@Sql` annotation is dropped from the file.** This is contract-authorised (spec: *"if Kotest's Spring extension cannot honor method-level `@Sql` inside a lambda, **move** the SQL load to a `beforeTest` block that runs the same SQL"*). The literal `"AccountPersistenceAdapterTest.sql"` resource name is preserved as a string argument to `loadSql(...)` at line 51 — the contract's positive grep gate `AccountPersistenceAdapterTest\.sql` matches.

3. **`ActivityWindow.getActivities()` (Java-style getter) is used, not the `activities` property.** This is acceptable per the contract: *"Either form (`activityWindow.activities shouldHaveSize 2` or `activityWindow.getActivities() shouldHaveSize 2`) is acceptable; the grep check below does not enforce which form is used — only that AssertJ is gone."* Kept `getActivities()` to minimise the diff against the prior test body.

4. **First test run failed; second test run passes.** The first `./gradlew test --tests "*AccountPersistenceAdapterTest"` exited with `BUILD FAILED` (PK collision on activity id=1). The fix (`DataSourceUtils`) was applied in the same Generator session, and the second run passed. The current working tree reflects the post-fix state. The handoff is green.

## TODOs deferred to later sprints

- **Sprint 05** — migrate `DependencyRuleTests` and `BuckPalApplicationTests` to Kotest. Currently they still use JUnit + AssertJ (`tests="2"` for `DependencyRuleTests`, `tests="1"` for `BuckPalApplicationTests` — these counts will be preserved by Sprint 05).
- **Sprint 06** — migrate `SendMoneySystemTest` (the `@SpringBootTest`-flavored end-to-end test) to Kotest. The springmockk class FQN gotcha (`com.ninjasquad.springmockk.MockkBean`, no underscore — recorded by the Sprint 03 review) does not apply here (no mocks in this sprint), but will apply when Sprint 06 wires springmockk if it needs to.
- **Sprint 07** — strip legacy test deps (`junit-jupiter-engine`, `mockito-junit-jupiter`, `kotlin-test`, `kotlin-test-junit5`) from `build.gradle`.
- **No production-code TODOs.** Sprint 04 touched zero production files.

## Commit

Proposed one-line summary for the orchestrator:

```
feat(kotest): sprint 04 — migrate AccountPersistenceAdapterTest to Kotest + DataJpaTest
```

Working tree is left dirty (one file modified, `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt`); the orchestrator stages and commits after the Evaluator PASSes.
