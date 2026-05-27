STATUS: AGREED

# Sprint 01 Contract — Migrate pure-domain unit tests (`account/domain/*`) to Kotest

## Sprint goal (verbatim from spec)

> Rewrite these two zero-dependency unit tests as `BehaviorSpec` classes using
> Kotest matchers. No mocks involved (these tests use only `AccountTestData`
> builders), so this is the simplest sprint and also the smoke test that
> Sprint 00 wired Kotest in correctly.

## Files in scope

Only these two test files may be edited in Sprint 01. Anything else — including
fixtures in `common/`, production code under `src/main/kotlin/**`, `build.gradle`,
ArchUnit code, or any other test file — is off-limits.

- `src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt`
- `src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt`

Files explicitly **not** in scope (read-only, Generator must not touch them):

- `src/test/kotlin/io/reflectoring/buckpal/common/AccountTestData.kt`
- `src/test/kotlin/io/reflectoring/buckpal/common/ActivityTestData.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Account.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Money.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindow.kt`
- `build.gradle`
- every file under `src/test/kotlin/io/reflectoring/buckpal/archunit/**`
- every other test class in the suite

## Hard exit criteria (verbatim from spec)

- Both files extend `io.kotest.core.spec.style.BehaviorSpec` (or `FunSpec` if
  the Generator prefers for a 3-test file — must be one consistent choice per
  file).
- Zero references to `org.junit.jupiter.api.*` and zero references to
  `org.assertj.core.api.*` in these two files.
- Each `@Test` becomes one `then(...)` (BehaviorSpec) or `test(...)` /
  `it(...)` block; behavior asserted is identical.
- `./gradlew test --tests "io.reflectoring.buckpal.account.domain.*"` exits 0
  and reports the same number of leaf tests as before (`AccountTest`: 4;
  `ActivityWindowTest`: 3).
- `./gradlew test` (full suite) exits 0.

## Out of scope (verbatim from spec, plus additions)

From spec:
- `account/application/**`
- `account/adapter/**`
- any test outside `account/domain/`
- fixture files in `common/`
- the build script

Generator-added:
- ArchUnit code under `src/test/kotlin/io/reflectoring/buckpal/archunit/**`
- `DependencyRuleTests` and `BuckPalApplicationTests` (Sprint 05 territory)
- `SendMoneySystemTest` (Sprint 06 territory)
- Any change to `Account.kt`, `Money.kt`, `ActivityWindow.kt`, or any other
  production class — even widening `internal` to `public` or relaxing
  nullability. Production code stays untouched (non-negotiable invariant of
  the whole migration).
- Introducing `kotest-property`, `kotest-assertions-json`, or any Kotest
  add-on not already on the testRuntimeClasspath via Sprint 00.
- Adding coroutine APIs (`coEvery`, `coVerify`, `runBlocking`) — the existing
  tests are pure synchronous logic, no suspend functions exist.
- Editing the `compileTestKotlin` block in `build.gradle` (we are not in
  build-script scope).


---

## Generator-proposed concrete plan

### Spec-style choice per file

I will use **`BehaviorSpec`** for **both** files. Rationale:

- The spec's "Target Kotest + MockK conventions → Spec style selection"
  paragraph explicitly names `AccountTest` and `ActivityWindowTest` as
  `BehaviorSpec` candidates ("Pure-unit tests with several scenarios per
  behavior").
- `BehaviorSpec` maps cleanly onto the existing test names — each `@Test`
  method is a small "given X, when Y, then Z" narrative even though the
  current code does not literally use those words. The migration becomes a
  one-`then`-block-per-`@Test` rewrite.
- A single consistent style per file is required by the contract; using
  `BehaviorSpec` for both keeps the domain-test cluster uniform and avoids
  context-switching for the next reader.
- `FunSpec` is technically allowed for the 3-test `ActivityWindowTest`, but
  mixing styles inside the same package adds cognitive load with no payoff.

`AccountTest` will have **one** top-level `given(...)` block wrapping all four
scenarios (they all start from the same `defaultAccount()` baseline of
`555L + 999L + 1L`). Inside that, each existing `@Test` becomes a
`` `when`("...") { then("...") { ... } } `` pair.

`ActivityWindowTest` will have **one** top-level `given(...)` block ("an
ActivityWindow with three activities") wrapping the three `@Test` methods, each
becoming `` `when`("...") { then("...") { ... } } ``.

(Note: in Kotest's `BehaviorSpec`, the `when` builder is a reserved Kotlin
keyword and must be back-ticked. The Generator will use back-ticks; the
Evaluator should expect `` `when`("...") { ... } `` in the diff.)

### Leaf-test count budget

The Hard exit criteria require **4 leaf tests in `AccountTest`** and **3 leaf
tests in `ActivityWindowTest`** — identical to today. In Kotest, only the
innermost `then(...)` (or `it(...)` / `test(...)`) blocks count as leaves;
`given(...)` and `` `when`(...) `` are containers. The mapping is therefore
strictly 1-to-1:

| Today (`@Test` method)                             | Tomorrow (leaf)                                              |
|----------------------------------------------------|--------------------------------------------------------------|
| `AccountTest.calculatesBalance`                    | `given(...) { ` `when`(...) { then("calculates balance") } }` |
| `AccountTest.withdrawalSucceeds`                   | `... then("withdrawal succeeds") { ... }`                    |
| `AccountTest.withdrawalFailure`                    | `... then("withdrawal fails when insufficient funds") { ... }` |
| `AccountTest.depositSuccess`                       | `... then("deposit succeeds") { ... }`                       |
| `ActivityWindowTest.calculatesStartTimestamp`      | `... then("calculates start timestamp") { ... }`             |
| `ActivityWindowTest.calculatesEndTimestamp`        | `... then("calculates end timestamp") { ... }`               |
| `ActivityWindowTest.calculatesBalance`             | `... then("calculates per-account balance") { ... }`         |

Total: 4 + 3 = 7 leaf tests, identical to today.

### Assertion mapping (JUnit/AssertJ → Kotest)

The two files use **only AssertJ** today (`org.assertj.core.api.Assertions.assertThat`
in `AccountTest`, `org.assertj.core.api.Assertions` in `ActivityWindowTest`).
There are **zero** JUnit-Jupiter assertions (no `assertEquals`, `assertTrue`,
etc.); the only JUnit import in either file is `@Test`. Mapping is therefore:

| Existing call                                            | Kotest replacement                                |
|----------------------------------------------------------|---------------------------------------------------|
| `assertThat(money).isEqualTo(Money.of(N))`               | `money shouldBe Money.of(N)`                      |
| `assertThat(boolean).isTrue()`                           | `boolean.shouldBeTrue()`                          |
| `assertThat(boolean).isFalse()`                          | `boolean.shouldBeFalse()`                         |
| `assertThat(collection).hasSize(N)`                      | `collection shouldHaveSize N`                     |
| `assertThat(timestamp).isEqualTo(expectedTimestamp)`     | `timestamp shouldBe expectedTimestamp`            |
| `Assertions.assertThat(...).isEqualTo(...)` (qualified)  | `... shouldBe ...` (no qualifier)                 |

All replacements come from `kotest-assertions-core` (already on the
testRuntimeClasspath via Sprint 00). The required imports are:

- `import io.kotest.core.spec.style.BehaviorSpec`
- `import io.kotest.matchers.shouldBe`
- `import io.kotest.matchers.booleans.shouldBeTrue`
- `import io.kotest.matchers.booleans.shouldBeFalse`
- `import io.kotest.matchers.collections.shouldHaveSize`

`ActivityWindowTest` only ever asserts equality and collection size today (no
boolean truthiness assertions), so its required Kotest imports reduce to:

- `import io.kotest.core.spec.style.BehaviorSpec`
- `import io.kotest.matchers.shouldBe`
- `import io.kotest.matchers.collections.shouldHaveSize`

No JUnit, no AssertJ, no Mockito, no MockK, no Spring imports are needed in
either file (mocks/Spring are not in scope for the domain layer).

`Money` equality: production `Money` is a Kotlin `data class` wrapping
`BigDecimal`. `data class` `equals` delegates to `BigDecimal.equals`, which is
scale-sensitive (`1.0 != 1.00`). Today's AssertJ `isEqualTo` calls all use
`Money.of(long)` on both sides, so scales match by construction; the new
`shouldBe` comparisons preserve that exact semantics. No behavior change.

### Conversion targets

| `.kt` file in scope                                    | Class type                                              |
|--------------------------------------------------------|---------------------------------------------------------|
| `AccountTest.kt`                                       | `class AccountTest : BehaviorSpec({ ... })`             |
| `ActivityWindowTest.kt`                                | `class ActivityWindowTest : BehaviorSpec({ ... })`      |

Both classes:

- Stay in package `io.reflectoring.buckpal.account.domain`.
- Keep their class names (`AccountTest`, `ActivityWindowTest`) so test
  filtering (`--tests "io.reflectoring.buckpal.account.domain.*"`) continues
  to work unchanged.
- Drop the no-arg constructor body entirely; all logic moves into the
  `BehaviorSpec` lambda.
- Drop the `@Test` annotation and the `org.junit.jupiter.api.Test` import.
- Drop the `org.assertj.core.api.Assertions` / `Assertions.assertThat`
  imports.
- `ActivityWindowTest`'s private helper methods `startDate()`,
  `inBetweenDate()`, `endDate()` move to **`val`** declarations at the top of
  the `BehaviorSpec` lambda (or remain as nested local functions inside the
  lambda — Generator's call at implementation time; both are idiomatic). The
  `java.time.LocalDateTime` import stays.

### Idiomatic Kotlin commitments

1. **`BehaviorSpec` lambda style** — Both files become `class XTest :
   BehaviorSpec({ ... })`; no `init { }` block, no override of
   `extensions()` / `listeners()` (no Spring needed in the domain layer).
2. **`shouldBe` infix form** — All `isEqualTo` replacements use infix
   `actual shouldBe expected` rather than method-call form
   `actual.shouldBe(expected)`, matching Kotest convention.
3. **Container/leaf separation** — `given` and `` `when` `` are pure containers
   with no `shouldBe` calls; every assertion lives inside the innermost
   `then(...)` block. This is what makes Kotest count exactly 4 + 3 = 7
   leaves.
4. **No `lateinit var` test fixtures** — The current files re-build the
   account inside each `@Test`. The migrated files keep the same
   per-leaf-test construction (no shared mutable state). If two `then`
   blocks happen to share setup, the shared `val`s go just inside the
   enclosing `` `when` `` block so each `then` still sees a fresh value (Kotest
   re-evaluates `BehaviorSpec` lambda blocks per leaf by default; for a
   stateless `val account = ...` that re-evaluation is what we want).
5. **`shouldHaveSize` infix** — Same reasoning as `shouldBe`; matches the
   spec's "Assertions" guidance.
6. **Backticked `when`** — Acknowledged Kotlin syntactic cost of using
   `BehaviorSpec`. The Generator will not switch to `FunSpec` to avoid the
   back-ticks; consistency with the spec's spec-style guidance outweighs
   the syntactic noise.
7. **No `!!` non-null assertions** — The migrated tests never construct values
   that need to be unwrapped from `Optional` / nullable types. The Generator
   will not introduce `!!` operators in either file.

### Risk handling specific to this sprint

- **Risk: JUnit Platform discovers the same class twice.** Sprint 00's
  decision to keep both `junit-jupiter-engine:5.0.1` and
  `kotest-runner-junit5:5.5.5` registered means a class that accidentally
  carries both `@Test` (Jupiter) and inherits a Kotest `Spec` would run on
  both engines. **Mitigation:** the migrated classes have zero `@Test`
  annotations and inherit `BehaviorSpec` — Jupiter only scans
  `@Test`-annotated methods, so it cannot pick these up. Generator will
  `grep -nR "org.junit.jupiter" src/test/kotlin/io/reflectoring/buckpal/account/domain/`
  after the rewrite and expect zero matches.
- **Risk: `kotest-extensions-spring` coordinate.** Per Sprint 00 review
  followup, the Maven Central coordinate is
  `io.kotest.extensions:kotest-extensions-spring:1.1.3` (group
  `io.kotest.extensions`, **not** `io.kotest`). Sprint 01 does not import
  anything from this artifact (domain tests have no Spring context), so this
  is informational only and does not affect Sprint 01's diff. It is recorded
  here so the next Spring-flavored sprint contract can name the coordinate
  correctly from the start.
- **Risk: ArchUnit fallout.** ArchUnit (`DependencyRuleTests`) is unrelated
  to test-class structure; it scans **production** package dependencies, not
  test code. Migrating `AccountTest` / `ActivityWindowTest` to Kotest cannot
  affect ArchUnit results. Generator will verify by running the full suite
  in the self-check (criterion below) and by running `./gradlew check`
  explicitly so ArchUnit failure surfaces as a sprint failure rather than a
  silent regression.
- **Risk: `Money` `BigDecimal` equality scale.** Already covered above; no
  test today compares `Money.of(1.0)` to `Money.of(1.00)`, so this is a
  non-issue for Sprint 01.
- **Risk: leaf-count drift.** The hard exit criterion is "same number of
  leaf tests as before". Generator will count leaves before handoff by
  parsing the JUnit XML reports under
  `build/test-results/test/TEST-*.xml` and asserting
  `AccountTest`: 4 tests / `ActivityWindowTest`: 3 tests.
- **Risk: TEST-*.xml report naming under the Kotest engine.**
  `kotest-runner-junit5` is a JUnit Platform `TestEngine`. When Gradle's
  `Test` task runs with `useJUnitPlatform()`, it writes one XML report per
  test class to `build/test-results/test/` using the **same** filename
  convention regardless of which engine produced the result —
  `TEST-<fully-qualified-class-name>.xml`. Therefore the migrated specs
  produce `TEST-io.reflectoring.buckpal.account.domain.AccountTest.xml`
  and `TEST-io.reflectoring.buckpal.account.domain.ActivityWindowTest.xml`
  at exactly the same paths used today. If those paths are absent after the
  migration (e.g., the engine emits per-engine subdirectories instead),
  treat that as a sprint failure rather than silently bypassing the count
  assertion — the acceptance check explicitly requires the files at the
  documented paths.

## Acceptance checks (mechanically verifiable by Evaluator)

- [ ] `grep -E "^class AccountTest\\s*:\\s*BehaviorSpec" src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt` → matches one line
- [ ] `grep -E "^class ActivityWindowTest\\s*:\\s*BehaviorSpec" src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt` → matches one line
- [ ] `grep -n "org.junit.jupiter" src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt` → no matches (exit 1)
- [ ] `grep -n "org.assertj.core" src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt` → no matches (exit 1)
- [ ] `grep -n "@Test" src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt` → no matches (exit 1)
- [ ] `grep -nE "import io\\.kotest\\.(core\\.spec\\.style\\.BehaviorSpec|matchers\\.shouldBe|matchers\\.booleans\\.shouldBe(True|False)|matchers\\.collections\\.shouldHaveSize)" src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt` → at least the `BehaviorSpec` and `shouldBe` lines match (other matcher imports appear as needed)
- [ ] `grep -nE "import io\\.kotest\\.(core\\.spec\\.style\\.BehaviorSpec|matchers\\.shouldBe|matchers\\.collections\\.shouldHaveSize)" src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt` → at least the `BehaviorSpec` and `shouldBe` lines match (boolean matchers are not expected in this file)
- [ ] `git diff --name-only HEAD -- src/ build.gradle` lists exactly the two domain test files; no other file is modified
- [ ] `git diff --name-only HEAD -- src/main/` → empty (no production-code edits)
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/common/` → empty (fixtures untouched)
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.account.domain.*"` → exits 0
- [ ] Parsed `build/test-results/test/TEST-io.reflectoring.buckpal.account.domain.AccountTest.xml` reports `tests="4"` and `failures="0"` and `errors="0"` and `skipped="0"` (filename is the standard `TEST-<FQCN>.xml` Gradle/JUnit-Platform naming — `kotest-runner-junit5` writes to the same path)
- [ ] Parsed `build/test-results/test/TEST-io.reflectoring.buckpal.account.domain.ActivityWindowTest.xml` reports `tests="3"` and `failures="0"` and `errors="0"` and `skipped="0"` (filename is the standard `TEST-<FQCN>.xml` Gradle/JUnit-Platform naming — `kotest-runner-junit5` writes to the same path)
- [ ] `./gradlew test` (full suite) → exits 0; aggregate leaf-test count is unchanged at 16
- [ ] **Architectural integrity:** `./gradlew check` → exits 0 (this runs the full test task plus any extra verification tasks; in particular `DependencyRuleTests` must still pass). Evaluator may equivalently run `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` and expect exit 0.
- [ ] **Idiomatic Kotlin — no banned patterns:** `grep -nE "(\\blateinit\\b|!!)" src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt` → no matches (exit 1). The regex uses `\b` so `!=` (which does not contain `!!`) is not flagged, and `lateinit` only matches as a whole word.
- [ ] **Idiomatic Kotlin — `shouldBe` is used in infix form:** `grep -nE "\\.shouldBe\\(" src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt` → no matches (exit 1). Non-infix method-call form `actual.shouldBe(expected)` is forbidden; only infix `actual shouldBe expected` is allowed.

## Verification commands the Generator will run before handoff

In order, from the worktree root, with
`JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home`:

1. `./gradlew --no-daemon compileKotlin compileTestKotlin` → expect
   `BUILD SUCCESSFUL`. (Sanity: no production code edits, so `compileKotlin`
   is essentially a no-op; `compileTestKotlin` proves the rewrites parse.)
2. `./gradlew --no-daemon test --tests "io.reflectoring.buckpal.account.domain.*"`
   → expect `BUILD SUCCESSFUL` and the two TEST-*.xml files above with the
   expected counts.
3. `./gradlew --no-daemon test` → expect `BUILD SUCCESSFUL`, aggregate
   16 leaf tests, 0 failures (identical to Sprint 00 baseline).
4. `./gradlew --no-daemon check` → expect `BUILD SUCCESSFUL` (ArchUnit
   `DependencyRuleTests` is exercised here and must still pass).
5. `git diff --name-only HEAD` → expect exactly two paths:
   `src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt`
   and
   `src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt`.
6. Negative greps:
   - `grep -nR "org.junit.jupiter" src/test/kotlin/io/reflectoring/buckpal/account/domain/`
     → no output.
   - `grep -nR "org.assertj.core" src/test/kotlin/io/reflectoring/buckpal/account/domain/`
     → no output.
   - `grep -nR "@Test" src/test/kotlin/io/reflectoring/buckpal/account/domain/`
     → no output.
   - `grep -nER "(\\blateinit\\b|!!)" src/test/kotlin/io/reflectoring/buckpal/account/domain/`
     → no output.
   - `grep -nER "\\.shouldBe\\(" src/test/kotlin/io/reflectoring/buckpal/account/domain/`
     → no output.

If any step fails, the Generator will diagnose and rerun. No red handoff.
