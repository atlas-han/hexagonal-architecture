# Sprint 06 Handoff

## What changed

- Rewrote
  `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` from a
  JUnit 5 + AssertJ `BDDAssertions.then` test class into a Kotest
  `DescribeSpec` class-body spec that registers Kotest's
  `SpringExtension`. The `@SpringBootTest(webEnvironment =
  WebEnvironment.RANDOM_PORT)` annotation is preserved verbatim on the
  class.
- The single `@Test fun sendMoney()` method became one `describe("POST
  /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}")
  { it("sends money between two accounts") { ... } }` leaf.
- Replaced the three `BDDAssertions.then(...).isEqualTo(...)` call sites
  with Kotest infix `shouldBe` matchers (HTTP status + source/target
  balance deltas).
- Dropped the `@Sql("SendMoneySystemTest.sql")` method-level annotation
  (Kotest 5.5.x lambda leaves are not `Method` objects, so
  `SqlScriptsTestExecutionListener` cannot discover them) and replaced
  it with an in-leaf `loadSql("SendMoneySystemTest.sql")` call, mirroring
  Sprint 04's proven `DataSourceUtils` + `ScriptUtils.executeSqlScript`
  + `ClassPathResource` pattern. The SQL classpath resource at
  `src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql`
  is untouched.
- Added a third `@Autowired private lateinit var dataSource: DataSource`
  property to support the programmatic SQL load (Sprint-04 pattern). The
  two original `@Autowired` properties (`restTemplate: TestRestTemplate`,
  `loadAccountPort: LoadAccountPort`) are preserved verbatim in name and
  type.
- The four private helper functions (`sourceAccount`, `targetAccount`,
  `loadAccount`, `whenSendMoney`) and the three private constants-as-funs
  (`transferredAmount`, `sourceAccountId`, `targetAccountId`) stay as
  private class-body member functions — they reference
  `loadAccountPort` / `restTemplate`, which are class-level `lateinit`
  properties.
- No files outside this single test file were edited. No `.java` files
  were touched. No production code was touched.

## Contract checklist

### Behavioral correctness

- [x] `./gradlew test --tests "*SendMoneySystemTest"` → `BUILD SUCCESSFUL`
  (exit 0).
- [x] `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"`
  → `BUILD SUCCESSFUL` (exit 0).
- [x] `./gradlew test` (full suite) → `BUILD SUCCESSFUL` (exit 0).
- [x] Aggregate leaf-test count is **16** (unchanged from Sprint 05
  baseline). Per
  `build/test-results/test/TEST-*.xml`:

  | Suite | tests | failures | errors |
  |-------|-------|----------|--------|
  | `account.adapter.in.web.SendMoneyControllerTest` | 1 | 0 | 0 |
  | `account.adapter.out.persistence.AccountPersistenceAdapterTest` | 2 | 0 | 0 |
  | `account.application.service.SendMoneyServiceTest` | 2 | 0 | 0 |
  | `account.domain.AccountTest` | 4 | 0 | 0 |
  | `account.domain.ActivityWindowTest` | 3 | 0 | 0 |
  | `BuckPalApplicationTests` | 1 | 0 | 0 |
  | `DependencyRuleTests` | 2 | 0 | 0 |
  | `SendMoneySystemTest` | 1 | 0 | 0 |
  | **Total** | **16** | **0** | **0** |
- [x] `TEST-io.reflectoring.buckpal.SendMoneySystemTest.xml` reports
  `tests="1" skipped="0" failures="0" errors="0"`, and the single
  testcase `sends money between two accounts` passes (time=0.157s,
  random port bound, SQL load inserted accounts and activities, POST
  returned 200, balance deltas verified).
- [x] The leaf body asserts:
  - HTTP `200 OK` on the response from `POST /accounts/send/1/2/500`.
  - Source balance after = source balance before `- 500`.
  - Target balance after = target balance before `+ 500`.

### Architectural integrity

- [x] `./gradlew check` → `BUILD SUCCESSFUL` (exit 0). ArchUnit
  `DependencyRuleTests` continues to pass; ArchUnit infrastructure and
  production code were not touched.

### Code quality — JUnit / AssertJ / Mockito residue is gone

- [x] `grep -nE "^import org\.junit\.jupiter" …SendMoneySystemTest.kt` → no match.
- [x] `grep -nE "@Test\b" …SendMoneySystemTest.kt` → no match.
- [x] `grep -nE "^import org\.assertj\.core" …SendMoneySystemTest.kt` → no match.
- [x] `grep -nE "BDDAssertions" …SendMoneySystemTest.kt` → no match.
- [x] `grep -nE "^import org\.mockito" …SendMoneySystemTest.kt` → no match.
- [x] `grep -nE "BDDMockito" …SendMoneySystemTest.kt` → no match.
- [x] `grep -nE "@MockBean\b|@MockkBean\b" …SendMoneySystemTest.kt` → no match.
- [x] `grep -nE "\bmockk\b|\bevery\s*\{|\bverify\s*\{" …SendMoneySystemTest.kt` → no match.
- [x] `grep -nE "^import org\.springframework\.test\.context\.jdbc\.Sql" …SendMoneySystemTest.kt` → no match.
- [x] `grep -nE "@Sql\b" …SendMoneySystemTest.kt` → no match (the
  annotation and every textual reference to `@Sql` are gone — the
  helper comment was reworded to "annotation-driven SQL load
  semantics" to keep this grep clean).

### Code quality — Kotest spec wrappers are present

- [x] `grep -nE "^class SendMoneySystemTest\s*:\s*DescribeSpec" …` → 1 match (line 26).
- [x] `grep -nE "^import io\.kotest\.core\.spec\.style\.DescribeSpec" …` → 1 match (line 3).
- [x] `grep -nE "^import io\.kotest\.extensions\.spring\.SpringExtension" …` → 1 match (line 4).
- [x] `grep -nE "^import io\.kotest\.matchers\.shouldBe" …` → 1 match (line 5).
- [x] `grep -nE "override fun extensions\(\)" …` → 1 match (line 28).
- [x] `grep -nE "@SpringBootTest" …` → 1 match (line 25).
- [x] `grep -nE "WebEnvironment\.RANDOM_PORT" …` → 1 match (line 25).
- [x] `grep -nE "\bdescribe\(\"" …SendMoneySystemTest.kt | wc -l` → **1**.
- [x] `grep -nE "\bit\(\"" …SendMoneySystemTest.kt | wc -l` → **1**.
- [x] `grep -nE "\bshouldBe\b" …SendMoneySystemTest.kt | wc -l` → **4**
  (≥3 required: 1 status-code + 2 balance deltas; the `import` line
  also matches the word `shouldBe`, hence 4).
- [x] `grep -nE "loadSql\(\"SendMoneySystemTest\.sql\"\)" …` → 1 match (line 57).
- [x] `grep -nE "ScriptUtils\.executeSqlScript" …` → 1 match (line 48).
- [x] `grep -nE "ClassPathResource" …` → 2 matches (import + call site).

### Code quality — HTTP shape and assertion semantics preserved

- [x] `grep -nE "TestRestTemplate" …` → 2 matches (import + `lateinit var`).
- [x] `grep -nE "restTemplate\.exchange" …` → 1 match (line 93).
- [x] `grep -nE "HttpMethod\.POST" …` → 1 match (line 95).
- [x] `grep -nE "/accounts/send/" …` → 2 matches (describe container + URL template).
- [x] `grep -nE "HttpStatus\.OK" …` → 1 match (line 68).
- [x] `grep -nE "LoadAccountPort" …` → 2 matches (import + `lateinit var`).

### Idiomatic Kotlin — no banned patterns, mandatory positive shape

- [x] `grep -nE "!!" …SendMoneySystemTest.kt` → no match (no
  double-bang non-null assertions).
- [x] `grep -nE "\.shouldBe\(" …SendMoneySystemTest.kt` → no match
  (only infix `actual shouldBe expected` is used).
- [x] `grep -n "lateinit var" …SendMoneySystemTest.kt | wc -l` → **3**
  (`restTemplate`, `loadAccountPort`, `dataSource`).
- [x] `grep -nE "@Autowired\b" …SendMoneySystemTest.kt | wc -l` → **3**
  (one per `lateinit var`).

### Scope — only the one file in scope changed

- [x] `git diff --name-only HEAD -- src/` → exactly one line:
  `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`.
- [x] `git diff --name-only HEAD -- src/main/` → empty (production
  code untouched — the non-negotiable migration invariant).
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/common/`
  → empty (fixtures untouched).
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/archunit/`
  → empty (ArchUnit infrastructure untouched).
- [x] `git diff --name-only HEAD -- build.gradle` → empty (build script
  untouched; Sprint 00 / Sprint 07 territory).
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/`
  → empty (Sprints 01–04 files untouched).
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`
  → empty (Sprint 05 files untouched).
- [x] `git diff --name-only HEAD -- src/test/resources/` → empty (SQL
  resource untouched — spec out-of-scope).

## Idiomatic Kotlin choices worth flagging

- **`DescribeSpec()` class-body / `init { }` form.** Required because
  Kotest's `override fun extensions() = listOf(SpringExtension)` is a
  member function and cannot live inside a `FunSpec({ ... })`
  constructor-arg lambda. This matches the wiring shape used in Sprint
  03 (`SendMoneyControllerTest`), Sprint 04
  (`AccountPersistenceAdapterTest`), and Sprint 05
  (`BuckPalApplicationTests`).
- **Three `@Autowired private lateinit var` properties — `restTemplate`,
  `loadAccountPort`, `dataSource`.** The first two are preserved verbatim
  from the JUnit version. The third (`dataSource`) is the Sprint-04
  pattern addition needed to load the SQL programmatically: Kotest
  5.5.x's `kotest-extensions-spring:1.1.3` cannot deliver method-level
  `@Sql` discovery onto lambda leaves (Spring's
  `SqlScriptsTestExecutionListener` reflects on `Method` objects, not on
  Kotest's lambda leaves). The hard exit criterion explicitly sanctions
  "the same `@Sql` fallback strategy as Sprint 04", and Sprint 04's
  realized strategy was to drop the annotation and use `DataSourceUtils`
  + `ScriptUtils.executeSqlScript(ClassPathResource(...))`. Sprint 06
  adopts the same.
- **No `!!` non-null assertions.** The original file had none; the
  migration preserves that. The `lateinit var` properties are populated
  by Spring's `TestContextManager` (driven by Kotest's `SpringExtension`)
  before any leaf body runs, so accesses are always safe.
- **Three `shouldBe` infix assertions in the leaf body** (plus one
  `import io.kotest.matchers.shouldBe` line — the regex counts both,
  giving 4 total). All in infix form `actual shouldBe expected`; never
  the method-call form `.shouldBe(...)`.
- **HTTP round-trip shape preserved verbatim.** `restTemplate.exchange`
  is called with the same path template, `HttpMethod.POST`,
  `HttpEntity<Void>(null, headers)`, `Content-Type: application/json`,
  and the same path-variable order (`sourceAccountId.value`,
  `targetAccountId.value`, `amount.amount`). The Generator did not
  refactor the HTTP shape.
- **Helper functions stay as private class-body member functions.** They
  reference `loadAccountPort` / `restTemplate`, so they cannot move to
  top-level without losing access to the injected properties. Today's
  shape is preserved.

## Anything the Evaluator should pay extra attention to

- The `loadSql` helper is the only behavioral difference between the
  pre- and post-migration test. Spring's
  `SqlScriptsTestExecutionListener` runs `@Sql` scripts through
  `ScriptUtils.executeSqlScript` against a `Connection` obtained from
  `DataSourceUtils` — the same two-line call the helper makes here.
  The semantics are therefore identical: the SQL inserts run inside the
  current test thread before any HTTP exchange.
- `@SpringBootTest` does **not** auto-rollback the way `@DataJpaTest`
  does. This file has exactly one leaf, so the inserted rows live for
  the duration of this test class's Spring context and the test is
  isolated by virtue of running in its own H2 instance. If a future
  sprint adds a second leaf to this file, that author will need to add
  `@DirtiesContext` or a per-leaf truncate — but that is out of scope
  for Sprint 06.
- The helper comment originally contained the phrase "`@Sql`
  semantics", which would have been a false positive against the
  negative grep `@Sql\b`. The comment was reworded to "annotation-driven
  SQL load semantics" so the negative grep is clean. Behavior is
  unaffected.
- The TEST-*.xml for `SendMoneySystemTest` reports the new leaf name
  (`sends money between two accounts`) instead of the old method name
  (`sendMoney`). The classname is unchanged
  (`io.reflectoring.buckpal.SendMoneySystemTest`), so test-filter args
  (`--tests "io.reflectoring.buckpal.SendMoneySystemTest"`,
  `--tests "*SendMoneySystemTest"`) still resolve to this single leaf.

## TODOs deferred to later sprints

- Sprint 07 strips the now-unused legacy test dependencies
  (`junit-jupiter-engine`, `mockito-junit-jupiter`, `kotlin-test`,
  `kotlin-test-junit5`) from `build.gradle`. This sprint did not edit
  the build script.

## Commit

Proposed one-line commit subject for the orchestrator to use:

`feat(kotlin): sprint 6 — migrate SendMoneySystemTest to Kotest DescribeSpec + SpringExtension`
