STATUS: AGREED

# Sprint 02 Contract — Migrate `SendMoneyServiceTest` (Mockito → MockK, BehaviorSpec)

## Sprint goal (verbatim from spec)

> Replace Mockito + BDDMockito + the hand-rolled `eq` / `capture` /
> `accountSentinel` null-safety wrappers with MockK primitives
> (`mockk<T>()`, `every { } returns`, `verify { }`, `slot<T>()`).
> Express the two existing scenarios as a `BehaviorSpec`
> (`given` / `when` / `then`).

## Files in scope

Only this **one** test file may be edited in Sprint 02. Anything outside this
list — including production code, fixtures, the build script, or any other
test class — is off-limits.

- `src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`

Files explicitly **not** in scope (read-only, Generator must not touch them):

- `src/main/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyService.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/service/MoneyTransferProperties.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/port/in/SendMoneyCommand.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/port/out/AccountLock.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/port/out/LoadAccountPort.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/application/port/out/UpdateAccountStatePort.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Account.kt`
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Money.kt`
- `src/test/kotlin/io/reflectoring/buckpal/common/AccountTestData.kt`
- `src/test/kotlin/io/reflectoring/buckpal/common/ActivityTestData.kt`
- `src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt`
- `src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt`
- `build.gradle`
- every file under `src/test/kotlin/io/reflectoring/buckpal/archunit/**`
- every other test class in the suite (controller, persistence, system, smoke,
  archunit hosting) — those are Sprints 03–07 territory

## Hard exit criteria (verbatim from spec)

- Class extends `BehaviorSpec`. No `@Test` annotation remains in the file.
- All Mockito-related imports (`org.mockito.*`, `org.mockito.BDDMockito.*`)
  are gone.
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

## Out of scope (verbatim from spec, plus additions)

From spec:
- production code under `src/main/kotlin/io/reflectoring/buckpal/account/application/service/**`
- any other test file
- fixtures in `common/`

Generator-added:
- All production code (any file under `src/main/kotlin/**`). Production code
  stays untouched — this is a non-negotiable invariant of the whole migration.
- `build.gradle` — Sprint 00 already wired Kotest + MockK; Sprint 07 removes
  the legacy stack. This sprint is test-source-only.
- ArchUnit code under `src/test/kotlin/io/reflectoring/buckpal/archunit/**`.
- Domain tests (`AccountTest`, `ActivityWindowTest`) — already migrated in
  Sprint 01.
- Any Spring wiring (`@SpringBootTest`, `@WebMvcTest`, `SpringExtension`,
  `kotest-extensions-spring`) — `SendMoneyServiceTest` is a pure unit test
  with no Spring context. Adding Spring would be a regression.
- Coroutine APIs (`coEvery`, `coVerify`, `runBlocking`) — none of the mocked
  methods are `suspend`. Risk register item 7 notes coroutines are only
  mentioned for completeness.
- Introducing `mockito-kotlin` or any compatibility shim. The whole point of
  Sprint 02 is to switch off Mockito; reintroducing it under a different
  artifact would defeat that.
- Touching `MoneyTransferProperties` (production class) or `SendMoneyCommand`
  (production data class) — both are constructed by the test but neither is
  edited.

---

## Generator-proposed concrete plan

### Spec-style choice — `BehaviorSpec` (consistent with Sprint 01)

I will use **`BehaviorSpec`** for this file, matching the choice made in
Sprint 01 for `AccountTest` / `ActivityWindowTest`. Rationale:

- The spec's "Target Kotest + MockK conventions → Spec style selection"
  paragraph explicitly names `SendMoneyServiceTest` as a `BehaviorSpec`
  candidate ("Pure-unit tests with several scenarios per behavior →
  `BehaviorSpec` (`given` / `when` / `then`), which maps cleanly onto the
  current `given*` / `when*` / `then*` helper naming and BDDMockito flow").
- The existing file's helper method naming (`givenAnAccountWithId`,
  `givenWithdrawalWillFail`, `givenDepositWillSucceed`,
  `givenWithdrawalWillSucceed`, `thenAccountsHaveBeenUpdated`) is *literally*
  BDD-shaped. `BehaviorSpec` is the cleanest landing zone — each `@Test`
  becomes one `given { when { then { ... } } }` triple, and the existing
  `given*` helpers can be called unchanged inside the `given` container or
  the `when` block.
- Consistency with Sprint 01 keeps the test cluster uniform for the next
  reader. The spec allows `DescribeSpec` / `FunSpec` for other clusters but
  steers application-service tests to `BehaviorSpec` — no intentional
  deviation here.

The two existing `@Test` methods become two leaf tests, each wrapped in a
descriptive `given` / `when` / `then` triple:

| Today (`@Test` method)                                                  | Tomorrow (Kotest containers + leaf)                                                                                                       |
|-------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `givenWithdrawalFails_thenOnlySourceAccountIsLockedAndReleased`         | `` given("the source account's withdrawal will fail") { `when`("sending money") { then("only the source account is locked and released") { … } } } `` |
| `transactionSucceeds`                                                   | `` given("a source and target account both ready to transact") { `when`("sending money") { then("the transaction succeeds and both accounts are locked, mutated, released, and persisted") { … } } } `` |

Total: 2 leaf tests, identical to today. `given(...)` and `` `when`(...) `` are
pure containers; only `then(...)` blocks are counted by Kotest as leaves.

(Note: in Kotest's `BehaviorSpec`, the `when` builder is a reserved Kotlin
keyword and must be back-ticked. The Generator will use back-ticks; the
Evaluator should expect `` `when`("…") { … } `` in the diff. Same convention
as Sprint 01.)

### Inventory of Mockito API used today

Grepped from the live file. Every API listed below must have a MockK
counterpart in the migrated file.

| Mockito API in `SendMoneyServiceTest.kt` today                                                                  | Where it appears                                                                                                                  |
|------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `Mockito.mock(LoadAccountPort::class.java)`                                                                      | property `loadAccountPort` (line 22-23)                                                                                            |
| `Mockito.mock(AccountLock::class.java)`                                                                          | property `accountLock` (line 25-26)                                                                                                |
| `Mockito.mock(UpdateAccountStatePort::class.java)`                                                               | property `updateAccountStatePort` (line 28-29)                                                                                     |
| `Mockito.mock(Account::class.java)` (twice — once for the global sentinel, again inside `givenAnAccountWithId`)  | properties / helper `accountSentinel` (line 41), `givenAnAccountWithId` (line 157)                                                  |
| `BDDMockito.given(x).willReturn(y)`                                                                              | `givenAnAccountWithId` (`account.id` → `id`, line 158-159 and `loadAccountPort.loadAccount` line 168-169), `givenDepositWillSucceed` (line 134-135), `givenWithdrawalWillFail` (line 141-142), `givenWithdrawalWillSucceed` (line 148-149) |
| `BDDMockito.then(x).should().foo(...)`                                                                           | `thenAccountsHaveBeenUpdated` (line 105) and both `@Test` bodies (lines 64-66, 92-98)                                              |
| `BDDMockito.then(x).should(times(0)).foo(...)`                                                                   | `givenWithdrawalFails_…` (line 66)                                                                                                  |
| `BDDMockito.then(x).should(times(N)).foo(...)`                                                                   | `thenAccountsHaveBeenUpdated` (line 105 — `times(accountIds.size)`)                                                                |
| `org.mockito.Mockito.times`                                                                                       | imported at line 16, used as `times(0)` and `times(accountIds.size)`                                                                |
| `ArgumentCaptor.forClass(Account::class.java)` + `.capture()` + `.allValues`                                     | `thenAccountsHaveBeenUpdated` (lines 104-111)                                                                                       |
| `Mockito.eq(value)`                                                                                              | private helper `eq<T>(value: T): T = Mockito.eq(value) ?: value` (line 180), invoked on every `should().foo(eq(...))` site         |
| `Mockito.eq(value)` direct (no helper wrap)                                                                       | `Mockito.eq(accountIdValue)` inside `givenAnAccountWithId` (line 166)                                                               |
| `Mockito.any(Money::class.java)` / `Mockito.any(AccountId::class.java)` / `Mockito.any(LocalDateTime::class.java)` | `givenDepositWillSucceed` (line 132-133), `givenWithdrawalWillFail` (line 139-140), `givenWithdrawalWillSucceed` (line 146-147), `givenAnAccountWithId` (line 167) |
| Hand-rolled `capture(captor)` wrapping `captor.capture()` + returning `accountSentinel`                          | private helper at line 123-126                                                                                                       |
| Hand-rolled `eq(value)` wrapping `Mockito.eq(value) ?: value`                                                    | private helper at line 180                                                                                                           |
| `accountSentinel: Account = Mockito.mock(Account::class.java)` (non-null sentinel for matcher-queued calls)      | property at line 41                                                                                                                  |

Imports to delete:

- `org.assertj.core.api.Assertions.assertThat`
- `org.junit.jupiter.api.Test`
- `org.mockito.ArgumentCaptor`
- `org.mockito.BDDMockito.given`
- `org.mockito.BDDMockito.then`
- `org.mockito.Mockito`
- `org.mockito.Mockito.times`
- `java.util.stream.Collectors` (now unnecessary — Kotlin's `.map` returns a
  `List` directly)

Imports likely **retained**:

- `io.reflectoring.buckpal.account.application.port.in.SendMoneyCommand` (back-ticked `` `in` `` package segment stays)
- `io.reflectoring.buckpal.account.application.port.out.AccountLock`
- `io.reflectoring.buckpal.account.application.port.out.LoadAccountPort`
- `io.reflectoring.buckpal.account.application.port.out.UpdateAccountStatePort`
- `io.reflectoring.buckpal.account.domain.Account`
- `io.reflectoring.buckpal.account.domain.Account.AccountId`
- `io.reflectoring.buckpal.account.domain.Money`
- `java.time.LocalDateTime`

Imports to add:

- `io.kotest.core.spec.style.BehaviorSpec`
- `io.kotest.matchers.shouldBe` (for `success shouldBe true/false`)
- `io.mockk.every`
- `io.mockk.mockk`
- `io.mockk.verify`
- `io.mockk.slot` (and `io.mockk.mutableListOf`-style capture is not needed —
  see "Capture strategy" below)
- `io.mockk.Runs` and `io.mockk.just` **iff** `verify { … }` on a `Unit`-returning
  method (`AccountLock.lockAccount` / `releaseAccount`,
  `UpdateAccountStatePort.updateActivities`) requires a prior stub. MockK does
  **not** require a `Unit` stub when the call is invoked on a `relaxed = true`
  mock, but on a strict mock the call would throw. See "Relaxation strategy"
  below — the Generator may end up not needing `Runs`/`just` at all.

### Mockito → MockK 1:1 mapping table

This is the canonical mapping the Generator will apply line-by-line. Every
row corresponds to at least one occurrence in the current file.

| Mockito / BDDMockito form (today)                                                                                     | MockK form (Sprint 02)                                                                                                       |
|------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `Mockito.mock(LoadAccountPort::class.java)`                                                                            | `mockk<LoadAccountPort>()`                                                                                                   |
| `Mockito.mock(AccountLock::class.java)`                                                                                | `mockk<AccountLock>(relaxUnitFun = true)` — `lockAccount`/`releaseAccount` return `Unit`; relaxing only `Unit` funs avoids over-relaxation while removing the need to `just Runs` every call site |
| `Mockito.mock(UpdateAccountStatePort::class.java)`                                                                     | `mockk<UpdateAccountStatePort>(relaxUnitFun = true)` — `updateActivities` returns `Unit`; same rationale                      |
| `Mockito.mock(Account::class.java)` (the per-test account)                                                              | `mockk<Account>()` — `withdraw` / `deposit` return `Boolean` and **must** be stubbed; `id` returns `AccountId?` and is also stubbed explicitly. No relaxation needed because every interaction is stubbed |
| `Mockito.mock(Account::class.java)` (the `accountSentinel` field)                                                       | **Deleted.** MockK doesn't need a non-null placeholder to satisfy Kotlin's null-safety inside matcher-queued calls — MockK matchers (`any()`, `eq(...)`) are direct expression arguments and return the proper type |
| `given(account.id).willReturn(id)`                                                                                     | `every { account.id } returns id`                                                                                            |
| `given(account.deposit(any(), any())).willReturn(true)`                                                                | `every { account.deposit(any(), any()) } returns true`                                                                       |
| `given(account.withdraw(any(), any())).willReturn(true/false)`                                                         | `every { account.withdraw(any(), any()) } returns true` / `… returns false`                                                  |
| `given(loadAccountPort.loadAccount(eq(id), any())).willReturn(account)`                                                | `every { loadAccountPort.loadAccount(id, any()) } returns account` — `id` is passed by value (MockK compares with `equals`), `any()` matches the `LocalDateTime` baseline |
| `then(mock).should().foo(eq(x))`                                                                                       | `verify { mock.foo(x) }` — argument equality is the default; explicit `eq(x)` is unnecessary in MockK                          |
| `then(mock).should(times(0)).foo(eq(x))`                                                                               | `verify(exactly = 0) { mock.foo(x) }`                                                                                         |
| `then(mock).should(times(N)).foo(capture(captor))`                                                                     | `verify(exactly = N) { mock.foo(capture(slot)) }` — or `verify(exactly = N) { mock.foo(capture(list)) }` if multiple values are needed (see Capture strategy) |
| `ArgumentCaptor.forClass(Account::class.java)` + `captor.allValues`                                                    | `mutableListOf<Account>()` + `capture(list)` inside `verify`, then read `list` directly                                       |
| `Mockito.times(N)`                                                                                                      | `exactly = N` argument to `verify`                                                                                            |
| Hand-rolled `eq<T>(value: T): T = Mockito.eq(value) ?: value`                                                          | **Deleted.** MockK's argument matching is `equals`-based by default; just pass the value (`mock.foo(value)`) or `any()` for "don't care" |
| Hand-rolled `capture(captor: ArgumentCaptor<Account>): Account` returning `accountSentinel`                            | **Deleted.** MockK's `capture(slot)` returns the correct type natively                                                         |

#### Argument matchers, ordering, capturing

- **Matchers used today**: only `eq(value)` (effective default in MockK — drop
  the matcher and pass the value) and `any(Class)` (MockK: `any()` — no class
  argument, inferred from the call site). No `argThat`, `intThat`, `same`, or
  `isA` are used today, so no further matcher translation is needed.
- **Ordering**: today's `then(mock).should()` calls are *unordered* — Mockito's
  `should()` without `inOrder` does not assert call order. MockK's plain
  `verify { ... }` is also unordered. **No `verifyOrder` / `verifySequence`
  will be introduced** — that would tighten the contract relative to the
  original test.
- **Times(0)**: maps to `verify(exactly = 0) { ... }`. Used once today on
  `accountLock.lockAccount(targetAccountId)` in the withdrawal-failure path.
- **Times(N)**: maps to `verify(exactly = N) { ... }`. Used once today on
  `updateAccountStatePort.updateActivities(...)` with `N = accountIds.size`
  (i.e. 2 in the success path). The migrated test asserts the same `exactly = 2`.
- **Capturing**: today uses `ArgumentCaptor<Account>` to collect both
  `updateActivities(account)` calls and then asserts each captured account's
  `id` is in the expected set. The MockK form is:

  ```kotlin
  val captured = mutableListOf<Account>()
  verify(exactly = 2) {
      updateAccountStatePort.updateActivities(capture(captured))
  }
  captured.map { it.id } shouldContainAll listOf(sourceAccountId, targetAccountId)
  ```

  `mutableListOf<Account>()` + `capture(list)` is the spec-blessed pattern for
  "multiple captures". A single `slot<Account>()` would only retain the
  *last* invocation's argument, so the list form is required here.
  `shouldContainAll` is order-insensitive, matching the original
  `assertThat(updatedAccountIds).contains(accountId)` loop semantics.

#### Why `eq(...)` disappears entirely

In MockK, when you pass a value (not a matcher function) inside an `every { }`
or `verify { }` block, the call is recorded with that value and matched by
`equals`. There is no separate matcher queue, no thread-local matcher state,
and no `null` return value to wrap with `?: value`. The hand-rolled
`eq<T>(value: T): T = Mockito.eq(value) ?: value` helper exists today *purely*
to paper over Mockito-on-Kotlin's null-matcher problem. MockK is
Kotlin-native and has no such problem, so the helper is deleted.

#### Why `accountSentinel` disappears entirely

The `accountSentinel: Account = Mockito.mock(Account::class.java)` field exists
today as a non-null placeholder returned by the `capture(captor)` helper —
because Mockito's `ArgumentCaptor.capture()` returns `null` at runtime
(matcher-queue side effect), which Kotlin's strict null-safety rejects on
`updateActivities(account: Account)`. MockK's `capture(slot)` /
`capture(list)` returns the correct non-null type directly (the function
signature is `fun <T : Any> MockKMatcherScope.capture(lst: MutableList<T>): T`),
so the sentinel is unnecessary. Removing it is required by the spec
("hand-rolled `eq` / `capture` / `accountSentinel` null-safety wrappers are
deleted").

### Relaxation strategy

MockK has three mock-creation modes relevant here:

- `mockk<T>()` — strict; every interaction must be stubbed or it throws.
- `mockk<T>(relaxed = true)` — every interaction returns a relaxed default
  (`null` for nullable, `0` for numbers, a child mock for reference types,
  empty collections, etc.). Over-relaxation can hide missing stubs.
- `mockk<T>(relaxUnitFun = true)` — strict for all return types **except**
  `Unit`-returning methods, which become no-ops.

For this test, the precise mocking surface is:

- `loadAccountPort.loadAccount(...)` returns `Account` — non-null, must be
  stubbed. → `mockk<LoadAccountPort>()` (strict).
- `accountLock.lockAccount(...)` / `releaseAccount(...)` return `Unit`. We
  never want to stub them; we only `verify`. → `mockk<AccountLock>(relaxUnitFun = true)`.
- `updateAccountStatePort.updateActivities(...)` returns `Unit`. Same as
  above. → `mockk<UpdateAccountStatePort>(relaxUnitFun = true)`.
- `Account.id` returns `AccountId?` — stubbed explicitly via `every`.
- `Account.withdraw(...)` / `deposit(...)` return `Boolean` — stubbed
  explicitly via `every`. → `mockk<Account>()` (strict). Any unexpected
  interaction (e.g., the production code calling some other method on
  `Account` we forgot to stub) becomes a test failure, which is exactly the
  behavior today.

**No `relaxed = true` mocks anywhere** — that would weaken the test. The
choice is `mockk<T>()` (strict) for everything that needs explicit return
stubs, and `mockk<T>(relaxUnitFun = true)` for the two `Unit`-only
collaborators.

### `Account.id` nullable boundary

Production `Account.id` is `AccountId?` (nullable). Today's test uses
`sourceAccount.id!!` in two places (lines 80-81 and 89-90) to unwrap. In the
migrated test:

- The `givenAnAccountWithId(id)` helper stubs `every { account.id } returns id`
  where `id: AccountId` is non-null at the call site. The stubbed return is
  `AccountId` (non-null) at the use site even though the property type is
  `AccountId?`, because the test holds the source `id` value in a local `val`
  *before* creating the mock and uses *that local* in the assertion — not
  `account.id`. This is exactly the pattern Sprint 02's "TODOs deferred to
  later sprints" note in Sprint 01's territory anticipated; the goal here is
  to eliminate `!!` from this test file.
- For the `transactionSucceeds` scenario, the two `givenSourceAccount()` /
  `givenTargetAccount()` helpers each take a hard-coded `AccountId(41L)` /
  `AccountId(42L)`; the test binds these to local `val sourceAccountId` /
  `val targetAccountId` *before* calling the helpers, then asserts against
  those locals. No `account.id!!` remains.
- The `command` is built with the same local `AccountId` values:

  ```kotlin
  val command = SendMoneyCommand(sourceAccountId, targetAccountId, money)
  ```

No `!!` in the migrated file. No `lateinit var`. No nullable shadowing.

### Capture strategy — `slot` vs `mutableListOf`

The spec allows either `slot<Account>()` (single-value) or
`mutableListOf<Account>()` + `capture(list)` (multi-value). The
`thenAccountsHaveBeenUpdated` helper today captures **two** calls (one per
account in the success scenario; zero in the failure scenario). A `slot`
would only retain the last value, losing the source-account assertion. The
migrated test will use **`mutableListOf<Account>()`** for that helper so both
captures are preserved and `containsAll(...)` semantics map 1-to-1 to
`shouldContainAll`. For the failure scenario where the spec asserts
`verify(exactly = 0) { ... }`, no captor is needed at all.

### Assertion mapping (AssertJ → Kotest)

The two AssertJ calls in the file map cleanly:

| Today                              | Tomorrow                              |
|------------------------------------|---------------------------------------|
| `assertThat(success).isFalse()`    | `success shouldBe false`              |
| `assertThat(success).isTrue()`     | `success shouldBe true`               |
| `assertThat(list).contains(x)` loop | `list shouldContainAll listOf(x, y)` (single matcher for the whole expected set) |

Imports added on top of `io.kotest.matchers.shouldBe`:

- `io.kotest.matchers.collections.shouldContainAll`

### Conversion targets

| `.kt` file in scope                  | Class type                                          |
|--------------------------------------|-----------------------------------------------------|
| `SendMoneyServiceTest.kt`            | `class SendMoneyServiceTest : BehaviorSpec({ ... })` |

The class:

- Stays in package
  `io.reflectoring.buckpal.account.application.service`.
- Keeps its class name (`SendMoneyServiceTest`) so test filtering
  (`--tests "io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest"`)
  continues to work unchanged.
- Drops the no-arg constructor body entirely; all logic moves into the
  `BehaviorSpec` lambda.
- Drops the `@Test` annotation and the `org.junit.jupiter.api.Test` import.
- Drops the `org.assertj.core.api.Assertions.assertThat` import.
- Drops every `org.mockito.*` import.
- The existing private helpers (`givenAnAccountWithId`, `givenWithdrawalWillFail`,
  `givenWithdrawalWillSucceed`, `givenDepositWillSucceed`, `givenSourceAccount`,
  `givenTargetAccount`, `moneyTransferProperties`, `thenAccountsHaveBeenUpdated`)
  either move to local `fun` declarations inside the `BehaviorSpec` lambda or
  become inline. The `eq(...)` and `capture(...)` helpers are **deleted
  outright**, along with the `accountSentinel` field.

### Idiomatic Kotlin commitments

1. **`BehaviorSpec` lambda style** — `class SendMoneyServiceTest :
   BehaviorSpec({ ... })`; no `init { }` block, no override of
   `extensions()` / `listeners()` (no Spring needed; this is a pure unit
   test).
2. **`shouldBe` infix form** — All assertions use infix
   `actual shouldBe expected`, never method-call form. Same convention as
   Sprint 01.
3. **`verify { ... }` infix-style block** — Same Kotest/MockK convention:
   `verify { mock.foo(x) }` and `verify(exactly = N) { mock.foo(x) }`. No
   `verifyAll`, no `verifyOrder`, no `verifySequence` — the original test
   does not assert exhaustiveness or ordering, and tightening that would be
   a silent behavior change.
4. **No `!!` non-null assertions** — eliminated via local `val
   sourceAccountId = AccountId(41L)` / `val targetAccountId = AccountId(42L)`
   bindings before mock creation. The migrated file will contain zero `!!`.
5. **No `lateinit var`** — every mock and every test scaffold (`sendMoneyService`,
   `loadAccountPort`, etc.) lives as `val` inside the `BehaviorSpec` lambda.
   Kotest re-runs the spec lambda per leaf by default, so each leaf gets a
   fresh mock graph — no shared mutable state, no `clearAllMocks()`
   gymnastics needed.
6. **No `Mockito.` prefix anywhere; no `org.mockito` imports** — the spec's
   "All Mockito-related imports … are gone" criterion is taken literally.
7. **No `@Test` annotation; no `org.junit.jupiter.api` imports** — the spec's
   "No `@Test` annotation remains" criterion is taken literally.
8. **No `org.assertj.core.api.*` imports** — same migration policy as
   Sprint 01: AssertJ is dead in migrated files.
9. **Backticked `when`** — Acknowledged Kotlin syntactic cost of using
   `BehaviorSpec`. The Generator will not switch to `DescribeSpec` /
   `FunSpec` to avoid the back-ticks; consistency with Sprint 01 and the
   spec's spec-style guidance outweighs the syntactic noise.

### Risk handling specific to this sprint

- **Risk: MockK on final Kotlin classes (`Account`)**. From the spec's risk
  register #4: "MockK can mock final classes only with `mockkClass(...)` or
  with the `mockk-agent` … but `mockk<T>()` on a final class is supported
  out of the box from MockK 1.13 onward (uses `mockk-agent-jvm`)." The
  production `Account` class is declared `open class Account` (verified by
  grepping `src/main/kotlin/.../Account.kt` line 14: `open val id: AccountId?`
  and line 31: `open fun withdraw(...)` and line 57: `open fun deposit(...)`),
  so even classic `mockk<Account>()` works without the agent. **No
  mitigation needed; documenting for the Evaluator.**
- **Risk: `relaxUnitFun = true` masking a real bug.** Relaxing only `Unit`
  funs means `lockAccount` / `releaseAccount` / `updateActivities` become
  silent no-ops by default, but their return type is `Unit` in production
  too — there is no observable behavior we are masking. Every call is then
  re-verified via `verify { … }`. Strict-mock semantics on `Account.id` /
  `withdraw` / `deposit` and on `loadAccountPort.loadAccount` are preserved.
- **Risk: `clearMocks` / per-leaf isolation.** Kotest re-runs the
  `BehaviorSpec` lambda once per leaf by default. The two leaves get
  independent mock instances. No `clearMocks` / `MockKAnnotations.init` /
  `unmockkAll` needed. (If Kotest's default lifecycle ever changes, the
  fallback is a `beforeTest { clearAllMocks() }` block — but this is **not**
  added preemptively.)
- **Risk: matcher confusion when the same call appears in `every` and
  `verify`.** MockK's `every { mock.foo(any()) } returns x` and
  `verify { mock.foo(value) }` are both fine — the stub uses `any()` for
  loose match on the call setup, the verify uses an exact value because
  that's what the production code actually called. No conflict.
- **Risk: argument-equality on `Money` / `AccountId`.** Both are Kotlin
  `data class`es in production (`Money` wraps `BigDecimal`; `AccountId`
  wraps `Long`), so `equals` is value-based. MockK's default equality
  matching is `equals`-based, so `verify { mock.foo(Money.of(500)) }`
  matches a real call `mock.foo(Money.of(500))` even though they are
  distinct instances. No `eq(...)` needed. Same `BigDecimal`-scale caveat
  as Sprint 01 — today's test uses `Money.of(long)` on both sides so scales
  match by construction.
- **Risk: full-suite cross-talk.** ArchUnit (`DependencyRuleTests`) and the
  Spring slices (controller, persistence, system) are not in scope but must
  still pass after the migration. ArchUnit scans **compiled production
  classes**, not test classes, so reshaping the test file cannot affect it.
  The Spring slices live in separate classes and import their own deps; no
  shared state with `SendMoneyServiceTest`. Verified by running the full
  suite in the self-check.
- **Risk: TEST-*.xml leaf count under Kotest.** Today the JUnit engine
  emits `TEST-io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest.xml`
  with `tests="2"`. Under Kotest's JUnit Platform engine, the same path is
  used (one XML per FQCN, regardless of engine). Each `then(...)` is one
  leaf, so the file will report `tests="2"`. Acceptance check #15 below
  formalizes this.

## Acceptance checks (mechanically verifiable by Evaluator)

Each box is one shell command or one observable artifact. The Evaluator runs
these as the source of truth; the Generator runs the same set in self-check
before handoff.

### Behavioral correctness

- [ ] `./gradlew test --tests "io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest"` → exits 0.
- [ ] `./gradlew test` (full suite) → exits 0; aggregate leaf-test count is unchanged versus the pre-sprint baseline. (Sprint 01 set aggregate count to 16; Sprint 02 expects the same 16 because `SendMoneyServiceTest` had 2 leaves before and has 2 leaves after.)
- [ ] Parsed `build/test-results/test/TEST-io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest.xml` reports `tests="2"` and `failures="0"` and `errors="0"` and `skipped="0"`.

### Architectural integrity

- [ ] `./gradlew check` → exits 0 (full test task + any extra verification tasks; `DependencyRuleTests` in particular must still pass).
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → exits 0 (explicit ArchUnit smoke check, equivalent to the previous line).

### Code quality — Mockito and JUnit residue is gone

- [ ] `grep -nE "^import org\\.mockito" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → no matches (exit 1).
- [ ] `grep -n "Mockito\\." src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → no matches (exit 1). This catches any stray `Mockito.mock(...)`, `Mockito.eq(...)`, `Mockito.any(...)`, or `Mockito.times(...)` reference.
- [ ] `grep -nE "^import org\\.assertj\\.core" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → no matches (exit 1).
- [ ] `grep -nE "^import org\\.junit\\.jupiter" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → no matches (exit 1).
- [ ] `grep -n "@Test" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → no matches (exit 1).
- [ ] `grep -nE "(ArgumentCaptor|accountSentinel|BDDMockito)" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → no matches (exit 1). This is the explicit "spec-named helpers were deleted" check.

### Code quality — MockK and Kotest are present

- [ ] `grep -nE "^class SendMoneyServiceTest\\s*:\\s*BehaviorSpec" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → matches exactly one line.
- [ ] `grep -nE "^import io\\.kotest\\.core\\.spec\\.style\\.BehaviorSpec" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → matches at least one line.
- [ ] `grep -nE "^import io\\.kotest\\.matchers\\.shouldBe" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → matches at least one line.
- [ ] `grep -nE "^import io\\.mockk\\.(every|mockk|verify)" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → matches at least three lines (`every`, `mockk`, `verify` all present).

### Idiomatic Kotlin — no banned patterns

- [ ] `grep -nE "(\\blateinit\\b|!!)" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → no matches (exit 1). The regex uses `\b` for `lateinit` and a literal `!!` pair (so `!=` is not flagged).
- [ ] `grep -nE "\\.shouldBe\\(" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → no matches (exit 1). Only infix `actual shouldBe expected` is allowed.
- [ ] `grep -nE "\\.verify\\(" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → no matches (exit 1). `verify` is used in block form `verify { … }`, never method-call form.

### Scope — only one file changed

- [ ] `git diff --name-only HEAD -- src/` → exactly the single line `src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`. No other source file is modified.
- [ ] `git diff --name-only HEAD -- src/main/` → empty (no production-code edits — protects the non-negotiable invariant).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/common/` → empty (fixtures untouched).
- [ ] `git diff --name-only HEAD -- build.gradle` → empty (build-script untouched; Sprint 00/07 territory).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/account/domain/` → empty (Sprint 01 files untouched).
- [ ] `git diff --name-only HEAD -- src/test/kotlin/io/reflectoring/buckpal/archunit/` → empty (ArchUnit infrastructure untouched).

## Verification commands the Generator will run before handoff

In order, from the worktree root, with
`JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home`:

1. `./gradlew --no-daemon compileKotlin compileTestKotlin` → expect
   `BUILD SUCCESSFUL`. (Sanity: no production code edits, so `compileKotlin`
   is essentially a no-op; `compileTestKotlin` proves the rewrite parses.)
2. `./gradlew --no-daemon test --tests "io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest"`
   → expect `BUILD SUCCESSFUL` and the TEST-*.xml file above with
   `tests="2"`, `failures="0"`, `errors="0"`, `skipped="0"`.
3. `./gradlew --no-daemon test` → expect `BUILD SUCCESSFUL`, aggregate 16
   leaf tests (same as Sprint 01 baseline), 0 failures.
4. `./gradlew --no-daemon check` → expect `BUILD SUCCESSFUL` (ArchUnit
   `DependencyRuleTests` is exercised here and must still pass).
5. `git diff --name-only HEAD` → expect exactly one path:
   `src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`.
6. Negative greps (all expect "no output"):
   - `grep -nE "^import org\\.mockito" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
   - `grep -n "Mockito\\." src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
   - `grep -nE "^import org\\.assertj\\.core" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
   - `grep -nE "^import org\\.junit\\.jupiter" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
   - `grep -n "@Test" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
   - `grep -nE "(ArgumentCaptor|accountSentinel|BDDMockito)" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
   - `grep -nE "(\\blateinit\\b|!!)" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
   - `grep -nE "\\.shouldBe\\(" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
   - `grep -nE "\\.verify\\(" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`

If any step fails, the Generator will diagnose and rerun. No red handoff.
