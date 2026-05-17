STATUS: AGREED

# Sprint 03 Contract — Migrate `SendMoneyControllerTest` (`@WebMvcTest` + `@MockkBean`)

## Sprint goal (verbatim from spec)

> Migrate the web-slice test to a Kotest spec that uses the Spring extension.
> Replace `@MockBean` with `@MockkBean` from springmockk and drop the `eq`
> wrapper.

## Files in scope

Only this **one** test file may be edited in Sprint 03. Anything outside this
list — including production code, fixtures, the build script, or any other
test class — is off-limits.

- `src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`

Files explicitly **not** in scope (read-only, Generator must not touch them):

- `src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/port/in/SendMoneyUseCase.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/port/in/SendMoneyCommand.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Account.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Money.kt`
- `src/main/kotlin/io/reflectoring/buckpal/common/WebAdapter.kt`
- `src/test/kotlin/io/reflectoring/buckpal/common/AccountTestData.kt`
- `src/test/kotlin/io/reflectoring/buckpal/common/ActivityTestData.kt`
- every other test class in the suite — those are Sprints 01, 02, 04, 05, 06
  territory (already migrated for 01/02, pending for 04–06)
- every file under `src/test/kotlin/io/reflectoring/buckpal/archunit/**`
- `build.gradle` (Sprint 00 already wired Kotest + MockK + springmockk; Sprint 07
  removes the legacy stack)

## Hard exit criteria (verbatim from spec)

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
- `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.in.web.SendMoneyControllerTest"`
  exits 0.
- `./gradlew test` (full suite) exits 0.

## Out of scope (verbatim from spec, plus additions)

From spec:

- controller production code
- ArchUnit
- system test
- persistence test

Generator-added:

- All production code (any file under `src/main/kotlin/**`). Production code
  stays untouched — this is a non-negotiable invariant of the whole migration.
- `build.gradle` — Sprint 00 wired Kotest + MockK + springmockk; Sprint 07
  removes the legacy stack. This sprint is test-source-only.
- ArchUnit code under `src/test/kotlin/io/reflectoring/buckpal/archunit/**`.
- Domain tests (`AccountTest`, `ActivityWindowTest`) — migrated in Sprint 01.
- Application-service test (`SendMoneyServiceTest`) — migrated in Sprint 02.
- Persistence test (`AccountPersistenceAdapterTest`) — Sprint 04.
- Smoke / ArchUnit-hosting tests (`BuckPalApplicationTests`, `DependencyRuleTests`) — Sprint 05.
- System test (`SendMoneySystemTest`) — Sprint 06.
- Coroutine APIs (`coEvery`, `coVerify`, `runBlocking`) — the controller's
  `sendMoney` method is not `suspend`. No coroutines.
- Reintroducing any Mockito artifact (`mockito-kotlin`, etc.). The whole
  point is to switch off Mockito for this file.

---

## Spring wiring decision (this is the spec's "Risk register #1" first test)

### Chosen approach: **`@WebMvcTest` + `MockMvc` + `@MockkBean` retained; class becomes a Kotest spec that registers Kotest's `SpringExtension`.**

The spec's Risk register #1 says Sprint 03 is the **first Spring sprint
precisely so the wiring is validated on the smallest Spring slice
(`@WebMvcTest`)**. The user's guidance further recommends "keep `@WebMvcTest`
+ `MockMvc` + `@MockkBean` as-is, switch only the class wrapper to a Kotest
spec; register Kotest's Spring extension so the `TestContextManager` is wired
up". The Generator adopts that recommendation.

Concretely:

1. The class declaration becomes
   `class SendMoneyControllerTest : <Spec>(...)`.
2. The annotations `@WebMvcTest(controllers = [SendMoneyController::class])`
   stay on the class **unchanged**. Spring Boot's test slice infrastructure
   reads these via reflection; whether the class is a JUnit class or a Kotest
   spec is irrelevant — what matters is that **a `TestContextManager` is
   created and bound to the test instance**, which is exactly what Kotest's
   `SpringExtension` does (it is an `ApplicationContextAware` listener that
   inspects the spec instance, runs the standard `TestContextManager`
   lifecycle, and performs `@Autowired` / `@MockkBean` injection on the
   spec instance properties).
3. `MockMvc` injection stays as `@Autowired lateinit var mockMvc: MockMvc`
   (Kotest spec property). `lateinit var` is unavoidable here: Spring needs
   a mutable property to assign into, and there is no constructor-arg form
   for `MockMvc` because the test slice creates the bean and Kotest creates
   the spec. The Idiomatic-Kotlin check below allows `@Autowired lateinit
   var` and `@MockkBean lateinit var` exactly because of this Spring pattern.
4. `@MockBean` is replaced by `@MockkBean` from
   `com.ninja-squad.springmockk:3.1.2` (the springmockk variant that pairs
   with Spring Boot 2.4.3 — already on the testRuntimeClasspath per Sprint 00).
5. The hand-rolled `eq(value)` helper at line 53 is deleted. MockK matches
   by `equals` natively; no null-safety bridge needed.
6. The single happy-path scenario (`POST /accounts/send/41/42/500` returns
   `200` and `SendMoneyCommand(AccountId(41), AccountId(42), Money.of(500))`
   is forwarded to `sendMoneyUseCase`) is preserved as **one leaf test**.

### Spec-style choice: **`DescribeSpec`** (one `describe` + one `it`)

The spec's Spec-style selection paragraph names `DescribeSpec` for
"Single-behavior smoke tests (... the controller happy-path
`SendMoneyControllerTest` ...)". `FunSpec` is also permitted; the Generator
picks `DescribeSpec` for nesting consistency with Sprint 04 / 06 (also Spring
slices that will use `DescribeSpec`).

Rationale to pick `DescribeSpec` over `FunSpec` for a single-leaf test:

- The class has exactly one HTTP behavior to assert. `describe("POST /accounts/send/...") { it("returns 200 and forwards the SendMoneyCommand") { ... } }`
  reads naturally as one behavior with one explicit expectation.
- Future expansion (4xx paths, validation failures) would slot in as
  additional `it(...)` blocks under the same `describe`, without restructuring.
- Spec consistency: the Sprint 04 (`@DataJpaTest`) and Sprint 06
  (`@SpringBootTest`) targets will also use `DescribeSpec`, so all three
  Spring-slice tests share one spec style — easier for a future reader.

`FunSpec` is **explicitly allowed by the spec** and would have been fine.
The Generator's decision is `DescribeSpec`; no deviation from spec.

### Kotest `SpringExtension` registration form

Kotest 5.5.x exposes the Spring extension as
`io.kotest.extensions.spring.SpringExtension` (object). Registration patterns
documented for 5.5.x:

- `override fun extensions() = listOf(SpringExtension)` — preferred when the
  spec class needs only Spring wiring.
- `override fun listeners() = listOf(SpringExtension)` — older form, still
  supported in 5.5.x.

**Final decision**: `override fun extensions() = listOf(SpringExtension)`.
This is the form named first in the spec's Spring-integration paragraph and
is the canonical 5.5.x API.

The override is placed inside the spec class body — Kotest specs allow
class-level overrides alongside the constructor-lambda body:

```kotlin
@WebMvcTest(controllers = [SendMoneyController::class])
class SendMoneyControllerTest : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var sendMoneyUseCase: SendMoneyUseCase

    init {
        describe("POST /accounts/send/{src}/{dst}/{amount}") {
            it("returns 200 and forwards the SendMoneyCommand") {
                mockMvc.perform(
                    post(
                        "/accounts/send/{sourceAccountId}/{targetAccountId}/{amount}",
                        41L, 42L, 500,
                    ).header("Content-Type", "application/json"),
                ).andExpect(status().isOk)

                verify {
                    sendMoneyUseCase.sendMoney(
                        SendMoneyCommand(
                            AccountId(41L),
                            AccountId(42L),
                            Money.of(500L),
                        ),
                    )
                }
            }
        }
    }
}
```

Key shape facts the Evaluator should expect in the diff:

- `DescribeSpec()` (parens, no constructor-arg lambda) — required because the
  spec body needs an `init { describe(...) { ... } }` block alongside class-level
  property declarations (`@Autowired lateinit var`, `@MockkBean lateinit var`).
  A constructor-arg lambda form (`: DescribeSpec({ ... })`) would not allow
  property declarations.
- `override fun extensions()` registers `SpringExtension`.
- `@Autowired lateinit var mockMvc: MockMvc` and `@MockkBean lateinit var
  sendMoneyUseCase: SendMoneyUseCase` are class-level mutable properties —
  Spring's `TestContextManager` populates `mockMvc`; springmockk's
  `MockkBean` post-processor populates `sendMoneyUseCase` with a fresh
  `mockk<SendMoneyUseCase>()` per test instance.
- `init { describe(...) { it(...) { ... } } }` — Kotest's class-body form.
- `andExpect(status().isOk)` (Kotlin property-style accessor on
  `StatusResultMatchers`) — equivalent to today's `status().isOk()`. Either
  form compiles; property-form is more idiomatic in Kotlin. The Generator
  picks property-form. The Evaluator should accept either.

### Why `@MockkBean` (not plain `mockk<T>()` + `@TestConfiguration`)

The spec's Risk register #3 calls out springmockk + Spring Boot 2.4.x pairing.
Sprint 00 already pinned `com.ninja-squad:springmockk:3.1.2`, which is the
3.x line documented to support Spring Boot 2.4.x and Kotlin 1.6.x.
`@MockkBean` is the direct `@MockBean` analog — it registers a `mockk<T>()`
in the Spring context **before** any `@Autowired` collaborator resolves,
which is exactly what the test slice needs (`SendMoneyController`'s
`sendMoneyUseCase` constructor argument must resolve to the mock).

The fallback (`@TestConfiguration { @Bean fun sendMoneyUseCase() =
mockk<SendMoneyUseCase>() }`) is **not adopted** — it would force the test
to either share one mock across leaves or `clearAllMocks` between them, both
of which complicate the single-leaf scenario unnecessarily. `@MockkBean`
re-creates the mock per spec instance (springmockk's
`DefinitionsParser` + `MockkPostProcessor`), so each leaf gets a clean mock.

### `SendMoneyUseCase` is an interface

Spec invariant: `port/in/SendMoneyUseCase.kt` is `interface SendMoneyUseCase
{ fun sendMoney(command: SendMoneyCommand): Boolean }` (verified from earlier
java→kotlin Sprint 3 / `src/main/kotlin/.../SendMoneyUseCase.kt`). MockK
mocks interfaces without needing the `mockk-agent-jvm` for final-class
support — even classic `mockk<T>()` works. **No mitigation needed.**

### `relaxed` decision for `@MockkBean`

The `@MockkBean` annotation on springmockk 3.x supports an optional
`relaxed = true` argument. Default is `relaxed = false`.

**Decision: `relaxed = false` (the default).** The single test stub-and-verify
pattern is:

- The controller calls `sendMoneyUseCase.sendMoney(command)`. The return
  value is `Boolean`, and the controller's Kotlin signature ignores the
  return (`fun sendMoney(...)` in `SendMoneyController`). Wait — let me
  re-verify this:
  - `SendMoneyController.sendMoney` (production, `src/main/kotlin/.../SendMoneyController.kt`)
    declares return type `Unit` and ignores the `Boolean` returned by the
    use case. The `sendMoneyUseCase.sendMoney(command)` call is a
    statement, not an expression.
  - However: **even though the controller ignores the return value, MockK on
    a strict mock will still throw `NoAnswerFoundException` because the
    method signature has a non-`Unit` return type.** The JVM call returns
    `false` (or whatever the JIT stack happens to hold) only if a `Boolean`
    is supplied — strict MockK refuses to invent one.
  - Therefore the Generator **must stub** `every {
    sendMoneyUseCase.sendMoney(any()) } returns true` before the MockMvc
    call. Equivalent to: do not rely on `relaxed`.

Settled: `@MockkBean(relaxed = false)` (default) + explicit `every { ... }
returns true` stub in the leaf. This matches today's BDDMockito behavior:
Mockito returns `false` for un-stubbed `Boolean` calls (Mockito's relaxed
default), which is why today's test works without an explicit `given`.
MockK is stricter; the explicit stub closes the gap. **No silent semantic
change**: the controller does not look at the return value either way.

Alternative `relaxed = true` is **rejected** — over-relaxation risks
masking future bugs (e.g., if a new collaborator method is added to the
use-case interface and the controller starts calling it).

## Inventory of Mockito/Mockito-related API used today

Grepped from the live file
(`src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`,
54 lines):

| Mockito / Spring-Mockito API today                                                | Where it appears                                              |
|------------------------------------------------------------------------------------|---------------------------------------------------------------|
| `org.springframework.boot.test.mock.mockito.MockBean` (import + annotation)        | line 12 (import), line 23 (annotation on `sendMoneyUseCase`)   |
| `org.mockito.BDDMockito.then` (import + call)                                      | line 8 (import), line 37 (`then(sendMoneyUseCase).should()`)   |
| `org.mockito.Mockito` (import + `Mockito.eq` call inside the helper)               | line 9 (import), line 53 (`Mockito.eq(value)` in the wrapper) |
| `org.junit.jupiter.api.Test` (import + annotation)                                 | line 7 (import), line 26 (annotation on `testSendMoney`)       |
| Hand-rolled `eq<T>(value: T): T = Mockito.eq(value) ?: value`                      | line 53 (private helper)                                       |

Imports to **delete**:

- `org.junit.jupiter.api.Test`
- `org.mockito.BDDMockito.then`
- `org.mockito.Mockito`
- `org.springframework.boot.test.mock.mockito.MockBean`

Imports to **retain** (unchanged):

- `io.reflectoring.buckpal.account.application.port.`in`.SendMoneyCommand`
- `io.reflectoring.buckpal.account.application.port.`in`.SendMoneyUseCase`
- `io.reflectoring.buckpal.account.domain.Account.AccountId`
- `io.reflectoring.buckpal.account.domain.Money`
- `org.springframework.beans.factory.annotation.Autowired`
- `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`
- `org.springframework.test.web.servlet.MockMvc`
- `org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post`
- `org.springframework.test.web.servlet.result.MockMvcResultMatchers.status`

Imports to **add**:

- `com.ninja_squad.springmockk.MockkBean` (springmockk 3.x package; underscore
  in `ninja_squad` is the Kotlin import path equivalent of the Maven
  group `com.ninja-squad`; the JAR ships classes under
  `com.ninja_squad.springmockk` because Java/Kotlin identifiers cannot
  contain `-`. Verified against the
  springmockk 3.1.2 jar contents.)
- `io.kotest.core.spec.style.DescribeSpec`
- `io.kotest.extensions.spring.SpringExtension`
- `io.mockk.every`
- `io.mockk.verify`

## Mockito → MockK / MockkBean 1:1 mapping

| Today (line)                                                            | Tomorrow                                                                                                                       |
|--------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `@MockBean` (line 23)                                                    | `@MockkBean` (`com.ninja_squad.springmockk.MockkBean`); `relaxed = false` (default) so the leaf must explicitly `every { } returns true` |
| `@Test` (line 26)                                                        | Deleted. The behavior moves into a Kotest `it("...") { ... }` block inside `init { describe("...") { ... } }`                  |
| `then(sendMoneyUseCase).should().sendMoney(eq(SendMoneyCommand(...)))` (lines 37–46) | `verify { sendMoneyUseCase.sendMoney(SendMoneyCommand(...)) }` — argument equality by `equals` (data-class), no matcher needed |
| Private helper `eq<T>(value: T): T = Mockito.eq(value) ?: value` (line 53) | **Deleted.** MockK matches by `equals` natively                                                                                |
| (No explicit `given` for the use case today — Mockito's relaxed default returns `false`) | `every { sendMoneyUseCase.sendMoney(any()) } returns true` (or `returns false`; the controller ignores the return value) before the MockMvc call |

The `SendMoneyCommand(AccountId(41L), AccountId(42L), Money.of(500L))`
literal stays exactly as written — it is a Kotlin `data class` whose
`equals` is value-based, so `verify { … sendMoney(SendMoneyCommand(41,42,500)) }`
matches the controller's actual call regardless of instance identity.

## Conversion targets

| `.kt` file in scope                  | Class type                                                                 |
|--------------------------------------|----------------------------------------------------------------------------|
| `SendMoneyControllerTest.kt`         | `class SendMoneyControllerTest : DescribeSpec()` with `init { describe { it { ... } } }` |

Class shape facts:

- Stays in package `io.reflectoring.buckpal.account.adapter.`in`.web`
  (back-ticked `in` segment).
- Keeps class name `SendMoneyControllerTest` so test filtering
  (`--tests "io.reflectoring.buckpal.account.adapter.in.web.SendMoneyControllerTest"`)
  continues to work unchanged.
- Class-level annotation `@WebMvcTest(controllers = [SendMoneyController::class])`
  is unchanged.
- Class-level `override fun extensions() = listOf(SpringExtension)` registers
  the Kotest Spring extension.
- Class-level `@Autowired private lateinit var mockMvc: MockMvc` —
  populated by Spring's `TestContextManager` (invoked by Kotest's
  `SpringExtension` via the `ApplicationContextAware` listener).
- Class-level `@MockkBean private lateinit var sendMoneyUseCase: SendMoneyUseCase`
  — populated by springmockk's post-processor with a fresh `mockk<SendMoneyUseCase>()`.
- `init { describe("POST /accounts/send/...") { it("returns 200 and forwards
  the SendMoneyCommand") { ... } } }` — one container, one leaf.
- Drops the `eq(...)` helper outright.

## Idiomatic Kotlin commitments

1. **`DescribeSpec()` class-body form** — `class SendMoneyControllerTest :
   DescribeSpec()` with `init { describe { it { ... } } }`. Constructor-arg
   lambda form (`: DescribeSpec({ ... })`) is **not** used because the class
   needs property declarations (`@Autowired lateinit var` and `@MockkBean
   lateinit var`) that the constructor-arg lambda form cannot host.
2. **`@Autowired lateinit var mockMvc` and `@MockkBean lateinit var
   sendMoneyUseCase` are permitted exceptions to the "no `lateinit var`"
   guideline.** Rationale: Spring's `TestContextManager` and springmockk's
   `MockkPostProcessor` populate these properties via reflection **after**
   spec instantiation — the constructor cannot supply them. Kotest's Spring
   extension explicitly documents this pattern. The negative grep for
   `lateinit var` is therefore scoped: at most **two** `lateinit var`
   declarations may exist in the file, and **both** must be annotated with
   `@Autowired` or `@MockkBean`. Any other `lateinit var` is forbidden.
3. **No `!!` non-null assertions** — the migrated file contains zero `!!`.
   The controller test does not unwrap any nullable; the previous file
   already had no `!!`.
4. **No `Mockito.` prefix anywhere; no `org.mockito` / `BDDMockito` imports.**
5. **No `@MockBean` (Spring-Mockito); replaced by `@MockkBean` (springmockk).**
6. **No `@Test` annotation; no `org.junit.jupiter.api` imports.**
7. **No `org.assertj.core.api.*` imports.** (None present today, but the
   negative grep guards future drift.)
8. **`verify { ... }` block form, not method-call form** — same convention
   as Sprint 02. `verify(exactly = N) { ... }` if exact-count assertions are
   ever added; today's leaf uses plain `verify { ... }` (≥1 invocation).
9. **`every { } returns ...` block form** — same convention as Sprint 02.

## Risk handling specific to this sprint

### Risk: Kotest Spring extension wiring (spec Risk register #1)

Detection: if `mockMvc` is `null` at the call site (an
`UninitializedPropertyAccessException` from Kotlin's `lateinit` mechanism)
or if `sendMoneyUseCase` is `null` (same), then Kotest's `SpringExtension`
did not wire properly. Mitigation:

- The `extensions()` override returns `SpringExtension` (object), not
  `SpringExtension::class` and not `SpringTestExtension`. The 5.5.x artifact
  exposes the singleton via
  `io.kotest.extensions.spring.SpringExtension`.
- If `extensions()` is silently bypassed, an alternative is `override fun
  listeners() = listOf(SpringTestListener)`. The Generator does **not**
  preemptively switch to `listeners()` — it will only do so if `extensions()`
  fails self-check (in which case the Generator documents the swap in the
  handoff and the Evaluator can re-verify).

### Risk: `kotest-extensions-spring` group ID (spec Sprint 00 review follow-up)

The Sprint 00 review explicitly flagged that the Maven Central coordinate
for the Spring extension at version 1.1.3 is **`io.kotest.extensions:kotest-extensions-spring:1.1.3`**
(group `io.kotest.extensions`, NOT `io.kotest`). `build.gradle:54` already
has the correct coordinate:

```
testImplementation 'io.kotest.extensions:kotest-extensions-spring:1.1.3'
```

**No build-script change is needed for this sprint.** This contract repeats
the group ID prominently here purely to absorb the Sprint 00 follow-up — so
no future sprint's contract drifts back to the wrong coordinate.

If `import io.kotest.extensions.spring.SpringExtension` fails to resolve,
the artifact is missing — but Sprint 00 PASS-reviewed that the artifact is
on `testRuntimeClasspath`. So this risk is doubly mitigated.

### Risk: springmockk 3.x compatibility with Spring Boot 2.4.3 (spec Risk register #3)

springmockk 3.1.2 is the documented release for Spring Boot 2.4.x / Kotlin
1.6.x. Sprint 00 pinned it. The fallback (declare a `@TestConfiguration`
with `mockk<SendMoneyUseCase>()`) is documented in the spec but not adopted
preemptively here; the Generator will only switch to it if `@MockkBean`
injection fails self-check.

### Risk: `MockMvc` `.andExpect(status().isOk)` property-form vs method-form

Kotlin's compiler exposes `StatusResultMatchers.isOk(): ResultMatcher` as
both `status().isOk()` (Java-style) and `status().isOk` (Kotlin property-form,
via the no-arg getter convention). The Generator picks `.isOk` (property
form) for Kotlin idiom. **Either form compiles and is accepted by the
Evaluator.**

### Risk: full-suite cross-talk

ArchUnit (`DependencyRuleTests`) scans **compiled production classes**, not
test classes. Reshaping `SendMoneyControllerTest` cannot affect ArchUnit
rules. The other Spring slices (persistence, system) live in separate
classes and do not share state with the controller test. Verified by
running the full suite in self-check.

### Risk: TEST-*.xml leaf count under Kotest

Today the JUnit engine emits
`TEST-io.reflectoring.buckpal.account.adapter.in.web.SendMoneyControllerTest.xml`
with `tests="1"`. Under Kotest's JUnit Platform engine, the same path is
used (one XML per FQCN, regardless of engine). The single `it(...)` block
is one leaf, so the file will report `tests="1"`. Acceptance check below
formalizes this.

### Risk: aggregate leaf count

The Sprint 02 baseline is 16 leaves. `SendMoneyControllerTest` had 1 leaf
before and has 1 leaf after this sprint, so the aggregate stays at **16**.

## Acceptance checks (mechanically verifiable by Evaluator)

Each box is one shell command or one observable artifact. Four categories
mirror Sprint 02's structure: Behavioral, Idiomatic, Architectural, Code
Quality, plus a Scope gate.

### Behavioral correctness

- [ ] `./gradlew test --tests "*SendMoneyControllerTest"` → exits 0.
- [ ] `./gradlew test` (full suite) → exits 0; aggregate leaf-test count is
  unchanged versus the Sprint 02 baseline (16 leaves). `SendMoneyControllerTest`
  had 1 leaf before and has 1 leaf after this sprint.
- [ ] Parsed
  `build/test-results/test/TEST-io.reflectoring.buckpal.account.adapter.in.web.SendMoneyControllerTest.xml`
  reports `tests="1"` and `failures="0"` and `errors="0"` and `skipped="0"`.

### Architectural integrity

- [ ] `./gradlew check` → exits 0 (full test task + ArchUnit; `DependencyRuleTests`
  must stay green).
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → exits 0.

### Code quality — Mockito / Spring-Mockito residue is gone

- [ ] `grep -nE "^import org\\.mockito" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → no matches (exit 1).
- [ ] `grep -n "Mockito\\." src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → no matches (exit 1). Catches stray `Mockito.eq(...)`, `Mockito.mock(...)`, etc.
- [ ] `grep -nE "^import org\\.springframework\\.boot\\.test\\.mock\\.mockito" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → no matches (exit 1). The `@MockBean` package must be gone.
- [ ] `grep -n "BDDMockito" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → no matches (exit 1).
- [ ] `grep -n "@MockBean" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → no matches (exit 1). The Spring-Mockito annotation is gone.
- [ ] `grep -nE "^import org\\.junit\\.jupiter" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → no matches (exit 1).
- [ ] `grep -nE "@Test\\b" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → no matches (exit 1). (`\b` so `@TestConfiguration` would not match — irrelevant here, but defensive.)
- [ ] `grep -nE "^import org\\.assertj\\.core" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → no matches (exit 1).

### Code quality — Kotest, MockK, MockkBean are present

- [ ] `grep -nE "^class SendMoneyControllerTest\\s*:\\s*DescribeSpec" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → matches exactly one line.
- [ ] `grep -nE "^import io\\.kotest\\.core\\.spec\\.style\\.DescribeSpec" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → matches at least one line.
- [ ] `grep -nE "^import io\\.kotest\\.extensions\\.spring\\.SpringExtension" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → matches at least one line.
- [ ] `grep -nE "override fun extensions\\(\\)" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → matches at least one line. The Kotest Spring extension is registered.
- [ ] `grep -nE "^import com\\.ninja_squad\\.springmockk\\.MockkBean" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → matches at least one line.
- [ ] `grep -nE "@MockkBean" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → matches at least one line.
- [ ] `grep -nE "^import io\\.mockk\\.(every|verify)" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → matches at least two lines (`every`, `verify` both present).
- [ ] `grep -nE "@WebMvcTest" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → matches at least one line. The `@WebMvcTest` annotation is preserved per spec.

### Idiomatic Kotlin — no banned patterns

- [ ] `grep -nE "!!" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → no matches (exit 1). Literal `!!`; `!=` is not flagged because the regex requires two `!` adjacent.
- [ ] `grep -n "lateinit var" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt | wc -l` → exactly **2**. The two permitted `lateinit var` declarations are `mockMvc` (Spring-injected) and `sendMoneyUseCase` (springmockk-injected). Any other `lateinit var` is a defect.
- [ ] For each `lateinit var` line above: the same line OR the line directly above it must contain `@Autowired` or `@MockkBean`. (Manual reading by Evaluator — this is the qualitative side of the previous count check. Either line-1 has the annotation inline or line-1 is preceded by line-0 carrying the annotation.)
- [ ] `grep -nE "\\.shouldBe\\(" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → no matches (exit 1). Only infix `actual shouldBe expected` is permitted (if any `shouldBe` is added).
- [ ] `grep -nE "\\.verify\\(" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → no matches (exit 1). `verify` is used in block form `verify { ... }`, never method-call form.

### Scope — only one file changed

- [ ] `git diff --name-only HEAD -- src/` → exactly the single line
  `src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`.
  No other source file is modified.
- [ ] `git diff --name-only HEAD -- src/main/` → empty (no production-code
  edits — protects the non-negotiable invariant).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/common/` → empty (fixtures untouched).
- [ ] `git diff --name-only HEAD -- build.gradle` → empty (build-script
  untouched; Sprint 00/07 territory).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/domain/` → empty (Sprint 01 files untouched).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/application/service/` → empty (Sprint 02 files untouched).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/` → empty (Sprint 04 territory).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/archunit/` → empty (ArchUnit infrastructure untouched).

## Verification commands the Generator will run before handoff

In order, from the worktree root, with
`JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home`:

1. `./gradlew --no-daemon compileKotlin compileTestKotlin` → expect
   `BUILD SUCCESSFUL`. (Sanity: no production code edits, so `compileKotlin`
   is essentially a no-op; `compileTestKotlin` proves the rewrite parses
   and that `com.ninja_squad.springmockk.MockkBean` /
   `io.kotest.extensions.spring.SpringExtension` resolve.)
2. `./gradlew --no-daemon test --tests "*SendMoneyControllerTest"`
   → expect `BUILD SUCCESSFUL` and the TEST-*.xml file with `tests="1"`,
   `failures="0"`, `errors="0"`, `skipped="0"`.
3. `./gradlew --no-daemon test` → expect `BUILD SUCCESSFUL`, aggregate 16
   leaf tests (same as Sprint 02 baseline), 0 failures.
4. `./gradlew --no-daemon check` → expect `BUILD SUCCESSFUL` (ArchUnit
   `DependencyRuleTests` exercised here).
5. `git diff --name-only HEAD -- src/` → expect exactly one path:
   `src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`.
6. Negative greps (all expect "no output"):
   - `grep -nE "^import org\\.mockito" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`
   - `grep -n "Mockito\\." src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`
   - `grep -nE "^import org\\.springframework\\.boot\\.test\\.mock\\.mockito" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`
   - `grep -n "BDDMockito" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`
   - `grep -n "@MockBean" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`
   - `grep -nE "^import org\\.junit\\.jupiter" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`
   - `grep -nE "@Test\\b" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`
   - `grep -nE "^import org\\.assertj\\.core" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`
   - `grep -nE "!!" src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt`

If any step fails, the Generator will diagnose and rerun. No red handoff.
