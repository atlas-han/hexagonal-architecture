# Sprint 02 Handoff

## What changed

- **Modified**: `src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
  - Class redeclared as `class SendMoneyServiceTest : BehaviorSpec({ ... })`.
  - Both `@Test` methods rewritten as two `BehaviorSpec` leaves under
    `given { \`when\` { then { ... } } }` triples.
  - All Mockito (`Mockito.mock`, `BDDMockito.given/then`, `Mockito.times`,
    `Mockito.eq`, `Mockito.any`, `ArgumentCaptor`) replaced with MockK
    (`mockk<T>()`, `every { } returns`, `verify { }`, `verify(exactly = N) { }`,
    `mutableListOf<Account>()` + `capture(list)`).
  - Hand-rolled helpers deleted: the file-level `accountSentinel`,
    `private fun capture(captor: ArgumentCaptor<Account>)`, and
    `private fun <T> eq(value: T)` are all gone.
  - Mock-creation modes: `mockk<LoadAccountPort>()` and `mockk<Account>()`
    stay strict; `mockk<AccountLock>(relaxUnitFun = true)` and
    `mockk<UpdateAccountStatePort>(relaxUnitFun = true)` relax only their
    `Unit`-returning methods to avoid `just Runs` boilerplate.
  - AssertJ (`assertThat(...).isTrue()` / `isFalse()` / `.contains(x)` loop)
    replaced with Kotest matchers (`success shouldBe true/false`,
    `updatedAccountIds shouldContainAll accountIds.toList()`).
  - `!!` eliminated: `sourceAccountId`/`targetAccountId` are declared as
    local `val`s of type `AccountId` *before* the mock is created, then
    used for both the `every`/`verify` sites and for building
    `SendMoneyCommand`.
  - `givenAnAccountWithId(...)` / `givenWithdrawalWillFail(...)` /
    `givenWithdrawalWillSucceed(...)` / `givenDepositWillSucceed(...)` /
    `thenAccountsHaveBeenUpdated(...)` / `moneyTransferProperties()` are
    declared as **local `fun`s inside the `BehaviorSpec` lambda** rather
    than instance methods, which removes the need for any class-level
    state.

- **No other source file modified.** No production code touched, no other
  test file touched, `build.gradle` untouched, fixtures untouched, ArchUnit
  files untouched, Sprint 01 files untouched.

## Contract checklist

### Behavioral correctness

- [x] `./gradlew test --tests "io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest"` → `BUILD SUCCESSFUL`.
- [x] `./gradlew test` (full suite) → `BUILD SUCCESSFUL`; aggregate leaf-test count
      is **16** (1 + 2 + 2 + 4 + 3 + 1 + 2 + 1 across all 8 TEST-*.xml
      reports), unchanged from the Sprint 01 baseline.
- [x] `build/test-results/test/TEST-io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest.xml`
      reports `tests="2" failures="0" errors="0" skipped="0"`.

### Architectural integrity

- [x] `./gradlew check` → `BUILD SUCCESSFUL`.
- [x] `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → `BUILD SUCCESSFUL` (ArchUnit XML reports `tests="2"`).

### Code quality — Mockito and JUnit residue is gone

- [x] `grep -nE "^import org\.mockito" SendMoneyServiceTest.kt` → no matches.
- [x] `grep -n "Mockito\." SendMoneyServiceTest.kt` → no matches.
- [x] `grep -nE "^import org\.assertj\.core" SendMoneyServiceTest.kt` → no matches.
- [x] `grep -nE "^import org\.junit\.jupiter" SendMoneyServiceTest.kt` → no matches.
- [x] `grep -n "@Test" SendMoneyServiceTest.kt` → no matches.
- [x] `grep -nE "(ArgumentCaptor|accountSentinel|BDDMockito)" SendMoneyServiceTest.kt` → no matches.

### Code quality — MockK and Kotest are present

- [x] `grep -nE "^class SendMoneyServiceTest\s*:\s*BehaviorSpec" SendMoneyServiceTest.kt` → 1 match on line 17.
- [x] `grep -nE "^import io\.kotest\.core\.spec\.style\.BehaviorSpec" SendMoneyServiceTest.kt` → 1 match on line 3.
- [x] `grep -nE "^import io\.kotest\.matchers\.shouldBe" SendMoneyServiceTest.kt` → 1 match on line 5.
- [x] `grep -nE "^import io\.mockk\.(every|mockk|verify)" SendMoneyServiceTest.kt` → 3 matches on lines 6–8 (`every`, `mockk`, `verify`).

### Idiomatic Kotlin — no banned patterns

- [x] `grep -nE "(\blateinit\b|!!)" SendMoneyServiceTest.kt` → no matches.
- [x] `grep -nE "\.shouldBe\(" SendMoneyServiceTest.kt` → no matches.
- [x] `grep -nE "\.verify\(" SendMoneyServiceTest.kt` → no matches.

### Scope — only one file changed

- [x] `git diff --name-only HEAD -- src/` → exactly
      `src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`.
- [x] `git diff --name-only HEAD -- src/main/` → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/common/` → empty.
- [x] `git diff --name-only HEAD -- build.gradle` → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/domain/` → empty.
- [x] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/archunit/` → empty.

## Idiomatic Kotlin choices worth flagging

- **`BehaviorSpec` lambda style + local `fun`s.** The class body is a
  single `BehaviorSpec({ ... })` constructor argument. All test helpers
  (`givenAnAccountWithId`, `givenWithdrawalWillFail`,
  `givenWithdrawalWillSucceed`, `givenDepositWillSucceed`,
  `thenAccountsHaveBeenUpdated`, `moneyTransferProperties`) live as
  **local functions inside the lambda** rather than as instance members.
  This works because Kotest re-runs the spec lambda once per leaf, so
  every leaf gets a fresh closure with fresh mocks — no `init { }`, no
  `@BeforeEach`, no `lateinit var`, no `clearMocks()`.

- **Mocks created inside each leaf, not at the spec scope.** The
  `loadAccountPort`, `accountLock`, `updateAccountStatePort`, and
  `sendMoneyService` instances are declared inside each
  `then { ... }` block (after the local-fun declarations earlier in the
  lambda). This makes the per-leaf isolation explicit and prevents any
  reader from assuming shared state. The helper functions take the mocks
  as parameters rather than closing over them.

- **`relaxUnitFun = true` for the two `Unit`-only collaborators.**
  `AccountLock.lockAccount/releaseAccount` and
  `UpdateAccountStatePort.updateActivities` all return `Unit`. Marking
  those two mocks with `relaxUnitFun = true` lets us call them without
  stubbing each one as `every { ... } just Runs`, while keeping the rest
  of the surface (`loadAccount`, `withdraw`, `deposit`, `id`) strictly
  unstubbed-throws. Strict mode on `Account` and `LoadAccountPort` matches
  Mockito's previous behavior — any unexpected interaction still fails
  the test.

- **`mutableListOf<Account>()` + `capture(list)` for the multi-capture
  case, not `slot<Account>()`.** The `updateActivities` call fires twice
  in the success scenario; `slot<T>()` only retains the *last* captured
  value, which would silently lose the source-account assertion. The
  list-form capture preserves both invocations, and
  `updatedAccountIds shouldContainAll accountIds.toList()` is the
  order-insensitive Kotest analog of the previous AssertJ
  `for (accountId in accountIds) assertThat(updatedAccountIds).contains(accountId)`
  loop.

- **`success shouldBe false` / `shouldBe true` instead of
  `success.shouldBeFalse()` / `shouldBeTrue()`.** The contract's quality
  checks explicitly require `actual shouldBe expected` infix form
  (`grep -nE "\.shouldBe\(" → no matches`). This keeps the assertion
  style symmetrical for the two `@Test`-replacement leaves and matches
  Sprint 01's `balance shouldBe Money.of(1555L)` convention.

- **Back-ticked `` `when`("...") ``.** Kotest's `BehaviorSpec` `when`
  builder collides with Kotlin's reserved keyword. Same convention as
  Sprint 01.

## Anything the Evaluator should pay extra attention to

- **Mocking final-`Account` requires no extra setup.** Production
  `Account` is declared `open class Account` (line 11 of `Account.kt`)
  with `open val id` / `open fun withdraw` / `open fun deposit` (lines
  14, 31, 57). MockK can mock the class with a plain `mockk<Account>()`;
  no `mockk-agent-jvm` global, no `MockKAnnotations.init`. This matches
  the spec's risk-register #4 expectation.

- **Per-leaf mock isolation is automatic.** Kotest's default
  `IsolationMode` for `BehaviorSpec` re-runs the lambda per leaf, so the
  two leaves are run with two independent mock graphs. No `clearMocks`
  block was added preemptively; if a future change to the Kotest version
  alters the lifecycle, the fallback is to add `beforeTest { clearAllMocks() }`,
  but that is out of scope for Sprint 02.

- **Argument-equality on `Money` and `AccountId` is value-based.** Both
  production types are Kotlin `data class`es, so `Money.of(500L)` /
  `AccountId(41L)` instances compare equal regardless of identity. The
  `verify { mock.foo(Money.of(500L)) }` calls therefore match the
  production-side invocation without any explicit matcher. No `eq(...)`
  wrapper exists or is needed.

- **The two scenarios preserve the exact same call-count assertions as
  before.** In the failure leaf: `lockAccount(source)` and
  `releaseAccount(source)` are verified (at least once) and
  `lockAccount(target)` is verified `exactly = 0`. In the success leaf:
  `lockAccount/releaseAccount` for both source and target,
  `withdraw(money, target)` on the source mock,
  `deposit(money, source)` on the target mock, and
  `updateActivities(...)` `exactly = 2` (once per account, captured into
  a `mutableListOf<Account>()` and asserted as containing both ids).
  These are all *unordered* `verify { }` calls — no `verifyOrder` or
  `verifySequence` introduced, matching the original Mockito flow which
  also did not assert ordering.

- **`./gradlew test` aggregate leaf count is still 16.** Per-file counts
  from the XML reports: `BuckPalApplicationTests`=1,
  `DependencyRuleTests`=2, `SendMoneySystemTest`=1,
  `SendMoneyControllerTest`=1, `AccountPersistenceAdapterTest`=2,
  `SendMoneyServiceTest`=2, `AccountTest`=4, `ActivityWindowTest`=3.
  Sum = 16, identical to the Sprint 01 baseline (`SendMoneyServiceTest`
  had 2 leaves before and has 2 leaves after).

## TODOs deferred to later sprints

- `SendMoneyControllerTest` still uses `@MockBean` + `@WebMvcTest` on
  JUnit 5; migrate in **Sprint 03**.
- `AccountPersistenceAdapterTest` still uses JUnit 5 + `@DataJpaTest`;
  migrate in **Sprint 04**.
- `DependencyRuleTests` and `BuckPalApplicationTests` still use JUnit 5;
  migrate in **Sprint 05**.
- `SendMoneySystemTest` still uses JUnit 5 + `BDDAssertions`; migrate in
  **Sprint 06**.
- `org.junit.jupiter:junit-jupiter-engine`, `org.mockito:mockito-junit-jupiter`,
  `org.jetbrains.kotlin:kotlin-test`, and `kotlin-test-junit5` remain in
  `build.gradle`; remove in **Sprint 07**.

## Commit

Proposed one-line subject for the orchestrator's commit:

```
feat(kotlin): sprint 2 — migrate SendMoneyServiceTest to Kotest BehaviorSpec + MockK
```

(SHA pending — the orchestrator stages and commits.)
