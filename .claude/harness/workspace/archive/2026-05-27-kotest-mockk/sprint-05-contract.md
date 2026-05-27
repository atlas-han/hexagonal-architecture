STATUS: AGREED

# Sprint 05 Contract — Migrate ArchUnit and Spring smoke tests to Kotest

## Sprint goal (verbatim from spec)

> Rehost two thin tests under Kotest. `DependencyRuleTests` becomes a
> `FunSpec` whose blocks invoke the existing `HexagonalArchitecture` builder
> unchanged. `BuckPalApplicationTests` becomes a `FunSpec` / `DescribeSpec`
> with one `test("context loads") {}` block, still annotated `@SpringBootTest`
> and using the Kotest Spring extension.

## Files in scope

Only these **two** test files may be edited in Sprint 05. Anything outside
this list — including production code, ArchUnit infrastructure, the build
script, or any other test class — is off-limits.

- `src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt`
- `src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`

Files explicitly **not** in scope (read-only, Generator must not touch them):

- Every file under `src/test/kotlin/io/reflectoring/buckpal/archunit/**`
  (`HexagonalArchitecture`, `Adapters`, `ApplicationLayer`,
  `ArchitectureElement`) — spec invariant: these are support types, not
  tests, and must remain unchanged.
- All production code under `src/main/kotlin/**` — non-negotiable migration
  invariant.
- `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` — Sprint 06
  territory.
- `src/test/kotlin/io/reflectoring/buckpal/common/AccountTestData.kt` and
  `ActivityTestData.kt` — fixtures, untouched per spec.
- Every other test class already migrated in Sprints 01–04
  (`AccountTest`, `ActivityWindowTest`, `SendMoneyServiceTest`,
  `SendMoneyControllerTest`, `AccountPersistenceAdapterTest`).
- `build.gradle` — Sprint 00 wired Kotest + MockK + springmockk; Sprint 07
  removes the legacy stack. This sprint is test-source-only.

## Hard exit criteria (verbatim from spec)

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

## Out of scope (verbatim from spec, plus additions)

From spec:

- Any file under `src/test/kotlin/io/reflectoring/buckpal/archunit/**`
  (those are infrastructure classes, not tests).
- The system test (`SendMoneySystemTest`) — migrated in the next sprint.

Generator-added:

- All production code (any file under `src/main/kotlin/**`). Production code
  stays untouched — non-negotiable invariant of the whole migration.
- `build.gradle` — Sprint 00 wired Kotest + MockK + springmockk; Sprint 07
  removes the legacy stack. This sprint is test-source-only.
- Sprint 01 files (`AccountTest`, `ActivityWindowTest`), Sprint 02
  (`SendMoneyServiceTest`), Sprint 03 (`SendMoneyControllerTest`),
  Sprint 04 (`AccountPersistenceAdapterTest`) — already migrated, untouched.
- The ArchUnit rules themselves: `HexagonalArchitecture.boundedContext(...)`
  fluent chain and the `noClasses().that()....check(...)` rule body are
  **transferred verbatim** into the new Kotest leaves. **No semantic
  weakening of the ArchUnit checks is permitted.** Removing, narrowing,
  loosening, or skipping any `.withDomainLayer`, `.withAdaptersLayer`,
  `.incoming`, `.outgoing`, `.withApplicationLayer`, `.services`,
  `.incomingPorts`, `.outgoingPorts`, `.withConfiguration`, or the
  `.check(ClassFileImporter().importPackages(...))` call is a defect.
- Coroutine APIs (`coEvery`, `coVerify`, `runBlocking`) — neither file under
  test involves suspend functions. No coroutines.
- Reintroducing any Mockito artifact — neither file uses mocks.

---

## Spec-style decision per file

### `DependencyRuleTests` — `FunSpec`

The spec is explicit: *"ArchUnit hosting (`DependencyRuleTests`) → `FunSpec`
(each `@Test` becomes one `test("...") { ... }` block); body logic
unchanged."* The Generator adopts that literally.

Rationale (also covered in the spec):

- The two existing `@Test` methods are flat, leaf-level checks: one calls
  `HexagonalArchitecture.boundedContext(...)....check(...)`; the other calls
  `noClasses().that()....check(...)`. There is no nesting, no shared given /
  when scaffolding, no behavior-driven narrative — exactly the shape
  `FunSpec` is built for.
- `BehaviorSpec` (`given` / `when` / `then`) would force two layers of
  artificial wrapping for what are really self-contained one-shot rule
  invocations.
- `DescribeSpec` would add one layer of `describe(...)` nesting with no
  semantic gain over flat `FunSpec` `test(...)` blocks.

**Final decision: `class DependencyRuleTests : FunSpec({ ... })`**
(constructor-arg lambda form — no class-level properties needed; no
`@Autowired`, no `@MockkBean`, no `lateinit var`). The two `test(...)`
blocks live inside the constructor lambda directly.

### `BuckPalApplicationTests` — `DescribeSpec` (class-body form)

The spec offers both `FunSpec` and `DescribeSpec`. The Generator picks
`DescribeSpec` for **consistency with Sprints 03 and 04**, which both used
`DescribeSpec` for Spring-slice tests, and for **future-readability** — if
this smoke test ever grows (e.g., a second `it("...")` block verifying a
specific bean is present), it slots in without restructuring.

The class-body form (`: DescribeSpec()` + `init { ... }`) is used, not
the constructor-arg lambda form, because Kotest's Spring extension
registration in 5.5.x is performed via an `override fun extensions()`
class-body declaration — which is impossible inside a constructor-arg
lambda. (Sprint 03 / Sprint 04 used the same class-body pattern for the
identical reason.)

**Final decision:**
```kotlin
@SpringBootTest
class BuckPalApplicationTests : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        describe("Spring application context") {
            it("loads") {
                // intentionally empty: success is the absence of
                // BeanCreationException from the @SpringBootTest container
            }
        }
    }
}
```

This matches the spec's *"one `test("context loads") {}` block"* requirement
(here phrased as one `it("loads")` block inside one `describe("Spring
application context")` container — semantically identical: a single
context-load leaf with an empty body, success = no startup exception).

`FunSpec` with one flat `test("context loads") {}` is **explicitly allowed
by the spec** and would also pass review; the Generator's choice of
`DescribeSpec` is documented here to make the Evaluator's diff expectations
explicit.

---

## Conversion targets

| `.kt` file in scope                                            | Class type                                                                                                       |
|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` | `class DependencyRuleTests : FunSpec({ ... })` with two `test("...") { ... }` blocks inside the constructor lambda |
| `src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` | `@SpringBootTest class BuckPalApplicationTests : DescribeSpec()` with class-body `override fun extensions()` + `init { describe(...) { it(...) { } } }` |

### Class shape facts — `DependencyRuleTests`

- Stays in package `io.reflectoring.buckpal` (root test package).
- Class name stays `DependencyRuleTests` so test filtering
  (`--tests "io.reflectoring.buckpal.DependencyRuleTests"` and
  `--tests "*DependencyRuleTests"`) continues to work unchanged.
- No class-level annotations (none today; none added).
- Two `test("...") { ... }` blocks. Test names are at the Generator's
  discretion but **must clearly correspond to the two original methods**.
  The Generator picks:
  - `test("validateRegistrationContextArchitecture") { ... }` — invokes the
    `HexagonalArchitecture.boundedContext(...)....check(...)` chain
    **verbatim** (body lines 12–32 of today's file).
  - `test("testPackageDependencies") { ... }` — invokes the
    `noClasses().that()....check(...)` chain **verbatim** (body lines 35–46).
- ArchUnit imports
  (`com.tngtech.archunit.core.importer.ClassFileImporter`,
  `com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses`) and the
  internal `io.reflectoring.buckpal.archunit.HexagonalArchitecture` import
  are **unchanged**.
- No assertions are added — the ArchUnit `.check(...)` call throws
  `AssertionError` on rule violation; Kotest reports the throw as the
  leaf failure. This is the same JVM behavior as today under JUnit.
- No `lateinit var`, no `!!`, no `@Test`, no JUnit / AssertJ / Mockito.

### Class shape facts — `BuckPalApplicationTests`

- Stays in package `io.reflectoring.buckpal` (root test package).
- Class name stays `BuckPalApplicationTests` so test filtering
  (`--tests "io.reflectoring.buckpal.BuckPalApplicationTests"` and
  `--tests "*BuckPalApplicationTests"`) continues to work unchanged.
- `@SpringBootTest` annotation is **preserved**.
- `@ExtendWith(SpringExtension::class)` (JUnit's Spring extension) is
  **removed** — Kotest's `io.kotest.extensions.spring.SpringExtension`
  replaces it, registered via `override fun extensions()`. Per spec:
  *"drops `@ExtendWith(SpringExtension::class)` (Kotest's Spring
  extension takes over)."*
- The two JUnit imports for `org.junit.jupiter.api.Test` and
  `org.junit.jupiter.api.extension.ExtendWith` are deleted.
- The JUnit-flavor `org.springframework.test.context.junit.jupiter.SpringExtension`
  import is **deleted** (it was only needed for `@ExtendWith`); the Kotest
  Spring-extension import `io.kotest.extensions.spring.SpringExtension`
  replaces it.
- One `describe("Spring application context")` container wrapping one
  `it("loads") { /* empty */ }` leaf — empty body matches today's empty
  `contextLoads()` method.
- No `lateinit var` is added: this file does not `@Autowired` anything and
  does not use `@MockkBean`. Zero `lateinit var` declarations is correct.
- No `!!`, no `@Test`, no `org.junit.*`, no AssertJ.

---

## Idiomatic Kotlin commitments

1. **`FunSpec` constructor-arg lambda form for `DependencyRuleTests`.**
   `class DependencyRuleTests : FunSpec({ test("...") { ... }; test("...") { ... } })`.
   No `init { }`, no class-level properties, no constructor parameters.
   The two leaves are flat siblings inside the lambda.
2. **`DescribeSpec()` class-body form for `BuckPalApplicationTests`.**
   `class BuckPalApplicationTests : DescribeSpec() { override fun
   extensions() = listOf(SpringExtension); init { describe(...) { it(...)
   { } } } }`. The class-body form is required because
   `override fun extensions()` cannot live inside a constructor-arg lambda.
3. **No `lateinit var` in either file.** `DependencyRuleTests` has no
   Spring wiring; `BuckPalApplicationTests` has no `@Autowired` /
   `@MockkBean` properties (the spec only requires an empty context-load
   leaf). Total `lateinit var` count across both files: **0**.
4. **No `!!` non-null assertions in either file.** Today's files already
   have zero `!!`; the migration preserves that.
5. **No `Mockito.` prefix, no `org.mockito` / `BDDMockito` imports, no
   `@MockBean` / `@MockkBean`.** Neither file uses mocks. The negative
   greps guard against accidental drift.
6. **No `@Test` annotation, no `org.junit.jupiter.api.*` imports, no
   `org.assertj.core.api.*` imports in either file.**
7. **ArchUnit rule bodies copied verbatim, not paraphrased.** The
   fluent-builder chain on `HexagonalArchitecture.boundedContext("io.reflectoring.buckpal.account")....check(...)`
   keeps every layer call (`withDomainLayer("domain")`,
   `withAdaptersLayer("adapter")`, `.incoming("in.web")`,
   `.outgoing("out.persistence")`, `.and()`, `withApplicationLayer("application")`,
   `.services("service")`, `.incomingPorts("port.in")`,
   `.outgoingPorts("port.out")`, `.and()`, `withConfiguration("configuration")`,
   `.check(ClassFileImporter().importPackages("io.reflectoring.buckpal.."))`).
   The `noClasses()` rule keeps the same package strings
   (`io.reflectoring.reviewapp.domain..` / `io.reflectoring.reviewapp.application..` —
   identical to today's typo; the Generator must **not** "fix" it because
   the spec forbids behavior changes; if the typo is intentional [it's
   today's behavior], leaving it is correct).
8. **Kotest 5.5.x `SpringExtension` is the singleton object
   `io.kotest.extensions.spring.SpringExtension`**, not
   `org.springframework.test.context.junit.jupiter.SpringExtension`.
   Registered as `override fun extensions() = listOf(SpringExtension)`.
9. **Empty `it("loads") { }` body matches today's empty
   `fun contextLoads() {}`** — success means the Spring application context
   started without throwing. No assertion is added; none was present
   before.

---

## Risk handling specific to this sprint

### Risk: ArchUnit rule weakening (spec Risk register #6)

The spec is explicit: *"ArchUnit scans **compiled classes**, not files —
the existing `DependencyRuleTests` uses `ClassFileImporter().importPackages(...)`,
which is source-language-agnostic. Hosting it in a Kotest spec does not
change package scanning, but Sprint 05 must keep the `archunit` artifact
on `testImplementation` to avoid an `import` failure."*

Mitigation:

- The two ArchUnit `.check(...)` bodies are copied **verbatim** from
  today's file lines 12–32 and 35–46. No expression is paraphrased,
  reorganised, or shortened.
- The `com.tngtech.archunit.*` imports remain unchanged.
- `build.gradle` is not edited; `archunit:0.16.0` stays on
  `testImplementation` (Sprint 00 invariant, untouched here).
- The Evaluator's acceptance gates include a positive grep ensuring the
  ArchUnit import lines and the literal package string
  `io.reflectoring.buckpal.account` (the bounded context) survive, and a
  green `./gradlew test --tests "*DependencyRuleTests"` confirms the
  scanner finds the rules.

### Risk: Spring context fails to load under Kotest (spec Risk register #1)

`BuckPalApplicationTests` is a full-context smoke test — `@SpringBootTest`
boots the entire `BuckPalApplication`. If Kotest's Spring extension is
mis-registered, the context never starts and the leaf throws an
`UninitializedPropertyAccessException` (no `lateinit var`s here, so this is
ruled out) or a `BeanCreationException`. Mitigation:

- Sprints 03 and 04 already validated `kotest-extensions-spring:1.1.3` on
  `@WebMvcTest` and `@DataJpaTest` slices. `@SpringBootTest` uses the same
  `TestContextManager` lifecycle, so the wiring pattern carries over.
- The `extensions()` override returns the singleton `SpringExtension`
  (object), not `SpringExtension::class`. Same convention as Sprints 03
  and 04.
- The fallback `override fun listeners() = listOf(SpringExtension)` is
  documented in Sprint 03's contract; the Generator will only swap to it
  if `extensions()` self-check fails.

### Risk: leaf-count mismatch on `DependencyRuleTests` XML

Today the JUnit engine emits
`TEST-io.reflectoring.buckpal.DependencyRuleTests.xml` with `tests="2"`
(two `@Test` methods). Under Kotest's JUnit Platform engine, the same XML
path is used (one XML per FQCN). Two `test(...)` blocks become two
leaves, so the file must continue to report `tests="2"`. Acceptance check
below formalizes this.

### Risk: leaf-count mismatch on `BuckPalApplicationTests` XML

Today `tests="1"` (one `@Test` method `contextLoads`). After migration to
`DescribeSpec` with one `describe { it { } }`, Kotest emits a single
leaf — `tests="1"` is preserved. Acceptance check below formalizes this.

### Risk: aggregate leaf count

The Sprint 04 baseline (from the Sprint 04 PASS review's TEST-*.xml
table) is **16 leaves total**:

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

Sprint 05 touches only the last two rows and keeps their counts (2 + 1).
Aggregate must remain **16 leaves**.

### Risk: ArchUnit typo `io.reflectoring.reviewapp.domain..`

Lines 38 and 41 of today's `DependencyRuleTests` reference packages
`io.reflectoring.reviewapp.domain..` and
`io.reflectoring.reviewapp.application..` — note `reviewapp`, not
`buckpal`. This is a no-op rule: no class in this project resides in
`io.reflectoring.reviewapp.**`, so `noClasses().that().resideInAPackage(...)`
selects an empty set and the rule passes vacuously. **This is today's
behavior.** The Generator must preserve it exactly — paraphrasing the
package strings to `io.reflectoring.buckpal.**` would change behavior
(the rule would suddenly start checking real classes). Per Generator
hard-rule *"Don't change behavior. If you see a bug, leave a
`// TODO(kotlin-migration):` comment and flag it in the handoff; do not
silently fix it."* The Generator will copy the strings verbatim and flag
the typo in the handoff as a deferred TODO. No fix this sprint.

### Risk: Kotest spec class import path

Kotest 5.5.x exposes:

- `io.kotest.core.spec.style.FunSpec`
- `io.kotest.core.spec.style.DescribeSpec`
- `io.kotest.extensions.spring.SpringExtension`

These are the imports added to the two files. The artifacts are already
on `testRuntimeClasspath` per Sprint 00 / Sprints 03 + 04, so resolution
will succeed.

### Risk: full-suite cross-talk

ArchUnit's `ClassFileImporter` scans compiled production classes, not
test classes. Reshaping the two tests under `src/test/kotlin/io/reflectoring/buckpal/`
cannot affect ArchUnit scanning of `src/main/kotlin/**`. The other Spring
slices (controller, persistence, system) live in separate classes and
do not share state with `BuckPalApplicationTests`. Verified by running
the full suite in self-check.

---

## Inventory of JUnit / Spring-Mockito / AssertJ API used today

Grepped from the live files (Read above):

### `DependencyRuleTests.kt` (47 lines)

| Today's API                                         | Where it appears                       |
|------------------------------------------------------|----------------------------------------|
| `org.junit.jupiter.api.Test` (import + annotation)   | line 6 (import), lines 10 & 34 (annotations) |
| `com.tngtech.archunit.core.importer.ClassFileImporter` | line 3 (import) — **retained**          |
| `com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses` | line 4 (import) — **retained**          |
| `io.reflectoring.buckpal.archunit.HexagonalArchitecture` | line 5 (import) — **retained**          |

### `BuckPalApplicationTests.kt` (15 lines)

| Today's API                                                              | Where it appears                  |
|---------------------------------------------------------------------------|-----------------------------------|
| `org.junit.jupiter.api.Test` (import + annotation)                        | line 3 (import), line 12 (annotation) |
| `org.junit.jupiter.api.extension.ExtendWith` (import + annotation)        | line 4 (import), line 8 (annotation)  |
| `org.springframework.test.context.junit.jupiter.SpringExtension` (import) | line 6 (import) — JUnit-flavored, **deleted** |
| `org.springframework.boot.test.context.SpringBootTest` (import + annotation) | line 5 (import), line 9 (annotation) — **retained** |

### Imports to **delete** across both files

- `org.junit.jupiter.api.Test` (both files)
- `org.junit.jupiter.api.extension.ExtendWith` (`BuckPalApplicationTests` only)
- `org.springframework.test.context.junit.jupiter.SpringExtension`
  (`BuckPalApplicationTests` only — the JUnit-flavored Spring extension is
  replaced by Kotest's)

### Imports to **retain** (unchanged)

`DependencyRuleTests`:

- `com.tngtech.archunit.core.importer.ClassFileImporter`
- `com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses`
- `io.reflectoring.buckpal.archunit.HexagonalArchitecture`

`BuckPalApplicationTests`:

- `org.springframework.boot.test.context.SpringBootTest`

### Imports to **add**

`DependencyRuleTests`:

- `io.kotest.core.spec.style.FunSpec`

`BuckPalApplicationTests`:

- `io.kotest.core.spec.style.DescribeSpec`
- `io.kotest.extensions.spring.SpringExtension`

---

## JUnit → Kotest 1:1 mapping

### `DependencyRuleTests`

| Today (line)                                       | Tomorrow                                                                                         |
|-----------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `class DependencyRuleTests {` (line 8)              | `class DependencyRuleTests : FunSpec({ ... })`                                                   |
| `@Test fun validateRegistrationContextArchitecture()` (lines 10–11) | `test("validateRegistrationContextArchitecture") { ... }` — body lines 12–32 copied verbatim     |
| `@Test fun testPackageDependencies()` (lines 34–35) | `test("testPackageDependencies") { ... }` — body lines 36–46 copied verbatim                     |
| `import org.junit.jupiter.api.Test` (line 6)        | Deleted                                                                                          |

### `BuckPalApplicationTests`

| Today (line)                                                    | Tomorrow                                                                                       |
|------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `@ExtendWith(SpringExtension::class)` (line 8)                   | Deleted (Kotest extension takes over)                                                          |
| `@SpringBootTest` (line 9)                                       | Unchanged (retained on the new class)                                                          |
| `class BuckPalApplicationTests {` (line 10)                      | `class BuckPalApplicationTests : DescribeSpec() {`                                             |
| `@Test fun contextLoads() { }` (lines 12–14)                     | `init { describe("Spring application context") { it("loads") { } } }`                          |
| `import org.junit.jupiter.api.Test` (line 3)                     | Deleted                                                                                        |
| `import org.junit.jupiter.api.extension.ExtendWith` (line 4)     | Deleted                                                                                        |
| `import org.springframework.test.context.junit.jupiter.SpringExtension` (line 6) | Deleted; replaced by `import io.kotest.extensions.spring.SpringExtension`           |
| (no `override fun extensions()` today)                           | Added: `override fun extensions() = listOf(SpringExtension)`                                   |

---

## Acceptance checks (mechanically verifiable by Evaluator)

Each box is one shell command or one observable artifact. Five categories:
Behavioral, Idiomatic, Architectural, Code Quality, plus a Scope gate.
Mirrors Sprint 03 / Sprint 04 structure.

### Behavioral correctness

- [ ] `./gradlew test --tests "*DependencyRuleTests"` → exits 0.
- [ ] `./gradlew test --tests "*BuckPalApplicationTests"` → exits 0.
- [ ] `./gradlew test` (full suite) → exits 0; 0 failures across the suite.
- [ ] Aggregate leaf-test count remains **16** (unchanged from the Sprint 04
  baseline). `DependencyRuleTests` had 2 leaves before and has 2 leaves
  after this sprint; `BuckPalApplicationTests` had 1 leaf before and has 1
  leaf after.
- [ ] Parsed
  `build/test-results/test/TEST-io.reflectoring.buckpal.DependencyRuleTests.xml`
  reports `tests="2"` and `failures="0"` and `errors="0"` and `skipped="0"`.
  Both ArchUnit leaves
  (`validateRegistrationContextArchitecture`, `testPackageDependencies`) pass.
- [ ] Parsed
  `build/test-results/test/TEST-io.reflectoring.buckpal.BuckPalApplicationTests.xml`
  reports `tests="1"` and `failures="0"` and `errors="0"` and `skipped="0"`.
  The single context-load leaf passes (Spring context boots).

### Architectural integrity

- [ ] `./gradlew check` → exits 0 (full test task + ArchUnit; the migrated
  `DependencyRuleTests` is the very rule that must stay green here).
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → exits 0
  (FQCN form; same suite as above but explicit).
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.BuckPalApplicationTests"` → exits 0.

### Code quality — JUnit / AssertJ / Mockito residue is gone

- [ ] `grep -nE "^import org\\.junit\\.jupiter" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → no matches (exit 1).
- [ ] `grep -nE "^import org\\.junit\\.jupiter" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → no matches (exit 1).
- [ ] `grep -nE "@Test\\b" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → no matches (exit 1).
- [ ] `grep -nE "@Test\\b" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → no matches (exit 1).
- [ ] `grep -nE "^import org\\.assertj\\.core" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → no matches (exit 1).
- [ ] `grep -nE "^import org\\.assertj\\.core" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → no matches (exit 1).
- [ ] `grep -nE "@ExtendWith" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → no matches (exit 1). The JUnit Spring-extension annotation is gone (Kotest's `override fun extensions()` takes over).
- [ ] `grep -nE "^import org\\.springframework\\.test\\.context\\.junit\\.jupiter\\.SpringExtension" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → no matches (exit 1). The JUnit-flavored Spring-extension import is gone.
- [ ] `grep -nE "^import org\\.mockito" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → no matches (exit 1).
- [ ] `grep -nE "^import org\\.mockito" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → no matches (exit 1).
- [ ] `grep -n "BDDMockito" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → no matches (exit 1).
- [ ] `grep -n "@MockBean\\|@MockkBean" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → no matches (exit 1). Neither file mocks anything.

### Code quality — Kotest spec wrappers are present

- [ ] `grep -nE "^class DependencyRuleTests\\s*:\\s*FunSpec" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches exactly one line. Kotest spec wrapper.
- [ ] `grep -nE "^import io\\.kotest\\.core\\.spec\\.style\\.FunSpec" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line.
- [ ] `grep -nE "^class BuckPalApplicationTests\\s*:\\s*DescribeSpec" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → matches exactly one line. Kotest spec wrapper.
- [ ] `grep -nE "^import io\\.kotest\\.core\\.spec\\.style\\.DescribeSpec" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → matches at least one line.
- [ ] `grep -nE "^import io\\.kotest\\.extensions\\.spring\\.SpringExtension" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → matches at least one line.
- [ ] `grep -nE "override fun extensions\\(\\)" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → matches at least one line. The Kotest Spring extension is registered.
- [ ] `grep -nE "@SpringBootTest" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → matches at least one line. The `@SpringBootTest` annotation is preserved per spec.
- [ ] `grep -nE "\\btest\\(\\\"" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt | wc -l` → exactly **2**. The two ArchUnit `@Test` methods become exactly two `test("...")` leaves.

### Code quality — ArchUnit rule bodies preserved verbatim

- [ ] `grep -nE "^import com\\.tngtech\\.archunit\\.core\\.importer\\.ClassFileImporter" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line. ArchUnit import retained.
- [ ] `grep -nE "^import com\\.tngtech\\.archunit\\.lang\\.syntax\\.ArchRuleDefinition\\.noClasses" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line. ArchUnit import retained.
- [ ] `grep -nE "^import io\\.reflectoring\\.buckpal\\.archunit\\.HexagonalArchitecture" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line. Internal ArchUnit-infrastructure import retained.
- [ ] `grep -n "HexagonalArchitecture.boundedContext" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line. The hex-architecture rule is invoked.
- [ ] `grep -n "io.reflectoring.buckpal.account" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line. The bounded-context package string is preserved verbatim.
- [ ] `grep -nE "withDomainLayer\\(\\\"domain\\\"\\)" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line. Domain layer string preserved.
- [ ] `grep -nE "withAdaptersLayer\\(\\\"adapter\\\"\\)" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line. Adapters layer string preserved.
- [ ] `grep -nE "withApplicationLayer\\(\\\"application\\\"\\)" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line. Application layer string preserved.
- [ ] `grep -nE "withConfiguration\\(\\\"configuration\\\"\\)" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line. Configuration layer string preserved.
- [ ] `grep -n "noClasses()" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line. Second ArchUnit rule preserved.
- [ ] `grep -n "io.reflectoring.reviewapp" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least two lines. The today-vacuous `reviewapp` package strings are preserved verbatim (no silent typo fix; flagged in handoff as deferred TODO).
- [ ] `grep -nE "importPackages\\(\\\"io\\.reflectoring\\.buckpal\\.\\.\\\"\\)" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line. The `ClassFileImporter` package scope for the first rule is preserved verbatim.
- [ ] `grep -nE "importPackages\\(\\\"io\\.reflectoring\\.reviewapp\\.\\.\\\"\\)" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → matches at least one line. The `ClassFileImporter` package scope for the second rule is preserved verbatim (preserving today's behavior).

### Idiomatic Kotlin — no banned patterns

- [ ] `grep -nE "!!" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → no matches (exit 1). Literal double-bang non-null assertion. (`!=` does not match because the regex requires two adjacent `!`.)
- [ ] `grep -nE "!!" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → no matches (exit 1).
- [ ] `grep -n "lateinit var" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt | wc -l` → exactly **0**. `DependencyRuleTests` has no Spring wiring.
- [ ] `grep -n "lateinit var" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt | wc -l` → exactly **0**. The smoke test does not `@Autowired` any property — empty leaf body.
- [ ] `grep -nE "\\.shouldBe\\(" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → no matches (exit 1). Only infix `actual shouldBe expected` is permitted (neither file is expected to add `shouldBe`, but if any are added later, infix form only).
- [ ] `grep -nE "\\.verify\\(" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → no matches (exit 1). Neither file mocks; no `verify` is needed.
- [ ] `grep -nE "every\\s*\\{" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → no matches (exit 1). Neither file mocks; no `every` is needed.

### Scope — only the two files in scope changed

- [ ] `git diff --name-only HEAD -- src/` → exactly these two lines (order irrelevant):
  - `src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt`
  - `src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`

  No other source file is modified.
- [ ] `git diff --name-only HEAD -- src/main/` → empty (no production-code
  edits — protects the non-negotiable invariant).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/common/` → empty (fixtures untouched).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/archunit/` → empty (ArchUnit infrastructure untouched per spec).
- [ ] `git diff --name-only HEAD -- build.gradle` → empty (build-script untouched; Sprint 00 / Sprint 07 territory).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/` → empty (Sprints 01–04 files untouched).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → empty (Sprint 06 territory).
- [ ] `git diff --name-only HEAD -- src/test/resources/` → empty (no SQL or resource edits).

---

## Verification commands the Generator will run before handoff

In order, from the worktree root, with
`JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home`:

1. `./gradlew --no-daemon compileKotlin compileTestKotlin` → expect
   `BUILD SUCCESSFUL`. (Sanity: no production-code edits, so `compileKotlin`
   is a no-op; `compileTestKotlin` proves the rewrites parse and that
   `io.kotest.core.spec.style.FunSpec`, `io.kotest.core.spec.style.DescribeSpec`,
   and `io.kotest.extensions.spring.SpringExtension` resolve.)
2. `./gradlew --no-daemon test --tests "*DependencyRuleTests"`
   → expect `BUILD SUCCESSFUL` and the TEST-*.xml file with `tests="2"`,
   `failures="0"`, `errors="0"`, `skipped="0"`.
3. `./gradlew --no-daemon test --tests "*BuckPalApplicationTests"`
   → expect `BUILD SUCCESSFUL` and the TEST-*.xml file with `tests="1"`,
   `failures="0"`, `errors="0"`, `skipped="0"`. Spring context boots.
4. `./gradlew --no-daemon test` → expect `BUILD SUCCESSFUL`, aggregate 16
   leaf tests (same as Sprint 04 baseline), 0 failures.
5. `./gradlew --no-daemon check` → expect `BUILD SUCCESSFUL` (ArchUnit
   `DependencyRuleTests` exercised here; the very rule we just migrated).
6. `git diff --name-only HEAD -- src/` → expect exactly two paths:
   - `src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt`
   - `src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`
7. Negative greps (all expect "no output"):
   - `grep -nE "^import org\\.junit\\.jupiter" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt`
   - `grep -nE "^import org\\.junit\\.jupiter" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`
   - `grep -nE "@Test\\b" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt`
   - `grep -nE "@Test\\b" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`
   - `grep -nE "@ExtendWith" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`
   - `grep -nE "^import org\\.springframework\\.test\\.context\\.junit\\.jupiter\\.SpringExtension" src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`
   - `grep -nE "^import org\\.assertj\\.core" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`
   - `grep -nE "^import org\\.mockito" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`
   - `grep -nE "!!" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`
   - `grep -n "lateinit var" src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt`

If any step fails, the Generator will diagnose and rerun. No red handoff.
