# Product Spec — Kotest + MockK Test Migration

## Migration goal

Migrate every Kotlin test under `src/test/kotlin/**` from its current
**JUnit 5 + Mockito + kotlin-test** stack to **Kotest** (with the appropriate
spec style per test class — `DescribeSpec`, `BehaviorSpec`, or `FunSpec`) plus
**MockK** for mocking. Test fixtures (`AccountTestData`, `ActivityTestData`),
SQL resources, ArchUnit rules, and all production code remain untouched in
behavior; only the test framework changes. The Spring Boot integration tests
(`@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`, `SendMoneySystemTest`,
`BuckPalApplicationTests`) must continue to work — they will be rehosted under
Kotest specs using `kotest-extensions-spring` plus `springmockk` for
`@MockkBean`. `./gradlew test` must remain green at every sprint boundary.

## Non-negotiable invariants

These hold true at the end of every sprint:

- The production source tree under `src/main/kotlin/io/reflectoring/buckpal/**`
  is **not modified** by any sprint in this migration. Only `src/test/**` and
  `build.gradle` change.
- Public package paths under `io.reflectoring.buckpal.**` (both main and test)
  remain stable. Test classes keep their existing package; only the class body
  and surrounding spec wrapper change.
- The set of behaviors verified by the test suite does not shrink. Every
  assertion that exists today must have an equivalent assertion in the migrated
  spec (no `xit`/`!`/`Ignored` to mask work). Total test count, when counted as
  Kotest *leaf tests* (each `it` / `then` / individual `FunSpec` block),
  matches or exceeds the JUnit `@Test` count of the same class.
- ArchUnit dependency (`com.tngtech.archunit:archunit:0.16.0`) and its rules in
  `io.reflectoring.buckpal.archunit.*` plus `DependencyRuleTests` keep passing
  with semantically identical checks. The ArchUnit classes themselves
  (`HexagonalArchitecture`, `Adapters`, `ApplicationLayer`,
  `ArchitectureElement`) are **not** rewritten as specs — they are support
  types, not tests.
- The Spring Boot application still boots end-to-end via `SendMoneySystemTest`
  and still serves `POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}`.
- `./gradlew test` exits 0 at every sprint boundary. Partial migration is
  allowed mid-sprint, but commits land only at green boundaries.
- `spring-boot-starter-test`, `archunit`, `h2`, and
  `junit-platform-launcher` remain as test dependencies. `kotlin-test` and
  `kotlin-test-junit5` are removed by the final sprint, as are
  `junit-jupiter-engine` and `mockito-junit-jupiter` (replaced by transitive
  JUnit Platform from `kotest-runner-junit5`).

## Target Kotest + MockK conventions

Guidance for the Generator (not prescriptive line-by-line):

- **Spec style selection** — pick exactly **one** spec style per class, based
  on the existing test shape:
  - Pure-unit tests with several scenarios per behavior (`AccountTest`,
    `ActivityWindowTest`, `SendMoneyServiceTest`) → `BehaviorSpec`
    (`given` / `when` / `then`), which maps cleanly onto the current
    `given*` / `when*` / `then*` helper naming and BDDMockito flow.
  - Single-behavior smoke tests (`BuckPalApplicationTests`, the controller
    happy-path `SendMoneyControllerTest`, system test `SendMoneySystemTest`)
    → `DescribeSpec` (`describe` / `it`) or `FunSpec`; choose `FunSpec` when
    there is one flat assertion block, `DescribeSpec` when nesting helps
    readability.
  - Persistence test (`AccountPersistenceAdapterTest`) → `DescribeSpec` to
    group `loadsAccount` and `updatesActivities`.
  - ArchUnit hosting (`DependencyRuleTests`) → `FunSpec` (each `@Test` becomes
    one `test("...") { ... }` block); body logic unchanged.
- **Assertions** — prefer Kotest matchers
  (`shouldBe`, `shouldHaveSize`, `shouldBeTrue`, `shouldBeFalse`,
  `shouldContain`) over AssertJ in migrated specs. AssertJ may stay only if
  retaining a specific matcher is materially clearer; do not add new AssertJ
  usage. `kotest-assertions-core` is the source of truth.
- **Mocking** —
  - `Mockito.mock(X::class.java)` → `mockk<X>()`.
  - `BDDMockito.given(x).willReturn(y)` → `every { x } returns y`.
  - `BDDMockito.then(m).should().foo(...)` → `verify { m.foo(...) }` (use
    `verify(exactly = 0)` for the `times(0)` cases and `verify(exactly = N)`
    elsewhere).
  - `ArgumentCaptor` → MockK `slot<T>()` + `capture(slot)`. The hand-rolled
    `eq` / `capture` null-safety wrappers in `SendMoneyServiceTest` /
    `SendMoneyControllerTest` are deleted — MockK is Kotlin-native and has no
    null-matcher problem.
  - `@MockBean` (Spring) → `@MockkBean` from `com.ninja-squad:springmockk`.
- **Spring integration** — adopt **`kotest-extensions-spring`** for
  Spring-managed tests. Each Spring-flavored spec extends the chosen Kotest
  spec base class and adds `override fun extensions() = listOf(SpringExtension)`
  (or the equivalent listener). The existing Spring annotations
  (`@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`, `@Sql`, `@Import`,
  `@Autowired`, `@MockkBean`) remain on the class / property — Kotest's
  Spring extension wires them. `useJUnitPlatform()` in Gradle stays;
  `kotest-runner-junit5` registers itself as a JUnit Platform engine, so
  Spring's JUnit 5 integration keeps working underneath.
- **Test data builders** — `AccountTestData` and `ActivityTestData` (currently
  Kotlin `object`s with `@JvmStatic`) stay as-is. Specs import their builders
  unchanged. The `@JvmStatic` annotations are harmless and out of scope.
- **No coroutine APIs** — none of the existing tests are suspend functions, so
  `coEvery` / `coVerify` are mentioned in the user intent only for
  completeness; do not introduce coroutines where none exist. If MockK rejects
  a suspend stub later, fall back to `coEvery`.
- **Property naming** — keep `val mockMvc: MockMvc` etc. as Spring-injected
  `lateinit var` properties where the existing code does so; only switch to
  Kotest's `bean` / `bind` patterns if the simpler `lateinit var` form breaks.

## Sprint plan

Each sprint below is independently green: after the sprint's commit,
`./gradlew test` exits 0 and the JUnit + Kotest engines coexist on the JUnit
Platform. Order is chosen so each sprint touches one logical file cluster,
matches the layered architecture, and never depends on a later sprint.

### Sprint 00 — build config: introduce Kotest + MockK alongside the existing stack

- **Files in scope**:
  - `build.gradle`
- **User-visible goal**: Add Kotest (JUnit5 runner + core assertions + Spring
  extension) and MockK (core + springmockk) as test dependencies, **without
  removing** any existing test dependency. The repository continues to compile
  every existing test class unchanged.
- **Hard exit criteria**:
  - `build.gradle` declares `io.kotest:kotest-runner-junit5`,
    `io.kotest:kotest-assertions-core`,
    `io.kotest:kotest-extensions-spring`,
    `io.mockk:mockk`, and `com.ninja-squad:springmockk` at versions
    compatible with Kotlin 1.6.21 / Spring Boot 2.4.3
    (Kotest 5.5.x line, MockK 1.13.x, springmockk 3.x).
  - `junit-jupiter-engine`, `mockito-junit-jupiter`, `kotlin-test`,
    `kotlin-test-junit5`, `archunit`, `junit-platform-launcher`, `h2`, and the
    `spring-boot-starter-test` declaration are **unchanged**.
  - `test { useJUnitPlatform() }` block is unchanged.
  - `./gradlew dependencies --configuration testRuntimeClasspath | grep -E "(kotest|mockk)"` lists the new artifacts.
  - `./gradlew test` exits 0; no test classes are edited in this sprint.
- **Out of scope**: any change to `src/test/**`; removing any existing test
  dependency; switching the build script to `.kts`; bumping Kotlin / Spring
  Boot versions; touching `compileKotlin` options.

### Sprint 01 — migrate pure-domain unit tests (`account/domain/*`)

- **Files in scope**:
  - `src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt`
  - `src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt`
- **User-visible goal**: Rewrite these two zero-dependency unit tests as
  `BehaviorSpec` classes using Kotest matchers. No mocks involved (these tests
  use only `AccountTestData` builders), so this is the simplest sprint and
  also the smoke test that Sprint 00 wired Kotest in correctly.
- **Hard exit criteria**:
  - Both files extend `io.kotest.core.spec.style.BehaviorSpec` (or `FunSpec`
    if the Generator prefers for a 3-test file — must be one consistent
    choice per file).
  - Zero references to `org.junit.jupiter.api.*` and zero references to
    `org.assertj.core.api.*` in these two files.
  - Each `@Test` becomes one `then(...)` (BehaviorSpec) or `test(...)` /
    `it(...)` block; behavior asserted is identical.
  - `./gradlew test --tests "io.reflectoring.buckpal.account.domain.*"` exits
    0 and reports the same number of leaf tests as before (`AccountTest`: 4;
    `ActivityWindowTest`: 3).
  - `./gradlew test` (full suite) exits 0.
- **Out of scope**: `account/application/**`, `account/adapter/**`, any test
  outside `account/domain/`, fixture files in `common/`, build script.

### Sprint 02 — migrate `SendMoneyServiceTest` (Mockito → MockK, BehaviorSpec)

- **Files in scope**:
  - `src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
- **User-visible goal**: Replace Mockito + BDDMockito + the hand-rolled
  `eq` / `capture` / `accountSentinel` null-safety wrappers with MockK
  primitives (`mockk<T>()`, `every { } returns`, `verify { }`, `slot<T>()`).
  Express the two existing scenarios as a `BehaviorSpec`
  (`given` / `when` / `then`).
- **Hard exit criteria**:
  - Class extends `BehaviorSpec`. No `@Test` annotation remains in the file.
  - All Mockito-related imports
    (`org.mockito.*`, `org.mockito.BDDMockito.*`) are gone.
  - The `accountSentinel`, `eq(...)`, and `capture(...)` helpers are deleted —
    MockK has no need for them.
  - `ArgumentCaptor.forClass(Account::class.java)` is replaced by
    `slot<Account>()` (or `mutableListOf<Account>()` with `capture(list)` if
    multiple captures are needed — both are acceptable).
  - The two original scenarios
    (`givenWithdrawalFails_thenOnlySourceAccountIsLockedAndReleased`,
    `transactionSucceeds`) are preserved as leaf tests with equivalent
    assertions about `lockAccount`, `releaseAccount`, `withdraw`, `deposit`,
    and `updateActivities` call counts.
  - `./gradlew test --tests "io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest"` exits 0.
  - `./gradlew test` (full suite) exits 0.
- **Out of scope**: production code under
  `src/main/kotlin/io/reflectoring/buckpal/account/application/service/**`;
  any other test file; fixtures in `common/`.

### Sprint 03 — migrate `SendMoneyControllerTest` (`@WebMvcTest` + `@MockkBean`)

- **Files in scope**:
  - `src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`
- **User-visible goal**: Migrate the web-slice test to a Kotest spec that uses
  the Spring extension. Replace `@MockBean` with `@MockkBean` from springmockk
  and drop the `eq` wrapper.
- **Hard exit criteria**:
  - Class extends a Kotest spec (`DescribeSpec` or `FunSpec`) and registers
    Kotest's `SpringExtension` via `override fun extensions()` (or the
    `listener`/`extension()` block, whichever the chosen Kotest version
    documents).
  - `@WebMvcTest(controllers = [SendMoneyController::class])` annotation
    remains on the class.
  - `@MockBean` is replaced by `@MockkBean` from `com.ninja-squad.springmockk`;
    `import org.springframework.boot.test.mock.mockito.MockBean` is removed.
  - The `eq(value)` helper is removed; the MockMvc assertion uses
    `verify { sendMoneyUseCase.sendMoney(SendMoneyCommand(...)) }` directly.
  - The HTTP assertion (`POST /accounts/send/41/42/500` returns 200) is
    preserved.
  - `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.in.web.SendMoneyControllerTest"` exits 0.
  - `./gradlew test` (full suite) exits 0.
- **Out of scope**: controller production code; ArchUnit; system test;
  persistence test.

### Sprint 04 — migrate `AccountPersistenceAdapterTest` (`@DataJpaTest`)

- **Files in scope**:
  - `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt`
- **User-visible goal**: Convert the JPA-slice test to a Kotest spec hosting
  the existing `@DataJpaTest` + `@Import` + `@Sql` annotations through the
  Spring extension. No mocks involved; this validates that Kotest +
  `@DataJpaTest` + H2 + `@Sql` interact cleanly.
- **Hard exit criteria**:
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
  - `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest"` exits 0; both
    `loadsAccount` and `updatesActivities` pass and the H2 row count remains
    `1` for the update path.
  - `./gradlew test` (full suite) exits 0.
- **Out of scope**: `AccountPersistenceAdapter` / `AccountMapper` /
  `ActivityRepository` production code;
  `src/test/resources/.../AccountPersistenceAdapterTest.sql` (do not edit).

### Sprint 05 — migrate ArchUnit and Spring smoke tests

- **Files in scope**:
  - `src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt`
  - `src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`
- **User-visible goal**: Rehost two thin tests under Kotest. `DependencyRuleTests`
  becomes a `FunSpec` whose blocks invoke the existing `HexagonalArchitecture`
  builder unchanged. `BuckPalApplicationTests` becomes a `FunSpec` /
  `DescribeSpec` with one `test("context loads") {}` block, still annotated
  `@SpringBootTest` and using the Kotest Spring extension.
- **Hard exit criteria**:
  - `DependencyRuleTests` extends a Kotest spec, has zero `@Test` / JUnit
    imports, and runs both `validateRegistrationContextArchitecture` and
    `testPackageDependencies` as separate leaf tests. ArchUnit imports
    (`com.tngtech.archunit.*`) are **unchanged**.
  - `BuckPalApplicationTests` extends a Kotest spec, retains `@SpringBootTest`,
    drops `@ExtendWith(SpringExtension::class)` (Kotest's Spring extension
    takes over), and exercises one empty context-load block.
  - `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"`
    and `./gradlew test --tests "io.reflectoring.buckpal.BuckPalApplicationTests"`
    both exit 0.
  - `./gradlew test` (full suite) exits 0.
- **Out of scope**: any file under `src/test/kotlin/io/reflectoring/buckpal/archunit/**`
  (those are infrastructure classes, not tests). The system test
  (`SendMoneySystemTest`) is migrated in the next sprint.

### Sprint 06 — migrate `SendMoneySystemTest` (full Spring Boot system test)

- **Files in scope**:
  - `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
- **User-visible goal**: Convert the end-to-end Spring Boot system test to a
  Kotest spec (`DescribeSpec` or `FunSpec`) while keeping
  `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` and the
  `@Sql("SendMoneySystemTest.sql")` data load. Replace
  `org.assertj.core.api.BDDAssertions.then` with Kotest's `shouldBe`.
- **Hard exit criteria**:
  - Class extends a Kotest spec and registers Kotest's `SpringExtension`.
  - The `@SpringBootTest(... RANDOM_PORT)` annotation is preserved.
  - `@Sql("SendMoneySystemTest.sql")` is preserved on the leaf test (same
    `@Sql` fallback strategy as Sprint 04 if needed).
  - The HTTP exchange via `TestRestTemplate` produces a 200 and balance deltas
    of `-500` (source) / `+500` (target), asserted via Kotest matchers.
  - No imports from `org.assertj.core.*` remain in this file.
  - `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"`
    exits 0; the test launches a random port and round-trips through the
    controller.
  - `./gradlew test` (full suite) exits 0.
- **Out of scope**: `src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql`;
  any production code; any other test file.

### Sprint 07 — strip obsolete test dependencies and final verification

- **Files in scope**:
  - `build.gradle`
- **User-visible goal**: Now that every test class is on Kotest + MockK,
  remove the now-unused legacy test dependencies and confirm the suite is
  still green. Leaves the build script representing the final desired state.
- **Hard exit criteria**:
  - Remove `org.junit.jupiter:junit-jupiter-engine:5.0.1` and
    `org.mockito:mockito-junit-jupiter:2.23.0` from `dependencies`.
    (`junit-platform-launcher` stays because some tooling expects it.)
  - Remove `org.jetbrains.kotlin:kotlin-test` and
    `org.jetbrains.kotlin:kotlin-test-junit5`.
  - `spring-boot-starter-test`, `archunit:0.16.0`, `h2`, and
    `junit-platform-launcher:1.4.2` remain unchanged.
  - `test { useJUnitPlatform() }` remains.
  - Grep confirms no remaining imports of `org.junit.jupiter.api.*`,
    `org.mockito.*`, `kotlin.test.*`, `org.junit.jupiter.api.extension.ExtendWith`,
    or `org.springframework.test.context.junit.jupiter.SpringExtension`
    inside `src/test/kotlin/**` (`ArchUnit` helper imports of
    `com.tngtech.archunit.*` are unaffected; that package is fine).
  - `./gradlew clean test` exits 0.
  - `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"`
    still boots Spring and serves
    `POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}`.
- **Out of scope**: Kotlin / Spring Boot version bumps; conversion to
  `build.gradle.kts`; any change to `src/test/**`.

## Risk register

1. **Spring + Kotest extension wiring** — `kotest-extensions-spring` activates
   Spring's `TestContextManager` for Kotest specs; misconfiguring it (e.g.,
   forgetting `override fun extensions()` / wrong artifact coordinates) silently
   skips `@Autowired` injection. Mitigation: Sprint 03 is the first Spring
   sprint precisely so the wiring is validated on the smallest Spring slice
   (`@WebMvcTest`) before applying it to `@DataJpaTest` and `@SpringBootTest`.
2. **`@Sql` resolution inside Kotest test lambdas** — JUnit's `@Sql` is
   discovered on `Method` objects; Kotest leaf tests are lambdas, not
   reflective methods. If `kotest-extensions-spring` does not honor
   class- + method-level `@Sql` on lambda leaves, Sprints 04 and 06 must
   fall back to executing the same SQL in a `beforeTest` block.
3. **`@MockkBean` Spring Boot 2.4.x compatibility** — `springmockk` versions
   are paired to Spring Boot lines. Sprint 00 must pick the springmockk
   release that supports Spring Boot 2.4.3 (the 3.x line). If injection fails,
   fall back to declaring the bean in a `@TestConfiguration` and using
   `mockk<SendMoneyUseCase>()`.
4. **MockK + Kotlin final classes** — Production classes like `Account` are
   not `open`; MockK can mock final classes only with
   `mockkClass(...)` or with the `mockk-agent` `MockK { ... }` global, but
   `mockk<T>()` on a final class is supported out of the box from MockK 1.13
   onward (uses `mockk-agent-jvm`). If a `MissingMockitoExtension` or
   "cannot mock final" failure surfaces, Sprint 02 must add
   `io.mockk:mockk-agent-jvm` (already a transitive of `mockk`) and / or
   the `MockKAnnotations.init(this, relaxUnitFun = true)` pattern.
5. **JUnit Platform engine collisions** — `kotest-runner-junit5` registers as
   a JUnit Platform engine. Until Sprint 07 strips `junit-jupiter-engine`,
   both engines run concurrently. This is intentional (allows partial
   migration), but is the reason `./gradlew test` is the green-bar gate at
   every sprint, not just the final one.
6. **ArchUnit scans compiled classes, not files** — the existing
   `DependencyRuleTests` uses `ClassFileImporter().importPackages(...)`,
   which is source-language-agnostic. Hosting it in a Kotest spec does not
   change package scanning, but Sprint 05 must keep the `archunit` artifact
   on `testImplementation` to avoid an `import` failure.
7. **`AccountId(...)!!` / nullable boundaries** — `SendMoneyServiceTest`
   accesses `sourceAccount.id!!`. After moving to MockK, `every { account.id }
   returns id` returns the non-null `AccountId` directly, so the `!!` is no
   longer needed. Be careful not to over-relax the type of `Account.id` in
   production code while migrating tests — production code stays untouched.

## Sprint Index

- sprint-00: build config — add Kotest + MockK + springmockk alongside existing test stack
- sprint-01: account/domain — migrate AccountTest and ActivityWindowTest to Kotest
- sprint-02: account/application/service — migrate SendMoneyServiceTest to Kotest + MockK
- sprint-03: account/adapter/in/web — migrate SendMoneyControllerTest to Kotest + MockkBean
- sprint-04: account/adapter/out/persistence — migrate AccountPersistenceAdapterTest to Kotest + DataJpaTest
- sprint-05: archunit + smoke — migrate DependencyRuleTests and BuckPalApplicationTests to Kotest
- sprint-06: system test — migrate SendMoneySystemTest to Kotest + SpringBootTest
- sprint-07: cleanup — remove junit-jupiter-engine / mockito-junit-jupiter / kotlin-test from build.gradle
