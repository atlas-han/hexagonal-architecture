# Sprint 01 Handoff — Migrate `account/domain/*` tests to Kotest BehaviorSpec

## Status

**Green / ready for Evaluator.** The previous `needs input:` handoff is
superseded by this one. The blocking BOM downgrade described there
(`kotlinx-coroutines-test` pinned to 1.4.2 by Spring Boot 2.4.3 BOM) was
resolved out-of-band in a Sprint 00 follow-up hot-fix commit (`753c1dc
fix(kotest): sprint 00 follow-up — pin kotlinx-coroutines to 1.6.4 for Kotest
runtime`). That commit is **not part of Sprint 01's diff** — Sprint 01's own
working-tree change remains the two `.kt` files declared in scope.

## What changed (this sprint's working-tree edits)

- `src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt`
  rewritten as `class AccountTest : BehaviorSpec({ ... })`. One top-level
  `given(...)` wraps four `` `when`(...) `` blocks, each containing a single
  `then(...)` leaf — 4 leaf tests total, identical to the prior JUnit `@Test`
  count. AssertJ `assertThat(x).isEqualTo(y)` → infix `x shouldBe y`,
  `.isTrue()` / `.isFalse()` → `.shouldBeTrue()` / `.shouldBeFalse()`,
  `.hasSize(n)` → infix `shouldHaveSize n`. JUnit + AssertJ imports removed.

- `src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt`
  rewritten as `class ActivityWindowTest : BehaviorSpec({ ... })`. One
  top-level `given(...)` wraps three `` `when`(...) `` blocks with one
  `then(...)` leaf each — 3 leaf tests. The original private
  `startDate()` / `inBetweenDate()` / `endDate()` helpers became
  `val ... : () -> LocalDateTime = { ... }` lambdas at the top of the spec
  body, invoked as `startDate()` at each call site to keep the diff visually
  1-to-1 with the JUnit version.

`git diff --name-only HEAD -- src/ build.gradle` lists exactly these two
paths. No production code, no fixtures, no other tests, no build script are
touched in this sprint's diff.

## How Sprint 00 hot-fix relates

Commit `753c1dc` (separate from Sprint 01) added
`kotlinx-coroutines-{core,test}` and their `-jvm` counterparts at 1.6.4 to
`build.gradle`'s test deps, overriding the Spring Boot 2.4.3 BOM constraint
that was downgrading `kotlinx-coroutines-test` to 1.4.2 and stripping
`TestDispatcher` from the test classpath. That fix landed as its own commit;
this sprint did not edit `build.gradle`.

## Self-check results

Run from the worktree root with
`JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home`:

| Step | Command | Result |
|------|---------|--------|
| 1 | `./gradlew --no-daemon compileKotlin compileTestKotlin` | **BUILD SUCCESSFUL** in 3s. Both up-to-date / parse cleanly. |
| 2 | `./gradlew --no-daemon test --tests "io.reflectoring.buckpal.account.domain.*"` | **BUILD SUCCESSFUL** in 5s. `AccountTest` 4 tests / `ActivityWindowTest` 3 tests, 0 failures, 0 errors, 0 skipped. |
| 3 | `./gradlew --no-daemon test` (full suite) | **BUILD SUCCESSFUL** in 10s. Aggregate 16 tests across all `TEST-*.xml` reports, 0 failures (identical to Sprint 00 baseline). |
| 4 | `./gradlew --no-daemon check` | **BUILD SUCCESSFUL** in 3s. ArchUnit `DependencyRuleTests` included; passes. |

JUnit XML evidence:

- `build/test-results/test/TEST-io.reflectoring.buckpal.account.domain.AccountTest.xml`
  → `tests="4" skipped="0" failures="0" errors="0"`.
- `build/test-results/test/TEST-io.reflectoring.buckpal.account.domain.ActivityWindowTest.xml`
  → `tests="3" skipped="0" failures="0" errors="0"`.
- Aggregate `tests=` sum across all `TEST-*.xml` files in `build/test-results/test/` = **16**.
- Aggregate `failures=` sum = **0**.

## Contract checklist

| Contract acceptance check | Status | Evidence |
|---------------------------|--------|----------|
| `grep -E "^class AccountTest\s*:\s*BehaviorSpec" AccountTest.kt` matches one line | **PASS** | Matches `class AccountTest : BehaviorSpec({` at line 12 |
| `grep -E "^class ActivityWindowTest\s*:\s*BehaviorSpec" ActivityWindowTest.kt` matches one line | **PASS** | Matches `class ActivityWindowTest : BehaviorSpec({` at line 9 |
| `grep -n "org.junit.jupiter"` in both files → no matches | **PASS** | Exit 1 (empty) |
| `grep -n "org.assertj.core"` in both files → no matches | **PASS** | Exit 1 (empty) |
| `grep -n "@Test"` in both files → no matches | **PASS** | Exit 1 (empty) |
| Required matcher / spec imports present in `AccountTest.kt` | **PASS** | Lines 3–7: `BehaviorSpec`, `shouldBeFalse`, `shouldBeTrue`, `shouldHaveSize`, `shouldBe` |
| Required matcher / spec imports present in `ActivityWindowTest.kt` | **PASS** | Lines 3–4: `BehaviorSpec`, `shouldBe`. No boolean matchers (none used in this file, per contract note "boolean matchers are not expected in this file"). `shouldHaveSize` not imported because no size assertions exist in this file's body — the contract grep marks `shouldHaveSize` as "appear as needed". |
| `git diff --name-only HEAD -- src/ build.gradle` lists exactly the two domain test files | **PASS** | Output is exactly the two `.kt` paths |
| `git diff --name-only HEAD -- src/main/` empty | **PASS** | Empty output |
| `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/common/` empty | **PASS** | Empty output |
| `./gradlew test --tests "io.reflectoring.buckpal.account.domain.*"` exits 0 | **PASS** | BUILD SUCCESSFUL |
| `TEST-...AccountTest.xml` reports `tests="4"` / 0 failures / 0 errors / 0 skipped | **PASS** | Confirmed in `build/test-results/test/` |
| `TEST-...ActivityWindowTest.xml` reports `tests="3"` / 0 failures / 0 errors / 0 skipped | **PASS** | Confirmed in `build/test-results/test/` |
| `./gradlew test` full suite exits 0; aggregate leaf-test count is unchanged at 16 | **PASS** | BUILD SUCCESSFUL; aggregate `tests=` sum is 16 |
| `./gradlew check` exits 0 (ArchUnit included) | **PASS** | BUILD SUCCESSFUL |
| No `lateinit` / `!!` in either file | **PASS** | `grep -nE "(\blateinit\b|!!)"` → empty (exit 1) |
| Only infix `shouldBe`, never `.shouldBe(` | **PASS** | `grep -nE "\.shouldBe\("` → empty (exit 1) |

All 16 acceptance checks pass.

## Idiomatic Kotlin choices worth flagging

- **`BehaviorSpec` for both files**, as the round-1 AGREED contract committed.
  Each file is one `class XTest : BehaviorSpec({ ... })` with a single
  top-level `given` wrapping one `` `when` `` per former `@Test`, and one
  `then` leaf per former `@Test` body. 4 + 3 = 7 leaf tests, matching the
  contract's leaf-test budget exactly.
- **Back-ticked `` `when` ``** — Kotest 5.5.x's `BehaviorSpec` exposes the
  builder name `when`, which collides with the Kotlin keyword. Both files
  use back-ticks throughout. No `When { ... }` alias is provided by 5.5.5,
  so back-ticks are mandatory at this version.
- **Infix `shouldBe`** everywhere; the negative-grep AC for non-infix
  method-call form passes empty.
- **`shouldHaveSize` infix** in `AccountTest.kt`
  (`account.activityWindow.getActivities() shouldHaveSize 3`).
- **No `!!`, no `lateinit var`.** Each leaf re-builds its own `Account` /
  `ActivityWindow` from the test fixtures — same per-test isolation the
  original `@Test`-per-method form had. Kotest re-evaluates the
  `BehaviorSpec` lambda per leaf by default, so even shared `val`s inside a
  `` `when` `` block would be re-evaluated; but the migration kept each
  `then` self-contained for maximum clarity.
- **Helper functions in `ActivityWindowTest`** — the three private
  `LocalDateTime` helpers became
  `val startDate: () -> LocalDateTime = { LocalDateTime.of(...) }` lambdas at
  the top of the `BehaviorSpec` body, invoked as `startDate()` at every call
  site. `LocalDateTime.of(...)` is pure, so plain `val`s would behave
  identically; lambdas were chosen to preserve the syntactic shape of "method
  call" at every call site, keeping the conversion visually 1-to-1 with the
  JUnit version.
- **Container/leaf separation is strict**: no `shouldBe` calls in `given`
  or `` `when` `` containers. Every assertion is inside a `then(...) { ... }`
  block. This is what makes Kotest count exactly 4 / 3 leaves under the
  documented `TEST-<FQCN>.xml` reports.

## Anything the Evaluator should pay extra attention to

1. **The earlier `needs input:` handoff is now resolved.** The blocker was
   a build-script BOM defect resolved in commit `753c1dc` (separate Sprint 00
   follow-up commit). The Generator did not edit `build.gradle` in Sprint 01;
   that change is owned by the Sprint 00 hot-fix.
2. **No production code edits.** `git diff --name-only HEAD -- src/main/` is
   empty. The hexagonal-architecture invariants (`Account`, `Money`,
   `ActivityWindow` under `account/domain/`) are byte-identical to
   pre-sprint.
3. **No fixture edits.** `AccountTestData.kt` and `ActivityTestData.kt` under
   `src/test/kotlin/io/reflectoring/buckpal/common/` are untouched. The
   migrated specs import `defaultAccount` and `defaultActivity` exactly as
   the JUnit form did.
4. **JUnit Platform engine collision did not occur.** Even with both engines
   registered (Jupiter and Kotest), the migrated classes have zero `@Test`
   annotations and extend `BehaviorSpec`, so Jupiter does not discover them
   and the Kotest engine is the only one that picks them up. The 16-test
   aggregate (was 16 pre-sprint, is 16 post-sprint) confirms no
   duplicate-execution.
5. **`Money` `BigDecimal` scale equality is preserved.** All `shouldBe`
   comparisons in `AccountTest.kt` are between `Money.of(longLiteral)` on
   both sides (matching the original AssertJ form), so `BigDecimal.equals`
   scale-sensitivity is not a behavior change.

## TODOs deferred to later sprints

- **Sprint 02 (`SendMoneyServiceTest`)** — first sprint that introduces
  MockK. The coroutines 1.6.4 dependency that the Sprint 00 hot-fix added
  also unblocks any future MockK suspend-stub usage, though Sprint 02 does
  not need coroutines (the service is pure synchronous logic).
- **Sprint 03+ (Spring-flavored specs)** — `kotest-extensions-spring` is
  registered on `testRuntimeClasspath` (per Sprint 00) under the corrected
  coordinate `io.kotest.extensions:kotest-extensions-spring:1.1.3` (group is
  `io.kotest.extensions`, not `io.kotest`). The Sprint 03 contract should
  use this coordinate without re-discovering the typo.

## Commit

No `git commit` performed by the Generator (per generator.md hard rule —
the orchestrator owns git state).

Proposed one-line commit subject for the orchestrator:

```
feat(kotest): sprint 01 — migrate account/domain tests to Kotest BehaviorSpec
```
