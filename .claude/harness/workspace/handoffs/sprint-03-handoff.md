# Sprint 03 Handoff

## What changed

- Edited: `src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`
  — migrated from JUnit 5 + Spring `@MockBean` + Mockito to Kotest
  `DescribeSpec` + Kotest `SpringExtension` + springmockk `@MockkBean` + MockK
  `every` / `verify`. One leaf test preserved.

No other source file was edited.

## Contract checklist

### Behavioral correctness

- [x] `./gradlew test --tests "*SendMoneyControllerTest"` → `BUILD SUCCESSFUL`
  (single-use daemon, 7s).
- [x] `./gradlew test` (full suite) → `BUILD SUCCESSFUL`; aggregate leaf-test
  count is **16**, identical to the Sprint 02 baseline. Verified by
  `find build/test-results/test -name "TEST-*.xml"` + summing the `tests="…"`
  attribute on each `<testsuite>`.
- [x]
  `build/test-results/test/TEST-io.reflectoring.buckpal.account.adapter.in.web.SendMoneyControllerTest.xml`
  reports `tests="1" failures="0" errors="0" skipped="0"` (verified by reading
  the XML header line).

### Architectural integrity

- [x] `./gradlew check` → `BUILD SUCCESSFUL` (UP-TO-DATE after `test` ran).
- [x] `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` —
  covered by the full-suite run; `DependencyRuleTests` still appears as
  `tests="2"` in its TEST-*.xml.

### Code quality — Mockito / Spring-Mockito residue is gone

All negative greps exit non-zero (no matches), verified one by one:

- [x] `^import org\.mockito` — no matches.
- [x] `Mockito\.` (literal) — no matches.
- [x] `^import org\.springframework\.boot\.test\.mock\.mockito` — no matches.
- [x] `BDDMockito` — no matches.
- [x] `@MockBean` — no matches.
- [x] `^import org\.junit\.jupiter` — no matches.
- [x] `@Test\b` — no matches.
- [x] `^import org\.assertj\.core` — no matches.

### Code quality — Kotest, MockK, MockkBean are present

- [x] `^class SendMoneyControllerTest\s*:\s*DescribeSpec` → matches line 19.
- [x] `^import io\.kotest\.core\.spec\.style\.DescribeSpec` → matches line 4.
- [x] `^import io\.kotest\.extensions\.spring\.SpringExtension` → matches line 5.
- [x] `override fun extensions\(\)` → matches line 21.
- [x] **`^import com\.ninjasquad\.springmockk\.MockkBean`** → matches line 3.
  **Deviation from the contract's `com\.ninja_squad\.springmockk` regex.**
  See "Anything the Evaluator should pay extra attention to" below — the
  actual JAR ships `com.ninjasquad.springmockk` (no underscore) and the
  contract's `ninja_squad` form **does not resolve at the Kotlin compiler**.
- [x] `@MockkBean` → matches line 26.
- [x] `^import io\.mockk\.(every|verify)` → matches two lines (6 and 7).
- [x] `@WebMvcTest` → matches line 18.

### Idiomatic Kotlin — no banned patterns

- [x] `!!` → no matches.
- [x] `grep -n "lateinit var" … | wc -l` → exactly **2**.
- [x] Both `lateinit var` lines are annotated: line 23–24 is `@Autowired …
  lateinit var mockMvc` (annotation on line 23, declaration on line 24);
  line 26–27 is `@MockkBean … lateinit var sendMoneyUseCase` (annotation on
  line 26, declaration on line 27).
- [x] `\.shouldBe\(` — no matches (the test uses no `shouldBe` at all; the
  contract permits this).
- [x] `\.verify\(` — no matches (MockK `verify { … }` block form is used).

### Scope — only one file changed

- [x] `git diff --name-only HEAD -- src/` → exactly the one expected path.
- [x] `git diff --name-only HEAD -- src/main/` → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/common/`
  → empty.
- [x] `git diff --name-only HEAD -- build.gradle` → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/domain/`
  → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/application/service/`
  → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/`
  → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/archunit/`
  → empty.

## Idiomatic Kotlin choices worth flagging

1. **`DescribeSpec()` class-body form** — `class SendMoneyControllerTest :
   DescribeSpec()` with `init { describe { it { … } } }`. Constructor-arg
   lambda form (`: DescribeSpec({ … })`) was rejected because the class
   needs `@Autowired lateinit var` and `@MockkBean lateinit var` property
   declarations, which a constructor-arg lambda cannot host.
2. **`override fun extensions() = listOf(SpringExtension)`** registers
   Kotest's Spring extension. `SpringExtension` is the singleton object from
   `io.kotest.extensions.spring` (Kotest 5.5.5 + extensions 1.1.3). The
   integration was validated end-to-end by this run: `@Autowired
   lateinit var mockMvc` and `@MockkBean lateinit var sendMoneyUseCase` both
   resolved, springmockk's `MockkTestExecutionListener` and
   `ClearMocksTestExecutionListener` appear in the Spring test execution
   listener list (visible in the TEST-*.xml `system-out` log).
3. **`every { sendMoneyUseCase.sendMoney(any()) } returns true`** added
   before the MockMvc call — required because `@MockkBean(relaxed = false)`
   (the default) makes MockK strict, and the use case returns `Boolean`.
   The controller ignores the return value, so the stubbed value is
   irrelevant; the stub merely satisfies MockK's strict resolver. This is
   not a behavioral change versus the Mockito version, which silently
   returned `false` from un-stubbed `Boolean` calls.
4. **Property-form `status().isOk`** instead of method-form `status().isOk()`.
   Kotlin exposes the no-arg getter as a property; idiomatic Kotlin prefers
   the property form. Either form compiles.
5. **`verify { sendMoneyUseCase.sendMoney(SendMoneyCommand(...)) }`** uses
   MockK's block form. The `SendMoneyCommand` literal works directly because
   `SendMoneyCommand` is a Kotlin `data class` whose `equals` is value-based —
   no MockK matcher / no `eq` wrapper needed. The Mockito-era hand-rolled
   `private fun <T> eq(value: T): T = Mockito.eq(value) ?: value` helper is
   deleted outright (the contract called for this).
6. **No `kotlin.test.*` imports.** None were present before, none added.

## Anything the Evaluator should pay extra attention to

1. **springmockk package name correction.** The contract's `Imports to add`
   table named `com.ninja_squad.springmockk.MockkBean` (with underscore) and
   the Code-quality grep was `^import com\.ninja_squad\.springmockk\.MockkBean`.
   The **actual** JAR (`com.ninja-squad:springmockk:3.1.2`) ships the class
   at `com.ninjasquad.springmockk.MockkBean` — i.e., **no underscore between
   "ninja" and "squad"**. Verified by `unzip -l springmockk-3.1.2.jar | grep
   MockkBean` → `com/ninjasquad/springmockk/MockkBean.class`. The contract's
   underscore form does NOT resolve and the build fails with
   `Unresolved reference: ninja_squad`. The migrated file therefore imports
   `com.ninjasquad.springmockk.MockkBean`. Please update the contract's
   regex from `ninja_squad` to `ninjasquad` (or use a broader pattern such
   as `com\.ninjasquad?\.springmockk` or `com\.ninja[_]?squad\.springmockk`)
   if you want Sprint 04 / 06 to grep for the import the same way. This is
   a **contract bug**, not a Generator defect — the file as written matches
   the *intent* of the contract (springmockk `MockkBean`) and compiles.
2. **`@WebMvcTest` is honored by Kotest's Spring extension.** Verified by
   the run: `WebMvcTestContextBootstrapper` appears in the
   `system-out` log (`Loaded default TestExecutionListener class names …`
   block), the controller bean is constructed, MockMvc injects, and the
   `MockkContextCustomizer` registers `SendMoneyUseCase` as a `MockkDefinition`.
   The risk register #1 entry ("the first Spring sprint validates the
   wiring") is resolved positively.
3. **Single leaf test count.** The contract's Behavioral check requires
   `tests="1"` in the TEST-*.xml; the run reports exactly `tests="1"`. No
   spurious container-counts.
4. **Aggregate leaf count stays at 16.** Identical to Sprint 02 baseline.
   `AccountTest=4 + ActivityWindowTest=3 + SendMoneyServiceTest=2 +
   SendMoneyControllerTest=1 + BuckPalApplicationTests=1 +
   DependencyRuleTests=2 + AccountPersistenceAdapterTest=2 +
   SendMoneySystemTest=1 = 16` — matches.
5. **No production-code edits.** `git diff --name-only HEAD -- src/main/` is
   empty. The "non-negotiable invariant" of the migration is preserved.

## TODOs deferred to later sprints

- **Sprint 04 — `AccountPersistenceAdapterTest`** will exercise the
  `@DataJpaTest` + `@Sql` slice; this sprint's `@WebMvcTest` success
  confirms the springmockk + Kotest Spring extension wiring, but does not
  yet validate `@Sql` resolution inside a Kotest leaf lambda (Risk
  register #2). Sprint 04 will be the canary for that.
- **Sprint 06 — `SendMoneySystemTest`** will validate
  `@SpringBootTest(RANDOM_PORT)` on top of Kotest.
- **Sprint 07 — build script cleanup** will remove
  `junit-jupiter-engine`, `mockito-junit-jupiter`, and `kotlin-test` /
  `kotlin-test-junit5` from `build.gradle`.

## Commit

Proposed one-line summary for the orchestrator:

```
feat(kotlin): sprint 3 — migrate SendMoneyControllerTest to Kotest + MockkBean
```

No commit performed by Generator. Working tree contains exactly one modified
file:

```
src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt
```
